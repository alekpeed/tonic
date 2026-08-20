package com.tonic.feature.diagnostic.ui

import com.tonic.core.model.items.AnswerAlphabet
import com.tonic.feature.diagnostic.engine.M0SubTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class AnswerCopyTest {
    @Test
    fun `every sub-test has a distinct prompt and a valid raw label pair`() {
        for (subTest in M0SubTest.entries) {
            val copy = answerCopyFor(subTest)
            assertNotEquals(copy.firstRawLabel, copy.secondRawLabel, "the two choices must be distinct for $subTest")
        }
    }

    @Test
    fun `pitch direction uses the HigherLower alphabet`() {
        val copy = answerCopyFor(M0SubTest.PITCH_DIRECTION)
        assertEquals(AnswerAlphabet.HigherLower.HIGHER, copy.firstRawLabel)
        assertEquals(AnswerAlphabet.HigherLower.LOWER, copy.secondRawLabel)
    }

    @Test
    fun `same-different and tonal memory both use the SameDifferent alphabet with matching raw labels`() {
        val sameDifferent = answerCopyFor(M0SubTest.SAME_DIFFERENT)
        val tonalMemory = answerCopyFor(M0SubTest.TONAL_MEMORY)
        assertEquals(AnswerAlphabet.SameDifferent.SAME, sameDifferent.firstRawLabel)
        assertEquals(AnswerAlphabet.SameDifferent.DIFFERENT, sameDifferent.secondRawLabel)
        assertEquals(sameDifferent.firstRawLabel, tonalMemory.firstRawLabel)
        assertEquals(sameDifferent.secondRawLabel, tonalMemory.secondRawLabel)
        // Different prompt copy despite sharing an answer alphabet - distinct sub-tests.
        assertNotEquals(sameDifferent.promptRes, tonalMemory.promptRes)
    }

    @Test
    fun `amusia screen uses the IntactAltered alphabet`() {
        val copy = answerCopyFor(M0SubTest.AMUSIA_SCREEN)
        assertEquals(AnswerAlphabet.IntactAltered.INTACT, copy.firstRawLabel)
        assertEquals(AnswerAlphabet.IntactAltered.ALTERED, copy.secondRawLabel)
    }
}
