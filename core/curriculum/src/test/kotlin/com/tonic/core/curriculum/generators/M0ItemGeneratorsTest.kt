package com.tonic.core.curriculum.generators

import com.tonic.core.model.ids.SkillIds
import com.tonic.core.model.music.TimbreId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class M0ItemGeneratorsTest {
    @Test
    fun `pitch direction items are deterministic and encode direction as a signed cents offset`() {
        val a = M0ItemGenerators.generatePitchDirection(SkillIds.M0_PITCH_DIR, 200.0, TimbreId.PURE, seed = 1L)
        val b = M0ItemGenerators.generatePitchDirection(SkillIds.M0_PITCH_DIR, 200.0, TimbreId.PURE, seed = 1L)
        assertEquals(a, b)
        assertEquals(200.0, kotlin.math.abs(a.secondCentsOffset))
    }

    @Test
    fun `same-different respects the catch trial probability at the extremes`() {
        val alwaysCatch =
            M0ItemGenerators.generateSameDifferent(
                SkillIds.M0_SAME_DIFF,
                100.0,
                TimbreId.PURE,
                seed = 1L,
                catchTrialProbability = 1.0,
            )
        assertTrue(alwaysCatch.isCatchTrial)
        assertEquals(0.0, alwaysCatch.centsOffset)

        val neverCatch =
            M0ItemGenerators.generateSameDifferent(
                SkillIds.M0_SAME_DIFF,
                100.0,
                TimbreId.PURE,
                seed = 1L,
                catchTrialProbability = 0.0,
            )
        assertTrue(!neverCatch.isCatchTrial)
        assertEquals(100.0, neverCatch.centsOffset)
    }

    @Test
    fun `tonal memory produces the requested sequence length and a consistent alteration`() {
        val altered =
            M0ItemGenerators.generateTonalMemory(
                SkillIds.M0_TONAL_MEMORY,
                4,
                100.0,
                TimbreId.PURE,
                seed = 2L,
                sameProbability = 0.0,
            )
        assertEquals(4, altered.sequenceMidi.size)
        assertNotNull(altered.alteredIndex)
        assertEquals(100.0, altered.alterationCents)

        val same =
            M0ItemGenerators.generateTonalMemory(
                SkillIds.M0_TONAL_MEMORY,
                4,
                100.0,
                TimbreId.PURE,
                seed = 2L,
                sameProbability = 1.0,
            )
        assertNull(same.alteredIndex)
    }

    @Test
    fun `amusia screen phrases are never altered on the opening tonic`() {
        repeat(50) { i ->
            val item =
                M0ItemGenerators.generateAmusiaScreen(
                    SkillIds.M0_AMUSIA_SCREEN,
                    isAltered = true,
                    alterationSemitones = 2,
                    TimbreId.PURE,
                    seed = i.toLong(),
                )
            assertTrue(item.alteredIndex != 0)
        }
    }

    @Test
    fun `amusia screen intact phrases have no alteration`() {
        val item =
            M0ItemGenerators.generateAmusiaScreen(
                SkillIds.M0_AMUSIA_SCREEN,
                isAltered = false,
                alterationSemitones = 2,
                TimbreId.PURE,
                seed = 3L,
            )
        assertNull(item.alteredIndex)
        assertEquals(false, item.isAltered)
    }
}
