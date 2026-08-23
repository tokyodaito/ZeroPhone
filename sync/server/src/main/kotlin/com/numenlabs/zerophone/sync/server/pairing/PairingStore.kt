package com.numenlabs.zerophone.sync.server.pairing

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

/** Outcome of claiming one pairing shortcode. */
sealed interface PairingClaimResult {

    /** The code was valid and unclaimed; [deviceId] identifies the paired device. */
    data class Claimed(val deviceId: String) : PairingClaimResult

    /** No such outstanding code (unknown, expired or never issued). */
    data object UnknownCode : PairingClaimResult

    /** The code was already consumed — every shortcode is single-use. */
    data object AlreadyClaimed : PairingClaimResult
}

@Serializable
private data class PairingCodeRecord(
    val code: String,
    val createdAtMillis: Long,
    val ttlMillis: Long = PairingStore.DEFAULT_TTL_MILLIS,
    val claimedDeviceId: String? = null,
)

@Serializable
private data class PairingCodeFile(val codes: List<PairingCodeRecord> = emptyList())

/**
 * File-backed JSON store of outstanding pairing shortcodes: one code is
 * issued at a time (the newest wins), lives for its TTL and is strictly
 * single-use — a second claim gets [PairingClaimResult.AlreadyClaimed]
 * until the code expires and is pruned. Every read and mutation runs
 * under an in-process [Mutex] with atomic tmp+rename writes, mirroring
 * the persistence conventions of the token and state stores.
 */
class PairingStore(
    private val file: Path,
    private val clock: () -> Long = System::currentTimeMillis,
    private val codeGenerator: () -> String = Shortcodes::generate,
) {

    private val mutex = Mutex()
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    /** Issues a fresh shortcode, replacing any outstanding one. */
    suspend fun issue(ttlMillis: Long = DEFAULT_TTL_MILLIS): String = mutate { records ->
        pruneExpired(records)
        records.removeAll { it.claimedDeviceId == null }
        val code = uniqueCode(records)
        records += PairingCodeRecord(
            code = code,
            createdAtMillis = clock(),
            ttlMillis = ttlMillis,
        )
        code
    }

    suspend fun claim(code: String): PairingClaimResult = mutate { records ->
        pruneExpired(records)
        val record = records.firstOrNull { it.code == code }
            ?: return@mutate PairingClaimResult.UnknownCode
        if (record.claimedDeviceId != null) {
            return@mutate PairingClaimResult.AlreadyClaimed
        }
        val deviceId = PairingIds.newDeviceId()
        records[records.indexOf(record)] = record.copy(claimedDeviceId = deviceId)
        PairingClaimResult.Claimed(deviceId)
    }

    private fun uniqueCode(records: MutableList<PairingCodeRecord>): String {
        var code = codeGenerator()
        while (records.any { it.code == code }) code = codeGenerator()
        return code
    }

    private fun pruneExpired(records: MutableList<PairingCodeRecord>) {
        val now = clock()
        records.removeAll { now - it.createdAtMillis >= it.ttlMillis }
    }

    private suspend fun <T> mutate(
        block: (MutableList<PairingCodeRecord>) -> T,
    ): T = mutex.withLock {
        val records = load().toMutableList()
        val result = block(records)
        persist(PairingCodeFile(records.toList()))
        result
    }

    private fun load(): List<PairingCodeRecord> {
        if (!Files.isRegularFile(file)) return emptyList()
        return runCatching {
            json.decodeFromString(PairingCodeFile.serializer(), file.readText()).codes
        }.getOrDefault(emptyList())
    }

    private fun persist(state: PairingCodeFile) {
        file.parent?.createDirectories()
        val tmp = file.resolveSibling(file.fileName.toString() + ".tmp")
        tmp.writeText(json.encodeToString(PairingCodeFile.serializer(), state))
        try {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (expected: AtomicMoveNotSupportedException) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    companion object {
        const val FILE_NAME = "pairing-codes.json"
        const val DEFAULT_TTL_MILLIS: Long = 10 * 60 * 1000L
    }
}
