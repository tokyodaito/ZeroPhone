package com.numenlabs.zerophone.core.data.di

import android.content.Context
import com.numenlabs.zerophone.core.context.SnapshotProvider
import com.numenlabs.zerophone.core.data.calendar.CalendarProviderSource
import com.numenlabs.zerophone.core.data.calendar.CalendarSource
import com.numenlabs.zerophone.core.data.notifications.DataStoreNotificationSettingsRepository
import com.numenlabs.zerophone.core.data.notifications.NotificationSettingsRepository
import com.numenlabs.zerophone.core.data.snapshot.AndroidSnapshotProvider
import com.numenlabs.zerophone.core.data.sync.DataStoreSyncCredentialsStore
import com.numenlabs.zerophone.core.data.sync.KtorSyncTransport
import com.numenlabs.zerophone.core.data.sync.PhoneSyncEngine
import com.numenlabs.zerophone.core.data.sync.SyncCredentialsStore
import com.numenlabs.zerophone.core.data.sync.SyncServerConfig
import com.numenlabs.zerophone.core.data.sync.SyncTransport
import com.numenlabs.zerophone.core.data.tasks.DataStoreTaskRepository
import com.numenlabs.zerophone.core.data.tasks.TaskRepository
import com.numenlabs.zerophone.core.policy.PolicyApplier
import com.numenlabs.zerophone.core.policy.PolicyRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt bindings for the contextual data sources. The [SnapshotProvider]
 * binding here is what feeds [com.numenlabs.zerophone.core.policy.PolicyApplier]
 * with real calendar/time context (composed at the :app level).
 */
@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideCalendarSource(@ApplicationContext context: Context): CalendarSource =
        CalendarProviderSource(context)

    @Provides
    @Singleton
    fun provideTaskRepository(@ApplicationContext context: Context): TaskRepository =
        DataStoreTaskRepository(context)

    @Provides
    @Singleton
    fun provideNotificationSettingsRepository(
        @ApplicationContext context: Context
    ): NotificationSettingsRepository = DataStoreNotificationSettingsRepository(context)

    @Provides
    @Singleton
    fun provideSnapshotProvider(calendarSource: CalendarSource): SnapshotProvider =
        AndroidSnapshotProvider(calendarSource)

    @Provides
    @Singleton
    fun provideSyncCredentialsStore(
        @ApplicationContext context: Context
    ): SyncCredentialsStore = DataStoreSyncCredentialsStore(context)

    @Provides
    @Singleton
    fun provideSyncTransport(credentials: SyncCredentialsStore): SyncTransport =
        KtorSyncTransport(serverUrl = SyncServerConfig.DEFAULT_SERVER_URL, credentials = credentials)

    /**
     * Phone-side sync engine: remote applies land in the policy repository
     * and are immediately re-computed through the [PolicyApplier], keeping
     * the suspend-set and the launcher in sync with what arrived.
     */
    @Provides
    @Singleton
    fun providePhoneSyncEngine(
        transport: SyncTransport,
        credentials: SyncCredentialsStore,
        policyRepository: PolicyRepository,
        applier: PolicyApplier,
    ): PhoneSyncEngine = PhoneSyncEngine(
        transport = transport,
        credentials = credentials,
        policy = policyRepository,
        onPolicyApplied = { applier.reconcile() },
    )
}
