package com.numenlabs.zerophone.sync.server.state

import com.numenlabs.zerophone.core.model.LinkPayload
import com.numenlabs.zerophone.core.model.SyncEndpoints
import com.numenlabs.zerophone.core.model.SyncPushMessage
import com.numenlabs.zerophone.sync.server.auth.DeviceTokenStore
import com.numenlabs.zerophone.sync.server.auth.SyncServerJson
import com.numenlabs.zerophone.sync.server.auth.installDeviceTokenAuth
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.server.cio.CIO as ServerCIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.application.install
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
 * The send-to-PC uplink: a LinkPayload text frame sent by the phone on
 * its push socket is validated, stamped with the authenticated sender
 * and broadcast to every connected device as `link.received`; anything
 * malformed is dropped without disturbing the session.
 */
class LinkRelayTest {

    private val tempDirs = mutableListOf<Path>()
    private val json = Json { ignoreUnknownKeys = true }

    @After
    fun tearDown() {
        tempDirs.forEach { it.toFile().deleteRecursively() }
    }

    private fun newDir(): Path =
        Files.createTempDirectory("link-relay-test").also { tempDirs.add(it) }

    @Test
    fun `a link frame from the phone reaches every connected device`() {
        val dir = newDir()
        val tokens = DeviceTokenStore(dir.resolve("device-tokens.json"))
        val server: EmbeddedServer<*, *> = embeddedServer(ServerCIO, host = "127.0.0.1", port = 0) {
            install(ServerWebSockets)
            installDeviceTokenAuth(tokens)
            installStateSync(StateStore(dir.resolve("state.json")), WsBroadcaster())
        }.start(wait = false)
        val phone = HttpClient(CIO) { install(WebSockets) }
        val desktop = HttpClient(CIO) { install(WebSockets) }

        runBlocking {
            val phoneCreds = tokens.issue("phone-1")
            val desktopCreds = tokens.issue("desktop-1")
            val port = server.engine.resolvedConnectors().first().port
            val wsUrl = "ws://127.0.0.1:$port${SyncEndpoints.WEBSOCKET}"

            val desktopFrames = Channel<String>(capacity = 8)
            val desktopSocket = launch {
                desktop.webSocket(
                    urlString = wsUrl,
                    request = { header(HttpHeaders.Authorization, "Bearer ${desktopCreds.token}") },
                ) {
                    for (frame in incoming) if (frame is Frame.Text) desktopFrames.trySend(frame.readText())
                }
            }

            // Wait until the desktop socket is live (its state.snapshot).
            withTimeout(5_000) {
                json.decodeFromString<SyncPushMessage>(desktopFrames.receive())
            }

            val link = LinkPayload(
                id = "link-1",
                url = "https://example.com/article",
                title = "An article",
                sourceDeviceId = "spoofed-identity",
                sentAtMillis = 42L,
            )
            phone.webSocket(
                urlString = wsUrl,
                request = { header(HttpHeaders.Authorization, "Bearer ${phoneCreds.token}") },
            ) {
                // state.snapshot of the phone's own connection arrives first.
                send(Frame.Text(SyncServerJson.encodeToString(LinkPayload.serializer(), link)))
                // Malformed frames must be ignored, not kill the session.
                send(Frame.Text("{not-json"))
                send(Frame.Text(SyncServerJson.encodeToString(LinkPayload.serializer(), LinkPayload(id = " ", url = " "))))

                withTimeout(5_000) {
                    val push = json.decodeFromString<SyncPushMessage>(desktopFrames.receive())
                    assertTrue(push is SyncPushMessage.LinkReceived)
                    val received = (push as SyncPushMessage.LinkReceived).payload
                    assertEquals(link.copy(sourceDeviceId = "phone-1"), received)
                }
            }

            desktopSocket.cancel()
        }
        phone.close()
        desktop.close()
        server.stop(1000, 2000)
    }
}
