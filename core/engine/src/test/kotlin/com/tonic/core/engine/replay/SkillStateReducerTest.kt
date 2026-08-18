package com.tonic.core.engine.replay

import com.tonic.core.curriculum.graph.SkillGraph
import com.tonic.core.model.attempts.Attempt
import com.tonic.core.model.ids.SkillIds
import com.tonic.core.model.items.DifficultyAxis
import com.tonic.core.model.state.MasteryState
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * docs/09-BUILD-PLAN.md Stage 5 acceptance depends on this being correct:
 * `:core:data`'s `rebuildFromAttempts()` is only as trustworthy as this
 * reducer. These tests check the reduction itself, independent of any
 * database.
 */
class SkillStateReducerTest {
    private val skill = SkillIds.M2_DEG_SET_1

    private fun attempt(
        index: Int,
        correct: Boolean,
        target: String = "1",
        isWarmup: Boolean = false,
        isAbandoned: Boolean = false,
        isIndependenceCheckProbe: Boolean = false,
        axisLevels: Map<DifficultyAxis, Int> = emptyMap(),
        skillId: com.tonic.core.model.ids.SkillId = skill,
    ) = Attempt(
        skillId = skillId,
        sessionId = 1L,
        itemSeed = index.toLong(),
        axisLevels = axisLevels,
        targetLabel = target,
        responseLabel = if (correct) target else "3",
        correct = correct,
        latencyMs = 500,
        replayCount = 0,
        keyPitchClass = 0,
        targetMidi = 60,
        timbreId = "PURE",
        cadenceFadeLevel = axisLevels[DifficultyAxis.CADENCE_FADE] ?: 0,
        timestamp = Instant.EPOCH.plus(index.toLong(), ChronoUnit.SECONDS),
        isWarmup = isWarmup,
        isAbandoned = isAbandoned,
        isIndependenceCheckProbe = isIndependenceCheckProbe,
    )

    @Test
    fun `no attempts yields the initial state`() {
        val state = SkillStateReducer.replay(skill, emptyList())
        assertEquals(MasteryState.LOCKED, state.masteryState)
        assertEquals(0, state.totalAttempts)
    }

    @Test
    fun `abandoned attempts are excluded entirely - they never touch axis levels or the count`() {
        val attempts =
            listOf(
                attempt(0, correct = true, isAbandoned = true),
                attempt(1, correct = true),
            )
        val state = SkillStateReducer.replay(skill, attempts)
        assertEquals(1, state.totalAttempts, "the abandoned attempt must not count")
    }

    @Test
    fun `warmup attempts are recorded in totalAttempts but do not move the staircase`() {
        val attempts = (0 until 5).map { attempt(it, correct = true, isWarmup = true) }
        val state = SkillStateReducer.replay(skill, attempts)
        assertEquals(5, state.totalAttempts)
        assertEquals(0, state.axisLevels[DifficultyAxis.CADENCE_FADE], "warmups must not advance the staircase")
        assertEquals(MasteryState.IN_PROGRESS, state.masteryState)
    }

    @Test
    fun `a non-M2 skill (M0) gets the minimal reconstruction, not a mastery lifecycle`() {
        val diagnosticAttempt =
            attempt(0, correct = true).copy(
                skillId = SkillIds.M0_PITCH_DIR,
                axisLevels =
                    mapOf(
                        DifficultyAxis.CADENCE_FADE to 3,
                    ),
            )
        val state = SkillStateReducer.replay(SkillIds.M0_PITCH_DIR, listOf(diagnosticAttempt))
        assertEquals(1, state.totalAttempts)
        assertEquals(
            mapOf(DifficultyAxis.CADENCE_FADE to 3),
            state.axisLevels,
            "should snapshot the last attempt's axis levels",
        )
        assertEquals(MasteryState.IN_PROGRESS, state.masteryState)
        assertNull(state.masteredAt)
    }

