package com.numenlabs.zerophone.desktop.sync

import com.numenlabs.zerophone.core.model.DeviceCredentials
import com.numenlabs.zerophone.core.model.LinkPayload
import com.numenlabs.zerophone.desktop.state.InboxItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.Path

class LocalStoresTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `token roundtrip in config directory`() {
        val store = DeviceTokenStore(temp.newFolder().toPath())
        assertNull(store.load())

        val credentials = DeviceCredentials(
            deviceId = "desktop-1",
            token = "secret",
            serverUrl = "https://sync.example.com",
            pairedAtMillis = 42
        )
        store.save(credentials)
        assertEquals(credentials, store.load())
        assertTrue(Files.isRegularFile(store.path))
    }

    @Test
    fun `token survives a new store instance over the same directory`() {
        val dir = temp.newFolder().toPath()
        DeviceTokenStore(dir).save(DeviceCredentials(deviceId = "d", token = "t"))
        assertEquals("t", DeviceTokenStore(dir).load()?.token)
    }

    @Test
    fun `clear removes the stored token - pairing reset`() {
        val store = DeviceTokenStore(temp.newFolder().toPath())
        store.save(DeviceCredentials(deviceId = "d", token = "t"))
        assertTrue(store.clear())
        assertNull(store.load())
        assertFalse(store.clear())
    }

    @Test
    fun `malformed token file loads as null instead of crashing`() {
        val dir = temp.newFolder().toPath()
        val file = dir.resolve(DeviceTokenStore.FILE_NAME)
        Files.write(file, "not json".toByteArray())
        assertNull(DeviceTokenStore(dir).load())
    }

    @Test
    fun `token file is written with restrictive permissions best effort`() {
        val store = DeviceTokenStore(temp.newFolder().toPath())
        store.save(DeviceCredentials(deviceId = "d", token = "t"))
        try {
            val perms = Files.getPosixFilePermissions(store.path)
            assertEquals(
                setOf(
                    java.nio.file.attribute.PosixFilePermission.OWNER_READ,
                    java.nio.file.attribute.PosixFilePermission.OWNER_WRITE
                ),
                perms
            )
        } catch (unsupported: UnsupportedOperationException) {
            // Non-POSIX filesystem: best effort only, nothing to assert.
        }
    }

    @Test
    fun `inbox roundtrip and restart persistence`() {
        val dir = temp.newFolder().toPath()
        val store = InboxStore(dir)
        assertEquals(emptyList<InboxItem>(), store.load())

        val items = listOf(
            InboxItem(LinkPayload(id = "a", url = "https://a"), receivedAtMillis = 1, isOpened = true),
            InboxItem(LinkPayload(id = "b", url = "https://b"), receivedAtMillis = 2)
        )
        store.save(items)
        assertEquals(items, store.load())
        assertEquals(items, InboxStore(dir).load())
    }

    @Test
    fun `missing or malformed inbox file loads as empty`() {
        val empty = InboxStore(temp.newFolder().toPath())
        assertEquals(emptyList<InboxItem>(), empty.load())

        val brokenDir = temp.newFolder().toPath()
        Files.write(brokenDir.resolve(InboxStore.FILE_NAME), "[{bad".toByteArray())
        assertEquals(emptyList<InboxItem>(), InboxStore(brokenDir).load())
    }

    @Test
    fun `config dir follows the platform convention`() {
        val windows = ConfigDirs.appConfigDir(
            osName = "Windows 11",
            env = mapOf("APPDATA" to "C:\\Users\\u\\AppData\\Roaming"),
            userHome = "C:\\Users\\u"
        )
        assertEquals(Path.of("C:\\Users\\u\\AppData\\Roaming", "ZeroPhone"), windows)

        val windowsFallback = ConfigDirs.appConfigDir(
            osName = "Windows 11",
            env = emptyMap(),
            userHome = "C:\\Users\\u"
        )
        assertEquals(Path.of("C:\\Users\\u", "AppData", "Roaming", "ZeroPhone"), windowsFallback)

        val mac = ConfigDirs.appConfigDir(
            osName = "Mac OS X",
            env = emptyMap(),
            userHome = "/Users/u"
        )
        assertEquals(Path.of("/Users/u", "Library", "Application Support", "ZeroPhone"), mac)

        val linux = ConfigDirs.appConfigDir(
            osName = "Linux",
            env = emptyMap(),
            userHome = "/home/u"
        )
        assertEquals(Path.of("/home/u", ".config", "ZeroPhone"), linux)

        val linuxXdg = ConfigDirs.appConfigDir(
            osName = "Linux",
            env = mapOf("XDG_CONFIG_HOME" to "/custom/cfg"),
            userHome = "/home/u"
        )
        assertEquals(Path.of("/custom/cfg", "ZeroPhone"), linuxXdg)
    }
}
