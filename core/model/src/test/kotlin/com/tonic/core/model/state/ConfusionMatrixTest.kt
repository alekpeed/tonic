package com.tonic.core.model.state

import com.tonic.core.model.ids.SkillIds
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ConfusionMatrixTest {
    private val now = Instant.parse("2026-01-01T00:00:00Z")

    private fun cell(
        target: String,
        response: String,
        windowCount: Int,
    ) = ConfusionCell(target, response, count = windowCount, windowCount = windowCount, updatedAt = now)

    @Test
    fun `accuracy is correct-over-total for a target`() {
        val matrix =
            ConfusionMatrix(
                SkillIds.M2_FULL_DIATONIC,
                listOf(cell("4", "4", 6), cell("4", "3", 4)),
            )
        assertEquals(0.6, matrix.accuracyFor("4"))
    }

    @Test
    fun `accuracy is null for a target with no attempts`() {
        val matrix = ConfusionMatrix(SkillIds.M2_FULL_DIATONIC, emptyList())
        assertNull(matrix.accuracyFor("7"))
    }

    @Test
    fun `confusion pairs above threshold excludes the diagonal and below-threshold cells`() {
        val matrix =
            ConfusionMatrix(
                SkillIds.M2_FULL_DIATONIC,
                listOf(
                    cell("7", "7", 8),
                    cell("7", "1", 2), // 20% of 10 -> above 15%
                    cell("4", "4", 9),
                    cell("4", "3", 1), // 10% of 10 -> below 15%
                ),
            )
        val flagged = matrix.confusionPairsAbove(0.15)
        assertEquals(listOf("7" to "1"), flagged.map { it.target to it.response })
    }
}
