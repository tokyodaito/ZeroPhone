package com.numenlabs.zerophone.core.policy.di

import android.content.Context
import com.numenlabs.zerophone.core.context.SnapshotProvider
import com.numenlabs.zerophone.core.policy.DataStorePolicyRepository
import com.numenlabs.zerophone.core.policy.PolicyApplier
import com.numenlabs.zerophone.core.policy.PolicyRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt bindings for the policy layer. Both bindings are singletons so every
 * entry point (activity, boot receiver, re-lock alarm receiver) reconciles
 * through one [PolicyApplier] on top of one DataStore-backed repository.
 *
 * The [SnapshotProvider] parameter is resolved from the data-layer module
 * (:core:data) at the app level; the pure fallback
 * [com.numenlabs.zerophone.core.context.SnapshotProvider.None] stays available
 * for non-Android consumers and tests.
 */
@Module
@InstallIn(SingletonComponent::class)
object PolicyModule {

    @Provides
    @Singleton
    fun providePolicyRepository(@ApplicationContext context: Context): PolicyRepository =
        DataStorePolicyRepository(context)

    @Provides
    @Singleton
    fun providePolicyApplier(
        @ApplicationContext context: Context,
        repository: PolicyRepository,
        snapshotProvider: SnapshotProvider
    ): PolicyApplier = PolicyApplier(context, repository, snapshotProvider)
}
