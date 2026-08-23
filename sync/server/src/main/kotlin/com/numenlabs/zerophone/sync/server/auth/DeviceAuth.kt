package com.numenlabs.zerophone.sync.server.auth

import com.numenlabs.zerophone.core.model.DeviceCredentials
import com.numenlabs.zerophone.core.model.SyncEndpoints
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.authentication
import io.ktor.server.auth.bearer
import io.ktor.server.auth.principal
import io.ktor.server.response.respondText
import io.ktor.server.routing.delete
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json

/** Server-side wire codec: same conventions as the shared contracts. */
internal val SyncServerJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/**
 * Device-token authentication of the sync server.
 *
 * Transport: the token always travels in the `Authorization: Bearer`
 * header — on REST calls and on the WebSocket upgrade request alike.
 * It never appears in a query string or URL.
 *
 * Validation: the bearer token is hashed (SHA-256) and looked up in the
 * [DeviceTokenStore] under its mutex; a [DevicePrincipal] is issued only
 * for a stored, non-revoked hash. Pairing endpoints stay outside this
 * provider; every other `/api` resource nests its routes inside
 * `authenticate(DEVICE_AUTH)` — including `GET /api/v1/ws`, whose HTTP
 * upgrade request is checked before the handshake completes.
 */
fun Application.installDeviceTokenAuth(store: DeviceTokenStore) {
    authentication {
        bearer(DEVICE_AUTH) {
            realm = "zerophone-sync"
            authenticate { credential ->
                store.authenticate(credential.token)?.let(::DevicePrincipal)
            }
        }
    }
    routing {
        authenticate(DEVICE_AUTH) {
            post(SyncEndpoints.AUTH_ROTATE) {
                val device = call.principal<DevicePrincipal>()
                    ?: return@post call.respondUnauthorized()
                val issued = store.rotate(device.deviceId)
                    ?: return@post call.respondUnauthorized()
                call.respondText(
                    SyncServerJson.encodeToString(
                        DeviceCredentials.serializer(),
                        DeviceCredentials(deviceId = issued.deviceId, token = issued.token),
                    ),
                    ContentType.Application.Json,
                )
            }
            delete(SyncEndpoints.AUTH_TOKEN) {
                val device = call.principal<DevicePrincipal>()
                    ?: return@delete call.respondUnauthorized()
                val revoked = store.revoke(device.deviceId)
                call.respondText(
                    text = "",
                    status = if (revoked) HttpStatusCode.NoContent else HttpStatusCode.Unauthorized,
                )
            }
        }
    }
}

private suspend fun ApplicationCall.respondUnauthorized() {
    respondText(text = "", status = HttpStatusCode.Unauthorized)
}
