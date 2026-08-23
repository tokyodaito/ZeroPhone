package com.numenlabs.zerophone.sync.server.state

import com.numenlabs.zerophone.core.model.Availability
import com.numenlabs.zerophone.core.model.CapabilityAvailability
import com.numenlabs.zerophone.core.model.PolicySnapshot
import com.numenlabs.zerophone.core.model.SyncState
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

class StateStoreTest {

    private lateinit var dir: Path
    private lateinit var file: Path

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("state-store-test")
        file = dir.resolve("state.json")
    }

    @After
    fun tearDown() {
        dir.toFile().deleteRecursively()
    }

    private fun stateWith(vararg capabilityIds: String): SyncState = SyncState(
        policy = PolicySnapshot(
            deviceId = "phone-1",
            capabilities = capabilityIds.map {
                CapabilityAvailability(id = it, state = Availability.AVAILABLE)
            },
        ),
    )

    @Test
    fun `fresh store starts at revision zero with an empty document`() = runTest {
        val store = StateStore(file)

        val current = store.current()

        assertEquals(0L, current.revision)
        assertEquals(SyncState(), current.state)
    }

    @Test
    fun `accepted commit persists the document with the next revision`() = runTest {
        val store = StateStore(file)
        val written = stateWith("phone-feature")

        val commit = store.commit(baseRevision = 0, newState = written)

        assertTrue(commit is StateCommit.Accepted)
        val accepted = commit as StateCommit.Accepted
        assertEquals(1L, accepted.envelope.revision)
        assertEquals(written, accepted.envelope.state)
        assertEquals(accepted.envelope, store.current())
    }

    @Test
    fun `commit with a stale base conflicts with the winning revision and state`() = runTest {
        val store = StateStore(file)
        store.commit(0, stateWith("winner"))

        val conflict = store.commit(0, stateWith("loser"))

        assertTrue(conflict is StateCommit.Conflict)
        val envelope = (conflict as StateCommit.Conflict).envelope
        assertEquals(1L, envelope.revision)
        assertEquals(stateWith("winner"), envelope.state)
        assertEquals(1L, store.current().revision)
    }

    @Test
    fun `commit with a base from the future is a protocol error`() = runTest {
        val store = StateStore(file)

        assertEquals(StateCommit.BaseAhead, store.commit(1, stateWith("future")))
        assertEquals(0L, store.current().revision)
    }

    @Test
    fun `racing commits from one base accept exactly one`() = runTest {
        val store = StateStore(file)

        val results = (1..2).map { index ->
            async { store.commit(0, stateWith("writer-$index")) }
        }.awaitAll()

        val accepted = results.filterIsInstance<StateCommit.Accepted>()
        val conflicts = results.filterIsInstance<StateCommit.Conflict>()
        assertEquals("exactly one writer must win", 1, accepted.size)
        assertEquals("everyone else conflicts deterministically", 1, conflicts.size)
        assertEquals(1L, accepted.single().envelope.revision)
        assertEquals(1L, conflicts.single().envelope.revision)
        assertEquals(1L, store.current().revision)
        assertEquals(accepted.single().envelope, store.current())
    }

    @Test
    fun `state and revision survive a store reload from the same file`() = runTest {
        val first = StateStore(file)
        first.commit(0, stateWith("a"))
        first.commit(1, stateWith("a", "b"))

        val reloaded = StateStore(file)
        val current = reloaded.current()

        assertEquals(2L, current.revision)
        assertEquals(stateWith("a", "b"), current.state)
        assertEquals(
            "the reloaded store keeps counting from the persisted revision",
            3L,
            (reloaded.commit(2, stateWith("a", "b", "c")) as StateCommit.Accepted)
                .envelope.revision,
        )
    }

    @Test
    fun `mutations leave no temporary files behind`() = runTest {
        val store = StateStore(file)
        store.commit(0, stateWith("a"))

        val names = Files.list(dir).use { stream -> stream.map { it.fileName.toString() }.toList() }
        assertEquals(listOf("state.json"), names)
    }

    @Test
    fun `accepted hook runs inside the mutex in revision order`() = runTest {
        val store = StateStore(file)
        val broadcastOrder = mutableListOf<Long>()

        store.commit(0, stateWith("a")) { revision -> broadcastOrder += revision }
        store.commit(1, stateWith("a", "b")) { revision -> broadcastOrder += revision }
        store.commit(2, stateWith("a", "b", "c")) { revision -> broadcastOrder += revision }

        assertEquals(listOf(1L, 2L, 3L), broadcastOrder)
    }
}
