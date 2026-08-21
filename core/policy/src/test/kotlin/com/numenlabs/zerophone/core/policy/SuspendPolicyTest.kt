package com.numenlabs.zerophone.core.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SuspendPolicyTest {

    private val self = "com.numenlabs.zerophone"
    private val launchable = setOf(
        self,
        "com.android.settings",
        "com.android.dialer",
        "com.android.camera2",
        "com.whatsapp",
        "org.telegram.messenger",
        "com.example.game"
    )

    private fun compute(allowlist: Set<String> = emptySet()) = SuspendPolicy.computeSuspendSet(
        selfPackage = self,
        launchablePackages = launchable,
        allowlist = allowlist
    )

    @Test
    fun `never suspends itself even without allowlist entry`() {
        assertFalse(compute().contains(self))
    }

    @Test
    fun `suspends third-party launchable apps outside allowlist`() {
        assertEquals(setOf("com.whatsapp", "org.telegram.messenger", "com.example.game"), compute())
    }

    @Test
    fun `allowlisted apps are not suspended`() {
        val result = compute(allowlist = setOf("com.whatsapp", "com.example.game"))
        assertEquals(setOf("org.telegram.messenger"), result)
    }

    @Test
    fun `critical system packages are never suspended`() {
        val result = SuspendPolicy.computeSuspendSet(
            selfPackage = self,
            launchablePackages = setOf(self, "com.android.phone", "com.android.systemui", "com.android.settings", "com.whatsapp"),
            allowlist = emptySet(),
            protectedPackages = SuspendPolicy.DEFAULT_PROTECTED_PACKAGES,
            protectedPrefixes = emptySet()
        )
        assertEquals(setOf("com.whatsapp"), result)
    }

    @Test
    fun `protected prefixes exclude all com android packages`() {
        val result = SuspendPolicy.computeSuspendSet(
            selfPackage = self,
            launchablePackages = setOf(self, "com.android.contacts", "com.whatsapp"),
            allowlist = emptySet(),
            protectedPackages = emptySet(),
            protectedPrefixes = SuspendPolicy.DEFAULT_PROTECTED_PREFIXES
        )
        assertEquals(setOf("com.whatsapp"), result)
    }

    @Test
    fun `custom protected packages are excluded`() {
        val result = SuspendPolicy.computeSuspendSet(
            selfPackage = self,
            launchablePackages = setOf(self, "com.whatsapp", "com.some.ime"),
            allowlist = emptySet(),
            protectedPackages = setOf("com.some.ime"),
            protectedPrefixes = emptySet()
        )
        assertEquals(setOf("com.whatsapp"), result)
    }

    @Test
    fun `empty suspend set when everything is allowlisted`() {
        assertTrue(compute(allowlist = launchable).isEmpty())
    }

    @Test
    fun `unsuspend set is a superset of any suspend set`() {
        val unsuspendSet = compute(allowlist = emptySet())
        val partialAllowlist = setOf("com.whatsapp", "org.telegram.messenger")
        assertTrue(unsuspendSet.containsAll(compute(partialAllowlist)))
        assertTrue(unsuspendSet.containsAll(compute(launchable)))
    }

    @Test
    fun `empty launchable set produces empty suspend set`() {
        assertTrue(
            SuspendPolicy.computeSuspendSet(self, emptySet(), emptySet()).isEmpty()
        )
    }

    @Test
    fun `dialer and package installer are protected even without prefix rule`() {
        val result = SuspendPolicy.computeSuspendSet(
            selfPackage = self,
            launchablePackages = setOf(
                self,
                "com.android.dialer",
                "com.android.packageinstaller",
                "com.android.installer",
                "com.whatsapp"
            ),
            allowlist = emptySet(),
            protectedPackages = SuspendPolicy.DEFAULT_PROTECTED_PACKAGES,
            protectedPrefixes = emptySet()
        )
        assertEquals(setOf("com.whatsapp"), result)
    }

    @Test
    fun `active IME is protected from suspension`() {
        val result = SuspendPolicy.computeSuspendSet(
            selfPackage = self,
            launchablePackages = setOf(self, "com.whatsapp", "com.example.ime"),
            allowlist = emptySet(),
            protectedPackages = SuspendPolicy.DEFAULT_PROTECTED_PACKAGES + "com.example.ime",
            protectedPrefixes = emptySet()
        )
        assertEquals(setOf("com.whatsapp"), result)
    }

    @Test
    fun `default launcher other than self is protected`() {
        val result = SuspendPolicy.computeSuspendSet(
            selfPackage = self,
            launchablePackages = setOf(self, "com.whatsapp", "com.favorite.launcher"),
            allowlist = emptySet(),
            protectedPackages = SuspendPolicy.DEFAULT_PROTECTED_PACKAGES + "com.favorite.launcher",
            protectedPrefixes = emptySet()
        )
        assertEquals(setOf("com.whatsapp"), result)
    }

    @Test
    fun `third-party launcher is suspended when it is not the default`() {
        val result = SuspendPolicy.computeSuspendSet(
            selfPackage = self,
            launchablePackages = setOf(self, "com.whatsapp", "com.favorite.launcher"),
            allowlist = emptySet(),
            protectedPackages = SuspendPolicy.DEFAULT_PROTECTED_PACKAGES,
            protectedPrefixes = emptySet()
        )
        assertEquals(setOf("com.whatsapp", "com.favorite.launcher"), result)
    }

    @Test
    fun `packages that failed suspension are skipped on re-apply`() {
        val result = SuspendPolicy.computeSuspendSet(
            selfPackage = self,
            launchablePackages = setOf(self, "com.whatsapp", "com.example.game", "org.telegram.messenger"),
            allowlist = emptySet(),
            protectedPackages = SuspendPolicy.DEFAULT_PROTECTED_PACKAGES + "com.example.game",
            protectedPrefixes = emptySet()
        )
        assertEquals(setOf("com.whatsapp", "org.telegram.messenger"), result)
    }

    @Test
    fun `result only contains launchable packages`() {
        val launchableOnly = setOf(self, "com.whatsapp", "org.telegram.messenger")
        val result = SuspendPolicy.computeSuspendSet(
            selfPackage = self,
            launchablePackages = launchableOnly,
            allowlist = emptySet(),
            protectedPackages = emptySet(),
            protectedPrefixes = emptySet()
        )
        assertTrue(launchableOnly.containsAll(result))
        assertFalse(result.contains("com.not.launchable"))
    }

    @Test
    fun `computation is idempotent - repeated calls return the same set`() {
        assertEquals(compute(), compute())
        assertEquals(compute(allowlist = setOf("com.whatsapp")), compute(allowlist = setOf("com.whatsapp")))
    }
}
