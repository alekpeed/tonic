package com.tonic.core.data.di

import com.tonic.core.model.time.Clock
import com.tonic.core.model.time.SystemClock
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds [SystemClock] - "the only production implementation" of [Clock]
 * (see its KDoc in `:core:model`) - as the [Clock] this module's
 * repositories get injected with.
 */
@Module
@InstallIn(SingletonComponent::class)
internal object ClockModule {
    @Provides
    @Singleton
    fun provideClock(): Clock = SystemClock()
}
