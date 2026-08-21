package com.numenlabs.zerophone.core.policy

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.numenlabs.zerophone.core.context.ManualGrant
import com.numenlabs.zerophone.core.context.ModeIds
import com.numenlabs.zerophone.core.context.Rule
import com.numenlabs.zerophone.core.context.TimeBudgetLedger
import com.numenlabs.zerophone.core.model.EmergencyWindow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

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
 * Engine state (rules, grants, budget ledger) is stored as JSON strings; any
 * decode failure degrades to the safe default (empty) — never a crash.
 */
class DataStorePolicyRepository(context: Context) : PolicyRepository {

    private val dataStore = context.applicationContext.zeroPhoneDataStore

    private val json = Json { ignoreUnknownKeys = true }

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

    override suspend fun getRules(): List<Rule> =
        dataStore.data.map { prefs ->
            prefs[KEY_RULES]?.let { decode(it, ListSerializer(Rule.serializer())) } ?: emptyList()
        }.first()

    override suspend fun setRules(rules: List<Rule>) {
        dataStore.edit { it[KEY_RULES] = json.encodeToString(ListSerializer(Rule.serializer()), rules) }
    }

    override suspend fun getGrants(): List<ManualGrant> =
        dataStore.data.map { prefs ->
            prefs[KEY_GRANTS]?.let { decode(it, ListSerializer(ManualGrant.serializer())) } ?: emptyList()
        }.first()

    override suspend fun setGrants(grants: List<ManualGrant>) {
        dataStore.edit { it[KEY_GRANTS] = json.encodeToString(ListSerializer(ManualGrant.serializer()), grants) }
    }

    override suspend fun getActiveMode(): String =
        dataStore.data.map { it[KEY_ACTIVE_MODE] ?: ModeIds.WORK }.first()

    override suspend fun setActiveMode(mode: String) {
        dataStore.edit { it[KEY_ACTIVE_MODE] = mode }
    }

    override suspend fun getTimeBudgetLedger(): TimeBudgetLedger =
        dataStore.data.map { prefs ->
            prefs[KEY_TIME_BUDGET_LEDGER]?.let { decode(it, TimeBudgetLedger.serializer()) }
                ?: TimeBudgetLedger()
        }.first()

    override suspend fun setTimeBudgetLedger(ledger: TimeBudgetLedger) {
        dataStore.edit { it[KEY_TIME_BUDGET_LEDGER] = json.encodeToString(TimeBudgetLedger.serializer(), ledger) }
    }

    private fun <T> decode(value: String, serializer: kotlinx.serialization.KSerializer<T>): T? =
        try {
            json.decodeFromString(serializer, value)
        } catch (_: Exception) {
            null
        }

    companion object {
        private val KEY_ALLOWLIST = stringSetPreferencesKey("allowlist")
        private val KEY_EMERGENCY_DEADLINE = longPreferencesKey("emergency_deadline")
        private val KEY_LAST_SUSPENDED = stringSetPreferencesKey("last_suspended")
        private val KEY_RULES = stringPreferencesKey("rules_json")
        private val KEY_GRANTS = stringPreferencesKey("grants_json")
        private val KEY_ACTIVE_MODE = stringPreferencesKey("active_mode")
        private val KEY_TIME_BUDGET_LEDGER = stringPreferencesKey("time_budget_ledger_json")
    }
}
