package com.numenlabs.zerophone.sync.server.link

import com.numenlabs.zerophone.core.model.LinkPayload
import com.numenlabs.zerophone.core.model.SyncPushMessage
import com.numenlabs.zerophone.sync.server.auth.DeviceTokenStore
import com.numenlabs.zerophone.sync.server.auth.SyncServerJson
import com.numenlabs.zerophone.sync.server.auth.installDeviceTokenAuth
import com.numenlabs.zerophone.sync.server.state.StateStore
import com.numenlabs.zerophone.sync.server.state.WsBroadcaster
import com.numenlabs.zerophone.sync.server.state.installStateSync
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.server.application.install
import io.ktor.server.cio.CIO as ServerCIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.websocket.WebSockets as ServerWebSockets
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

/**
 * The send-to-PC REST surface and its store-and-forward delivery:
 * links POSTed by the phone are answered 202, pushed to connected
 * desktops immediately, and held for desktops that connect later —
 * flashed right after the `state.snapshot` of their socket.
 */
class LinkDeliveryTest {

    private val tempDirs = mutableListOf<Path>()
    private val json = Json { ignoreUnknownKeys = true }

    @After
    fun tearDown() {
        tempDirs.forEach { it.toFile().deleteRecursively() }
    }

    private fun newDir(): Path =
        Files.createTempDirectory("link-delivery-test").also { tempDirs.add(it) }

    private class TestServer(dir: Path) {
        private val tokens = DeviceTokenStore(dir.resolve("device-tokens.json"))
        private val pending = PendingLinkStore(dir.resolve(PendingLinkStore.FILE_NAME))
        private val broadcaster = WsBroadcaster()
        val server: EmbeddedServer<*, *> = embeddedServer(ServerCIO, host = "127.0.0.1", port = 0) {
            install(ServerWebSockets)
            installDeviceTokenAuth(tokens)
            installStateSync(StateStore(dir.resolve("state.json")), broadcaster, pending)
            installLinkDelivery(pending, broadcaster)
        }.start(wait = false)

        val port: Int get() = kotlinx.coroutines.runBlocking {
            server.engine.resolvedConnectors().first().port
        }
        val wsUrl: String get() = "ws://127.0.0.1:$port${com.numenlabs.zerophone.core.model.SyncEndpoints.WEBSOCKET}"
        val linkUrl: String get() = "http://127.0.0.1:$port${LINK}"

        suspend fun issueToken(deviceId: String) = tokens.issue(deviceId)

        fun stop() = server.stop(1000, 2000)
    }

    private fun linkBody(id: String, url: String = "https://example.com/$id"): String =
        SyncServerJson.encodeToString(
            LinkPayload.serializer(),
            LinkPayload(id = id, url = url, sourceDeviceId = "spoofed-identity"),
        )

    private suspend fun postLink(
        rest: HttpClient,
        server: TestServer,
        token: String?,
        body: String,
    ): HttpResponse = rest.post(server.linkUrl) {
        if (token != null) header(HttpHeaders.Authorization, "Bearer $token")
        contentType(ContentType.Application.Json)
        setBody(body)
    }

    @Test
    fun `posting a link requires a device token and a well-formed body`() {
        val server = TestServer(newDir())
        val rest = HttpClient(CIO)

        runBlocking {
            val phone = server.issueToken("phone-1")

            assertEquals(
                HttpStatusCode.Unauthorized,
                postLink(rest, server, token = null, body = linkBody("a")).status,
            )
            assertEquals(
                HttpStatusCode.BadRequest,
                postLink(rest, server, token = phone.token, body = "{not-json").status,
            )
            assertEquals(
                "blank ids and urls are rejected",
                HttpStatusCode.BadRequest,
                postLink(rest, server, token = phone.token, body = linkBody(" ")).status,
            )

            val accepted = postLink(rest, server, token = phone.token, body = linkBody("a"))
            assertTrue(accepted.status.isSuccess())
            assertEquals(HttpStatusCode.Accepted, accepted.status)
            assertEquals("", accepted.bodyAsText())
        }

        rest.close()
        server.stop()
    }

    @Test
    fun `a link posted while a desktop is connected is pushed to it immediately`() {
        val server = TestServer(newDir())
        val rest = HttpClient(CIO)
        val wsClient = HttpClient(CIO) { install(WebSockets) }

        runBlocking {
            val phone = server.issueToken("phone-1")
            val desktop = server.issueToken("desktop-1")

            val frames = Channel<String>(capacity = 8)
            val socket = launch {
                wsClient.webSocket(
                    urlString = server.wsUrl,
                    request = { header(HttpHeaders.Authorization, "Bearer ${desktop.token}") },
                ) {
                    for (frame in incoming) if (frame is Frame.Text) frames.trySend(frame.readText())
                }
            }

            val snapshot = json.decodeFromString<SyncPushMessage>(withTimeout(5_000) { frames.receive() })
            assertTrue("connect starts with state.snapshot: $snapshot", snapshot is SyncPushMessage.StateSnapshot)

            val accepted = postLink(rest, server, token = phone.token, body = linkBody("live-1"))
            assertEquals(HttpStatusCode.Accepted, accepted.status)

            val push = json.decodeFromString<SyncPushMessage>(withTimeout(5_000) { frames.receive() })
            assertTrue("the live session gets link.received: $push", push is SyncPushMessage.LinkReceived)
            val received = (push as SyncPushMessage.LinkReceived).payload
            assertEquals("live-1", received.id)
            assertEquals(
                "the authenticated sender overrides the claimed identity",
                "phone-1",
                received.sourceDeviceId,
            )

            socket.cancel()
        }

        wsClient.close()
        rest.close()
        server.stop()
    }

    @Test
    fun `a link posted while nobody is connected is flashed on the next connect after the snapshot`() {
        val server = TestServer(newDir())
        val rest = HttpClient(CIO)
        val wsClient = HttpClient(CIO) { install(WebSockets) }

        runBlocking {
            val phone = server.issueToken("phone-1")
            val desktop = server.issueToken("desktop-1")

            val accepted = postLink(rest, server, token = phone.token, body = linkBody("offline-1"))
            assertEquals(HttpStatusCode.Accepted, accepted.status)
            val acceptedToo = postLink(rest, server, token = phone.token, body = linkBody("offline-2"))
            assertEquals(HttpStatusCode.Accepted, acceptedToo.status)

            val frames = Channel<String>(capacity = 8)
            val socket = launch {
                wsClient.webSocket(
                    urlString = server.wsUrl,
                    request = { header(HttpHeaders.Authorization, "Bearer ${desktop.token}") },
                ) {
                    for (frame in incoming) if (frame is Frame.Text) frames.trySend(frame.readText())
                }
            }

            val snapshot = json.decodeFromString<SyncPushMessage>(withTimeout(5_000) { frames.receive() })
            assertTrue(snapshot is SyncPushMessage.StateSnapshot)

            val ids = (1..2).map {
                val push = json.decodeFromString<SyncPushMessage>(withTimeout(5_000) { frames.receive() })
                assertTrue("pending links flash as link.received: $push", push is SyncPushMessage.LinkReceived)
                (push as SyncPushMessage.LinkReceived).payload.id
            }
            assertEquals(listOf("offline-1", "offline-2"), ids)

            socket.cancel()
        }

        wsClient.close()
        rest.close()
        server.stop()
    }
}
