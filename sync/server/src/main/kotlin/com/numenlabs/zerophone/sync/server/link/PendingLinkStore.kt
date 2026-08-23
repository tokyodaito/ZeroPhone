package com.numenlabs.zerophone.sync.server.link

import com.numenlabs.zerophone.core.model.LinkPayload
import com.numenlabs.zerophone.core.model.SyncPushMessage
import com.numenlabs.zerophone.sync.server.state.WsBroadcaster
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.createDirectories
import kotlin.io.path.readText
import kotlin.io.path.writeText

@Serializable
private data class PendingLinkRecord(
    val link: LinkPayload,
    val expiresAtMillis: Long,
)

@Serializable
private data class PendingLinkFile(val links: List<PendingLinkRecord> = emptyList())

/**
 * File-backed store-and-forward queue of links sent from the phone
 * ("отправить на ПК") to desktops that may be offline: every accepted
 * link is persisted (one JSON document, atomic tmp+rename under an
 * in-process [Mutex], mirroring the token/state/pairing stores) and
 * survives a server restart; delivering it is a separate step.
 *
 * [flushTo] is the delivery half: when at least one WebSocket session
 * is live, every non-expired link is broadcast to all of them as
 * `link.received` and forgotten; with no live sessions the queue stays
 * intact for the next connect. Expired links are pruned lazily on
 * every operation.
 */
class PendingLinkStore(
    private val file: Path,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    private val mutex = Mutex()
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    /** Appends [link] to the queue with the default 24h TTL. */
    suspend fun enqueue(
        link: LinkPayload,
        ttlMillis: Long = DEFAULT_TTL_MILLIS,
    ): Unit = mutate { records ->
        pruneExpired(records)
        records += PendingLinkRecord(link = link, expiresAtMillis = clock() + ttlMillis)
    }

    /** Non-expired links still awaiting delivery, oldest first. */
    suspend fun pending(): List<LinkPayload> = mutex.withLock {
        val records = load().toMutableList()
        pruneExpired(records)
        persist(PendingLinkFile(records.toList()))
        records.map { it.link }
    }

    /**
     * Broadcasts the whole non-expired queue to every live session as
     * `link.received` and removes the delivered links; with no live
     * session nothing is sent and the queue is left untouched. Returns
     * the number of delivered links.
     */
    suspend fun flushTo(broadcaster: WsBroadcaster): Int = mutex.withLock {
        val records = load().toMutableList()
        pruneExpired(records)
        if (!broadcaster.hasSessions() || records.isEmpty()) {
            persist(PendingLinkFile(records.toList()))
            return@withLock 0
        }
        val links = records.map { it.link }
        persist(PendingLinkFile(emptyList()))
        links.forEach { broadcaster.broadcast(SyncPushMessage.LinkReceived(it)) }
        links.size
    }

    private fun pruneExpired(records: MutableList<PendingLinkRecord>) {
        val now = clock()
        records.removeAll { now >= it.expiresAtMillis }
    }

    private suspend fun <T> mutate(
        block: (MutableList<PendingLinkRecord>) -> T,
    ): T = mutex.withLock {
        val records = load().toMutableList()
        val result = block(records)
        persist(PendingLinkFile(records.toList()))
        result
    }

    private fun load(): List<PendingLinkRecord> {
        if (!Files.isRegularFile(file)) return emptyList()
        return runCatching {
            json.decodeFromString(PendingLinkFile.serializer(), file.readText()).links
        }.getOrDefault(emptyList())
    }

    private fun persist(state: PendingLinkFile) {
        file.parent?.createDirectories()
        val tmp = file.resolveSibling(file.fileName.toString() + ".tmp")
        tmp.writeText(json.encodeToString(PendingLinkFile.serializer(), state))
        try {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (expected: AtomicMoveNotSupportedException) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    companion object {
        const val FILE_NAME = "pending-links.json"
        const val DEFAULT_TTL_MILLIS: Long = 24 * 60 * 60 * 1000L
    }
}
