package com.tonic.core.data.di

import com.tonic.core.data.export.DataExportRepository
import com.tonic.core.data.export.DataExportRepositoryImpl
import com.tonic.core.data.repository.AttemptRepository
import com.tonic.core.data.repository.AttemptRepositoryImpl
import com.tonic.core.data.repository.ConfusionRepository
import com.tonic.core.data.repository.ConfusionRepositoryImpl
import com.tonic.core.data.repository.DebugProgressRepository
import com.tonic.core.data.repository.DebugProgressRepositoryImpl
import com.tonic.core.data.repository.DiagnosticRepository
import com.tonic.core.data.repository.DiagnosticRepositoryImpl
import com.tonic.core.data.repository.SessionRepository
import com.tonic.core.data.repository.SessionRepositoryImpl
import com.tonic.core.data.repository.SkillStateRepository
import com.tonic.core.data.repository.SkillStateRepositoryImpl
import com.tonic.core.data.settings.SettingsRepository
import com.tonic.core.data.settings.SettingsRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Binds every `:core:data` repository interface to its implementation.
 * Does NOT bind [com.tonic.core.model.state.SkillStateReplayer] or
 * [com.tonic.core.model.state.ConfusionTracking] - those are implemented
 * in `:core:engine`, which this module cannot depend on
 * (docs/04-ARCHITECTURE.md §2); the composition root (`:app`) binds them.
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class RepositoryModule {
    @Binds
    abstract fun bindAttemptRepository(impl: AttemptRepositoryImpl): AttemptRepository

    @Binds
    abstract fun bindSkillStateRepository(impl: SkillStateRepositoryImpl): SkillStateRepository

    @Binds
    abstract fun bindConfusionRepository(impl: ConfusionRepositoryImpl): ConfusionRepository

    @Binds
    abstract fun bindSessionRepository(impl: SessionRepositoryImpl): SessionRepository

    @Binds
    abstract fun bindDiagnosticRepository(impl: DiagnosticRepositoryImpl): DiagnosticRepository

    @Binds
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds
    abstract fun bindDataExportRepository(impl: DataExportRepositoryImpl): DataExportRepository

    /** Debug tooling only — see [DebugProgressRepository]. Bound unconditionally; only a
     * `BuildConfig.DEBUG`-gated screen ever injects it. */
    @Binds
    abstract fun bindDebugProgressRepository(impl: DebugProgressRepositoryImpl): DebugProgressRepository
}
