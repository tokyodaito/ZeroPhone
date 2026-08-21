package com.numenlabs.zerophone.core.context

import kotlinx.serialization.Serializable

/**
 * A capability of the phone the contextual engine resolves a state for.
 * Either a concrete package ("can this app run?") or a logical capability
 * ("can I call / navigate / pay?") used by quick actions and future features.
 */
@Serializable
sealed interface CapabilityRef {

    /** Stable identity used by rules, grants and budget ledgers. */
    val id: String

    @Serializable
    data class Package(val packageName: String) : CapabilityRef {
        override val id: String get() = packageName
    }

    @Serializable
    data class Logical(val name: String) : CapabilityRef {
        override val id: String get() = name
    }
}
