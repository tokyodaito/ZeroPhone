package com.numenlabs.zerophone.core.data.di

import android.content.Context
import com.numenlabs.zerophone.core.context.SnapshotProvider
import com.numenlabs.zerophone.core.data.calendar.CalendarProviderSource
import com.numenlabs.zerophone.core.data.calendar.CalendarSource
import com.numenlabs.zerophone.core.data.notifications.DataStoreNotificationSettingsRepository
import com.numenlabs.zerophone.core.data.notifications.NotificationSettingsRepository
import com.numenlabs.zerophone.core.data.snapshot.AndroidSnapshotProvider
import com.numenlabs.zerophone.core.data.tasks.DataStoreTaskRepository
import com.numenlabs.zerophone.core.data.tasks.TaskRepository
import com.numenlabs.zerophone.core.data.usage.AndroidUsageStatsSource
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
    fun provideUsageStatsSource(@ApplicationContext context: Context): AndroidUsageStatsSource =
        AndroidUsageStatsSource(context)
}
