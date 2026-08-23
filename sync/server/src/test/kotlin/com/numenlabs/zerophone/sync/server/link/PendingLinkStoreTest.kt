package com.numenlabs.zerophone.sync.server.link

import com.numenlabs.zerophone.core.model.LinkPayload
import com.numenlabs.zerophone.core.model.SyncPushMessage
import com.numenlabs.zerophone.sync.server.state.WsBroadcaster
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

/**
 * Pure store semantics of the send-to-PC queue: TTL pruning with an
 * injected clock, store-and-forward across reloads, and flushing only
 * when a session can actually receive.
 */
class PendingLinkStoreTest {

    private lateinit var dir: Path
    private var now: Long = 1_000_000L

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("pending-links-test")
    }

    @After
    fun tearDown() {
        dir.toFile().deleteRecursively()
    }

    private fun newStore(): PendingLinkStore =
        PendingLinkStore(file = dir.resolve(PendingLinkStore.FILE_NAME), clock = { now })

    private fun link(id: String): LinkPayload =
        LinkPayload(id = id, url = "https://example.com/$id", sentAtMillis = now)

    @Test
    fun `queued links are pending oldest first and survive a reload`() = runBlocking {
        newStore().enqueue(link("a"))
        newStore().enqueue(link("b"))

        val reloaded = newStore().pending().map { it.id }
        assertEquals(listOf("a", "b"), reloaded)
    }

    @Test
    fun `links expire after their ttl`() = runBlocking {
        val store = newStore()
        store.enqueue(link("short-lived"), ttlMillis = 60_000L)
        store.enqueue(link("durable"), ttlMillis = 24 * 60 * 60 * 1000L)

        now += 60_000
        assertEquals(listOf("durable"), store.pending().map { it.id })

        now += 24 * 60 * 60 * 1000L
        assertTrue("everything expired", store.pending().isEmpty())
    }

    @Test
    fun `flush without a live session keeps the queue intact`() = runBlocking {
        val store = newStore()
        store.enqueue(link("a"))
        store.enqueue(link("b"))

        assertEquals("nobody is connected, nothing is delivered", 0, store.flushTo(WsBroadcaster()))
        assertEquals(listOf("a", "b"), store.pending().map { it.id })
    }

    @Test
    fun `flush with a live session delivers everything and clears the queue`() = runBlocking {
        val store = newStore()
        val broadcaster = WsBroadcaster()
        store.enqueue(link("a"))
        store.enqueue(link("b"))

        val session = FakeSession()
        broadcaster.register(session)

        assertEquals(2, store.flushTo(broadcaster))
        assertEquals("the queue is drained by a successful flush", 0, store.flushTo(broadcaster))
        assertTrue(store.pending().isEmpty())
        assertEquals(listOf("a", "b"), session.receivedIds())

        broadcaster.unregister(session)
    }

    @Test
    fun `expired links are not delivered by a flush`() = runBlocking {
        val store = newStore()
        val broadcaster = WsBroadcaster()
        store.enqueue(link("expired"), ttlMillis = 60_000L)
        now += 60_000

        val session = FakeSession()
        broadcaster.register(session)

        assertEquals(0, store.flushTo(broadcaster))

        broadcaster.unregister(session)
    }

    @Test
    fun `mutations leave no temporary files behind`() = runBlocking {
        val store = newStore()
        store.enqueue(link("a"))
        store.enqueue(link("b"))

        val names = Files.list(dir).use { it.map { it.fileName.toString() }.toList() }
        assertEquals(listOf(PendingLinkStore.FILE_NAME), names)
    }

    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

    /**
     * A [io.ktor.websocket.Frame] sink around a real channel, so every
     * [kotlinx.coroutines.channels.SendChannel] member (including the
     * select clause) behaves as production code expects it to.
     */
    private class FakeSession(
        private val backing: Channel<Frame> = Channel(capacity = 8),
    ) : kotlinx.coroutines.channels.SendChannel<Frame> by backing {

        fun receivedIds(): List<String> =
            generateSequence { backing.tryReceive().getOrNull() }
                .map { json.decodeFromString(SyncPushMessage.serializer(), (it as Frame.Text).readText()) }
                .map { (it as SyncPushMessage.LinkReceived).payload.id }
                .toList()

        private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
    }
}
