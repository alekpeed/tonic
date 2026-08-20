package com.tonic.core.model

import com.tonic.core.model.ids.SkillIds
import com.tonic.core.model.items.AnswerAlphabet
import com.tonic.core.model.items.CadenceFadeLevel
import com.tonic.core.model.items.ReferenceElement
import com.tonic.core.model.items.ReferencePlan
import com.tonic.core.model.music.Mode
import com.tonic.core.model.music.TimbreId
import com.tonic.core.model.state.DiagnosticResult
import com.tonic.core.model.state.EntryPoint
import com.tonic.core.model.state.FsrsState
import com.tonic.core.model.state.MasteryState
import com.tonic.core.model.state.SkillState
import com.tonic.core.model.time.SystemClock
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Straightforward types that don't warrant their own test file. */
class MiscCoverageTest {
    @Test
    fun `mode phase 1 only is major`() {
        assertEquals(Mode.MAJOR, Mode.PHASE_1_ONLY)
    }

    @Test
    fun `timbre bank has all four phase 1 families`() {
        assertEquals(setOf(TimbreId.PURE, TimbreId.SOFT, TimbreId.PLUCK, TimbreId.REED), TimbreId.ALL_PHASE_1)
    }

    @Test
    fun `answer alphabets carry their documented labels`() {
        assertEquals(listOf("HIGHER", "LOWER"), AnswerAlphabet.HigherLower.labels)
        assertEquals(listOf("SAME", "DIFFERENT"), AnswerAlphabet.SameDifferent.labels)
        assertEquals(listOf("UP", "DOWN", "UP_DOWN", "DOWN_UP"), AnswerAlphabet.Contour.labels)
        assertEquals(listOf("STEP", "LEAP"), AnswerAlphabet.StepLeap.labels)
        assertEquals(listOf("INTACT", "ALTERED"), AnswerAlphabet.IntactAltered.labels)
    }

    @Test
    fun `reference plan carries its elements and reuse count`() {
        val plan =
            ReferencePlan(
                cadenceFadeLevel = CadenceFadeLevel.L4,
                elements =
                    listOf(
                        ReferenceElement.DroneEvent(
                            midi = 60,
                            durationMs = 3000,
                            timbre = TimbreId.SOFT,
                            relativeDb = -18.0,
                        ),
                        ReferenceElement.Silence(durationMs = 200),
                    ),
            )
        assertEquals(1, plan.reusableForItems)
        assertEquals(2, plan.elements.size)
        assertTrue(plan.elements[0] is ReferenceElement.DroneEvent)
    }

    @Test
    fun `system clock reports a plausible current instant`() {
        val before = Instant.now()
        val now = SystemClock().now()
        val after = Instant.now()
        assertTrue(!now.isBefore(before) && !now.isAfter(after))
    }

    @Test
    fun `diagnostic result and skill state hold together as plain data`() {
        val diagnostic =
            DiagnosticResult(
                pitchDirectionThresholdCents = 40,
                discriminationDPrime = 2.3,
                tonalMemorySpan = 4,
                amusiaIndicatorFlag = false,
                recommendedEntry = EntryPoint.M2_STAGE_1,
                initialAxisLevels = emptyMap(),
                completedAt = Instant.parse("2026-01-01T00:00:00Z"),
                seed = 99L,
            )
        assertEquals(EntryPoint.M2_STAGE_1, diagnostic.recommendedEntry)

        val skillState =
            SkillState(
                skillId = SkillIds.M2_DEG_SET_1,
                axisLevels = emptyMap(),
                staircaseStates = emptyMap(),
                activeAxis = null,
                masteryState = MasteryState.AVAILABLE,
                masteredAt = null,
                fsrs = FsrsState(stability = 0.0, difficulty = 0.0, lastReview = null, due = null),
                totalAttempts = 0,
                updatedAt = Instant.parse("2026-01-01T00:00:00Z"),
            )
        assertEquals(MasteryState.AVAILABLE, skillState.masteryState)
    }
}
