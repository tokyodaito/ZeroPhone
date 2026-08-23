package com.numenlabs.zerophone.sync.server.auth

import com.numenlabs.zerophone.core.model.DeviceCredentials
import com.numenlabs.zerophone.core.model.SyncEndpoints
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.auth.authenticate
import io.ktor.server.cio.CIO as ServerCIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.ktor.server.websocket.WebSockets as ServerWebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

/**
 * End-to-end guard of the device-token mechanism over HTTP semantics:
 * Bearer on REST, Bearer on the WebSocket upgrade, rotation and revocation
 * as immediate store operations.
 */
class DeviceTokenAuthTest {

    private val tempDirs = mutableListOf<Path>()
    private val json = Json { ignoreUnknownKeys = true }

    @After
    fun tearDown() {
        tempDirs.forEach { it.toFile().deleteRecursively() }
    }

    private fun newStore(): DeviceTokenStore {
        val dir = Files.createTempDirectory("device-auth-test")
        tempDirs.add(dir)
        return DeviceTokenStore(file = dir.resolve("device-tokens.json"))
    }

    private suspend fun EmbeddedServer<*, *>.probeUrl(): String {
        val port = engine.resolvedConnectors().first().port
        return "127.0.0.1:$port"
    }

    /** Stand-in for a protected REST resource, run through the test host. */
    private fun ApplicationTestBuilder.deviceAuthApp(store: DeviceTokenStore) {
        application {
            installDeviceTokenAuth(store)
            routing {
                authenticate(DEVICE_AUTH) {
                    get(SyncEndpoints.STATE) {
                        call.respondText("""{"probe":"snapshot"}""")
                    }
                }
            }
        }
    }

    /**
     * The same app on a real CIO socket: the WebSocket upgrade then goes
     * through full real HTTP semantics, including rejected handshakes.
     */
    private fun realServerApp(store: DeviceTokenStore): EmbeddedServer<*, *> =
        embeddedServer(ServerCIO, host = "127.0.0.1", port = 0) {
            install(ServerWebSockets)
            installDeviceTokenAuth(store)
            routing {
                authenticate(DEVICE_AUTH) {
                    get(SyncEndpoints.STATE) {
                        call.respondText("""{"probe":"snapshot"}""")
                    }
                    webSocket(SyncEndpoints.WEBSOCKET) {
                        send(Frame.Text("connected"))
                    }
                }
            }
        }

    private fun bearer(token: String): String = "Bearer $token"

    /** Real CIO client, so WS upgrades travel over a real socket. */
    private fun newWsClient(): HttpClient = HttpClient(CIO) { install(WebSockets) }

    /** Completes the handshake and returns the first pushed frame's text. */
    private suspend fun HttpClient.receiveWsGreeting(url: String, token: String?): String? {
        var received: String? = null
        webSocket(
            urlString = url,
            request = {
                if (token != null) header(HttpHeaders.Authorization, bearer(token))
            },
        ) {
            received = (incoming.receive() as Frame.Text).readText()
        }
        return received
    }

    /**
     * Returns the rejection status of the HTTP upgrade, or null on success.
     * A rejected handshake surfaces as a client exception whose message
     * carries the unexpected status code.
     */
    private suspend fun HttpClient.wsHandshakeStatus(url: String, token: String?): HttpStatusCode? = try {
        receiveWsGreeting(url, token)
        null
    } catch (rejected: Exception) {
        Regex("but was (\\d+)")
            .find(rejected.message ?: "")
            ?.groupValues
            ?.get(1)
            ?.toIntOrNull()
            ?.let(HttpStatusCode::fromValue)
            ?: throw AssertionError(
                "websocket handshake failed without a rejection status: ${rejected.message}",
                rejected,
            )
    }

    @Test
    fun `bearer authentication passes only valid non-revoked tokens`() = testApplication {
        val store = newStore()
        val issued = store.issue("desktop-1")
        deviceAuthApp(store)

        val missing = client.get(SyncEndpoints.STATE)
        assertEquals(HttpStatusCode.Unauthorized, missing.status)

        val garbage = client.get(SyncEndpoints.STATE) {
            header(HttpHeaders.Authorization, bearer("not-a-real-token"))
        }
        assertEquals(HttpStatusCode.Unauthorized, garbage.status)

        val valid = client.get(SyncEndpoints.STATE) {
            header(HttpHeaders.Authorization, bearer(issued.token))
        }
        assertTrue(valid.status.isSuccess())

        val urlLeak = client.get(SyncEndpoints.STATE + "?token=${issued.token}")
        assertEquals(
            "tokens must not authenticate from a query string",
            HttpStatusCode.Unauthorized,
            urlLeak.status,
        )
    }

