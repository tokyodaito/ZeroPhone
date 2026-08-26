package com.numenlabs.zerophone.core.context

/**
 * Heuristic mapping of logical capabilities to the packages that typically
 * implement them, used to accrue daily time budgets from real foreground
 * usage (UsageStats). Deliberately conservative: an unmapped package simply
 * does not accrue the budget — nothing breaks.
 */
object CapabilityPackages {

    val PACKAGES: Map<String, Set<String>> = mapOf(
        LogicalCapabilities.CALL to setOf(
            "com.android.dialer",
            "com.google.android.dialer",
            "com.samsung.android.dialer",
            "com.oneplus.dialer",
            "com.android.incallui"
        ),
        LogicalCapabilities.MESSAGE to setOf(
            "com.google.android.apps.messaging",
            "com.android.mms",
            "com.samsung.android.messaging"
        ),
        LogicalCapabilities.NAVIGATE to setOf(
            "com.google.android.apps.maps",
            "com.yandex.yandexnavi"
        ),
        LogicalCapabilities.PAY to setOf(
            "com.google.android.apps.walletnfcrent",
            "com.google.android.apps.nbu.paisa.user"
        ),
        LogicalCapabilities.CAMERA to setOf(
            "com.android.camera2",
            "com.google.android.GoogleCamera",
            "com.sec.android.app.camera",
            "com.oneplus.camera",
            "com.android.camera"
        )
    )

    fun packagesOf(capabilityId: String): Set<String> = PACKAGES[capabilityId] ?: emptySet()
}
