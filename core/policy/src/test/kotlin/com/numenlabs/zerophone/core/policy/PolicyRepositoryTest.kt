package com.numenlabs.zerophone.core.policy

import com.numenlabs.zerophone.core.model.EmergencyWindow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-Kotlin suspend logic of the policy repository contract, tested against
 * [InMemoryPolicyRepository]. [DataStorePolicyRepository] implements the same
 * contract on Android (Preferences DataStore).
 */
class PolicyRepositoryTest {

    private val repo: PolicyRepository = InMemoryPolicyRepository()

    @Test
    fun `allowlist defaults to empty set`() = runTest {
        assertTrue(repo.getAllowlist().isEmpty())
    }

    @Test
    fun `emergency deadline defaults to none`() = runTest {
        assertEquals(EmergencyWindow.NONE_DEADLINE, repo.getEmergencyDeadline())
    }

    @Test
    fun `last suspended defaults to empty set`() = runTest {
        assertTrue(repo.getLastSuspended().isEmpty())
    }

    @Test
    fun `allowlist roundtrip persists`() = runTest {
        repo.setAllowlist(setOf("com.whatsapp", "org.telegram.messenger"))
        assertEquals(setOf("com.whatsapp", "org.telegram.messenger"), repo.getAllowlist())
    }

    @Test
    fun `empty allowlist overwrite clears the set`() = runTest {
        repo.setAllowlist(setOf("com.whatsapp"))
        repo.setAllowlist(emptySet())
        assertTrue(repo.getAllowlist().isEmpty())
    }

    @Test
    fun `emergency deadline roundtrip persists`() = runTest {
        val deadline = 1_770_000_000_000L
        repo.setEmergencyDeadline(deadline)
        assertEquals(deadline, repo.getEmergencyDeadline())
    }

    @Test
    fun `resetting deadline to none means no window`() = runTest {
        repo.setEmergencyDeadline(123L)
        repo.setEmergencyDeadline(EmergencyWindow.NONE_DEADLINE)
        assertEquals(EmergencyWindow.NONE_DEADLINE, repo.getEmergencyDeadline())
    }

    @Test
    fun `last suspended roundtrip persists`() = runTest {
        repo.setLastSuspended(setOf("com.example.game"))
        assertEquals(setOf("com.example.game"), repo.getLastSuspended())
    }

    @Test
    fun `allowlist toggle semantics used by setAllowed`() = runTest {
        suspend fun setAllowed(packageName: String, allowed: Boolean) {
            val updated = repo.getAllowlist().toMutableSet()
            val changed = if (allowed) updated.add(packageName) else updated.remove(packageName)
            if (changed) repo.setAllowlist(updated)
        }
        setAllowed("com.whatsapp", true)
        assertTrue("com.whatsapp" in repo.getAllowlist())
        // Setting the same value again must not rewrite (no-op check stays possible).
        setAllowed("com.whatsapp", true)
        assertEquals(setOf("com.whatsapp"), repo.getAllowlist())
        setAllowed("com.whatsapp", false)
        assertFalse("com.whatsapp" in repo.getAllowlist())
        assertTrue(repo.getAllowlist().isEmpty())
    }

    @Test
    fun `mutating a returned allowlist copy does not corrupt the repository`() = runTest {
        repo.setAllowlist(setOf("com.whatsapp", "org.telegram.messenger"))
        runCatching { (repo.getAllowlist() as? MutableSet<String>)?.clear() }
        assertEquals(setOf("com.whatsapp", "org.telegram.messenger"), repo.getAllowlist())
    }
}
