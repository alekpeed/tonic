package com.tonic.core.model.state

import kotlin.test.Test
import kotlin.test.assertEquals

/** Boundaries from docs/07-ADAPTIVE-ENGINE.md §6's block-accuracy -> grade table. */
class FsrsGradeTest {
    @Test
    fun `below 60 percent is AGAIN`() {
        assertEquals(FsrsGrade.AGAIN, FsrsGrade.fromBlockAccuracy(0.0))
        assertEquals(FsrsGrade.AGAIN, FsrsGrade.fromBlockAccuracy(0.59))
    }

    @Test
    fun `60 to 79 percent is HARD`() {
        assertEquals(FsrsGrade.HARD, FsrsGrade.fromBlockAccuracy(0.60))
        assertEquals(FsrsGrade.HARD, FsrsGrade.fromBlockAccuracy(0.79))
    }

    @Test
    fun `80 to 92 percent is GOOD`() {
        assertEquals(FsrsGrade.GOOD, FsrsGrade.fromBlockAccuracy(0.80))
        assertEquals(FsrsGrade.GOOD, FsrsGrade.fromBlockAccuracy(0.92))
    }

    @Test
    fun `above 92 percent is EASY`() {
        assertEquals(FsrsGrade.EASY, FsrsGrade.fromBlockAccuracy(0.93))
        assertEquals(FsrsGrade.EASY, FsrsGrade.fromBlockAccuracy(1.0))
    }
}
