package com.tonic.feature.progress.ui

import com.tonic.core.model.music.ScaleDegree
import com.tonic.core.model.state.LabelStyle
import com.tonic.core.model.state.MasteryCriterion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MasteryCopyTest {
    private fun criterion(
        kind: MasteryCriterion.Kind,
        measured: Double,
        required: Double,
    ) = MasteryCriterion(kind, met = measured >= required, measuredValue = measured, requiredValue = required)

    @Test
    fun `overall accuracy renders as whole-number percentages, the docs' own worked example`() {
        val copy =
            copyFor(
                criterion(MasteryCriterion.Kind.OVERALL_ACCURACY, measured = 0.87, required = 0.90),
                LabelStyle.NUMBERS,
            )
        assertEquals(listOf(87, 90), copy.args)
    }

    @Test
    fun `degree coverage renders as whole attempt counts, not percentages`() {
        val copy =
            copyFor(
                criterion(MasteryCriterion.Kind.DEGREE_COVERAGE, measured = 3.0, required = 5.0),
                LabelStyle.NUMBERS,
            )
        assertEquals(listOf(3, 5), copy.args)
    }

    @Test
    fun `weakest degree accuracy renders as whole-number percentages`() {
        val copy =
            copyFor(
                criterion(MasteryCriterion.Kind.WEAKEST_DEGREE_ACCURACY, measured = 0.72, required = 0.80),
                LabelStyle.NUMBERS,
            )
        assertEquals(listOf(72, 80), copy.args)
    }

    @Test
    fun `confusion cap renders as whole-number percentages`() {
        val copy =
            copyFor(
                criterion(MasteryCriterion.Kind.CONFUSION_CAP, measured = 0.22, required = 0.15),
                LabelStyle.NUMBERS,
            )
        assertEquals(listOf(22, 15), copy.args)
    }

    @Test
    fun `cadence fade minimum is a fixed sentence with no numeric arguments - a fade level isn't a rate`() {
        val copy =
            copyFor(
                criterion(MasteryCriterion.Kind.CADENCE_FADE_MINIMUM, measured = 2.0, required = 4.0),
                LabelStyle.NUMBERS,
            )
        assertTrue(copy.args.isEmpty())
    }

    @Test
    fun `the focus-degree sentence names the note, in the learner's own label style`() {
        // Added with the criterion in Phase 2 Stage 2.5. The sentence is only actionable if it says
        // *which* note - "the newest one" would make a learner count back through their own history.
        val criterion =
            MasteryCriterion(
                MasteryCriterion.Kind.FOCUS_DEGREE,
                met = false,
                measuredValue = 0.5,
                requiredValue = 0.8,
                subject = ScaleDegree(2, -1),
            )
        assertEquals(listOf("\u266d2"), copyFor(criterion, LabelStyle.NUMBERS).args)
        assertEquals(listOf("Ra"), copyFor(criterion, LabelStyle.SOLFEGE).args)
    }

    @Test
    fun `every criterion kind maps to a distinct string resource`() {
        val resources =
            MasteryCriterion.Kind.entries.map { kind ->
                copyFor(criterion(kind, measured = 0.5, required = 0.5), LabelStyle.NUMBERS).textRes
            }
        assertEquals(resources.distinct(), resources)
    }
}
