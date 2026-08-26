package com.numenlabs.zerophone.sync.server.state

import com.numenlabs.zerophone.core.model.Availability
import com.numenlabs.zerophone.core.model.CapabilityAvailability
import com.numenlabs.zerophone.core.model.PolicySnapshot
import com.numenlabs.zerophone.core.model.StateEnvelope
import com.numenlabs.zerophone.core.model.StateUpdateRequest
import com.numenlabs.zerophone.core.model.SyncEndpoints
import com.numenlabs.zerophone.core.model.SyncPushMessage
import com.numenlabs.zerophone.core.model.SyncState
import com.numenlabs.zerophone.sync.server.auth.DeviceTokenStore
import com.numenlabs.zerophone.sync.server.auth.SyncServerJson
import com.numenlabs.zerophone.sync.server.auth.installDeviceTokenAuth
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.cio.CIO as ServerCIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.ktor.server.websocket.WebSockets as ServerWebSockets
import io.ktor.server.websocket.webSocket as serverWebSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
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
 * End-to-end guard of the revision model over real HTTP semantics:
 * full-document pull, conditional PUT with deterministic 409, the
 * one-GET catch-up contract and the WebSocket push envelope.
 */
class StateSyncTest {

    private val tempDirs = mutableListOf<Path>()
    private val json = Json { ignoreUnknownKeys = true }

    @After
    fun tearDown() {
        tempDirs.forEach { it.toFile().deleteRecursively() }
    }

    private fun newDir(): Path =
        Files.createTempDirectory("state-sync-test").also { tempDirs.add(it) }

    private fun ApplicationTestBuilder.stateApp(
        tokens: DeviceTokenStore,
        store: StateStore,
        broadcaster: WsBroadcaster = WsBroadcaster(),
    ) {
        application {
            installStateSyncApp(tokens, store, broadcaster)
        }
    }

    private fun Application.installStateSyncApp(
        tokens: DeviceTokenStore,
        store: StateStore,
        broadcaster: WsBroadcaster,
    ) {
        install(ServerWebSockets)
        installDeviceTokenAuth(tokens)
        installStateSync(store, broadcaster)
    }

    private fun stateWith(vararg capabilityIds: String): SyncState = SyncState(
        policy = PolicySnapshot(
            deviceId = "phone-1",
            capabilities = capabilityIds.map {
                CapabilityAvailability(id = it, state = Availability.AVAILABLE)
            },
        ),
    )

    private fun putBody(baseRevision: Long, state: SyncState, hint: String? = null): String =
        SyncServerJson.encodeToString(
            StateUpdateRequest.serializer(),
            StateUpdateRequest(baseRevision = baseRevision, state = state, hint = hint),
        )

    private fun bearer(token: String): String = "Bearer $token"

    private suspend fun decodeEnvelope(response: HttpResponse): StateEnvelope =
        json.decodeFromString(StateEnvelope.serializer(), response.bodyAsText())

    private suspend fun putState(
        client: io.ktor.client.HttpClient,
        url: String,
        token: String,
        baseRevision: Long,
        state: SyncState,
        hint: String? = null,
    ): HttpResponse = client.put(url) {
        header(HttpHeaders.Authorization, bearer(token))
        contentType(ContentType.Application.Json)
        setBody(putBody(baseRevision, state, hint))
    }

    @Test
    fun `GET returns the full document with its revision`() = testApplication {
        val tokens = DeviceTokenStore(newDir().resolve("device-tokens.json"))
        val phone = tokens.issue("phone-1")
        stateApp(tokens, StateStore(newDir().resolve("state.json")))

        val anonymous = client.get(SyncEndpoints.STATE)
        assertEquals(HttpStatusCode.Unauthorized, anonymous.status)

        val response = client.get(SyncEndpoints.STATE) {
            header(HttpHeaders.Authorization, bearer(phone.token))
        }

        assertTrue(response.status.isSuccess())
        val envelope = decodeEnvelope(response)
        assertEquals(0L, envelope.revision)
        assertEquals(SyncState(), envelope.state)
    }

