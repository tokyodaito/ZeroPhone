package com.numenlabs.zerophone.core.policy

import com.numenlabs.zerophone.core.context.AvailabilityState
import com.numenlabs.zerophone.core.context.CapabilityRef

/**
 * Pure Kotlin translation from contextual-engine decisions to the suspend set.
 *
 * Only a capability explicitly resolved to [AvailabilityState.Blocked] is
 * suspended — and only if it is already part of the guarded candidate set
 * computed by [SuspendPolicy] (self / allowlist / protected packages /
 * protected prefixes are filtered there first). Restricted, Temporary and
 * Contextual states stay runnable; the states that cannot be expressed by
 * package suspension are enforced at launcher level.
 */
object EngineSuspendPolicy {

    fun computeSuspendSet(
        suspendCandidates: Set<String>,
        states: Map<CapabilityRef, AvailabilityState>
    ): Set<String> = suspendCandidates.filter { packageName ->
        states[CapabilityRef.Package(packageName)] == AvailabilityState.Blocked
    }.toSet()
}
