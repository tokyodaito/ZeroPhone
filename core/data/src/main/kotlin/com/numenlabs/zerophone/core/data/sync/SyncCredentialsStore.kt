package com.numenlabs.zerophone.core.data.sync

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.numenlabs.zerophone.core.model.DeviceCredentials
import com.numenlabs.zerophone.core.model.SyncEndpoints
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

/**
 * Phone-side sync credentials: the paired device token and the server
 * revision this device has applied. Kept separate from the policy prefs
 * so clearing pairing never touches policy state.
 */
interface SyncCredentialsStore {

    suspend fun load(): DeviceCredentials?

    suspend fun save(credentials: DeviceCredentials)

    suspend fun clear()

    /** Server revision already applied locally; -1 before the first sync. */
    suspend fun revision(): Long

    suspend fun setRevision(revision: Long)
}

/** Pure Kotlin in-memory store — unit tests and previews. */
class InMemorySyncCredentialsStore : SyncCredentialsStore {

    private var credentials: DeviceCredentials? = null
    private var currentRevision: Long = -1L

    override suspend fun load(): DeviceCredentials? = credentials

    override suspend fun save(credentials: DeviceCredentials) {
        this.credentials = credentials
    }

    override suspend fun clear() {
        credentials = null
        currentRevision = -1L
    }

    override suspend fun revision(): Long = currentRevision

    override suspend fun setRevision(revision: Long) {
        currentRevision = revision
    }
}

private val Context.syncDataStore: DataStore<Preferences> by preferencesDataStore(name = "sync_prefs")

/**
 * Android-backed [SyncCredentialsStore] (Preferences DataStore): the token
 * file of the sync pairing plus the tracked revision. A decode failure
 * degrades to "not paired" — never a crash.
 */
class DataStoreSyncCredentialsStore(context: Context) : SyncCredentialsStore {

    private val dataStore = context.applicationContext.syncDataStore

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun load(): DeviceCredentials? =
        dataStore.data.map { prefs ->
            prefs[KEY_CREDENTIALS]?.let {
                runCatching { json.decodeFromString(DeviceCredentials.serializer(), it) }.getOrNull()
            }
        }.first()

    override suspend fun save(credentials: DeviceCredentials) {
        dataStore.edit {
            it[KEY_CREDENTIALS] = json.encodeToString(DeviceCredentials.serializer(), credentials)
        }
    }

    override suspend fun clear() {
        dataStore.edit {
            it.remove(KEY_CREDENTIALS)
            it[KEY_REVISION] = -1L
        }
    }

    override suspend fun revision(): Long =
        dataStore.data.map { it[KEY_REVISION] ?: -1L }.first()

    override suspend fun setRevision(revision: Long) {
        dataStore.edit { it[KEY_REVISION] = revision }
    }

    private companion object {
        val KEY_CREDENTIALS = stringPreferencesKey("device_credentials_json")
        val KEY_REVISION = longPreferencesKey("state_revision")
    }
}

/** Where the phone's sync server lives; overridable per deployment. */
object SyncServerConfig {
    const val DEFAULT_SERVER_URL: String = "http://10.0.2.2:8080"
}
