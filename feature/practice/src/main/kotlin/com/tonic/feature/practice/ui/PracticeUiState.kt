package com.tonic.feature.practice.ui

import com.tonic.core.model.items.AxisChange
import com.tonic.core.model.items.Item
import com.tonic.core.model.music.AudiatedPitch
import com.tonic.core.model.music.Mode
import com.tonic.core.model.music.ScaleDegree
import com.tonic.core.model.rhythm.RhythmScore
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
    /**
     * The mode of the item just answered, shown only after the answer — docs/20-PHASE-2-SPEC.md §5.4.
     *
     * Null on every node whose mode the learner already knows, and null *before* the answer at
     * `M10.MIXED_MODE`, which is the whole point of that node. After the answer it is mandatory: "the
     * feedback must state which mode it was, or the learner cannot learn from a mistake." A learner
     * who picks `3` on a minor item and is told only "wrong, it was ♭3" has no way to tell whether
     * they misheard the degree or misheard the key, which are different problems with different fixes.
     */
    val revealedMode: Mode? = null,
    /**
     * Whether this item can be answered by singing — docs/30-PHASE-3-SPEC.md §5.3.
     *
     * False whenever singing is switched off, permission is absent, or the item has no pitch to sing
     * (`M9` asks major-or-minor; there is nothing to produce). It never gates the ladder: §6.3 requires
     * the tap fallback to be present on every sung item, always visible, so this adds a control and
     * removes nothing.
     */
    val sungResponseAvailable: Boolean = false,
    /** Where the sung answer is in its cycle, if singing is on at all. */
    val sungCapture: SungCaptureState = SungCaptureState.IDLE,
    /**
     * How far the last sung answer landed from the degree it resolved to, in cents — shown *after* the
     * answer as information, never as a grade (docs/30-PHASE-3-SPEC.md §3 mitigation 4 and §6.4).
     * Cleared on every new item. Null for a tapped answer.
     */
    val lastSungCents: Int? = null,
    /**
     * What the learner sang into this prediction item's audiation gap — docs/30-PHASE-3-SPEC.md §5.4.
     *
     * Null until a pitch has been captured *and* resolved, which is also its value for every item type
     * that has no gap to sing into. It is deliberately not an answer: §5.4 decided the sung prediction
     * "supplements the judgment, it does not replace it," so this sits here until the learner presses
     * one of the three buttons, and rides along on the attempt that button produces. Nothing in the
     * loop consults it to decide anything, and [PracticeViewModel.onLabelSelected] is the only reader.
     */
    val audiatedPitch: AudiatedPitch? = null,
    /**
     * How many taps the learner has entered for the rhythm item on screen — docs/40-PHASE-4-SPEC.md
     * §7.2. Cleared on every item.
     *
     * A count, never a target. §7.4 forbids a precision grade, and a count shown against the number of
     * events the pattern actually had would be one — it would also hand the learner the answer to a
     * question they are still being asked.
     */
    val tapCount: Int = 0,
    /**
     * Whether each tap makes a sound — docs/40-PHASE-4-SPEC.md §7.2's optional toggle, off by default.
     *
     * §7.2 is explicit that this is double-edged rather than an improvement: it helps a learner hear
     * their own timing against the metronome, and it also adds output latency to their own feedback
     * loop and can mask the pattern. So the default is off and the learner decides, and §7.2's own
     * note that it should be revisited with real testing stands.
     */
    val audibleTaps: Boolean = false,
    /**
     * How the last tapped attempt landed, shown after the answer — docs/40-PHASE-4-SPEC.md §7.4.
     *
     * Carried whole rather than reduced to a number on the way here, because §7.4 asks for "a simple
     * visual of where taps landed relative to where events were" and calls it "the most instructive
     * feedback in the whole module ... worth designing properly rather than reducing to a percentage."
     * A percentage is exactly what a summarized version would become.
     *
     * This is the display side of §6.3's split: raw asynchrony is shown here and never scored. Nothing
     * in `:core:engine` reads this field, and `RawAsynchronyIsNeverScoredTest` holds the other end.
     */
    val lastRhythmScore: RhythmScore? = null,
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

    /** The prediction item, when that is what is on screen — the audiation gap and its capture need the concrete type. */
    val predictionItem: Item.PredictionItem?
        get() = item as? Item.PredictionItem

    /** The rhythm item, when that is what is on screen — the tap surface and the pulse need the pattern. */
    val rhythmItem: Item.RhythmItem?
        get() = item as? Item.RhythmItem
}

/**
 * The sung answer's cycle — docs/30-PHASE-3-SPEC.md §5.2 and §6.4.
 *
 * Deliberately has no "wrong" state. §5.2: a mumble, a cough, silence or noise "produces a retry
 * prompt, never a recorded incorrect attempt," because a false wrong corrupts the staircase and the
 * confusion matrix. An unreadable answer therefore returns here, to [UNCLEAR], and the item is still
 * waiting — it has not been answered at all.
 */
enum class SungCaptureState {
    /** Nothing captured. The learner may sing or tap. */
    IDLE,

    /** Recording. Bounded by the capture window; the ladder stays live throughout, per §6.3. */
    LISTENING,

    /** Captured but not readable as a pitch. Ask again; score nothing. */
    UNCLEAR,
}

/** The first-run explanations, one per task shape (docs/08-UI-SPEC.md §3a). */
enum class IntroKind {
    NONE,

    /**
     * Answering by singing — docs/30-PHASE-3-SPEC.md §6.2. Unlike every other kind here this one is not
     * tied to a skill node: the question being asked is unchanged, only the way it is answered, so it
     * can surface on any node once the learner opts in.
     */
    SUNG,

    /** Degree identification in major. */
    M2,

    /**
     * Mode identification — what "major" and "minor" name, demonstrated as a labeled contrast pair.
     * The screen ([M9IntroContent]) existed, previewed and tested, since Phase 2 Stage 2.2; this
     * entry is what finally dispatches it. Its absence was the exact half-wired shape
     * `IntroDispatchTest`'s KDoc warns about: a correct screen no line of composition ever reached.
     */
    M9,

    /** Degree identification in minor — what changed, and what `♭3` means. */
    M10,

    /** Chromatic degrees — what a note *between* the familiar ones is, and why it borrows their number. */
    M11,

    /** Audiation — that the task runs backwards, that the silence is the exercise, and what to do in it. */
    M12,

    /** Mixed mode — that the mode stops being announced, and why the ladder grew to ten buttons. */
    MIXED_MODE,
}
