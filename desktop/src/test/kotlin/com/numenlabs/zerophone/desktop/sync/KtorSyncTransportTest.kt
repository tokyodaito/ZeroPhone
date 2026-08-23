package com.numenlabs.zerophone.desktop.sync

import com.numenlabs.zerophone.core.model.DeviceCredentials
import com.numenlabs.zerophone.core.model.PairingClaim
import com.numenlabs.zerophone.core.model.PolicySnapshot
import com.numenlabs.zerophone.core.model.StateEnvelope
import com.numenlabs.zerophone.core.model.StateUpdateRequest
import com.numenlabs.zerophone.core.model.SyncState
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * REST mapping of [KtorSyncTransport] against [MockEngine]: endpoints,
 * Bearer header, conditional GET (304), conditional PUT (200/409) and the
 * pairing claim — all against the shared `:core:model` wire contracts.
 */
class KtorSyncTransportTest {

    private val envelope = StateEnvelope(
        state = SyncState(policy = PolicySnapshot(deviceId = "phone-1", activeMode = "work")),
        revision = 5,
    )

    private val credentials = DeviceCredentials(deviceId = "desktop-1", token = "issued", pairedAtMillis = 1)

    private val jsonHeaders = headersOf(
        HttpHeaders.ContentType,
        ContentType.Application.Json.toString(),
    )

    private fun envelopeJson(): String =
        Json { encodeDefaults = true }.encodeToString(StateEnvelope.serializer(), envelope)

    private fun credentialsJson(): String =
        Json { encodeDefaults = true }.encodeToString(DeviceCredentials.serializer(), credentials)

    private inner class Recorder {
        val requests = mutableListOf<HttpRequestData>()
        var status: HttpStatusCode = HttpStatusCode.OK
        var body: String = envelopeJson()

        val engine = MockEngine { request ->
            requests.add(request)
            respond(body, status, jsonHeaders)
        }

        fun client(): HttpClient = HttpClient(engine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; encodeDefaults = true })
            }
        }
    }

    private fun transport(recorder: Recorder, token: String? = "device-token"): KtorSyncTransport =
        KtorSyncTransport(
            serverUrl = "https://sync.example.com",
            tokenProvider = { token },
            client = recorder.client(),
        )

    @Test
    fun `pull hits the state endpoint with the Bearer token`() = runTest {
        val recorder = Recorder()
        val result = transport(recorder).pull()

        assertTrue(result is PullResult.Envelope)
        assertEquals(5L, (result as PullResult.Envelope).envelope.revision)
        assertEquals("work", result.envelope.state.policy.activeMode)

        val request = recorder.requests.single()
        assertEquals("https://sync.example.com/api/v1/state", request.url.toString())
        assertEquals("Bearer device-token", request.headers[HttpHeaders.Authorization])
    }

    @Test
    fun `conditional pull appends baseRevision and maps 304`() = runTest {
        val recorder = Recorder()
        recorder.status = HttpStatusCode.NotModified

        assertEquals(PullResult.NotModified, transport(recorder).pull(baseRevision = 5))

        val request = recorder.requests.single()
        assertEquals("5", request.url.parameters[KtorSyncTransport.REVISION_QUERY])
    }

    @Test
    fun `pull maps 401 to unauthorized`() = runTest {
        val recorder = Recorder()
        recorder.status = HttpStatusCode.Unauthorized
        assertEquals(PullResult.Unauthorized, transport(recorder).pull())
    }

    @Test
    fun `push sends a conditional update and maps 200`() = runTest {
        val recorder = Recorder()
        val update = StateUpdateRequest(baseRevision = 5, state = envelope.state, hint = "policy")

        val result = transport(recorder).push(update)

        assertTrue(result is PushResult.Accepted)
        assertEquals(5L, (result as PushResult.Accepted).envelope.revision)

        val request = recorder.requests.single()
        assertEquals("https://sync.example.com/api/v1/state", request.url.toString())
        assertEquals("PUT", request.method.value)
        assertEquals("Bearer device-token", request.headers[HttpHeaders.Authorization])
        val body = request.body as io.ktor.http.content.TextContent
        assertTrue("body was: ${body.text}", body.text.contains("\"baseRevision\":5"))
        assertTrue(body.text.contains("\"hint\":\"policy\""))
    }

    @Test
    fun `push maps 409 with the winning envelope`() = runTest {
        val recorder = Recorder()
        recorder.body = envelopeJson()
        recorder.status = HttpStatusCode.Conflict

        val result = transport(recorder).push(StateUpdateRequest(baseRevision = 4, state = envelope.state))

        assertTrue(result is PushResult.Conflict)
        assertEquals(5L, (result as PushResult.Conflict).current.revision)
    }

    @Test
    fun `claim posts the shortcode and maps credentials`() = runTest {
        val recorder = Recorder()
        recorder.body = credentialsJson()
        val transport = KtorSyncTransport(
            serverUrl = "https://sync.example.com",
            tokenProvider = { null },
            client = recorder.client(),
        )

        val result = transport.claim(PairingClaim(code = "422222", deviceName = "macbook"))

        assertTrue(result is PairingResult.Claimed)
        assertEquals("desktop-1", (result as PairingResult.Claimed).credentials.deviceId)
        val request = recorder.requests.single()
        assertEquals("https://sync.example.com/api/v1/pairing/claim", request.url.toString())
        val body = request.body as io.ktor.http.content.TextContent
        assertTrue(body.text.contains("\"code\":\"422222\""))
        assertTrue(body.text.contains("\"deviceKind\":\"desktop\""))
    }

    @Test
    fun `claim maps unknown consumed and malformed codes to rejected`() = runTest {
        for (status in listOf(HttpStatusCode.NotFound, HttpStatusCode.Conflict, HttpStatusCode.BadRequest)) {
            val recorder = Recorder()
            recorder.status = status
            assertEquals(
                "status $status",
                PairingResult.Rejected,
                transport(recorder).claim(PairingClaim(code = "x", deviceName = "d")),
            )
        }
    }

    @Test
    fun `missing token fails before any request leaves`() = runTest {
        val recorder = Recorder()
        val transport = transport(recorder, token = null)

        val failure = runCatching { transport.pull() }.exceptionOrNull()
        assertTrue(failure is IllegalStateException)
        assertTrue(failure?.message.orEmpty().contains("pairing required"))
        assertTrue(recorder.requests.isEmpty())
    }

    @Test
    fun `ws urls are derived from the server url`() {
        assertEquals(
            "wss://sync.example.com/api/v1/ws",
            KtorSyncTransport.wsUrl("https://sync.example.com"),
        )
        assertEquals(
            "ws://127.0.0.1:8080/api/v1/ws",
            KtorSyncTransport.wsUrl("http://127.0.0.1:8080"),
        )
        assertEquals(
            "wss://sync.example.com/api/v1/ws",
            KtorSyncTransport.wsUrl("https://sync.example.com/"),
        )
        assertEquals(
            "ws://sync.example.com/api/v1/ws",
            KtorSyncTransport.wsUrl("sync.example.com"),
        )
        assertEquals(
            "wss://sync.example.com/api/v1/ws",
            KtorSyncTransport.wsUrl("wss://sync.example.com"),
        )
    }
}
