package com.numenlabs.zerophone.sync.server.auth

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

/** A freshly issued device token. The raw token exists only in this value. */
data class IssuedDeviceToken(val deviceId: String, val token: String)

/**
 * One stored device credential: only the SHA-256 hash of the token is
 * persisted, never the token itself. A device has at most one active
 * (non-revoked) record; revoked records are kept for audit and to prevent
 * hash reuse.
 */
@Serializable
data class DeviceTokenRecord(
    val deviceId: String,
    val tokenHash: String,
    val createdAtMillis: Long = 0L,
    val revoked: Boolean = false,
)

@Serializable
private data class DeviceTokenFile(val records: List<DeviceTokenRecord> = emptyList())

/**
 * File-backed JSON store of device token hashes: kotlinx.serialization,
 * atomic writes (tmp + rename) and an in-process [Mutex] around every
 * read and mutation. Rotating and revoking are plain store operations,
 * so a rotated or revoked token is rejected on the very next request —
 * no validity window, no denylist reconciliation.
 */
class DeviceTokenStore(
    private val file: Path,
    private val clock: () -> Long = System::currentTimeMillis,
    private val tokenGenerator: () -> String = OpaqueTokens::generate,
) {

    private val mutex = Mutex()
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    /**
     * Issues a new token for [deviceId], atomically replacing any previous
     * record of the device (re-pairing invalidates the old token).
     */
    suspend fun issue(deviceId: String): IssuedDeviceToken = mutate { records ->
        val token = tokenGenerator()
        records.removeAll { it.deviceId == deviceId }
        records += DeviceTokenRecord(
            deviceId = deviceId,
            tokenHash = TokenHasher.sha256(token),
            createdAtMillis = clock(),
        )
        IssuedDeviceToken(deviceId, token)
    }

    /** Returns the deviceId for a valid, non-revoked token — or null. */
    suspend fun authenticate(token: String): String? = read { records ->
        val hash = TokenHasher.sha256(token)
        records.firstOrNull { it.tokenHash == hash && !it.revoked }?.deviceId
    }

    /**
     * Replaces the token hash of the device's active record. The old hash
     * disappears from the store atomically, so the old token fails with
     * 401 on the very next request. Returns null if the device has no
     * active record.
     */
    suspend fun rotate(deviceId: String): IssuedDeviceToken? = mutate { records ->
        val index = records.indexOfFirst { it.deviceId == deviceId && !it.revoked }
        if (index < 0) {
            null
        } else {
            val token = tokenGenerator()
            records[index] = records[index].copy(
                tokenHash = TokenHasher.sha256(token),
                createdAtMillis = clock(),
            )
            IssuedDeviceToken(deviceId, token)
        }
    }

    /**
     * Revokes every active record of the device. All subsequent requests
     * presenting any of the device's tokens are rejected. Returns false
     * if there was no active record to revoke.
     */
    suspend fun revoke(deviceId: String): Boolean = mutate { records ->
        var changed = false
        for (index in records.indices) {
            if (records[index].deviceId == deviceId && !records[index].revoked) {
                records[index] = records[index].copy(revoked = true)
                changed = true
            }
        }
        changed
    }

    private suspend fun <T> read(
        block: (MutableList<DeviceTokenRecord>) -> T,
    ): T = mutex.withLock { block(load().toMutableList()) }

    private suspend fun <T> mutate(
        block: (MutableList<DeviceTokenRecord>) -> T,
    ): T = mutex.withLock {
        val records = load().toMutableList()
        val result = block(records)
        persist(DeviceTokenFile(records.toList()))
        result
    }

    private fun load(): List<DeviceTokenRecord> {
        if (!Files.isRegularFile(file)) return emptyList()
        return runCatching {
            json.decodeFromString(DeviceTokenFile.serializer(), file.readText()).records
        }.getOrDefault(emptyList())
    }

    private fun persist(state: DeviceTokenFile) {
        file.parent?.createDirectories()
        val tmp = file.resolveSibling(file.fileName.toString() + ".tmp")
        tmp.writeText(json.encodeToString(DeviceTokenFile.serializer(), state))
        try {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (expected: AtomicMoveNotSupportedException) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
