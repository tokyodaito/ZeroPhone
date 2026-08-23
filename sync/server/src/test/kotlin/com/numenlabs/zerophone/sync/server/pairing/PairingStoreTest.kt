package com.numenlabs.zerophone.sync.server.pairing

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

/**
 * Pure store semantics of shortcode pairing: single-use codes, TTL
 * pruning, replacement of the outstanding code and persistence across
 * reloads — all with an injected clock and tmp directories.
 */
class PairingStoreTest {

    private lateinit var dir: Path
    private var now: Long = 1_000_000L
    private val generated = mutableListOf<String>()
    private var generatedIndex = 0

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("pairing-store-test")
    }

    @After
    fun tearDown() {
        dir.toFile().deleteRecursively()
    }

    private fun newStore(): PairingStore = PairingStore(
        file = dir.resolve(PairingStore.FILE_NAME),
        clock = { now },
        codeGenerator = ::nextCode,
    )

    /** Deterministic unique codes over the shortcode alphabet. */
    private fun nextCode(): String {
        val alphabet = Shortcodes.ALPHABET
        var value = generatedIndex++
        val code = buildString {
            repeat(Shortcodes.CODE_LENGTH) {
                append(alphabet[value % alphabet.length])
                value /= alphabet.length
            }
        }
        generated += code
        return code
    }

    @Test
    fun `issue returns a six-character code that can be claimed exactly once`() = runBlocking {
        val store = newStore()
        val code = store.issue()

        assertEquals(6, code.length)
        assertTrue(code.all { it in Shortcodes.ALPHABET })

        val first = store.claim(code)
        assertTrue(first is PairingClaimResult.Claimed)
        val deviceId = (first as PairingClaimResult.Claimed).deviceId
        assertTrue(deviceId.isNotBlank())

        assertEquals(PairingClaimResult.AlreadyClaimed, store.claim(code))
    }

    @Test
    fun `unknown and malformed codes are unknown`() = runBlocking {
        val store = newStore()
        store.issue()

        assertEquals(PairingClaimResult.UnknownCode, store.claim("000000"))
        assertEquals(PairingClaimResult.UnknownCode, store.claim(""))
    }

    @Test
    fun `codes expire after their ttl`() = runBlocking {
        val store = newStore()
        val code = store.issue(ttlMillis = 60_000L)

        now += 59_999
        assertTrue(store.claim(code) is PairingClaimResult.Claimed)

        val fresh = newStore().issue(ttlMillis = 60_000L)
        now += 60_000
        assertEquals(PairingClaimResult.UnknownCode, newStore().claim(fresh))
    }

    @Test
    fun `issuing replaces the outstanding code`() = runBlocking {
        val store = newStore()
        val first = store.issue()
        val second = store.issue()

        assertNotEquals(first, second)

        assertEquals(PairingClaimResult.UnknownCode, store.claim(first))
        assertTrue(store.claim(second) is PairingClaimResult.Claimed)
    }

    @Test
    fun `state survives a store reload from the same file`() = runBlocking {
        val code = newStore().issue()

        val reloaded = newStore()
        assertTrue(reloaded.claim(code) is PairingClaimResult.Claimed)

        assertEquals(
            "the claim is durable",
            PairingClaimResult.AlreadyClaimed,
            newStore().claim(code),
        )
    }

    @Test
    fun `mutations leave no temporary files behind`() = runBlocking {
        val store = newStore()
        val code = store.issue()
        store.claim(code)

        val names = Files.list(dir).use { it.map { it.fileName.toString() }.toList() }
        assertEquals(listOf(PairingStore.FILE_NAME), names)
    }
}
