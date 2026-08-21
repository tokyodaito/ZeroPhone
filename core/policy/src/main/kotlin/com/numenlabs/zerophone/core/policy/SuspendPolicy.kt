package com.numenlabs.zerophone.core.policy

/**
 * Pure Kotlin suspend-policy computation (no Android dependencies, unit-testable).
 *
 * Rules:
 *  - never suspend the app itself;
 *  - never suspend allowlisted packages;
 *  - never suspend explicitly protected packages (critical system apps, active IME,
 *    default launcher, packages that failed suspension earlier);
 *  - never suspend packages matching a protected prefix (defensive: any com.android.*).
 */
object SuspendPolicy {

    val DEFAULT_PROTECTED_PACKAGES: Set<String> = setOf(
        "com.android.phone",
        "com.android.dialer",
        "com.android.settings",
        "com.android.systemui",
        "com.android.packageinstaller",
        "com.android.installer"
    )

    val DEFAULT_PROTECTED_PREFIXES: Set<String> = setOf("com.android.")

    fun computeSuspendSet(
        selfPackage: String,
        launchablePackages: Set<String>,
        allowlist: Set<String>,
        protectedPackages: Set<String> = DEFAULT_PROTECTED_PACKAGES,
        protectedPrefixes: Set<String> = DEFAULT_PROTECTED_PREFIXES
    ): Set<String> =
        launchablePackages.asSequence()
            .filter { it != selfPackage }
            .filter { it !in allowlist }
            .filter { it !in protectedPackages }
            .filter { pkg -> protectedPrefixes.none { prefix -> pkg.startsWith(prefix) } }
            .toSet()

    /**
     * Packages we suspended earlier that must be released now: only members of the
     * previous suspend set that left the new one (e.g. just allowlisted) — never
     * packages the policy did not suspend itself.
     */
    fun computeReleaseSet(lastSuspended: Set<String>, suspendSet: Set<String>): Set<String> =
        lastSuspended - suspendSet
}
