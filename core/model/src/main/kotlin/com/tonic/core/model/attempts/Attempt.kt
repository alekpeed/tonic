package com.tonic.core.model.attempts

import com.tonic.core.model.ids.SkillId
import com.tonic.core.model.items.DifficultyAxis
import com.tonic.core.model.rhythm.rhythmDriftSlope
import com.tonic.core.model.rhythm.rhythmPatternAccuracy
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
    /**
     * What a tapped rhythm attempt recorded — docs/40-PHASE-4-SPEC.md §8. Null on every other attempt,
     * which is every attempt written before Phase 4.
     *
     * Null rather than an empty [RhythmAttemptData] for the same reason `sungCents` is null rather than
     * zero: "no taps were recorded" and "the learner tapped nothing" are different facts, and only one
     * of them ever happened to a pitch attempt.
     */
    val rhythm: RhythmAttemptData? = null,
)

/**
 * The tapped half of a rhythm attempt — docs/40-PHASE-4-SPEC.md §8 and §6.3.
 *
 * §4.4 is what this exists for, in its own words: tap timestamps "are recorded with the attempt, which
 * makes any real session fully replayable and any scoring bug reproducible offline." Everything here is
 * an input to or an output of the pure scorer, so a stored attempt can be re-scored years later and
 * produce the same verdict.
 *
 * @property tapTimesMs each tap in milliseconds from the pattern's start, calibration already applied.
 *   Corrected offsets rather than raw instants, because a raw nanosecond instant means nothing without
 *   the output timebase of a playback that is long over.
 * @property calibrationOffsetMs the constant that was subtracted, recorded so the raw taps can be
 *   recovered and so a later change to calibration does not silently rewrite history.
 * @property toleranceHalfWidthMs the window that was actually applied, after §6.2's crowding clamp.
 *   Recorded rather than recomputed: a tolerance derived from today's axis levels would answer a
 *   different question than the one the learner was asked.
 * @property expectedEventTimesMs when each event should have sounded, in milliseconds from the
 *   pattern's start, in order and aligned with [perEventAsynchronyMs]. Without it an asynchrony list
 *   is half a record: it says how far each tap fell from its event but not *when* that event was, so
 *   the trend §5.3 criterion 3 is defined as cannot be recomputed from a stored attempt at all.
 *   Stored rather than regenerated from the item seed for the same reason `targetLabel` and
 *   `targetMidi` are (docs/05-DATA-MODEL.md §1): a replay that re-derived the item would silently
 *   re-decide old mastery the day a generator changed.
 * @property perEventFigures the Takadimi signature of the beat each event belongs to, aligned with
 *   [perEventAsynchronyMs]. §5.3 criterion 5 holds every figure to 80%, and for a *tapped* attempt
 *   the figure is not the label - a production item's `targetLabel` is only ever `"TAPPED"` - so the
 *   attribution has to be per event.
 * @property perEventAsynchronyMs how far each tap fell from the event it matched, in order, with null
 *   for a missed event. **Recorded and shown, never scored** (§6.3) — the guarantee
 *   `RawAsynchronyIsNeverScoredTest` holds the scorer to.
 * @property extraTaps taps that matched no event, and [missedTaps] events nothing matched. Counted
 *   separately because §6.2 says the two "mean different things pedagogically".
 */
public data class RhythmAttemptData(
    public val tapTimesMs: List<Double>,
    public val calibrationOffsetMs: Double,
    public val toleranceHalfWidthMs: Double,
    public val expectedEventTimesMs: List<Double>,
    public val perEventFigures: List<String>,
    public val perEventAsynchronyMs: List<Double?>,
    public val extraTaps: Int,
    public val missedTaps: Int,
) {
    init {
        require(expectedEventTimesMs.size == perEventAsynchronyMs.size) {
            "Every expected event needs an asynchrony slot: " +
                "${expectedEventTimesMs.size} events, ${perEventAsynchronyMs.size} asynchronies"
        }
        require(perEventFigures.size == perEventAsynchronyMs.size) {
            "Every expected event needs a figure: " +
                "${perEventFigures.size} figures, ${perEventAsynchronyMs.size} asynchronies"
        }
        require(missedTaps == perEventAsynchronyMs.count { it == null }) {
            "missedTaps says $missedTaps, the asynchrony list says " +
                "${perEventAsynchronyMs.count { it == null }}"
        }
    }

    /** Events a tap landed on. */
    public val matchedCount: Int get() = perEventAsynchronyMs.count { it != null }

    /**
     * The share of this pattern the learner reproduced — docs/40-PHASE-4-SPEC.md §5.3 criterion 1,
     * "right notes in the right places".
     *
     * Measured against whichever is larger, the events expected or the taps given, so that adding
     * sounds is never free. Shares its arithmetic with
     * [com.tonic.core.model.rhythm.RhythmScore.patternAccuracy] rather than restating it.
     */
    public val patternAccuracy: Double
        get() = rhythmPatternAccuracy(perEventAsynchronyMs.size, matchedCount, extraTaps)

    /**
     * How fast the learner's error grew across this pattern, as a fraction of a beat per beat —
     * docs/40-PHASE-4-SPEC.md §5.3 criterion 3.
     *
     * The same least-squares trend [com.tonic.core.model.rhythm.RhythmScore.driftSlope] computes at
     * scoring time, recomputed here from the stored record rather than carried alongside it. That is
     * the point of storing [expectedEventTimesMs]: a derived number frozen into a column can never be
     * corrected, while the two lists it comes from can be re-read by any later definition of drift.
     *
     * Dimensionless — milliseconds of error per millisecond elapsed — so it means the same thing at
     * every tempo and every pattern length, which is what a single mastery threshold needs.
     *
     * Null when fewer than two events were matched: a trend through one point is not a trend.
     */
    public val driftSlope: Double?
        get() =
            rhythmDriftSlope(
                expectedEventTimesMs.zip(perEventAsynchronyMs).mapNotNull { (dueAt, asynchrony) ->
                    asynchrony?.let { dueAt to it }
                },
            )

    /**
     * For each rhythmic figure this pattern contained, how many of its beats the learner produced
     * whole — docs/40-PHASE-4-SPEC.md §5.3 criterion 5.
     *
     * A figure instance counts as produced when *every* event belonging to it was struck. Extra taps
     * are not charged against any figure: a missed event belongs to a known beat, while a tap that
     * landed on nothing belongs nowhere in particular, and charging it to whichever figure it fell
     * nearest would make the weakest-figure number depend on how the stray was rounded. Extra taps
     * are already paid for in [patternAccuracy], which is criterion 1.
     *
     * @return figure signature to (produced whole, instances seen).
     */
    public fun figureOutcomes(): Map<String, Pair<Int, Int>> =
        perEventFigures
            .indices
            .groupBy { perEventFigures[it] }
            .mapValues { (_, indices) ->
                indices.count { perEventAsynchronyMs[it] != null } to indices.size
            }
}
