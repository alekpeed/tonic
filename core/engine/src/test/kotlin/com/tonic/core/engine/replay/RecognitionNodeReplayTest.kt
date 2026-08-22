package com.tonic.core.engine.replay

import com.tonic.core.curriculum.generators.GenerationHistory
import com.tonic.core.curriculum.generators.M2ItemGenerator
import com.tonic.core.curriculum.graph.SkillGraph
import com.tonic.core.model.attempts.Attempt
import com.tonic.core.model.ids.SkillId
import com.tonic.core.model.ids.SkillIds
import com.tonic.core.model.items.CadenceFadeLevel
import com.tonic.core.model.items.DifficultyAxis
import com.tonic.core.model.music.Mode
import com.tonic.core.model.music.ScaleDegree
import com.tonic.core.model.state.MasteryState
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Replay applies to every recognition node, not only to `M2`.
 *
 * A REAL BUG this file exists to pin. [SkillStateReducer] gated the whole reduction — staircase,
 * mastery, FSRS — on `skillId.moduleId != ModuleId.M2`, which was correct exactly as long as `M2` was
 * the only recognition module. Left alone through Phase 2 it would have denied every `M10` and `M11`
 * node a mastery verdict: those nodes would have accumulated attempts forever, stayed
 * [MasteryState.IN_PROGRESS] permanently, never scheduled a review, and never progressed to a
 * successor — and docs/20-PHASE-2-SPEC.md §3's per-degree criterion, Stage 2.5's whole subject, would
 * have been written and then never consulted by anything a user could reach.
 *
 * The test is now membership of [SkillGraph], which is the property that actually decides whether a
 * full reduction is defined for a skill.
 */
class RecognitionNodeReplayTest {
    private fun attempt(
        skillId: SkillId,
        index: Int,
        target: ScaleDegree,
        correct: Boolean,
    ) = Attempt(
        skillId = skillId,
        sessionId = 1L,
        itemSeed = index.toLong(),
        axisLevels = mapOf(DifficultyAxis.CADENCE_FADE to CadenceFadeLevel.MASTERY_MINIMUM.level),
        targetLabel = target.canonicalLabel,
        responseLabel = if (correct) target.canonicalLabel else "1",
        correct = correct,
        latencyMs = 800,
        replayCount = 0,
        keyPitchClass = 0,
        targetMidi = 60 + target.semitoneOffset(Mode.MAJOR),
        timbreId = "PURE",
        cadenceFadeLevel = CadenceFadeLevel.MASTERY_MINIMUM.level,
        timestamp = Instant.EPOCH.plus(index.toLong(), ChronoUnit.SECONDS),
    )

    /**
     * A clean run of [count] attempts, with the targets drawn from the **real generator** rather than
     * round-robin.
     *
     * This matters and is not incidental care. Round-robin is uniform, and uniform sampling cannot
     * satisfy docs/20-PHASE-2-SPEC.md §3's focus criterion at `M11`'s widths — that is precisely why
     * `SkillGraph.degreeWeightsFor` weights the introduced degree. A test that hand-rolled its own
     * distribution would be testing a learner the app never produces, and would report the criterion as
     * unreachable when in production it is reachable. Driving the generator keeps the sampling policy
     * and the mastery bar tested against each other.
     */
    private fun cleanRun(
        skillId: SkillId,
        count: Int,
        wrongOn: ScaleDegree? = null,
    ): List<Attempt> {
        var history = GenerationHistory()
        val axes = DifficultyAxis.RECOGNITION_AXES.associateWith { 0 }
        return (0 until count).map { i ->
            val result = M2ItemGenerator.generate(skillId, axes, seed = 900L + i, history = history)
            history = result.updatedHistory
            val target = result.item.targetDegree
            attempt(skillId, i, target, correct = target != wrongOn)
        }
    }

    @Test
    fun `a minor node reaches mastery from a clean run, exactly as a major one does`() {
        val state = SkillStateReducer.replay(SkillIds.M10_MIN_SET_1, cleanRun(SkillIds.M10_MIN_SET_1, 60))
        assertEquals(
            MasteryState.MASTERED,
            state.masteryState,
            "M10 nodes are structurally identical to M2's and must certify the same way",
        )
        assertTrue(state.fsrs.due != null, "and enter FSRS review, or they are never scheduled again")
    }

    @Test
    fun `a chromatic node reaches mastery from a clean run`() {
        val state = SkillStateReducer.replay(SkillIds.M11_CHROM_SHARP4, cleanRun(SkillIds.M11_CHROM_SHARP4, 60))
        assertEquals(MasteryState.MASTERED, state.masteryState)
    }

    @Test
    fun `being wrong about the degree the node introduces blocks its mastery`() {
        // docs/20-PHASE-2-SPEC.md §3's sixth criterion, reached through the production path rather than
        // by calling the evaluator directly - which is the only way to show it is actually consulted.
        val focus = SkillGraph.focusDegreeFor(SkillIds.M11_CHROM_SHARP4)!!
        assertEquals(ScaleDegree(4, 1), focus)

        val state =
            SkillStateReducer.replay(
                SkillIds.M11_CHROM_SHARP4,
                cleanRun(SkillIds.M11_CHROM_SHARP4, 60, wrongOn = focus),
            )
        assertFalse(
            state.masteryState == MasteryState.MASTERED,
            "a learner who never gets ♯4 right cannot master the node that exists to teach ♯4",
        )
    }

    @Test
    fun `a skill outside the graph still takes the fallback path`() {
        // M0's diagnostic screening has no degree set and no mastery lifecycle - the fallback is what
        // is well-defined for it, and widening the predicate must not have swept it in.
        val state =
            SkillStateReducer.replay(
                SkillIds.M0_SAME_DIFF,
                listOf(attempt(SkillIds.M0_SAME_DIFF, 0, ScaleDegree(1), correct = true)),
            )
        assertEquals(MasteryState.IN_PROGRESS, state.masteryState)
        assertEquals(1, state.totalAttempts)
        assertTrue(state.staircaseStates.isEmpty())
    }
}
