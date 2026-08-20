package com.tonic.feature.settings.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tonic.core.model.state.LabelStyle
import com.tonic.core.model.state.ThemeMode
import com.tonic.core.model.time.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * docs/09-BUILD-PLAN.md Stage 9 acceptance: "settings changes take effect immediately (label style
 * especially)." Every setter is asserted to be visible on [SettingsViewModel.uiState] without any
 * separate save/apply step - the same live-Flow-collection pattern `:feature:practice`'s
 * `PracticeViewModel` already proved out for [com.tonic.core.data.settings.SettingsRepository].
 */
@RunWith(AndroidJUnit4::class)
class SettingsViewModelTest {
    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Default)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(
        repository: FakeSettingsRepository = FakeSettingsRepository(),
        sessionRepository: FakeSessionRepository = FakeSessionRepository(),
    ) = SettingsViewModel(repository, sessionRepository, Clock { Instant.EPOCH }) to repository

    @Test
    fun `the initial DataStore read is reflected once loading completes`() =
        runBlocking {
            val (viewModel, _) = viewModel()
            val state = viewModel.uiState.first { !it.isLoading }
            assertEquals(LabelStyle.NUMBERS, state.settings.labelStyle)
        }

    @Test
    fun `changing the label style takes effect immediately, with no separate apply step`() =
        runBlocking {
            val (viewModel, _) = viewModel()
            viewModel.uiState.first { !it.isLoading }

            viewModel.onLabelStyleChanged(LabelStyle.SOLFEGE)

            val updated = viewModel.uiState.first { it.settings.labelStyle == LabelStyle.SOLFEGE }
            assertEquals(LabelStyle.SOLFEGE, updated.settings.labelStyle)
        }

    @Test
    fun `every other setting field is also live-wired through to the repository`() =
        runBlocking {
            val (viewModel, _) = viewModel()
            viewModel.uiState.first { !it.isLoading }

            viewModel.onReferenceA4HzChanged(432f)
            assertEquals(
                432f,
                viewModel.uiState
                    .first { it.settings.referenceA4Hz == 432f }
                    .settings.referenceA4Hz,
            )

            viewModel.onSessionLengthMinutesChanged(10)
            assertEquals(
                10,
                viewModel.uiState
                    .first { it.settings.sessionLengthMinutes == 10 }
                    .settings.sessionLengthMinutes,
            )

            viewModel.onHapticsEnabledChanged(false)
            assertFalse(
                viewModel.uiState
                    .first { !it.settings.hapticsEnabled }
                    .settings.hapticsEnabled,
            )

            viewModel.onSoundEffectsEnabledChanged(false)
            assertFalse(
                viewModel.uiState
                    .first { !it.settings.soundEffectsEnabled }
                    .settings.soundEffectsEnabled,
            )

            viewModel.onThemeModeChanged(ThemeMode.DARK)
            assertEquals(
                ThemeMode.DARK,
                viewModel.uiState
                    .first { it.settings.themeMode == ThemeMode.DARK }
                    .settings.themeMode,
            )

            viewModel.onReduceMotionChanged(true)
            assertTrue(
                viewModel.uiState
                    .first { it.settings.reduceMotion }
                    .settings.reduceMotion,
            )
        }

    @Test
    fun `enabling the daily reminder without a caller-supplied time still stores a non-null time`() =
        runBlocking {
            val (viewModel, _) = viewModel()
            viewModel.uiState.first { !it.isLoading }

            viewModel.onDailyReminderChanged(true, "08:00")
            val enabled = viewModel.uiState.first { it.settings.dailyReminderEnabled }
            assertEquals("08:00", enabled.settings.dailyReminderTime)

            viewModel.onDailyReminderChanged(false, "08:00")
            val disabled = viewModel.uiState.first { !it.settings.dailyReminderEnabled }
            assertNull(
                disabled.settings.dailyReminderTime,
                "disabling must always clear the time - docs/05-DATA-MODEL.md §3",
            )
        }

    /**
     * The escape hatch clears ONLY the saved session. Settings (which carry diagnostic placement's
     * `diagnostic_completed` flag) pass through this ViewModel and must be bit-identical afterwards;
     * skill mastery lives in Room behind repositories this ViewModel cannot even reach - its only
     * injected data ports are SettingsRepository and SessionRepository.discardResumable.
     */
    @Test
    fun `discarding the saved session touches nothing else and confirms visibly`() =
        runBlocking {
            val sessions =
                FakeSessionRepository(
                    resumable =
                        com.tonic.core.model.state.Session(
                            id = 7L,
                            rootSeed = 1L,
                            plannedItemCount = 27,
                            completedItemCount = 4,
                            startedAt = Instant.EPOCH,
                            endedAt = null,
                            resumeState = null,
                        ),
                )
            val settingsRepo = FakeSettingsRepository()
            val viewModel =
                SettingsViewModel(settingsRepo, sessions, Clock { Instant.EPOCH }).also { vm ->
                    vm.uiState.first { !it.isLoading }
                }
            val settingsBefore = settingsRepo.settings.value

            viewModel.onDiscardSavedSession()
            val state = viewModel.uiState.first { it.discardResult != null }

            assertEquals(DiscardResult.DISCARDED, state.discardResult)
            assertEquals(null, sessions.findResumable(), "the saved session is gone")
            assertEquals(
                settingsBefore,
                settingsRepo.settings.value,
                "settings - diagnostic placement's flag included - are bit-identical",
            )
        }

    @Test
    fun `with nothing saved, the control says so instead of silently doing nothing`() =
        runBlocking {
            val (viewModel, _) = viewModel(sessionRepository = FakeSessionRepository(resumable = null))
            viewModel.onDiscardSavedSession()
            val state = viewModel.uiState.first { it.discardResult != null }
            assertEquals(DiscardResult.NOTHING_SAVED, state.discardResult)
        }
}
