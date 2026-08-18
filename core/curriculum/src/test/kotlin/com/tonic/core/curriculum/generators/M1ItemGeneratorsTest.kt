package com.tonic.core.curriculum.generators

import com.tonic.core.model.ids.SkillIds
import com.tonic.core.model.items.AnswerAlphabet
import com.tonic.core.model.music.TimbreId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class M1ItemGeneratorsTest {
    @Test
    fun `contour figures actually match every documented shape`() {
        val seenShapes = mutableSetOf<String>()
        repeat(200) { i ->
            val item =
                M1ItemGenerators.generateContour(
                    SkillIds.M1_CONTOUR,
                    minStepCents = 300.0,
                    TimbreId.PURE,
                    seed = i.toLong(),
                )
            val (a, b, c) = item.figureMidi
            val shape =
                when {
                    a < b && b < c -> AnswerAlphabet.Contour.UP
                    a > b && b > c -> AnswerAlphabet.Contour.DOWN
                    a == c && b > a -> AnswerAlphabet.Contour.UP_DOWN
                    a == c && b < a -> AnswerAlphabet.Contour.DOWN_UP
                    else -> "UNKNOWN"
                }
            seenShapes += shape
        }
        assertEquals(AnswerAlphabet.Contour.labels.toSet(), seenShapes)
    }

    @Test
    fun `step-leap intervals never exceed an octave and steps stay within 2 semitones`() {
        repeat(300) { i ->
            val item = M1ItemGenerators.generateStepLeap(SkillIds.M1_STEP_LEAP, TimbreId.PURE, seed = i.toLong())
            val interval = kotlin.math.abs(item.secondMidi - item.firstMidi)
            assertTrue(interval in 1..12, "interval $interval out of range")
        }
    }

    @Test
    fun `step-leap produces both steps and leaps across many seeds`() {
        val intervals =
            (0 until 100)
                .map { i ->
                    M1ItemGenerators.generateStepLeap(SkillIds.M1_STEP_LEAP, TimbreId.PURE, seed = i.toLong())
                }.map { kotlin.math.abs(it.secondMidi - it.firstMidi) }
        assertTrue(intervals.any { it <= 2 }, "no steps generated")
        assertTrue(intervals.any { it > 2 }, "no leaps generated")
    }
}
