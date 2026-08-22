package com.tonic.feature.settings.ui

import com.tonic.core.model.ids.SkillId
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
    /**
     * Every node in [com.tonic.core.curriculum.graph.SkillGraph.practiceChain], for the debug-only
     * "jump to node" tool (`BuildConfig.DEBUG` only — see `SettingsScreen`'s debug section). Populated
     * unconditionally; it is the screen that decides whether to render it, since a `BuildConfig` check
     * belongs at the Android edge, not in a ViewModel this module's own JVM tests exercise.
     */
    val debugJumpTargets: List<SkillId> = emptyList(),
    val debugJumpResult: DebugJumpResult? = null,
    /** A jump is seconds of work, not instant — without this the press looks like nothing happened. */
    val debugJumpInProgress: Boolean = false,
    /**
     * One-shot navigation signal, deliberately separate from [debugJumpResult]. Folding the two
     * together meant consuming the navigation also erased the outcome line, so the press reported
     * nothing — the very symptom the result line exists to prevent, reintroduced by the fix for it.
     * Caught by `SettingsDebugSectionTest`, which is the point of having it.
     */
    val debugJumpNavigateTo: SkillId? = null,
)

/** Outcome of a debug "jump to node" press — how many nodes it had to seed, or why it couldn't. */
data class DebugJumpResult(
    val target: SkillId,
    val seededCount: Int,
    /** Null on success. Surfaced verbatim: a debug tool's failure is information, not noise. */
    val failure: String? = null,
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
