package com.numenlabs.zerophone.feature.home

import com.numenlabs.zerophone.core.context.AvailabilityState
import com.numenlabs.zerophone.core.context.LogicalCapabilities

/**
 * ZeroLauncher quick actions ("Позвонить / Написать / Доехать / Заплатить /
 * Камера"). Each action binds to a logical capability resolved by the same
 * contextual [engine][com.numenlabs.zerophone.core.context.RuleEngine] that
 * governs package availability — the engine state decides whether the action
 * is offered at all.
 */
enum class QuickAction(val capabilityId: String) {
    CALL(LogicalCapabilities.CALL),
    MESSAGE(LogicalCapabilities.MESSAGE),
    NAVIGATE(LogicalCapabilities.NAVIGATE),
    PAY(LogicalCapabilities.PAY),
    CAMERA(LogicalCapabilities.CAMERA),

    /**
     * "Отправить на ПК": relays a link to the paired desktop over the
     * self-hosted sync server. Not an engine-governed capability — its
     * availability comes from the sync wiring, not the contextual engine.
     */
    SEND_TO_PC(SEND_TO_PC_CAPABILITY);
}

/** Capability id of the send-to-PC quick action (outside the engine catalog). */
const val SEND_TO_PC_CAPABILITY: String = "send-to-pc"

/**
 * A quick action is usable in every engine state except a full block:
 * AVAILABLE, RESTRICTED, TEMPORARILY_AVAILABLE and CONTEXTUAL all remain
 * actionable (the engine only decides availability, the action stays useful).
 */
val AvailabilityState.allowsQuickAction: Boolean
    get() = this != AvailabilityState.Blocked
