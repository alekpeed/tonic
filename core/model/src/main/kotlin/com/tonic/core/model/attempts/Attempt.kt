package com.tonic.core.model.attempts

import com.tonic.core.model.ids.SkillId
import com.tonic.core.model.items.DifficultyAxis
import java.time.Instant

/**
 * One recorded response. The append-only event log — docs/05-DATA-MODEL.md
 * §1: "everything else is derivable from this table; treat it as the
 * source of truth." [id]/[sessionId] are null/unset for an attempt not yet
 * persisted.
 */
data class Attempt(
    val id: Long? = null,
    val skillId: SkillId,
    val sessionId: Long,
    /** Regenerates the exact item this attempt answered — docs/05-DATA-MODEL.md §1. */
    val itemSeed: Long,
    /** Axis levels at generation time, not current levels. */
    val axisLevels: Map<DifficultyAxis, Int>,
    val targetLabel: String,
    /** Null if skipped or abandoned (docs/06-AUDIO-ENGINE.md §8: an interruption marks the attempt abandoned). */
    val responseLabel: String?,
    val correct: Boolean,
    /** Recorded for diagnostics, never scored — docs/02-PEDAGOGY.md §6. */
    val latencyMs: Long,
    val replayCount: Int,
    val keyPitchClass: Int,
    val targetMidi: Int,
    val timbreId: String,
    /** Denormalized from axisLevels for cheap querying — this is the axis that matters most (docs/05-DATA-MODEL.md §1). */
    val cadenceFadeLevel: Int,
    val timestamp: Instant,
    /**
     * Warm-up attempts are still recorded but excluded from mastery
     * evaluation and the staircase — docs/07-ADAPTIVE-ENGINE.md §8.
     */
    val isWarmup: Boolean = false,
    /** True if the item was abandoned (interruption, session kill) rather than genuinely answered or skipped. */
    val isAbandoned: Boolean = false,
    /**
     * True for one of the 30 forced-`CADENCE_FADE`-L6 probes that make up
     * `M2.INDEPENDENCE_CHECK` (docs/03-CURRICULUM.md §5.6). Still recorded
     * against the mastered node's [skillId] so the attempt log stays
     * complete and replayable, but excluded from that node's ordinary
     * mastery window and FSRS review-block accumulation — "a separate,
     * non-blocking assessment," not a regular review, and not run at "the
     * node's own mastered axis levels" FSRS reviews use.
     */
    val isIndependenceCheckProbe: Boolean = false,
    /**
     * How this answer was given — docs/30-PHASE-3-SPEC.md §4. Defaults to [InputMethod.TAP] so every
     * attempt written before Phase 3, and every attempt from a learner who never grants microphone
     * permission, reads back exactly as it always did.
     *
     * Recorded, never adapted on. §2's invariant is that sung and tapped attempts feed one
     * `SkillState`, so nothing in the adaptive engine may branch on this. See [sungCents].
     */
    val inputMethod: InputMethod = InputMethod.TAP,
    /**
     * How far the sung pitch landed from the answered degree, in cents, signed — negative is flat.
     * Null for every tapped attempt, and null for a sung one whose pitch could not be read.
     *
     * **For display and analysis only — docs/30-PHASE-3-SPEC.md §7, in those words.** Never read by
     * `Staircase`, `AxisScheduler`, `MasteryEvaluator` or `ConfusionTracker`. §3 mitigation 4 is what
     * this serves: how close you were is shown as information, never as a grade, because scoring it
     * would make the app measure singing rather than hearing. The same guarantee `replayCount` has,
     * for the same reason, and asserted the same way.
     */
    val sungCents: Int? = null,
)