    @Test
    fun `PUT with the current base is accepted and assigns the next revision`() = testApplication {
        val tokens = DeviceTokenStore(newDir().resolve("device-tokens.json"))
        val phone = tokens.issue("phone-1")
        val store = StateStore(newDir().resolve("state.json"))
        stateApp(tokens, store)

        val response = client.put(SyncEndpoints.STATE) {
            header(HttpHeaders.Authorization, bearer(phone.token))
            contentType(ContentType.Application.Json)
            setBody(putBody(baseRevision = 0, state = stateWith("phone-feature"), hint = "policy"))
        }

        assertTrue(response.status.isSuccess())
        val envelope = decodeEnvelope(response)
        assertEquals(1L, envelope.revision)
        assertEquals(stateWith("phone-feature"), envelope.state)

        val pulled = client.get(SyncEndpoints.STATE) {
            header(HttpHeaders.Authorization, bearer(phone.token))
        }
        assertEquals("GET after PUT returns the same revision and state", envelope, decodeEnvelope(pulled))
    }

    @Test
    fun `PUT with a stale base gets 409 carrying the winning revision and state`() = testApplication {
        val tokens = DeviceTokenStore(newDir().resolve("device-tokens.json"))
        val phone = tokens.issue("phone-1")
        val desktop = tokens.issue("desktop-1")
        stateApp(tokens, StateStore(newDir().resolve("state.json")))

        val winner = putState(
            client, SyncEndpoints.STATE, phone.token,
            baseRevision = 0, state = stateWith("phone-feature"),
        )
        assertTrue(winner.status.isSuccess())

        val loser = putState(
            client, SyncEndpoints.STATE, desktop.token,
            baseRevision = 0, state = stateWith("desktop-feature"),
        )

        assertEquals(HttpStatusCode.Conflict, loser.status)
        val envelope = decodeEnvelope(loser)
        assertEquals(1L, envelope.revision)
        assertEquals(
            "the 409 body carries the state that beat the loser",
            stateWith("phone-feature"),
            envelope.state,
        )
    }

    @Test
    fun `PUT with a base from the future or a malformed body is a client error`() = testApplication {
        val tokens = DeviceTokenStore(newDir().resolve("device-tokens.json"))
        val phone = tokens.issue("phone-1")
        stateApp(tokens, StateStore(newDir().resolve("state.json")))

        val future = putState(
            client, SyncEndpoints.STATE, phone.token,
            baseRevision = 99, state = stateWith("x"),
        )
        assertEquals(HttpStatusCode.BadRequest, future.status)

        val malformed = client.put(SyncEndpoints.STATE) {
            header(HttpHeaders.Authorization, bearer(phone.token))
            contentType(ContentType.Application.Json)
            setBody("{not-json")
        }
        assertEquals(HttpStatusCode.BadRequest, malformed.status)
    }

