package com.numenlabs.zerophone.desktop.sync

import com.numenlabs.zerophone.core.model.DeviceCredentials
import com.numenlabs.zerophone.desktop.state.InboxItem
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import kotlin.io.path.createDirectories
import kotlin.io.path.readText
import kotlin.io.path.writeText

/** Pure resolution of the per-OS application config directory. */
object ConfigDirs {
    fun appConfigDir(
        osName: String,
        env: Map<String, String>,
        userHome: String,
        appName: String = "ZeroPhone",
    ): Path {
        val os = osName.lowercase()
        return when {
            os.contains("win") ->
                Path.of(env["APPDATA"] ?: Path.of(userHome, "AppData", "Roaming").toString(), appName)

            os.contains("mac") ->
                Path.of(userHome, "Library", "Application Support", appName)

            else ->
                Path.of(env["XDG_CONFIG_HOME"] ?: Path.of(userHome, ".config").toString(), appName)
        }
    }

    fun platform(appName: String = "ZeroPhone"): Path = appConfigDir(
        osName = System.getProperty("os.name") ?: "linux",
        env = System.getenv(),
        userHome = System.getProperty("user.home") ?: ".",
        appName = appName,
    )
}

/**
 * Device token storage: a JSON file in the OS config directory with
 * restrictive permissions (0600 best-effort, ignored on filesystems without
 * POSIX perms). Deleting the file resets the pairing.
 */
class DeviceTokenStore(dir: Path = ConfigDirs.platform()) {

    private val file: Path = dir.resolve(FILE_NAME)

    val path: Path get() = file

    fun load(): DeviceCredentials? {
        if (!Files.isRegularFile(file)) return null
        return runCatching { SyncMessageCodec.decodeCredentials(file.readText()) }.getOrNull()
    }

    fun save(credentials: DeviceCredentials) {
        file.parent?.createDirectories()
        file.writeText(SyncMessageCodec.encodeCredentials(credentials))
        restrictPermissions(file)
    }

    fun clear(): Boolean = Files.deleteIfExists(file)

    companion object {
        const val FILE_NAME = "device-token.json"

        internal fun restrictPermissions(file: Path) {
            try {
                Files.setPosixFilePermissions(
                    file,
                    PosixFilePermissions.fromString("rw-------")
                )
            } catch (expected: UnsupportedOperationException) {
                // Windows/other non-POSIX filesystems — best effort only.
            } catch (expected: Exception) {
                // Best effort: never fail saving because of permission hardening.
            }
        }
    }
}

/**
 * Local JSON persistence for the inbox: received links and their opened
 * marks survive application restarts.
 */
class InboxStore(dir: Path = ConfigDirs.platform()) {

    private val file: Path = dir.resolve(FILE_NAME)

    fun load(): List<InboxItem> {
        if (!Files.isRegularFile(file)) return emptyList()
        return runCatching { SyncMessageCodec.decodeInbox(file.readText()) }.getOrNull() ?: emptyList()
    }

    fun save(items: List<InboxItem>) {
        file.parent?.createDirectories()
        file.writeText(SyncMessageCodec.encodeInbox(items))
        DeviceTokenStore.restrictPermissions(file)
    }

    companion object {
        const val FILE_NAME = "inbox.json"
    }
}
