package com.tonic.feature.settings.ui

import com.tonic.core.model.state.AppSettings

/** A thin read model wrapping [AppSettings] with the one thing it lacks: whether the initial DataStore read has completed yet. */
data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val isLoading: Boolean = true,
    /** Outcome of the last "discard saved session" press, for §2a's visible confirmation. Null until pressed. */
    val discardResult: DiscardResult? = null,
)

/** What "Discard saved session" found - the confirmation copy differs (docs/08-UI-SPEC.md §2a). */
enum class DiscardResult {
    /** A saved session existed and its resume state was cleared. Attempts and skill progress untouched. */
    DISCARDED,

    /** Nothing to discard - stated rather than silently doing nothing. */
    NOTHING_SAVED,
}
