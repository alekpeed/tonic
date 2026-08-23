package com.tonic.core.engine.mastery

import com.tonic.core.model.attempts.Attempt
import com.tonic.core.model.attempts.RhythmAttemptData
import com.tonic.core.model.ids.SkillIds
import com.tonic.core.model.items.DifficultyAxis
import com.tonic.core.model.rhythm.MetronomeFadeLevel
import com.tonic.core.model.state.MasteryCriterion
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * docs/40-PHASE-4-SPEC.md §5.3's five production criteria.
 *
 * The reading of criterion 2 is the decision under test and is asserted directly, not assumed: taken
 * word for word it is vacuous, because a pattern is only correct once every tap is already inside the
 * tolerance. §6.1 and §6.3 between them force the reading implemented here — timing reaches mastery
 * through the window and only through the window.
 */
class RhythmProductionMasteryEvaluatorTest {
    private val beatMs = 600.0

    /** One bar of four plain beats, all struck [asynchronies] late. */
    private fun tapped(
        asynchronies: List<Double?>,
        figures: List<String> = List(asynchronies.size) { "ta" },
        extraTaps: Int = 0,
        tolerance: Double = 150.0,
    ): RhythmAttemptData {
        val times = asynchronies.indices.map { it * beatMs }
        return RhythmAttemptData(
            tapTimesMs = times.zip(asynchronies).mapNotNull { (at, a) -> a?.let { at + it } },
            calibrationOffsetMs = 0.0,
            toleranceHalfWidthMs = tolerance,
            expectedEventTimesMs = times,
            perEventFigures = figures,
            perEventAsynchronyMs = asynchronies,
            extraTaps = extraTaps,
            missedTaps = asynchronies.count { it == null },
        )
    }

    private fun attempt(
        rhythm: RhythmAttemptData?,
        correct: Boolean,
        index: Int = 0,
    ) = Attempt(
        skillId = SkillIds.M3_SUBDIV,
        sessionId = 1L,
        itemSeed = index.toLong(),
        axisLevels = emptyMap(),
        targetLabel = "TAPPED",
        responseLabel = "TAPPED",
        correct = correct,
        latencyMs = 0L,
        replayCount = 0,
        keyPitchClass = 0,
        targetMidi = 0,
        timbreId = "PURE",
        cadenceFadeLevel = 0,
        timestamp = Instant.EPOCH.plusSeconds(index.toLong()),
        rhythm = rhythm,
    )

    /** A flawless window: every event struck, all a steady 20 ms late, which is a calibration artifact. */
    private fun perfectWindow(size: Int = RhythmProductionMasteryEvaluator.WINDOW_SIZE) =
        (0 until size).map { i -> attempt(tapped(List(4) { 20.0 }), correct = true, index = i) }

    private fun evaluate(
        window: List<Attempt>,
        fade: Int = MetronomeFadeLevel.MASTERY_MINIMUM.level,
    ) = RhythmProductionMasteryEvaluator.evaluate(window, mapOf(DifficultyAxis.METRONOME_FADE to fade))

    private fun criterion(
        window: List<Attempt>,
        kind: MasteryCriterion.Kind,
        fade: Int = MetronomeFadeLevel.MASTERY_MINIMUM.level,
    ) = evaluate(window, fade).criteria.single { it.kind == kind }

    @Test
    fun `a clean window at a faded metronome is mastered`() {
        assertTrue(evaluate(perfectWindow()).isMastered)
    }

    @Test
    fun `every criterion 5,3 lists is reported, and nothing else is`() {
        // The list is the spec's, in the spec's order, so blockingCriterion surfaces the earliest
        // failure a learner has - plus WINDOW_COVERAGE, which is §5.3's "over a rolling 30" made
        // checkable rather than a sixth criterion of its own.
        assertEquals(
            listOf(
                MasteryCriterion.Kind.WINDOW_COVERAGE,
                MasteryCriterion.Kind.PATTERN_ACCURACY,
                MasteryCriterion.Kind.TIMING_CONSISTENCY,
                MasteryCriterion.Kind.TIMING_DRIFT,
                MasteryCriterion.Kind.METRONOME_FADE_MINIMUM,
                MasteryCriterion.Kind.WEAKEST_FIGURE_ACCURACY,
            ),
            evaluate(perfectWindow()).criteria.map { it.kind },
        )
    }

    @Test
    fun `a short window blocks on coverage before anything else`() {
        val verdict = evaluate(perfectWindow(size = 10))
        assertFalse(verdict.isMastered)
        assertEquals(MasteryCriterion.Kind.WINDOW_COVERAGE, verdict.blockingCriterion?.kind)
    }

    @Test
    fun `a metronome that has not faded blocks mastery however clean the tapping`() {
        // §5.3 criterion 4, and the whole reason it exists: without it a learner masters rhythm having
        // never once kept time unaided.
        val verdict = evaluate(perfectWindow(), fade = MetronomeFadeLevel.MASTERY_MINIMUM.level - 1)
        assertFalse(verdict.isMastered)
        assertEquals(MasteryCriterion.Kind.METRONOME_FADE_MINIMUM, verdict.blockingCriterion?.kind)
    }

