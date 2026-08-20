package com.tonic.app.di

import com.tonic.core.engine.confusion.ConfusionTracker
import com.tonic.core.engine.replay.SkillStateReducer
import com.tonic.core.model.state.ConfusionTracking
import com.tonic.core.model.state.SkillStateReplayer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * The composition root for the dependency-inversion boundary between
 * `:core:data` and `:core:engine` (docs/04-ARCHITECTURE.md §2 forbids
 * `:core:data` depending on `:core:engine` directly). `:core:data`'s
 * repositories depend only on [SkillStateReplayer] / [ConfusionTracking],
 * both declared in `:core:model`; this is the one place, reachable by
 * both `:core:data` and `:core:engine`, that wires the real
 * `:core:engine` implementations in. See
 * [com.tonic.core.model.state.SkillStateReplayer]'s KDoc for the full
 * reasoning.
 */
@Module
@InstallIn(SingletonComponent::class)
internal object EngineBindingsModule {
    @Provides
    fun provideSkillStateReplayer(): SkillStateReplayer = SkillStateReducer

    @Provides
    fun provideConfusionTracking(): ConfusionTracking = ConfusionTracker
}
