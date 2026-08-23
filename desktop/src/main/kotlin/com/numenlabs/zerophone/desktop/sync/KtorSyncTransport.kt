package com.numenlabs.zerophone.desktop.sync

import com.numenlabs.zerophone.core.model.DeviceCredentials
import com.numenlabs.zerophone.core.model.PairingClaim
import com.numenlabs.zerophone.core.model.StateEnvelope
import com.numenlabs.zerophone.core.model.StateUpdateRequest
import com.numenlabs.zerophone.core.model.SyncEndpoints
import com.numenlabs.zerophone.core.model.SyncPushMessage
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json

/**
 * [SyncTransport] over one ktor [HttpClient] (CIO, pure JVM):
 *
 *  - Bearer device token on every REST call and the WS upgrade;
 *  - JSON via content negotiation over the shared `:core:model` wire
 *    contracts — no client-side DTO duplication;
 *  - `GET /api/v1/state(?baseRevision=N)` mapped to [PullResult]
 *    (200 / 304 / 401);
 *  - conditional `PUT /api/v1/state` mapped to [PushResult]
 *    (200 / 409-with-envelope / 400 / 401);
 *  - `POST /api/v1/pairing/claim` mapped to [PairingResult];
 *  - WS pushes decoded frame-by-frame; malformed frames are skipped, a
 *    dropped socket completes the flow and the engine reconnects.
 */
class KtorSyncTransport(
    serverUrl: String,
    private val tokenProvider: () -> String?,
    private val client: HttpClient = defaultHttpClient(),
) : SyncTransport {

    private val stateUrl: String = serverUrl.trimEnd('/') + SyncEndpoints.STATE
    private val pairingUrl: String = serverUrl.trimEnd('/') + SyncEndpoints.PAIRING_CLAIM
    private val webSocketUrl: String = wsUrl(serverUrl)

    override suspend fun pull(baseRevision: Long?): PullResult {
        val token = requireToken()
        val response = client.get(stateUrl) {
            header(HttpHeaders.Authorization, "Bearer $token")
            if (baseRevision != null) {
                url.parameters.append(REVISION_QUERY, baseRevision.toString())
            }
        }
        return when (response.status) {
            HttpStatusCode.OK -> PullResult.Envelope(response.body())
            HttpStatusCode.NotModified -> PullResult.NotModified
            HttpStatusCode.Unauthorized -> PullResult.Unauthorized
            else -> error("state pull failed: HTTP ${response.status.value}")
        }
    }

    override suspend fun push(update: StateUpdateRequest): PushResult {
        val token = requireToken()
        val response = client.put(stateUrl) {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(update)
        }
        return when (response.status) {
            HttpStatusCode.OK -> PushResult.Accepted(response.body())
            HttpStatusCode.Conflict -> PushResult.Conflict(response.body())
            HttpStatusCode.BadRequest -> PushResult.BadRequest
            HttpStatusCode.Unauthorized -> PushResult.Unauthorized
            else -> error("state push failed: HTTP ${response.status.value}")
        }
    }

    override suspend fun claim(claim: PairingClaim): PairingResult {
        val response = client.post(pairingUrl) {
            contentType(ContentType.Application.Json)
            setBody(claim)
        }
        return when (response.status) {
            HttpStatusCode.OK -> PairingResult.Claimed(response.body<DeviceCredentials>())
            HttpStatusCode.NotFound,
            HttpStatusCode.Conflict,
            HttpStatusCode.BadRequest -> PairingResult.Rejected

            else -> error("pairing claim failed: HTTP ${response.status.value}")
        }
    }

    override fun pushes(): Flow<SyncPushMessage> = flow {
        val token = requireToken()
        client.webSocket(
            urlString = webSocketUrl,
            request = { header(HttpHeaders.Authorization, "Bearer $token") },
        ) {
            for (frame in incoming) {
                if (frame !is Frame.Text) continue
                val push = SyncMessageCodec.decodePush(frame.readText()) ?: continue
                emit(push)
            }
        }
    }

    private fun requireToken(): String =
        tokenProvider() ?: error("device token missing — pairing required")

    companion object {
        const val REVISION_QUERY = "baseRevision"

        fun defaultHttpClient(): HttpClient = HttpClient(CIO) {
            install(WebSockets)
            install(ContentNegotiation) {
                json(
                    Json {
                        ignoreUnknownKeys = true
                        encodeDefaults = true
                    }
                )
            }
        }

        fun wsUrl(serverUrl: String): String {
            val base = serverUrl.trimEnd('/')
            val wsBase = when {
                base.startsWith("https://", ignoreCase = true) ->
                    "wss://" + base.drop("https://".length)

                base.startsWith("http://", ignoreCase = true) ->
                    "ws://" + base.drop("http://".length)

                base.startsWith("ws://", ignoreCase = true) ||
                    base.startsWith("wss://", ignoreCase = true) -> base

                else -> "ws://$base"
            }
            return wsBase + SyncEndpoints.WEBSOCKET
        }
    }
}
