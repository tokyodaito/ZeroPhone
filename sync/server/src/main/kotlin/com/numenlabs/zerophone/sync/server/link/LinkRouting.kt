package com.numenlabs.zerophone.sync.server.link

import com.numenlabs.zerophone.core.model.LinkPayload
import com.numenlabs.zerophone.sync.server.auth.DEVICE_AUTH
import com.numenlabs.zerophone.sync.server.auth.DevicePrincipal
import com.numenlabs.zerophone.sync.server.auth.SyncServerJson
import com.numenlabs.zerophone.sync.server.state.WsBroadcaster
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.post
import io.ktor.server.routing.routing

/** REST surface of send-to-PC (the push half lives on the WebSocket). */
const val LINK: String = "/api/v1/link"

/**
 * Link delivery surface of the server, behind device-token auth:
 *
 *  `POST /api/v1/link { LinkPayload }` → 202 Accepted
 *
 * The link is stamped with the authenticated sender, persisted in the
 * [PendingLinkStore] (store-and-forward: a desktop that is offline
 * picks it up as `link.received` right after the `state.snapshot` of
 * its next WebSocket connect) and immediately flushed to every live
 * session. Malformed bodies and blank id/url answer 400.
 */
fun Application.installLinkDelivery(pending: PendingLinkStore, broadcaster: WsBroadcaster) {
    routing {
        authenticate(DEVICE_AUTH) {
            post(LINK) {
                val device = call.principal<DevicePrincipal>()
                    ?: return@post call.respondStatus(HttpStatusCode.Unauthorized)
                val link = decodeLinkOrNull(call.receiveText())
                    ?: return@post call.respondStatus(HttpStatusCode.BadRequest)
                acceptLink(pending, broadcaster, link.copy(sourceDeviceId = device.deviceId))
                call.respondStatus(HttpStatusCode.Accepted)
            }
        }
    }
}

/**
 * Single acceptance path of every link (the REST route above and the
 * WebSocket uplink alike): queue it durably, then try to hand it to
 * the live sessions.
 */
internal suspend fun acceptLink(
    pending: PendingLinkStore,
    broadcaster: WsBroadcaster,
    link: LinkPayload,
) {
    pending.enqueue(link)
    pending.flushTo(broadcaster)
}

/** Decodes a link body, rejecting anything with a blank id or url. */
internal fun decodeLinkOrNull(body: String): LinkPayload? =
    runCatching { SyncServerJson.decodeFromString(LinkPayload.serializer(), body) }
        .getOrNull()
        ?.takeIf { it.id.isNotBlank() && it.url.isNotBlank() }

private suspend fun ApplicationCall.respondStatus(status: HttpStatusCode) {
    respondText(text = "", status = status)
}
