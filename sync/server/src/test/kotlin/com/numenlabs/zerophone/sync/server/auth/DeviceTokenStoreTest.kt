package com.numenlabs.zerophone.sync.server.auth

import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

class DeviceTokenStoreTest {

    private lateinit var dir: Path
    private lateinit var file: Path

    /** Deterministic sequence: token-0, token-1, ... */
    private var counter = 0
    private val generator = { "token-${counter++}" }

    private fun newStore() = DeviceTokenStore(
        file = file,
        clock = { 1_000L + counter },
        tokenGenerator = generator,
    )

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("device-token-store")
        file = dir.resolve("device-tokens.json")
    }

    @After
    fun tearDown() {
        dir.toFile().deleteRecursively()
    }

    @Test
    fun `issued token authenticates and only its hash is persisted`() = runTest {
        val store = newStore()
        val issued = store.issue("desktop-1")

        assertEquals("desktop-1", issued.deviceId)
        assertEquals("token-0", issued.token)
        assertEquals("desktop-1", store.authenticate("token-0"))

        val persisted = Files.readString(file)
        assertFalse("raw token must never be persisted", persisted.contains("token-0"))
        assertTrue("hash must be persisted", persisted.contains(TokenHasher.sha256("token-0")))
    }

    @Test
    fun `unknown and malformed tokens do not authenticate`() = runTest {
        val store = newStore()
        store.issue("desktop-1")

        assertNull(store.authenticate("wrong-token"))
        assertNull(store.authenticate(""))
        assertNull(store.authenticate("abc.def"))
    }

    @Test
    fun `rotation issues a new token and immediately invalidates the old one`() = runTest {
        val store = newStore()
        val first = store.issue("desktop-1")

        val second = store.rotate("desktop-1")

        assertNotNull(second)
        assertEquals("desktop-1", second!!.deviceId)
        assertTrue("rotation must mint a different token", second.token != first.token)
        assertNull("old token must be dead immediately", store.authenticate(first.token))
        assertEquals("desktop-1", store.authenticate(second.token))
    }

    @Test
    fun `rotation of a device without an active record fails`() = runTest {
        val store = newStore()

        assertNull(store.rotate("unknown-device"))
    }

    @Test
    fun `revocation blocks all subsequent authentication of the device`() = runTest {
        val store = newStore()
        val issued = store.issue("phone-1")

        assertTrue(store.revoke("phone-1"))

        assertNull("revoked token must be rejected", store.authenticate(issued.token))
        assertNull("rotation after revocation must fail", store.rotate("phone-1"))
        assertFalse("second revoke has nothing to do", store.revoke("phone-1"))
    }

    @Test
    fun `re-issue for the same device replaces the previous token`() = runTest {
        val store = newStore()
        val first = store.issue("desktop-1")

        val second = store.issue("desktop-1")

        assertNull("old token dies on re-pair", store.authenticate(first.token))
        assertEquals("desktop-1", store.authenticate(second.token))
    }

    @Test
    fun `state survives a store reload from the same file`() = runTest {
        val first = newStore()
        val issued = first.issue("desktop-1")
        first.rotate("desktop-1")

        val reloaded = DeviceTokenStore(
            file = file,
            clock = { 0L },
            tokenGenerator = generator,
        )
        assertNull("pre-rotation token is still dead after reload", reloaded.authenticate(issued.token))
        assertNotNull("active token survives reload", reloaded.rotate("desktop-1"))

        val revoked = newStore()
        revoked.issue("desktop-2")
        revoked.revoke("desktop-2")
        val revokedReloaded = DeviceTokenStore(file = file, clock = { 0L }, tokenGenerator = generator)
        assertNull("revoked record stays revoked after reload", revokedReloaded.authenticate("token-1"))
        val persisted = Files.readString(file)
        assertTrue(
            "revoked flag must be persisted",
            persisted.contains("\"revoked\": true") || persisted.contains("\"revoked\":true"),
        )
    }

    @Test
    fun `each device keeps its own independent token`() = runTest {
        val store = newStore()
        val phone = store.issue("phone-1")
        val desktop = store.issue("desktop-1")

        assertEquals("phone-1", store.authenticate(phone.token))
        assertEquals("desktop-1", store.authenticate(desktop.token))

        assertTrue(store.revoke("phone-1"))
        assertEquals("desktop-1", store.authenticate(desktop.token))
        assertNull(store.authenticate(phone.token))
    }

    @Test
    fun `mutations leave no temporary files behind`() = runTest {
        val store = newStore()
        store.issue("desktop-1")
        store.rotate("desktop-1")
        store.revoke("desktop-1")

        val names = Files.list(dir).use { stream -> stream.map { it.fileName.toString() }.toList() }
        assertEquals(listOf("device-tokens.json"), names)
    }

    @Test
    fun `generated opaque tokens carry 256 bits of entropy`() {
        repeat(64) {
            val token = OpaqueTokens.generate()
            val bytes = java.util.Base64.getUrlDecoder().decode(token)
            assertEquals(OpaqueTokens.TOKEN_BYTES, bytes.size)
        }
        val distinct = (1..256).map { OpaqueTokens.generate() }.toSet()
        assertEquals(256, distinct.size)
    }

    @Test
    fun `hashing is stable and hex encoded`() {
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            TokenHasher.sha256(""),
        )
        assertTrue(TokenHasher.sha256("token").matches(Regex("[0-9a-f]{64}")))
    }
}