    @Test
    fun `an M2 skill reaches MASTERED and then accrues FSRS reps from post-mastery review blocks`() {
        // A generous, mostly-correct responder drives the skill to mastery quickly, then 25 more
        // attempts (2 full review blocks of 10, plus a partial one left over) simulate post-mastery
        // review probes.
        val random = Random(42)
        val activeDegrees = listOf("1", "3", "5")
        val attempts = mutableListOf<Attempt>()
        var index = 0
        var mastered = false

        // Drive toward mastery: high accuracy, cycling target degrees for coverage.
        while (!mastered && index < 500) {
            val correct = random.nextDouble() < 0.95
            attempts += attempt(index, correct, target = activeDegrees[index % activeDegrees.size])
            index++
            val probe = SkillStateReducer.replay(skill, attempts)
            if (probe.masteryState == MasteryState.MASTERED) mastered = true
        }
        assertTrue(mastered, "expected mastery to be reached within 500 attempts at 95% accuracy")

        val masteredAtIndex = index
        repeat(25) {
            val correct = random.nextDouble() < 0.90
            attempts += attempt(index, correct, target = activeDegrees[index % activeDegrees.size])
            index++
        }

        val state = SkillStateReducer.replay(skill, attempts)
        assertEquals(MasteryState.MASTERED, state.masteryState)
        assertEquals(attempts[masteredAtIndex - 1].timestamp, state.masteredAt)
        assertEquals(attempts.size, state.totalAttempts)
        // 2 full 10-item review blocks completed after mastery -> initial review (reps=1) + 2 more (reps=3).
        assertEquals(3, state.fsrs.reps)
        assertEquals(0, state.fsrs.lapses, "a 90%+ accurate reviewer should never grade AGAIN")
    }

    @Test
    fun `replaying the same history twice is byte-identical - determinism is not optional`() {
        val attempts = (0 until 60).map { attempt(it, correct = it % 5 != 0, target = listOf("1", "3", "5")[it % 3]) }
        val first = SkillStateReducer.replay(skill, attempts)
        val second = SkillStateReducer.replay(skill, attempts)
        assertEquals(first, second)
    }

    @Test
    fun `a failed independence check's fade-axis reduction survives a rebuild over later ordinary review attempts`() {
        // docs/09-BUILD-PLAN.md Stage 6: the check's failure consequence must be re-derivable from the
        // attempt log on every rebuild, not a one-off write - otherwise a later ordinary (non-probe)
        // attempt triggers a rebuild that replays the whole history from scratch and, since the fold
        // freezes axisLevels at whatever they were AT mastery, silently recomputes the reduction away.
        val node = SkillIds.M2_FULL_DIATONIC
        val degrees = SkillGraph.activeDegreesFor(node).map { it.degree.toString() }
        val random = Random(7)
        val attempts = mutableListOf<Attempt>()
        var index = 0
        var mastered = false
        while (!mastered && index < 2000) {
            val correct = random.nextDouble() < 0.95
            attempts += attempt(index, correct, target = degrees[index % degrees.size], skillId = node)
            index++
            if (SkillStateReducer.replay(node, attempts).masteryState == MasteryState.MASTERED) mastered = true
        }
        assertTrue(mastered, "expected M2_FULL_DIATONIC to reach mastery within 2000 attempts at 95% accuracy")

        val cadenceBeforeCheck =
            SkillStateReducer
                .replay(node, attempts)
                .axisLevels
                .getValue(DifficultyAxis.CADENCE_FADE)

        // 30 forced-L6 probes, mostly wrong - a clean fail (well under the 85% pass threshold).
        repeat(30) { probeIndex ->
            attempts +=
                attempt(
                    index,
                    correct = probeIndex % 4 == 0,
                    target = degrees[index % degrees.size],
                    isIndependenceCheckProbe = true,
                    axisLevels = mapOf(DifficultyAxis.CADENCE_FADE to 6),
                    skillId = node,
                )
            index++
        }
        val stateRightAfterCheck = SkillStateReducer.replay(node, attempts)
        assertEquals(
            cadenceBeforeCheck - 1,
            stateRightAfterCheck.axisLevels[DifficultyAxis.CADENCE_FADE],
            "a failed check must lower the fade axis one step immediately",
        )

        // Post-check ordinary review-block attempts - exactly what would trigger a later rebuild in
        // the real practice loop (persistAndAdapt calls rebuildFromAttempts on every non-probe attempt).
        repeat(12) {
            attempts += attempt(index, correct = true, target = degrees[index % degrees.size], skillId = node)
            index++
        }
        val finalState = SkillStateReducer.replay(node, attempts)
        assertEquals(
            cadenceBeforeCheck - 1,
            finalState.axisLevels[DifficultyAxis.CADENCE_FADE],
            "the reduction must still hold after later ordinary attempts force a full rebuild",
        )
    }
}
