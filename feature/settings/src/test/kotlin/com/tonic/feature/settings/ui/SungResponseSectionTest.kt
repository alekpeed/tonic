package com.tonic.feature.settings.ui

import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tonic.core.ui.theme.TonicTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The sung-response opt-in — docs/30-PHASE-3-SPEC.md §6.1.
 *
 * **This file exists because of a hole, not a hypothesis.** `sungResponseEnabled` sat in `AppSettings`
 * for three stages with nothing in the app able to change it, so Stages 3.1, 3.3 and 3.4 shipped
 * complete and unreachable — every one of them tested, none of them arrived at by a learner. What was
 * missing was never covered by a test because no test asked whether the setting could be turned on at
 * all. This one does, and the rest of it pins §6.1's ordering: a permission request that arrives
 * before the explanation is a request nobody can evaluate.
 */
@RunWith(AndroidJUnit4::class)
class SungResponseSectionTest {
    @get:Rule
    val compose = createComposeRule()

    private fun grantMicrophone() {
        Shadows
            .shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>())
            .grantPermissions(Manifest.permission.RECORD_AUDIO)
    }

    private fun render(
        enabled: Boolean = false,
        octaveAgnostic: Boolean = true,
        onEnabledChanged: (Boolean) -> Unit = {},
        onOctaveAgnosticChanged: (Boolean) -> Unit = {},
    ) {
        compose.setContent {
            TonicTheme(darkTheme = false) {
                SungResponseSection(
                    enabled = enabled,
                    octaveAgnostic = octaveAgnostic,
                    onEnabledChanged = onEnabledChanged,
                    onOctaveAgnosticChanged = onOctaveAgnosticChanged,
                )
            }
        }
    }

    /** The plain fact the missing screen made false: singing can be switched on from Settings. */
    @Test
    fun `the toggle exists and is reachable`() {
        render()
        compose.onNodeWithTag("settings_sung_toggle").assertIsDisplayed()
    }

    /**
     * §6.1's ordering, which is the whole reason this is a section rather than a switch row.
     *
     * Pressing the toggle without permission must raise the app's own explanation — what the mic is
     * for, that nothing leaves the device, that it is optional — and must *not* turn singing on yet.
     */
    @Test
    fun `turning it on without permission explains before asking`() {
        var enabledCalledWith: Boolean? = null
        render(onEnabledChanged = { enabledCalledWith = it })

        compose.onNodeWithTag("settings_sung_toggle").performClick()

        compose.onNodeWithTag("settings_sung_dialog").assertIsDisplayed()
        assertNull(enabledCalledWith, "singing was switched on before the permission was even requested")
    }

    /**
     * Declining costs nothing and is not argued with — §6.1's "no nagging, no repeat prompts."
     *
     * The dialog closes, the setting is untouched, and nothing re-raises it. A learner who says no
     * here has to be able to keep using every module exactly as before, which is what leaving the
     * setting alone means in practice.
     */
    @Test
    fun `declining the explanation leaves the setting untouched`() {
        var enabledCalledWith: Boolean? = null
        render(onEnabledChanged = { enabledCalledWith = it })

        compose.onNodeWithTag("settings_sung_toggle").performClick()
        compose.onNodeWithText("Not now").performClick()

        assertNull(enabledCalledWith)
        compose.onNodeWithTag("settings_sung_toggle").assertIsOff()
    }

    /** With permission already held there is nothing left to explain, so the toggle just works. */
    @Test
    fun `with permission already granted the toggle sets the value directly`() {
        grantMicrophone()
        var enabledCalledWith: Boolean? = null
        render(onEnabledChanged = { enabledCalledWith = it })

        compose.onNodeWithTag("settings_sung_toggle").performClick()

        assertEquals(true, enabledCalledWith)
    }

    /**
     * Singing switched on, permission revoked later — a real sequence on a real phone.
     *
     * The app must behave as if singing is off, because `MicrophoneSource.isAvailable` checks live and
     * will refuse. Saying so on this screen is not a prompt: the learner navigated here to look at
     * this setting, and a switch that reads "on" while the feature is inert is the dishonest option.
     */
    @Test
    fun `a revoked permission is stated rather than silently ignored`() {
        render(enabled = true)

        compose.onNodeWithTag("settings_sung_permission_missing").assertIsDisplayed()
        compose.onNodeWithTag("settings_sung_toggle").assertIsOff()
    }

    /** The octave setting is only meaningful once singing is actually running, so it appears with it. */
    @Test
    fun `the octave setting appears only when singing is on and permitted`() {
        grantMicrophone()
        render(enabled = true)

        compose.onNodeWithTag("settings_sung_octave_toggle").assertIsDisplayed()
    }
}
