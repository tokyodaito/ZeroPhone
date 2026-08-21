package com.numenlabs.zerophone.core.policy

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.numenlabs.zerophone.core.model.EmergencyWindow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Single DataStore instance ("zerophone_prefs"), migrated automatically from the
 * legacy SharedPreferences of the same name (same keys), so existing installs
 * keep their allowlist and emergency state.
 */
private val Context.zeroPhoneDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "zerophone_prefs",
    produceMigrations = { context -> listOf(SharedPreferencesMigration(context, "zerophone_prefs")) }
)

/**
 * Android-backed [PolicyRepository] (Preferences DataStore). Keys intentionally
 * keep the legacy SharedPreferences names so the migration maps them 1:1.
 */
class DataStorePolicyRepository(context: Context) : PolicyRepository {

    private val dataStore = context.applicationContext.zeroPhoneDataStore

    override suspend fun getAllowlist(): Set<String> =
        dataStore.data.map { it[KEY_ALLOWLIST] ?: emptySet() }.first()

    override suspend fun setAllowlist(packages: Set<String>) {
        dataStore.edit { it[KEY_ALLOWLIST] = packages }
    }

    override suspend fun getEmergencyDeadline(): Long =
        dataStore.data.map { it[KEY_EMERGENCY_DEADLINE] ?: EmergencyWindow.NONE_DEADLINE }.first()

    override suspend fun setEmergencyDeadline(deadlineMillis: Long) {
        dataStore.edit { it[KEY_EMERGENCY_DEADLINE] = deadlineMillis }
    }

    override suspend fun getLastSuspended(): Set<String> =
        dataStore.data.map { it[KEY_LAST_SUSPENDED] ?: emptySet() }.first()

    override suspend fun setLastSuspended(packages: Set<String>) {
        dataStore.edit { it[KEY_LAST_SUSPENDED] = packages }
    }

    companion object {
        private val KEY_ALLOWLIST = stringSetPreferencesKey("allowlist")
        private val KEY_EMERGENCY_DEADLINE = longPreferencesKey("emergency_deadline")
        private val KEY_LAST_SUSPENDED = stringSetPreferencesKey("last_suspended")
    }
}
