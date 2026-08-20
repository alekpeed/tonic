package com.tonic.feature.diagnostic.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tonic.feature.diagnostic.R
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertTrue

/**
 * docs/11-ONBOARDING-CLARITY.md §9.1. The stage the user is in has to be nameable from the screen, and
 * the calibration -> practice handoff has to be *stated*, not inferred from the app simply moving on.
 * Asserted on the strings rather than through a composition because that is where the requirement
 * actually lives - this is a copy contract, and it stays verifiable without an emulator.
 */
@RunWith(AndroidJUnit4::class)
class StageCopyTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `the diagnostic names its stage, and does not call itself a test`() {
        val stage = context.getString(R.string.diagnostic_stage_name)
        assertTrue(stage.isNotBlank(), "every screen must state which stage it belongs to")
        // docs/08-UI-SPEC.md §5: "Frame as calibration, not testing."
        assertTrue(
            !stage.contains("test", ignoreCase = true),
            "the stage name must not frame this as a test, it is calibration - was '$stage'",
        )
    }

    @Test
    fun `both result outcomes announce that calibration finished and practice is next`() {
        val bodies =
            listOf(
                context.getString(R.string.diagnostic_result_proceed_title) + " " +
                    context.getString(R.string.diagnostic_result_proceed_body),
                context.getString(R.string.diagnostic_result_fundamentals_title) + " " +
                    context.getString(R.string.diagnostic_result_fundamentals_body),
            )
        for (copy in bodies) {
            assertTrue(
                copy.contains("Calibration finished", ignoreCase = true),
                "the handoff must say calibration is over, not leave it to be inferred - was '$copy'",
            )
            assertTrue(
                copy.contains("Practice starts next", ignoreCase = true),
                "the handoff must say what comes next, before the first practice item plays - was '$copy'",
            )
        }
    }

    /**
     * docs/02-PEDAGOGY.md §8 and CLAUDE.md §9: the remediation path is routed to silently and described
     * neutrally. Rewriting this copy for §9.1 must not have introduced a deficit framing.
     */
    @Test
    fun `the remediation outcome stays neutral and never implies the user cannot learn`() {
        val copy =
            context.getString(R.string.diagnostic_result_fundamentals_title) + " " +
                context.getString(R.string.diagnostic_result_fundamentals_body)
        val forbidden = listOf("tone deaf", "tone-deaf", "cannot", "can't learn", "unable", "problem", "difficulty")
        for (word in forbidden) {
            assertTrue(
                !copy.contains(word, ignoreCase = true),
                "remediation copy must carry no implication of deficit - found '$word' in '$copy'",
            )
        }
    }
}
