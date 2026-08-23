package com.numenlabs.zerophone.sync.server.state

import com.numenlabs.zerophone.core.model.LinkPayload
import com.numenlabs.zerophone.core.model.StateEnvelope
import com.numenlabs.zerophone.core.model.StateUpdateRequest
import com.numenlabs.zerophone.core.model.SyncEndpoints
import com.numenlabs.zerophone.core.model.SyncPushMessage
import com.numenlabs.zerophone.sync.server.auth.DEVICE_AUTH
import com.numenlabs.zerophone.sync.server.auth.DevicePrincipal
import com.numenlabs.zerophone.sync.server.auth.SyncServerJson
import com.numenlabs.zerophone.sync.server.link.PendingLinkStore
import com.numenlabs.zerophone.sync.server.link.acceptLink
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.application.pluginOrNull
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText

/**
 * State sync surface of the server, all of it behind device-token auth:
 *
 *  - `GET /api/v1/state` — the full document and its revision. A client
 *    that is behind catches up with exactly this one request; passing
 *    `?baseRevision=N` turns the call into a cheap freshness check
 *    (304 when already current, 400 when N is from the future).
 *  - `PUT /api/v1/state` — conditional write of the whole document.
 *    `baseRevision == current` → 200 with the new revision;
 *    `baseRevision < current` → 409 carrying the winning revision and
 *    state, so the loser rebases and retries instead of losing its
 *    update silently; `baseRevision > current` → 400 protocol error.
 *  - `GET /api/v1/ws` — push channel. On connect the server immediately
 *    sends the current revision (`state.snapshot`); every accepted write
 *    broadcasts `state.updated` with the new revision, the winning
 *    device and the writer's optional hint. The socket is also the
 *    send-to-PC uplink: a text frame carrying a [LinkPayload] from the
 *    phone is validated and broadcast to every connected device as
 *    `link.received` — there is no separate REST surface for links.
 *
 * When [pendingLinks] is provided, links are routed through the
 * store-and-forward queue instead of being relayed fire-and-forget:
 * the queue is also flashed to a socket right after its
 * `state.snapshot`, so a desktop that was offline at send time
 * receives everything the server still holds for it.
 */
fun Application.installStateSync(
    store: StateStore,
    broadcaster: WsBroadcaster,
    pendingLinks: PendingLinkStore? = null,
) {
    if (pluginOrNull(WebSockets) == null) install(WebSockets)
    routing {
        authenticate(DEVICE_AUTH) {
            get(SyncEndpoints.STATE) {
                call.principal<DevicePrincipal>() ?: return@get call.respondStatus(
                    HttpStatusCode.Unauthorized,
                )
                val envelope = store.current()
                val baseParam = call.request.queryParameters["baseRevision"]
                if (baseParam == null) {
                    call.respondEnvelope(envelope, HttpStatusCode.OK)
                    return@get
                }
                val base = baseParam.toLongOrNull()
                when {
                    base == null -> call.respondStatus(HttpStatusCode.BadRequest)
                    base == envelope.revision -> call.respondStatus(HttpStatusCode.NotModified)
                    base > envelope.revision -> call.respondStatus(HttpStatusCode.BadRequest)
                    else -> call.respondEnvelope(envelope, HttpStatusCode.OK)
                }
            }

            put(SyncEndpoints.STATE) {
                val device = call.principal<DevicePrincipal>()
                    ?: return@put call.respondStatus(HttpStatusCode.Unauthorized)
                val request = runCatching {
                    SyncServerJson.decodeFromString(StateUpdateRequest.serializer(), call.receiveText())
                }.getOrNull()
                    ?: return@put call.respondStatus(HttpStatusCode.BadRequest)

                val commit = store.commit(
                    baseRevision = request.baseRevision,
                    newState = request.state,
                    onAccepted = { revision ->
                        broadcaster.broadcast(
                            SyncPushMessage.StateUpdated(
                                revision = revision,
                                actorDeviceId = device.deviceId,
                                hint = request.hint,
                            )
                        )
                    },
                )
                when (commit) {
                    is StateCommit.Accepted ->
                        call.respondEnvelope(commit.envelope, HttpStatusCode.OK)

                    is StateCommit.Conflict ->
                        call.respondEnvelope(commit.envelope, HttpStatusCode.Conflict)

                    StateCommit.BaseAhead -> call.respondStatus(HttpStatusCode.BadRequest)
                }
            }

            webSocket(SyncEndpoints.WEBSOCKET) {
                val device = call.principal<DevicePrincipal>()
                val revision = store.current().revision
                send(
                    Frame.Text(
                        SyncServerJson.encodeToString(
                            SyncPushMessage.serializer(),
                            SyncPushMessage.StateSnapshot(revision),
                        )
                    )
                )
                try {
                    broadcaster.register(outgoing)
                    // Catch-up for links that arrived while nobody was
                    // connected — strictly after the state.snapshot so a
                    // reconnecting desktop sees a well-defined order.
                    pendingLinks?.flushTo(broadcaster)
                    for (frame in incoming) {
                        if (frame is Frame.Close) break
                        val link = relayLinkOrNull(frame, device) ?: continue
                        if (pendingLinks == null) {
                            broadcaster.broadcast(SyncPushMessage.LinkReceived(link))
                        } else {
                            acceptLink(pendingLinks, broadcaster, link)
                        }
                    }
                } finally {
                    broadcaster.unregister(outgoing)
                }
            }
        }
    }
}

/**
 * A text frame on the push socket is the phone's "отправить на ПК"
 * uplink: it must decode to a well-formed [LinkPayload] (non-blank id
 * and url), anything else is silently dropped. The authenticated
 * [deviceId] of the sending device overrides whatever the payload
 * claims about its origin.
 */
private fun relayLinkOrNull(
    frame: Frame,
    device: DevicePrincipal?,
): LinkPayload? {
    val text = frame as? Frame.Text ?: return null
    val link = runCatching {
        SyncServerJson.decodeFromString(LinkPayload.serializer(), text.readText())
    }.getOrNull() ?: return null
    if (link.id.isBlank() || link.url.isBlank()) return null
    return link.copy(sourceDeviceId = device?.deviceId ?: link.sourceDeviceId)
}

private suspend fun ApplicationCall.respondEnvelope(
    envelope: StateEnvelope,
    status: HttpStatusCode,
) {
    respondText(
        SyncServerJson.encodeToString(StateEnvelope.serializer(), envelope),
        ContentType.Application.Json,
        status,
    )
}

private suspend fun ApplicationCall.respondStatus(
    status: HttpStatusCode,
) {
    respondText(text = "", status = status)
}
