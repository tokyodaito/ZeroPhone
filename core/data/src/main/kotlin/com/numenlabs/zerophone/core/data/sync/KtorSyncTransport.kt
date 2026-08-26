package com.numenlabs.zerophone.core.data.sync

import com.numenlabs.zerophone.core.model.DeviceCredentials
import com.numenlabs.zerophone.core.model.PairingClaim
import com.numenlabs.zerophone.core.model.StateEnvelope
import com.numenlabs.zerophone.core.model.StateUpdateRequest
import com.numenlabs.zerophone.core.model.SyncEndpoints
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * [SyncTransport] for the phone over one ktor [HttpClient] (CIO, pure
 * JVM): Bearer device token on every call, kotlinx-json content
 * negotiation over the shared `:core:model` wire contracts, and the
 * conditional-GET / conditional-PUT / pairing endpoints mapped to
 * the pure result types. The token is read per call from the
 * [SyncCredentialsStore], so pairing and re-pairing need no client
 * rebuild.
 */
class KtorSyncTransport(
    serverUrl: String,
    private val credentials: SyncCredentialsStore,
    private val client: HttpClient = defaultHttpClient(),
) : SyncTransport {

    private val stateUrl: String = serverUrl.trimEnd('/') + SyncEndpoints.STATE
    private val pairingUrl: String = serverUrl.trimEnd('/') + SyncEndpoints.PAIRING_CLAIM

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

    private suspend fun requireToken(): String =
        credentials.load()?.token ?: error("device token missing — pairing required")

    companion object {
        const val REVISION_QUERY = "baseRevision"

        fun defaultHttpClient(): HttpClient = HttpClient(CIO) {
            install(ContentNegotiation) {
                json(
                    Json {
                        ignoreUnknownKeys = true
                        encodeDefaults = true
                    }
                )
            }
            expectSuccess = false
        }
    }
}
