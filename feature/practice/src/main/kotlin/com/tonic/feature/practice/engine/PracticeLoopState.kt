package com.tonic.feature.practice.engine

import com.tonic.core.model.items.AxisChange
import com.tonic.core.model.items.Item

/** What a caller (Stage 7's ViewModel, or a headless test harness) needs to render one moment of a session. */
data class PracticeLoopState(
    val currentItem: Item.FunctionalRecognitionItem? = null,
    /** True while [currentItem] is one of the 30 forced-L6 `M2.INDEPENDENCE_CHECK` probes, not ordinary practice. */
    val isIndependenceCheckProbe: Boolean = false,
    val itemsCompleted: Int = 0,
    /** The number of ordinary (non-independence-check) slots originally planned for this session. */
    val itemsPlanned: Int = 0,
    val lastFeedback: AnswerFeedback? = null,
    val isFinished: Boolean = false,
    /** The persisted `SessionRepository` row id for the current run, set once [PracticeLoopEngine.start] creates it - `summary/{sessionId}`'s own nav argument. */
    val sessionId: Long? = null,
    /**
     * True after an interruption (docs/06-AUDIO-ENGINE.md §8) discarded the current item and stopped
     * playback. The session is *not* over — resume state has been persisted and
     * [PracticeLoopEngine.resumeAfterPause] continues from the next item. Distinct from [isFinished],
     * which means the plan ran out.
     */
    val isPaused: Boolean = false,
    /**
     * Set for exactly the item on which a difficulty axis changed, null otherwise — the signal the
     * practice screen turns into docs/11-ONBOARDING-CLARITY.md §9.3's one-line announcement. Clears on
     * the next item by construction, since that item's levels match the one before it.
     */
    val axisChange: AxisChange? = null,
    /** When this run began and when its wall-clock budget ends — what the practice screen's time bar is drawn from. */
    val sessionStartedAt: java.time.Instant? = null,
    val sessionEndsAt: java.time.Instant? = null,
)

/** Shown briefly after [PracticeLoopEngine.submitAnswer], before the next item starts. */
data class AnswerFeedback(
    val correct: Boolean,
    val correctLabel: String,
)

/**
 * Why the loop was interrupted — docs/06-AUDIO-ENGINE.md §8. Every one of these discards the current
 * item rather than scoring it ("do not score an item the user could not hear"); they differ only in
 * whether playback is expected to come back on its own.
 */
enum class InterruptionReason {
    /** The user chose to leave the practice screen - docs/08-UI-SPEC.md §2a. Same discard-and-persist path as a platform interruption; nothing about leaving may lose progress. */
    USER_EXIT,

    /** `AUDIOFOCUS_LOSS_TRANSIENT` (call, notification) or a duck request, which the spec says to treat as a pause, never a duck. Restores on regain. */
    TRANSIENT_FOCUS_LOSS,

    /** `AUDIOFOCUS_LOSS`: "end the session cleanly, persist resume state." No automatic restore. */
    PERMANENT_FOCUS_LOSS,

    /** `ACTION_AUDIO_BECOMING_NOISY` — headphones unplugged. No automatic restore; the user chooses when to continue. */
    BECOMING_NOISY,

    /** The app was backgrounded (a real `ON_STOP`, not a configuration change). */
    BACKGROUNDED,
}
