package com.tonic.feature.diagnostic.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tonic.core.ui.theme.TonicTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

/**
 * The escape from the intake flow, on the screen that actually blocks it.
 *
 * `:feature:settings`' jump tool clears onboarding and the diagnostic, but a tester could never reach
 * it: Settings opens from Home, and on a fresh install Home navigates straight to onboarding and then
 * to the diagnostic, popping itself off the back stack each time. The skip sat behind the screen the
 * diagnostic was blocking — so reinstalling to escape a bad state still meant sitting through the
 * whole diagnostic. A control is only an escape hatch if it is reachable from inside the trap.
 */
@RunWith(AndroidJUnit4::class)
class DiagnosticDebugSkipTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `the skip is offered on the intro, before any of the diagnostic has to be answered`() {
        var skipped = 0
        compose.setContent {
            TonicTheme { IntroState(onBegin = {}, onDebugSkip = { skipped++ }) }
        }

        compose.onNodeWithTag("diagnostic_debug_skip").assertIsDisplayed().performClick()

        assertEquals(1, skipped)
    }

    @Test
    fun `a release build has no skip control at all - not merely a hidden one`() {
        // :app passes null in release. Null must mean absent, so the control cannot be reached by
        // accessibility services, tooling, or a stray click.
        compose.setContent { TonicTheme { IntroState(onBegin = {}, onDebugSkip = null) } }

        compose.onNodeWithTag("diagnostic_debug_skip").assertDoesNotExist()
    }
}
