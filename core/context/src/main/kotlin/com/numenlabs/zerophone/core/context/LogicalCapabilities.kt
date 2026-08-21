package com.numenlabs.zerophone.core.context

/**
 * Stable identifiers of the logical capabilities used by ZeroLauncher quick
 * actions and mode rules ("can I call / write / navigate / pay / shoot?").
 * Distinct from package capabilities: a logical capability is resolved by the
 * same [RuleEngine], but is never tied to package suspension.
 */
object LogicalCapabilities {
    const val CALL = "call"
    const val MESSAGE = "message"
    const val NAVIGATE = "navigate"
    const val PAY = "pay"
    const val CAMERA = "camera"

    val ALL = listOf(CALL, MESSAGE, NAVIGATE, PAY, CAMERA)
}
