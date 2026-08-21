package com.tonic.feature.settings.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tonic.core.model.state.LabelStyle
import com.tonic.core.model.state.ThemeMode
import com.tonic.core.model.time.Clock
import com.tonic.core.ui.theme.TonicTheme
import com.tonic.feature.settings.debug.DebugSkillJumper
import com.tonic.feature.settings.debug.FakeAttemptRepository
import com.tonic.feature.settings.debug.FakeDebugProgressRepository
import com.tonic.feature.settings.debug.FakeSkillStateRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import kotlin.test.assertEquals

/**
 * The Settings controls, driven through the rendered screen.
 *
 * `SettingsViewModelTest` has always covered the setters; nothing covered the *screen* that calls them,
 * so a control wired to the wrong callback — or not wired at all — would have passed every test. That
 * is not hypothetical: the debug section's in-progress flag was silently dropped between
 * `SettingsScreen` and `SettingsContent`, and a Kotlin default parameter swallowed it without a
 * compiler warning. This file exists so a press has to actually reach the ViewModel.
 */
@RunWith(AndroidJUnit4::class)
class SettingsScreenControlsTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var settings: FakeSettingsRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        settings = FakeSettingsRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun render() {
        val attempts = FakeAttemptRepository()
        val skillStates = FakeSkillStateRepository(attempts)
        val viewModel =
            SettingsViewModel(
                settings,
                FakeSessionRepository(),
                FakeDataExportRepository(),
                DebugSkillJumper(
                    FakeDebugProgressRepository(attempts, skillStates),
                    skillStates,
                    FakeSettingsRepository(),
                    Clock { Instant.EPOCH },
                ),
                Clock { Instant.EPOCH },
            )
        compose.setContent { TonicTheme { SettingsScreen(viewModel = viewModel) } }
        compose.waitForIdle()
    }

    private fun tap(tag: String) {
        compose.onNodeWithTag("settings_list").performScrollToNode(hasTestTag(tag))
        compose.onNodeWithTag(tag).performClick()
        compose.waitForIdle()
    }

    @Test
    fun `choosing solfege reaches the repository`() {
        render()
        tap("settings_label_style_solfege")
        assertEquals(LabelStyle.SOLFEGE, settings.settings.value.labelStyle)
    }

    @Test
    fun `the tuning stepper moves in both directions`() {
        render()
        val start = settings.settings.value.referenceA4Hz
        tap("settings_reference_a4_increment")
        assertEquals(start + 1f, settings.settings.value.referenceA4Hz)
        tap("settings_reference_a4_decrement")
        assertEquals(start, settings.settings.value.referenceA4Hz)
    }

    @Test
    fun `the session length stepper moves in both directions`() {
        render()
        val start = settings.settings.value.sessionLengthMinutes
        tap("settings_session_length_increment")
        assertEquals(start + 1, settings.settings.value.sessionLengthMinutes)
        tap("settings_session_length_decrement")
        assertEquals(start, settings.settings.value.sessionLengthMinutes)
    }

    @Test
    fun `every toggle flips its own field and nothing else`() {
        render()

        tap("settings_haptics")
        assertEquals(false, settings.settings.value.hapticsEnabled)

        tap("settings_sound_effects")
        assertEquals(false, settings.settings.value.soundEffectsEnabled)

        tap("settings_reduce_motion")
        assertEquals(true, settings.settings.value.reduceMotion)
    }

    @Test
    fun `theme selection reaches the repository`() {
        render()
        tap("settings_theme_dark")
        assertEquals(ThemeMode.DARK, settings.settings.value.themeMode)
    }

    @Test
    fun `the daily reminder toggle stores a time, and clears it when switched off`() {
        // docs/05-DATA-MODEL.md §3's invariant, through the control that actually sets it: enabling
        // must never leave a null time, and disabling must never leave a stale one.
        render()

        tap("settings_daily_reminder_toggle")
        assertEquals(true, settings.settings.value.dailyReminderEnabled)
        assertEquals("08:00", settings.settings.value.dailyReminderTime)

        tap("settings_daily_reminder_toggle")
        assertEquals(false, settings.settings.value.dailyReminderEnabled)
        assertEquals(null, settings.settings.value.dailyReminderTime)
    }

    @Test
    fun `discarding a saved session reports back on screen`() {
        render()
        tap("settings_discard_session")
        compose.onNodeWithTag("settings_discard_result").assertIsDisplayed()
    }
}