    @Test
    fun `rotation issues a new token and immediately invalidates the old one`() = testApplication {
        val store = newStore()
        val issued = store.issue("desktop-1")
        deviceAuthApp(store)

        val response: HttpResponse = client.post(SyncEndpoints.AUTH_ROTATE) {
            header(HttpHeaders.Authorization, bearer(issued.token))
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val rotated = json.decodeFromString(DeviceCredentials.serializer(), response.bodyAsText())
        assertEquals("desktop-1", rotated.deviceId)
        assertNotEquals("rotation must mint a fresh token", issued.token, rotated.token)

        val withOld = client.get(SyncEndpoints.STATE) {
            header(HttpHeaders.Authorization, bearer(issued.token))
        }
        assertEquals("old token must be dead on the very next request", HttpStatusCode.Unauthorized, withOld.status)

        val rotateWithOld = client.post(SyncEndpoints.AUTH_ROTATE) {
            header(HttpHeaders.Authorization, bearer(issued.token))
        }
        assertEquals(HttpStatusCode.Unauthorized, rotateWithOld.status)

        val withNew = client.get(SyncEndpoints.STATE) {
            header(HttpHeaders.Authorization, bearer(rotated.token))
        }
        assertTrue(withNew.status.isSuccess())
    }

    @Test
    fun `revocation blocks all subsequent requests of the device`() {
        val store = newStore()
        val server = realServerApp(store).start(wait = false)
        val rest = HttpClient(CIO)
        val wsClient = newWsClient()
        runBlocking {
            val issued = store.issue("desktop-1")
            val base = "http://" + server.probeUrl()
            val wsUrl = "ws://" + server.probeUrl() + SyncEndpoints.WEBSOCKET

            val revoke = rest.delete("$base${SyncEndpoints.AUTH_TOKEN}") {
                header(HttpHeaders.Authorization, bearer(issued.token))
            }
            assertEquals(HttpStatusCode.NoContent, revoke.status)

            val snapshot = rest.get("$base${SyncEndpoints.STATE}") {
                header(HttpHeaders.Authorization, bearer(issued.token))
            }
            assertEquals(HttpStatusCode.Unauthorized, snapshot.status)

            val rotate = rest.post("$base${SyncEndpoints.AUTH_ROTATE}") {
                header(HttpHeaders.Authorization, bearer(issued.token))
            }
            assertEquals("a revoked device cannot rotate itself back in", HttpStatusCode.Unauthorized, rotate.status)

            val secondRevoke = rest.delete("$base${SyncEndpoints.AUTH_TOKEN}") {
                header(HttpHeaders.Authorization, bearer(issued.token))
            }
            assertEquals(HttpStatusCode.Unauthorized, secondRevoke.status)

            assertEquals(
                "websocket upgrade must reject the revoked token",
                HttpStatusCode.Unauthorized,
                wsClient.wsHandshakeStatus(wsUrl, issued.token),
            )
        }
        rest.close()
        wsClient.close()
        server.stop(1000, 2000)
    }

    @Test
    fun `websocket connection authorizes with the same device token`() {
        val store = newStore()
        val server = realServerApp(store).start(wait = false)
        val wsClient = newWsClient()
        runBlocking {
            val issued = store.issue("desktop-1")
            val url = "ws://" + server.probeUrl() + SyncEndpoints.WEBSOCKET

            assertEquals(
                "handshake without a token must be rejected before it completes",
                HttpStatusCode.Unauthorized,
                wsClient.wsHandshakeStatus(url, token = null),
            )
            assertEquals(
                "handshake with a wrong token must be rejected",
                HttpStatusCode.Unauthorized,
                wsClient.wsHandshakeStatus(url, token = "bogus"),
            )
            assertEquals(
                "a token in the query string is not authentication",
                HttpStatusCode.Unauthorized,
                wsClient.wsHandshakeStatus("$url?token=${issued.token}", token = null),
            )

            val greeting = wsClient.receiveWsGreeting(url, token = issued.token)
            assertEquals("connected", greeting)
        }
        wsClient.close()
        server.stop(1000, 2000)
    }
}
