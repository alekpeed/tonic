package com.tonic.feature.settings.ui

import com.tonic.core.model.state.AppSettings

/** A thin read model wrapping [AppSettings] with the one thing it lacks: whether the initial DataStore read has completed yet. */
data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val isLoading: Boolean = true,
    /** Outcome of the last "discard saved session" press, for §2a's visible confirmation. Null until pressed. */
    val discardResult: DiscardResult? = null,
    /**
     * Set when the user has asked for an export and the document is ready to be written. The screen
     * consumes it by launching the system's create-document picker; the write itself happens there,
     * because that is where a `ContentResolver` lives (docs/04-ARCHITECTURE.md §3 keeps Android I/O out
     * of the ViewModel).
     */
    val pendingExport: PendingExport? = null,
    val exportResult: ExportResult? = null,
)

/** What "Discard saved session" found - the confirmation copy differs (docs/08-UI-SPEC.md §2a). */
enum class DiscardResult {
    /** A saved session existed and its resume state was cleared. Attempts and skill progress untouched. */
    DISCARDED,

    /** Nothing to discard - stated rather than silently doing nothing. */
    NOTHING_SAVED,
}

/** A prepared export, waiting for the user to choose where it goes. */
data class PendingExport(
    val suggestedFileName: String,
    val json: String,
)

/** Outcome of the last export, for the one-line confirmation docs/08-UI-SPEC.md §2a requires. */
enum class ExportResult {
    /** Written to the location the user chose. */
    SAVED,

    /** The user backed out of the picker. Not an error, and not reported as one. */
    CANCELLED,

    /** The write itself failed - a full disk, a revoked URI. Stated plainly, never blamed on the user. */
    FAILED,
}