    @Test
    fun `pattern accuracy counts events, not attempts`() {
        // The distinction §6.1 insists on. Every attempt here missed exactly one of four events, so
        // not one of them is correct - and yet three quarters of the sounds were in the right places.
        // OVERALL_ACCURACY would call this window zero; criterion 1 calls it 0.75.
        val window =
            (0 until RhythmProductionMasteryEvaluator.WINDOW_SIZE).map { i ->
                attempt(tapped(listOf(10.0, 10.0, 10.0, null)), correct = false, index = i)
            }
        assertEquals(0.75, criterion(window, MasteryCriterion.Kind.PATTERN_ACCURACY).measuredValue, 1e-9)
        assertFalse(criterion(window, MasteryCriterion.Kind.PATTERN_ACCURACY).met)
    }

    @Test
    fun `an extra tap is never free, and does not cost the same as a missed one`() {
        // Four events, all struck, plus one tap that landed on nothing.
        val added =
            tapped(listOf(10.0, 10.0, 10.0, 10.0), extraTaps = 1).let {
                it.copy(tapTimesMs = it.tapTimesMs + 4 * beatMs)
            }
        assertTrue(added.patternAccuracy < 1.0, "a flurry between the events must not be free")

        // And it costs less than dropping one, which is not a defect: a miss takes something out of
        // the numerator while an addition only grows the denominator, and §6.2 keeps the two apart
        // deliberately because they "mean different things pedagogically".
        val missed = tapped(listOf(10.0, 10.0, 10.0, null))
        assertTrue(missed.patternAccuracy < added.patternAccuracy)
    }

    @Test
    fun `criterion 2 counts whole patterns, so a window of near-misses fails it`() {
        // 90% of events struck across the window, which clears criterion 1 - but four fifths of the
        // patterns had a slip in them, so criterion 2 does not clear. That gap is the reason §5.3 asks
        // for both: a learner who never quite finishes a pattern has not produced one.
        val size = RhythmProductionMasteryEvaluator.WINDOW_SIZE
        val slipped = size * 4 / 5
        val window =
            (0 until size).map { i ->
                if (i < slipped) {
                    // One event of ten missed: 90% of this pattern, and not a correct pattern.
                    attempt(tapped(List(9) { 10.0 } + listOf(null)), correct = false, index = i)
                } else {
                    attempt(tapped(List(10) { 10.0 }), correct = true, index = i)
                }
            }
        assertTrue(criterion(window, MasteryCriterion.Kind.PATTERN_ACCURACY).met, "criterion 1 clears")
        assertFalse(criterion(window, MasteryCriterion.Kind.TIMING_CONSISTENCY).met, "criterion 2 does not")
    }

    @Test
    fun `a constant offset is not drift, however large`() {
        // §5.3 criterion 3 in its own words: "a constant offset is a calibration artifact while a
        // growing offset is a real timekeeping failure." §9's simulation-2 learner, uniformly late,
        // must master.
        val window =
            (0 until RhythmProductionMasteryEvaluator.WINDOW_SIZE).map { i ->
                attempt(tapped(List(8) { 140.0 }), correct = true, index = i)
            }
        assertEquals(0.0, criterion(window, MasteryCriterion.Kind.TIMING_DRIFT).measuredValue, 1e-9)
        assertTrue(evaluate(window).isMastered)
    }

    @Test
    fun `a learner who speeds up across every pattern is blocked`() {
        // Sixteen beats, ending a third of a beat early - the case MAX_DRIFT_SLOPE is set against.
        // Every attempt is scored correct, so criteria 1 and 2 both clear and only criterion 3 can
        // catch it. That is the entire reason criterion 3 exists.
        // Centered on the window rather than starting at zero, which is what makes the attempt
        // genuinely correct: the taps run from 135 ms early to 135 ms late inside a 150 ms window, so
        // nothing about the pattern is scored wrong and only the trend under it is visible.
        val window =
            (0 until RhythmProductionMasteryEvaluator.WINDOW_SIZE).map { i ->
                attempt(
                    tapped((0 until 16).map { (it - MIDPOINT_OF_16) * 0.03 * beatMs }),
                    correct = true,
                    index = i,
                )
            }
        assertTrue(criterion(window, MasteryCriterion.Kind.PATTERN_ACCURACY).met)
        assertTrue(criterion(window, MasteryCriterion.Kind.TIMING_CONSISTENCY).met)
        assertFalse(criterion(window, MasteryCriterion.Kind.TIMING_DRIFT).met)
    }