    @Test
    fun `racing writers converge without silent loss`() = testApplication {
        val tokens = DeviceTokenStore(newDir().resolve("device-tokens.json"))
        val phone = tokens.issue("phone-1")
        val desktop = tokens.issue("desktop-1")
        stateApp(tokens, StateStore(newDir().resolve("state.json")))

        // Both devices push from the same base revision at the same moment.
        // WHICH writer wins is nondeterministic — the rebase below must use
        // the actual loser, not a hardcoded one.
        data class Writer(val token: String, val feature: String, val response: HttpResponse)

        val writers = coroutineScope {
            listOf(
                async {
                    Writer(
                        phone.token, "phone-feature",
                        putState(client, SyncEndpoints.STATE, phone.token, 0, stateWith("phone-feature"))
                    )
                },
                async {
                    Writer(
                        desktop.token, "desktop-feature",
                        putState(client, SyncEndpoints.STATE, desktop.token, 0, stateWith("desktop-feature"))
                    )
                }
            ).map { it.await() }
        }

        val ok = writers.filter { it.response.status.isSuccess() }
        val losers = writers.filter { it.response.status == HttpStatusCode.Conflict }
        assertEquals("exactly one racing write wins", 1, ok.size)
        assertEquals("the loser deterministically conflicts", 1, losers.size)

        // The loser rebases its intended change on the fresh state from the
        // 409 body and pushes again — both updates end up in the document.
        val loser = losers.single()
        val fresh = decodeEnvelope(loser.response)
        val loserCapabilities = fresh.state.policy.capabilities.map { it.id } + loser.feature
        val rebased = putState(
            client, SyncEndpoints.STATE, loser.token,
            baseRevision = fresh.revision,
            state = stateWith(*loserCapabilities.toTypedArray()),
            hint = "rebase",
        )
        assertTrue(rebased.status.isSuccess())
        assertEquals(2L, decodeEnvelope(rebased).revision)

        val final = decodeEnvelope(
            client.get(SyncEndpoints.STATE) {
                header(HttpHeaders.Authorization, bearer(phone.token))
            }
        )
        assertEquals(2L, final.revision)
        val finalIds = final.state.policy.capabilities.map { it.id }
        assertTrue(
            "both writers' changes survive: $finalIds",
            "phone-feature" in finalIds && "desktop-feature" in finalIds,
        )
    }

    @Test
    fun `baseRevision query turns GET into a freshness check`() = testApplication {
        val tokens = DeviceTokenStore(newDir().resolve("device-tokens.json"))
        val phone = tokens.issue("phone-1")
        stateApp(tokens, StateStore(newDir().resolve("state.json")))
        putState(client, SyncEndpoints.STATE, phone.token, 0, stateWith("a"))

        val current = client.get(SyncEndpoints.STATE + "?baseRevision=1") {
            header(HttpHeaders.Authorization, bearer(phone.token))
        }
        assertEquals(HttpStatusCode.NotModified, current.status)

        val stale = client.get(SyncEndpoints.STATE + "?baseRevision=0") {
            header(HttpHeaders.Authorization, bearer(phone.token))
        }
        assertTrue(stale.status.isSuccess())
        assertEquals(1L, decodeEnvelope(stale).revision)

        val ahead = client.get(SyncEndpoints.STATE + "?baseRevision=2") {
            header(HttpHeaders.Authorization, bearer(phone.token))
        }
        assertEquals(HttpStatusCode.BadRequest, ahead.status)

        val garbage = client.get(SyncEndpoints.STATE + "?baseRevision=soon") {
            header(HttpHeaders.Authorization, bearer(phone.token))
        }
        assertEquals(HttpStatusCode.BadRequest, garbage.status)
    }

    @Test
    fun `websocket delivers current revision on connect and state-updated on writes`() {
        val dir = newDir()
        val tokens = DeviceTokenStore(dir.resolve("device-tokens.json"))
        val store = StateStore(dir.resolve("state.json"))
        val server: EmbeddedServer<*, *> = embeddedServer(ServerCIO, host = "127.0.0.1", port = 0) {
            installStateSyncApp(tokens, store, WsBroadcaster())
        }.start(wait = false)
        val rest = HttpClient(CIO)
        val wsClient = HttpClient(CIO) { install(WebSockets) }

        runBlocking {
            val phone = tokens.issue("phone-1")
            val watcher = tokens.issue("desktop-1")
            val base = "http://127.0.0.1:" + server.engine.resolvedConnectors().first().port
            val wsUrl = "ws://127.0.0.1:" + server.engine.resolvedConnectors().first().port +
                SyncEndpoints.WEBSOCKET

            // Revision 1 exists before the watcher connects.
            val first = putState(rest, "$base${SyncEndpoints.STATE}", phone.token, 0, stateWith("a"))
            assertTrue(first.status.isSuccess())

            val frames = Channel<String>(capacity = 8)
            val socketJob = launch {
                wsClient.webSocket(
                    urlString = wsUrl,
                    request = { header(HttpHeaders.Authorization, bearer(watcher.token)) },
                ) {
                    for (frame in incoming) {
                        if (frame is Frame.Text) frames.trySend(frame.readText())
                    }
                }
            }

            val snapshot = json.decodeFromString<SyncPushMessage>(withTimeout(5_000) { frames.receive() })
            assertEquals(
                "connect must immediately report the current revision",
                SyncPushMessage.StateSnapshot(revision = 1),
                snapshot,
            )

            val updated = putState(
                rest, "$base${SyncEndpoints.STATE}", phone.token,
                baseRevision = 1, state = stateWith("a", "b"), hint = "policy",
            )
            assertTrue(updated.status.isSuccess())

            val push = json.decodeFromString<SyncPushMessage>(withTimeout(5_000) { frames.receive() })
            assertEquals(
                SyncPushMessage.StateUpdated(revision = 2, actorDeviceId = "phone-1", hint = "policy"),
                push,
            )

            socketJob.cancel()
        }
        rest.close()
        wsClient.close()
        server.stop(1000, 2000)
    }

