package com.numenlabs.zerophone.sync.server.state

import com.numenlabs.zerophone.core.model.StateEnvelope
import com.numenlabs.zerophone.core.model.SyncState
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.createDirectories
import kotlin.io.path.readText
import kotlin.io.path.writeText

/** Outcome of one conditional write attempt against [StateStore]. */
sealed interface StateCommit {

    /**
     * The write won: [envelope] holds the written state and the freshly
     * assigned revision (`baseRevision + 1`).
     */
    data class Accepted(val envelope: StateEnvelope) : StateCommit

    /**
     * The writer was behind: [envelope] holds the current revision and
     * state that beat it, so the loser can rebase its intended change on
     * top of them and retry — nothing is lost silently.
     */
    data class Conflict(val envelope: StateEnvelope) : StateCommit

    /** The writer claims a revision from the future — a protocol error. */
    data object BaseAhead : StateCommit
}

/**
 * File-backed JSON store of the synced state document: the serialized
 * [SyncState] payload and its revision live side by side in one file,
 * written atomically (tmp + rename) under an in-process [Mutex].
 *
 * The mutex turns concurrent writers into a total order: the first
 * check-and-increment wins, every later writer with the same base
 * deterministically gets [StateCommit.Conflict]. [commit] runs the
 * [onAccepted] hook inside the same critical section, so pushes are
 * broadcast strictly in revision order.
 */
class StateStore(private val file: Path) {

    private val mutex = Mutex()
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    /** Current document and revision — the exact GET response body. */
    suspend fun current(): StateEnvelope = mutex.withLock { load() }

    /**
     * Optimistic-concurrency write: accepted only while [baseRevision]
     * equals the current revision, in which case the stored revision
     * becomes `baseRevision + 1`. The revision counter is monotonic
     * because it only ever moves forward inside this critical section.
     */
    suspend fun commit(
        baseRevision: Long,
        newState: SyncState,
        onAccepted: (revision: Long) -> Unit = {},
    ): StateCommit = mutex.withLock {
        val current = load()
        when {
            baseRevision == current.revision -> {
                val accepted = StateEnvelope(state = newState, revision = current.revision + 1)
                persist(accepted)
                onAccepted(accepted.revision)
                StateCommit.Accepted(accepted)
            }

            baseRevision < current.revision -> StateCommit.Conflict(current)

            else -> StateCommit.BaseAhead
        }
    }

    private fun load(): StateEnvelope {
        if (!Files.isRegularFile(file)) return StateEnvelope()
        return runCatching {
            json.decodeFromString(StateEnvelope.serializer(), file.readText())
        }.getOrDefault(StateEnvelope())
    }

    private fun persist(envelope: StateEnvelope) {
        file.parent?.createDirectories()
        val tmp = file.resolveSibling(file.fileName.toString() + ".tmp")
        tmp.writeText(json.encodeToString(StateEnvelope.serializer(), envelope))
        try {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (expected: AtomicMoveNotSupportedException) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
