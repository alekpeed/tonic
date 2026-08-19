package com.tonic.feature.settings.ui

import com.tonic.core.model.state.AppSettings

/** A thin read model wrapping [AppSettings] with the one thing it lacks: whether the initial DataStore read has completed yet. */
data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val isLoading: Boolean = true,
)
