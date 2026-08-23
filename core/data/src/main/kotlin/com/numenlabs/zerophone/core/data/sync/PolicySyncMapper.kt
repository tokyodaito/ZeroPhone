package com.numenlabs.zerophone.core.data.sync

import com.numenlabs.zerophone.core.model.SyncState
import com.numenlabs.zerophone.core.policy.PolicyRepository

/**
 * Domain ↔ wire mapping between the policy persistence and [SyncState].
 * The DataStore-backed [PolicyRepository] stays the source of truth; sync
 * only carries the cross-device policy inputs:
 *  - active mode;
 *  - emergency window (as remaining time, so clocks never leak);
 *  - device identity of the last writer.
 * The resolved capability list is deliberately NOT synced — it is a
 * device-local computation of [com.numenlabs.zerophone.core.policy.PolicyApplier]
 * over the policy inputs.
 */
object PolicySyncMapper {

    /** Serializes the domain policy into the wire document (revision-less). */
    suspend fun PolicyRepository.toSyncState(deviceId: String, nowMillis: Long): SyncState =
        SyncState(
            policy = com.numenlabs.zerophone.core.model.PolicySnapshot(
                deviceId = deviceId,
                generatedAtMillis = nowMillis,
                activeMode = getActiveMode(),
                emergencyRemainingMillis = (getEmergencyDeadline() - nowMillis)
                    .coerceAtLeast(com.numenlabs.zerophone.core.model.EmergencyWindow.NONE_DEADLINE),
            ),
        )

    /** Applies the mappable subset of a remote document to the domain. */
    suspend fun SyncState.applyTo(repository: PolicyRepository, nowMillis: Long) {
        val mode = policy.activeMode
        if (!mode.isNullOrBlank() && mode != repository.getActiveMode()) {
            repository.setActiveMode(mode)
        }
        val remaining = policy.emergencyRemainingMillis
        val deadline = if (remaining > com.numenlabs.zerophone.core.model.EmergencyWindow.NONE_DEADLINE) {
            nowMillis + remaining
        } else {
            com.numenlabs.zerophone.core.model.EmergencyWindow.NONE_DEADLINE
        }
        if (deadline != repository.getEmergencyDeadline()) {
            repository.setEmergencyDeadline(deadline)
        }
    }
}
