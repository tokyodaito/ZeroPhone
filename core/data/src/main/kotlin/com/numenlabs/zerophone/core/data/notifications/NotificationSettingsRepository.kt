package com.numenlabs.zerophone.core.data.notifications

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.notificationSettingsDataStore: DataStore<Preferences> by
preferencesDataStore(name = "zerophone_notification_settings")

/**
 * Persistence of the user's priority packages for the important-unread
 * filter (packages whose notifications always count as important).
 */
interface NotificationSettingsRepository {

    suspend fun getPriorityPackages(): Set<String>

    suspend fun setPriorityPackages(packages: Set<String>)
}

/** Pure in-memory [NotificationSettingsRepository] for previews and tests. */
class InMemoryNotificationSettingsRepository : NotificationSettingsRepository {

    private var packages: Set<String> = emptySet()

    override suspend fun getPriorityPackages(): Set<String> = packages.toSet()

    override suspend fun setPriorityPackages(packages: Set<String>) {
        this.packages = packages.toSet()
    }
}

/** Preferences-DataStore-backed [NotificationSettingsRepository]. */
class DataStoreNotificationSettingsRepository(context: Context) : NotificationSettingsRepository {

    private val dataStore = context.applicationContext.notificationSettingsDataStore

    override suspend fun getPriorityPackages(): Set<String> =
        dataStore.data.map { it[KEY_PRIORITY_PACKAGES] ?: emptySet() }.first()

    override suspend fun setPriorityPackages(packages: Set<String>) {
        dataStore.edit { it[KEY_PRIORITY_PACKAGES] = packages }
    }

    private companion object {
        val KEY_PRIORITY_PACKAGES = stringSetPreferencesKey("priority_packages")
    }
}
