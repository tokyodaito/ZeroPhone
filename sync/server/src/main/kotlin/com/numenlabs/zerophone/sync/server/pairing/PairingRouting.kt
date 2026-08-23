package com.numenlabs.zerophone.sync.server.pairing

import com.numenlabs.zerophone.core.model.DeviceCredentials
import com.numenlabs.zerophone.core.model.PairingClaim
import com.numenlabs.zerophone.core.model.SyncEndpoints
import com.numenlabs.zerophone.sync.server.auth.DeviceTokenStore
import com.numenlabs.zerophone.sync.server.auth.SyncServerJson
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.post
import io.ktor.server.routing.routing

/**
 * Pairing surface of the server — the only unauthenticated route:
 *
 *  `POST /api/v1/pairing/claim { code, deviceName, deviceKind }`
 *
 * A valid, unclaimed shortcode is exchanged for freshly issued device
 * credentials: the opaque token exists only in the response (and in the
 * client's token file); the server persists just its hash. Shortcodes
 * are minted by the operator out-of-band (`java -jar server.jar
 * --issue-code`) so no anonymous client can create them. Unknown or
 * expired codes answer 404 (no oracle about which codes existed),
 * consumed codes answer 409.
 */
fun Application.installPairing(pairingStore: PairingStore, tokens: DeviceTokenStore) {
    routing {
        post(SyncEndpoints.PAIRING_CLAIM) {
            val claim = runCatching {
                SyncServerJson.decodeFromString(PairingClaim.serializer(), call.receiveText())
            }.getOrNull()
                ?: return@post call.respondStatus(HttpStatusCode.BadRequest)

            when (val result = pairingStore.claim(claim.code)) {
                PairingClaimResult.UnknownCode ->
                    call.respondStatus(HttpStatusCode.NotFound)

                PairingClaimResult.AlreadyClaimed ->
                    call.respondStatus(HttpStatusCode.Conflict)

                is PairingClaimResult.Claimed -> {
                    val issued = tokens.issue(result.deviceId)
                    call.respondText(
                        SyncServerJson.encodeToString(
                            DeviceCredentials.serializer(),
                            DeviceCredentials(
                                deviceId = issued.deviceId,
                                token = issued.token,
                                deviceName = claim.deviceName,
                                pairedAtMillis = System.currentTimeMillis(),
                            ),
                        ),
                        ContentType.Application.Json,
                        HttpStatusCode.OK,
                    )
                }
            }
        }
    }
}

private suspend fun ApplicationCall.respondStatus(status: HttpStatusCode) {
    respondText(text = "", status = status)
}