    @Test
    fun `websocket pushes reach every connected device and ordered by revision`() {
        val dir = newDir()
        val tokens = DeviceTokenStore(dir.resolve("device-tokens.json"))
        val store = StateStore(dir.resolve("state.json"))
        val broadcaster = WsBroadcaster()
        val server: EmbeddedServer<*, *> = embeddedServer(ServerCIO, host = "127.0.0.1", port = 0) {
            installStateSyncApp(tokens, store, broadcaster)
        }.start(wait = false)
        val rest = HttpClient(CIO)
        val wsA = HttpClient(CIO) { install(WebSockets) }
        val wsB = HttpClient(CIO) { install(WebSockets) }

        runBlocking {
            val phone = tokens.issue("phone-1")
            val port = server.engine.resolvedConnectors().first().port
            val wsUrl = "ws://127.0.0.1:$port${SyncEndpoints.WEBSOCKET}"
            val stateUrl = "http://127.0.0.1:$port${SyncEndpoints.STATE}"

            val receivedA = CompletableDeferred<Unit>()
            val receivedB = CompletableDeferred<Unit>()
            val framesA = Channel<String>(capacity = 8)
            val framesB = Channel<String>(capacity = 8)

            val socketA = launch {
                wsA.webSocket(wsUrl, request = { header(HttpHeaders.Authorization, bearer(phone.token)) }) {
                    for (frame in incoming) if (frame is Frame.Text) framesA.trySend(frame.readText())
                }
            }
            val socketB = launch {
                wsB.webSocket(wsUrl, request = { header(HttpHeaders.Authorization, bearer(phone.token)) }) {
                    for (frame in incoming) if (frame is Frame.Text) framesB.trySend(frame.readText())
                }
            }

            // Wait for both sockets to be live (their state.snapshot frames).
            withTimeout(5_000) {
                json.decodeFromString<SyncPushMessage>(framesA.receive())
                json.decodeFromString<SyncPushMessage>(framesB.receive())
            }

            // Two accepted writes from the same actor land in revision order.
            putState(rest, stateUrl, phone.token, 0, stateWith("a"))
            putState(rest, stateUrl, phone.token, 1, stateWith("a", "b"))

            val revisionsA = (1..2).map {
                (json.decodeFromString<SyncPushMessage>(withTimeout(5_000) { framesA.receive() })
                    as SyncPushMessage.StateUpdated).revision
            }
            val revisionsB = (1..2).map {
                (json.decodeFromString<SyncPushMessage>(withTimeout(5_000) { framesB.receive() })
                    as SyncPushMessage.StateUpdated).revision
            }
            assertEquals(listOf(1L, 2L), revisionsA)
            assertEquals(listOf(1L, 2L), revisionsB)

            receivedA.complete(Unit)
            receivedB.complete(Unit)
            socketA.cancel()
            socketB.cancel()
        }
        rest.close()
        wsA.close()
        wsB.close()
        server.stop(1000, 2000)
    }
}
