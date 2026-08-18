package com.tonic.core.model.items

import com.tonic.core.model.ids.SkillIds
import com.tonic.core.model.music.Mode
import com.tonic.core.model.music.PitchClass
import com.tonic.core.model.music.ScaleDegree
import com.tonic.core.model.music.TimbreId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ItemTest {
    private val timing = ItemTiming(referenceDurationMs = 600, gapAfterReferenceMs = 300, targetDurationMs = 800)

    @Test
    fun `pitch direction item exposes higher-lower alphabet`() {
        val item =
            Item.PitchDirectionItem(
                skill = SkillIds.M0_PITCH_DIR,
                firstMidi = 60,
                secondCentsOffset = 200.0,
                timbre = TimbreId.PURE,
                timing = timing,
                seed = 1L,
            )
        assertEquals(AnswerAlphabet.HigherLower.labels, item.answerAlphabet.labels)
    }

    @Test
    fun `functional recognition item alphabet matches its active degrees`() {
        val active = listOf(ScaleDegree(1), ScaleDegree(3), ScaleDegree(5))
        val item =
            Item.FunctionalRecognitionItem(
                skill = SkillIds.M2_DEG_SET_1,
                key = PitchClass(0),
                mode = Mode.MAJOR,
                targetDegree = ScaleDegree(3),
                targetMidi = 64,
                referencePlan =
                    ReferencePlan(
                        cadenceFadeLevel = CadenceFadeLevel.L3,
                        elements = listOf(ReferenceElement.ChordEvent(listOf(60, 64, 67), 800, TimbreId.SOFT)),
                    ),
                timbre = TimbreId.SOFT,
                referenceTimbre = TimbreId.SOFT,
                timing = timing,
                activeDegrees = active,
                seed = 7L,
            )
        assertEquals(listOf("1", "3", "5"), item.answerAlphabet.labels)
        assertIs<AnswerAlphabet.ScaleDegrees>(item.answerAlphabet)
    }

    @Test
    fun `amusia screen item alphabet is intact-altered`() {
        val item =
            Item.AmusiaScreenItem(
                skill = SkillIds.M0_AMUSIA_SCREEN,
                phraseMidi = listOf(60, 62, 64, 65),
                isAltered = true,
                alteredIndex = 2,
                alterationSemitones = 2,
                timbre = TimbreId.REED,
                timing = timing,
                seed = 3L,
            )
        assertEquals(AnswerAlphabet.IntactAltered.labels, item.answerAlphabet.labels)
    }

    @Test
    fun `remaining M0-M1 item types expose their documented alphabets`() {
        val sameDiff =
            Item.SameDifferentItem(SkillIds.M0_SAME_DIFF, 60, 0.0, isCatchTrial = true, TimbreId.PURE, timing, 1L)
        assertEquals(AnswerAlphabet.SameDifferent.labels, sameDiff.answerAlphabet.labels)

        val tonalMemory =
            Item.TonalMemoryItem(SkillIds.M0_TONAL_MEMORY, listOf(60, 62, 64), 1, 50.0, TimbreId.PURE, timing, 1L)
        assertEquals(AnswerAlphabet.SameDifferent.labels, tonalMemory.answerAlphabet.labels)

        val contour = Item.ContourItem(SkillIds.M1_CONTOUR, listOf(60, 64, 60), TimbreId.PURE, timing, 1L)
        assertEquals(AnswerAlphabet.Contour.labels, contour.answerAlphabet.labels)

        val stepLeap = Item.StepLeapItem(SkillIds.M1_STEP_LEAP, 60, 72, TimbreId.PURE, timing, 1L)
        assertEquals(AnswerAlphabet.StepLeap.labels, stepLeap.answerAlphabet.labels)
    }
}
