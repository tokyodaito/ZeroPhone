package com.numenlabs.zerophone.core.policy

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AllowlistStoreTest {

    private class FakeEditor(private val data: MutableMap<String, Any>) : SharedPreferences.Editor {

        override fun putString(key: String, value: String?) = apply {
            if (value == null) data.remove(key) else data[key] = value
        }

        override fun putStringSet(key: String, values: MutableSet<String>?) = apply {
            if (values == null) data.remove(key) else data[key] = values.toSet()
        }

        override fun putInt(key: String, value: Int) = apply { data[key] = value }

        override fun putLong(key: String, value: Long) = apply { data[key] = value }

        override fun putFloat(key: String, value: Float) = apply { data[key] = value }

        override fun putBoolean(key: String, value: Boolean) = apply { data[key] = value }

        override fun remove(key: String) = apply { data.remove(key) }

        override fun clear() = apply { data.clear() }

        override fun commit() = true

        override fun apply() {}
    }

    private class FakeSharedPreferences : SharedPreferences {
        val data = mutableMapOf<String, Any>()

        override fun getAll(): Map<String, *> = data.toMap()

        override fun getString(key: String, defValue: String?) =
            data[key] as? String ?: defValue

        override fun getStringSet(key: String, defValue: MutableSet<String>?) =
            (data[key] as? Set<String>)?.toMutableSet() ?: defValue

        override fun getInt(key: String, defValue: Int) = data[key] as? Int ?: defValue

        override fun getLong(key: String, defValue: Long) = data[key] as? Long ?: defValue

        override fun getFloat(key: String, defValue: Float) = data[key] as? Float ?: defValue

        override fun getBoolean(key: String, defValue: Boolean) = data[key] as? Boolean ?: defValue

        override fun contains(key: String) = data.containsKey(key)

        override fun edit(): SharedPreferences.Editor = FakeEditor(data)

        override fun registerOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener
        ) {}

        override fun unregisterOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener
        ) {}
    }

    private val prefs = FakeSharedPreferences()
    private val store = AllowlistStore(prefs)

    @Test
    fun `allowlist defaults to empty set`() {
        assertTrue(store.getAllowlist().isEmpty())
    }

    @Test
    fun `emergency deadline defaults to zero`() {
        assertEquals(0L, store.getEmergencyDeadline())
    }

    @Test
    fun `allowlist roundtrip persists under the allowlist key`() {
        store.setAllowlist(setOf("com.whatsapp", "org.telegram.messenger"))
        assertEquals(setOf("com.whatsapp", "org.telegram.messenger"), store.getAllowlist())
        assertTrue(prefs.data.containsKey(AllowlistStore.KEY_ALLOWLIST))
    }

    @Test
    fun `emergency deadline roundtrip persists under the emergency_deadline key`() {
        val deadline = 1_770_000_000_000L
        store.setEmergencyDeadline(deadline)
        assertEquals(deadline, store.getEmergencyDeadline())
        assertEquals(deadline, prefs.data[AllowlistStore.KEY_EMERGENCY_DEADLINE])
    }

    @Test
    fun `resetting deadline to zero means no window`() {
        store.setEmergencyDeadline(123L)
        store.setEmergencyDeadline(AllowlistStore.NO_EMERGENCY_DEADLINE)
        assertEquals(0L, store.getEmergencyDeadline())
    }

    @Test
    fun `setAllowed toggles package membership`() {
        store.setAllowed("com.whatsapp", true)
        assertTrue(store.isAllowed("com.whatsapp"))
        store.setAllowed("com.whatsapp", false)
        assertFalse(store.isAllowed("com.whatsapp"))
        assertTrue(store.getAllowlist().isEmpty())
    }

    @Test
    fun `allowlist change notifies registered listener`() {
        var notifications = 0
        val listener = { notifications = notifications + 1 }
        store.addOnAllowlistChangedListener(listener)
        store.setAllowed("com.whatsapp", true)
        assertEquals(1, notifications)
        store.setAllowlist(setOf("com.whatsapp", "org.telegram.messenger"))
        assertEquals(2, notifications)
    }

    @Test
    fun `no notification when allowlist content does not change`() {
        var notifications = 0
        store.addOnAllowlistChangedListener { notifications = notifications + 1 }
        store.setAllowed("com.whatsapp", true)
        store.setAllowed("com.whatsapp", true)
        assertEquals(1, notifications)
    }

    @Test
    fun `removed listener stops receiving notifications`() {
        var notifications = 0
        val listener = { notifications = notifications + 1 }
        store.addOnAllowlistChangedListener(listener)
        store.setAllowed("com.whatsapp", true)
        store.removeOnAllowlistChangedListener(listener)
        store.setAllowed("com.whatsapp", false)
        assertEquals(1, notifications)
    }

    @Test
    fun `last suspended set roundtrips`() {
        store.setLastSuspended(setOf("com.example.game"))
        assertEquals(setOf("com.example.game"), store.getLastSuspended())
    }

    @Test
    fun `mutating the returned allowlist copy does not corrupt the store`() {
        store.setAllowlist(setOf("com.whatsapp", "org.telegram.messenger"))
        runCatching { (store.getAllowlist() as? MutableSet<String>)?.clear() }
        assertEquals(setOf("com.whatsapp", "org.telegram.messenger"), store.getAllowlist())
    }
}
