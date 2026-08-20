package com.tonic.feature.practice.ui

import com.tonic.core.model.items.AxisChange
import com.tonic.core.model.items.Item
import com.tonic.core.model.music.ScaleDegree
import com.tonic.core.model.state.LabelStyle
import com.tonic.core.model.state.Session
import com.tonic.core.ui.components.PlaybackPhase

/** Everything docs/08-UI-SPEC.md §4's Practice screen needs to render one moment of a session. */
data class PracticeUiState(
    /** Any supported item type; the screen branches on it to pick an answer control. */
    val item: Item? = null,
    val labelStyle: LabelStyle = LabelStyle.NUMBERS,
    val reduceMotion: Boolean = false,
    val hapticsEnabled: Boolean = true,
    val phase: PlaybackPhase = PlaybackPhase.REFERENCE,
    val itemsCompleted: Int = 0,
    val itemsPlanned: Int = 0,
    /** The degree the learner tapped for the item currently on screen, if any. Cleared on advance. */
    val selectedDegree: ScaleDegree? = null,
    /** Set once feedback is in for the item currently on screen - null means "not answered yet." */
    val correctDegree: ScaleDegree? = null,
    /** False while audio is still playing (docs/08-UI-SPEC.md §3's `disabled` state) or while feedback for the previous answer is still resolving. */
    val inputEnabled: Boolean = false,
    val isFinished: Boolean = false,
    val isLoading: Boolean = true,
    /** The current session's persisted id, once known - `summary/{sessionId}`'s own nav argument. */
    val sessionId: Long? = null,
    /**
     * An interrupted session found at startup, waiting on the user's choice — docs/10-TESTING.md §11's
     * "force stop mid-session -> resume offered." Non-null means nothing has started yet: the screen is
     * showing the offer, not a live item.
     */
    val resumableSession: Session? = null,
    /** True while an interruption (docs/06-AUDIO-ENGINE.md §8) has paused the loop. The session is not over. */
    val isPaused: Boolean = false,
    /** Non-null only on the item where an axis moved - docs/11-ONBOARDING-CLARITY.md §9.3. */
    val axisChange: AxisChange? = null,
    /**
     * The Module 2 explanation screen is on top of the loop — docs/11-ONBOARDING-CLARITY.md §3/§5.
     * Automatic on first encounter, and thereafter only when the user asks for it via the help
     * affordance, which is why this is separate from the persisted `module2IntroSeen` flag.
     */
    val showIntro: Boolean = false,
    /** The worked example's answer, revealed only once the user asks — never before they've heard it. */
    val introAnswerRevealed: Boolean = false,
    /**
     * Transient acknowledgment that Skip was pressed — docs/08-UI-SPEC.md §2a: a control that claims to
     * advance must give immediate visible confirmation. Without it, skipping to a similar-sounding item
     * is indistinguishable from the button doing nothing.
     */
    val skipAcknowledged: Boolean = false,
    /**
     * Elapsed fraction of the session's wall-clock budget, 0..1 — what the progress bar draws. Time,
     * not items, by the maintainer's direct instruction after live use: the plan's item count is an
     * estimate, so an item-based bar barely moved for a deliberate learner while the session ran on;
     * the session is bounded by minutes, and the bar shows exactly that.
     */
    val timeFraction: Float = 0f,
    /**
     * The chosen and correct labels for an item answered with buttons rather than the ladder — `M9`'s
     * major/minor and `M12`'s matched/too-low/too-high. Null on a recognition item, which uses
     * [selectedDegree]/[correctDegree] instead.
     */
    val selectedAnswerLabel: String? = null,
    val correctAnswerLabel: String? = null,
    /**
     * Which first-run explanation to show, if any — docs/08-UI-SPEC.md §3a. Chosen from the module the
     * session actually resolved to, so a learner meets each new task shape's explanation once and is
     * never shown one for a module they are not practicing.
     */
    val introKind: IntroKind = IntroKind.NONE,
) {
    /** The ladder's contents. Empty for an item type that does not answer with a degree, such as `M9`. */
    val activeDegrees: List<ScaleDegree>
        get() = (item as? Item.FunctionalRecognitionItem)?.activeDegrees ?: emptyList()

    /** The recognition item, when that is what is on screen — the ladder and its captions need the concrete type. */
    val recognitionItem: Item.FunctionalRecognitionItem?
        get() = item as? Item.FunctionalRecognitionItem

    /** The mode-identification item, when that is what is on screen. */
    val modeItem: Item.ModeIdentificationItem?
        get() = item as? Item.ModeIdentificationItem
}

/** The first-run explanations, one per task shape (docs/08-UI-SPEC.md §3a). */
enum class IntroKind {
    NONE,

    /** Degree identification in major. */
    M2,

    /** Degree identification in minor — what changed, and what `♭3` means. */
    M10,

    /** Chromatic degrees — what a note *between* the familiar ones is, and why it borrows their number. */
    M11,

    /** Audiation — that the task runs backwards, that the silence is the exercise, and what to do in it. */
    M12,
}
