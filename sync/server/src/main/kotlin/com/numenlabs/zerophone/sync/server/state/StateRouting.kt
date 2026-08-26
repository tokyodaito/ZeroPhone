package com.numenlabs.zerophone.sync.server.state

import com.numenlabs.zerophone.core.model.StateEnvelope
import com.numenlabs.zerophone.core.model.StateUpdateRequest
import com.numenlabs.zerophone.core.model.SyncEndpoints
import com.numenlabs.zerophone.core.model.SyncPushMessage
import com.numenlabs.zerophone.sync.server.auth.DEVICE_AUTH
import com.numenlabs.zerophone.sync.server.auth.DevicePrincipal
import com.numenlabs.zerophone.sync.server.auth.SyncServerJson
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
 *    device and the writer's optional hint.
 */
fun Application.installStateSync(
    store: StateStore,
    broadcaster: WsBroadcaster,
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
                    for (frame in incoming) {
                        if (frame is Frame.Close) break
                    }
                } finally {
                    broadcaster.unregister(outgoing)
                }
            }
        }
    }
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