    @Test
    fun `rushing on some patterns and dragging on others is not systematic drift`() {
        // "Systematic" is the operative word. A learner whose direction flips from item to item is
        // inconsistent, and inconsistency is paid for by criteria 1 and 2 when it costs them the
        // window - not by a criterion that exists to catch a one-way trend.
        val window =
            (0 until RhythmProductionMasteryEvaluator.WINDOW_SIZE).map { i ->
                val direction = if (i % 2 == 0) 1.0 else -1.0
                attempt(
                    tapped((0 until 8).map { direction * (it - MIDPOINT_OF_8) * 0.05 * beatMs }),
                    correct = true,
                    index = i,
                )
            }
        assertTrue(criterion(window, MasteryCriterion.Kind.TIMING_DRIFT).met)
    }

    @Test
    fun `one scrambled attempt does not decide the drift verdict`() {
        // Median, not mean, and for the same reason Calibrator uses one.
        val window =
            (0 until RhythmProductionMasteryEvaluator.WINDOW_SIZE).map { i ->
                if (i == 0) {
                    attempt(
                        tapped((0 until 8).map { (it - MIDPOINT_OF_8) * 0.5 * beatMs }),
                        correct = true,
                        index = i,
                    )
                } else {
                    attempt(tapped(List(8) { 10.0 }), correct = true, index = i)
                }
            }
        assertTrue(criterion(window, MasteryCriterion.Kind.TIMING_DRIFT).met)
    }

    @Test
    fun `one weak figure blocks mastery even when everything else is clean`() {
        // §5.3 criterion 5. Every window here strikes all four beats of `ta` and misses `ta-ka-di-mi`
        // one time in three, which leaves overall accuracy comfortable and one figure at 67%.
        val window =
            (0 until RhythmProductionMasteryEvaluator.WINDOW_SIZE).map { i ->
                val subdivisionStruck = i % 3 != 0
                attempt(
                    tapped(
                        asynchronies = List(9) { 10.0 } + listOf(if (subdivisionStruck) 10.0 else null),
                        figures = List(9) { "ta" } + listOf("ta-ka-di-mi"),
                    ),
                    correct = subdivisionStruck,
                    index = i,
                )
            }
        val weakest = criterion(window, MasteryCriterion.Kind.WEAKEST_FIGURE_ACCURACY)
        assertFalse(weakest.met)
        assertTrue(weakest.measuredValue < RhythmProductionMasteryEvaluator.MIN_FIGURE_ACCURACY)
    }

    @Test
    fun `an attempt whose tap record was lost still counts against the window`() {
        // Mappers drops a rhythm record whose per-event lists do not line up. Such an attempt must not
        // silently leave the window, or a learner could shrink their own sample by generating
        // unreadable rows; it falls back to the coarsest honest reading, which is whether it was
        // marked correct.
        val window =
            (0 until RhythmProductionMasteryEvaluator.WINDOW_SIZE).map { i ->
                if (i < 6) attempt(rhythm = null, correct = false, index = i) else perfectWindow()[i]
            }
        assertEquals(
            RhythmProductionMasteryEvaluator.WINDOW_SIZE.toDouble(),
            criterion(window, MasteryCriterion.Kind.WINDOW_COVERAGE).measuredValue,
        )
        assertFalse(criterion(window, MasteryCriterion.Kind.PATTERN_ACCURACY).met)
    }

    @Test
    fun `raw asynchrony never moves a criterion on its own`() {
        // The §6.3 guarantee, restated for mastery: two windows differing only in how far inside the
        // window every tap fell must produce the same verdict, criterion by criterion. Drift is the
        // one sanctioned reader and it sees no difference here, because neither window trends.
        val tight =
            (0 until RhythmProductionMasteryEvaluator.WINDOW_SIZE).map { i ->
                attempt(tapped(List(8) { 2.0 }), correct = true, index = i)
            }
        val loose =
            (0 until RhythmProductionMasteryEvaluator.WINDOW_SIZE).map { i ->
                attempt(tapped(List(8) { 140.0 }), correct = true, index = i)
            }
        assertEquals(evaluate(tight).criteria, evaluate(loose).criteria)
    }

    @Test
    fun `the drift bar is inclusive at its boundary`() {
        // Pinned because the reasoning in MAX_DRIFT_SLOPE's KDoc is about which side of 0.033 the bar
        // sits on, and a strict comparison would quietly move the bar by one ulp.
        val window =
            (0 until RhythmProductionMasteryEvaluator.WINDOW_SIZE).map { i ->
                attempt(
                    tapped(
                        (0 until 16).map {
                            (it - MIDPOINT_OF_16) * RhythmProductionMasteryEvaluator.MAX_DRIFT_SLOPE * beatMs
                        },
                        tolerance = 250.0,
                    ),
                    correct = true,
                    index = i,
                )
            }
        assertTrue(criterion(window, MasteryCriterion.Kind.TIMING_DRIFT).met)
    }

    private companion object {
        /** Midpoints, so a drift can be centered on the tolerance window rather than starting at zero. */
        const val MIDPOINT_OF_8 = 3.5
        const val MIDPOINT_OF_16 = 7.5
    }
}
