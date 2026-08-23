package com.numenlabs.zerophone.core.data.sync

import com.numenlabs.zerophone.core.model.EmergencyWindow
import com.numenlabs.zerophone.core.model.SyncState
import com.numenlabs.zerophone.core.model.PolicySnapshot
import com.numenlabs.zerophone.core.policy.InMemoryPolicyRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [PolicySyncMapper] round-trips the cross-device policy subset: active
 * mode and the emergency window (as remaining time, never as wall clock).
 */
class PolicySyncMapperTest {

    @Test
    fun `domain serializes into a revision-less sync state`() = runTest {
        val repository = InMemoryPolicyRepository()
        repository.setActiveMode("rest")
        repository.setEmergencyDeadline(150_000L)

        val state = with(PolicySyncMapper) {
            repository.toSyncState(deviceId = "phone-9", nowMillis = 100_000L)
        }

        assertEquals("phone-9", state.policy.deviceId)
        assertEquals("rest", state.policy.activeMode)
        assertEquals(50_000L, state.policy.emergencyRemainingMillis)
        assertEquals(100_000L, state.policy.generatedAtMillis)
        assertEquals(emptyList<Any>(), state.policy.capabilities)
    }

    @Test
    fun `an expired emergency deadline serializes as none`() = runTest {
        val repository = InMemoryPolicyRepository()
        repository.setEmergencyDeadline(40_000L)

        val state = with(PolicySyncMapper) {
            repository.toSyncState(deviceId = "d", nowMillis = 100_000L)
        }
        assertEquals(EmergencyWindow.NONE_DEADLINE, state.policy.emergencyRemainingMillis)
    }

    @Test
    fun `a remote document applies mode and emergency window`() = runTest {
        val repository = InMemoryPolicyRepository()
        repository.setActiveMode("work")
        repository.setEmergencyDeadline(EmergencyWindow.NONE_DEADLINE)

        val remote = SyncState(
            policy = PolicySnapshot(
                deviceId = "desktop",
                activeMode = "focus",
                emergencyRemainingMillis = 30_000L,
            ),
        )

        with(PolicySyncMapper) { remote.applyTo(repository, nowMillis = 200_000L) }

        assertEquals("focus", repository.getActiveMode())
        assertEquals(230_000L, repository.getEmergencyDeadline())
    }

    @Test
    fun `a remote document without emergency closes the local window`() = runTest {
        val repository = InMemoryPolicyRepository()
        repository.setActiveMode("work")
        repository.setEmergencyDeadline(500_000L)

        val remote = SyncState(policy = PolicySnapshot(deviceId = "d", activeMode = "work"))

        with(PolicySyncMapper) { remote.applyTo(repository, nowMillis = 200_000L) }

        assertEquals("work", repository.getActiveMode())
        assertEquals(EmergencyWindow.NONE_DEADLINE, repository.getEmergencyDeadline())
    }

    @Test
    fun `a blank remote mode is ignored`() = runTest {
        val repository = InMemoryPolicyRepository()
        repository.setActiveMode("rest")

        val remote = SyncState(policy = PolicySnapshot(deviceId = "d", activeMode = ""))

        with(PolicySyncMapper) { remote.applyTo(repository, nowMillis = 0L) }

        assertEquals("rest", repository.getActiveMode())
    }
}
