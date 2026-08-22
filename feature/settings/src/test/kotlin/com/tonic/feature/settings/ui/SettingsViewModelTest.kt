package com.tonic.feature.settings.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tonic.core.model.ids.SkillIds
import com.tonic.core.model.state.AppSettings
import com.tonic.core.model.state.LabelStyle
import com.tonic.core.model.state.ThemeMode
import com.tonic.core.model.time.Clock
import com.tonic.feature.settings.debug.DebugSkillJumper
import com.tonic.feature.settings.debug.FakeAttemptRepository
import com.tonic.feature.settings.debug.FakeDebugProgressRepository
import com.tonic.feature.settings.debug.FakeSkillStateRepository
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
        exportRepository: FakeDataExportRepository = FakeDataExportRepository(),
    ) = SettingsViewModel(
        repository,
        sessionRepository,
        exportRepository,
        debugSkillJumper(),
        Clock { Instant.EPOCH },
    ) to repository

    /**
     * A real [DebugSkillJumper] over in-memory repositories. Its own session repository rather than
     * this file's [FakeSessionRepository], which deliberately throws on everything the settings screen
     * itself does not call - the jumper does create a session, and that is not this file's subject.
     */
    private fun debugSkillJumper(): DebugSkillJumper {
        val attemptRepository = FakeAttemptRepository()
        val skillStateRepository = FakeSkillStateRepository(attemptRepository)
        return DebugSkillJumper(
            FakeDebugProgressRepository(attemptRepository, skillStateRepository),
            skillStateRepository,
            FakeSettingsRepository(),
            Clock { Instant.EPOCH },
        )
    }

    @Test
    fun `the initial DataStore read is reflected once loading completes`() =
        runBlocking {
            val (viewModel, _) = viewModel()
            val state = viewModel.uiState.first { !it.isLoading }
            assertEquals(LabelStyle.NUMBERS, state.settings.labelStyle)
        }

    /**
     * docs/11-ONBOARDING-CLARITY.md §5's addendum: seen-once flags are clearable from Settings, so the
     * first-run explanations can be revisited as first-run experiences. Every flag, in one press -
     * missing one would silently leave that module's screen unrecoverable, which is the exact state
     * this control exists to end.
     */
    @Test
    fun `show explanations again clears every seen-once flag and says so`() =
        runBlocking {
            val allSeen =
                AppSettings(
                    module2IntroSeen = true,
                    module9IntroSeen = true,
                    module10IntroSeen = true,
                    module11IntroSeen = true,
                    module12IntroSeen = true,
                    mixedModeIntroSeen = true,
                    sungResponseIntroSeen = true,
                )
            val (viewModel, repository) = viewModel(FakeSettingsRepository(allSeen))
            viewModel.uiState.first { !it.isLoading }

            viewModel.onShowExplanationsAgain()

            viewModel.uiState.first { it.explanationsReset }
            val cleared = repository.settings.value
            assertFalse(cleared.module2IntroSeen)
            assertFalse(cleared.module9IntroSeen)
            assertFalse(cleared.module10IntroSeen)
            assertFalse(cleared.module11IntroSeen)
            assertFalse(cleared.module12IntroSeen)
            assertFalse(cleared.mixedModeIntroSeen)
            assertFalse(cleared.sungResponseIntroSeen)
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
                SettingsViewModel(
                    settingsRepo,
                    sessions,
                    FakeDataExportRepository(),
                    debugSkillJumper(),
                    Clock { Instant.EPOCH },
                ).also { vm ->
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

    @Test
    fun `requesting an export prepares a payload for the picker, and never writes anything itself`() =
        runBlocking {
            // docs/20-PHASE-2-SPEC.md §6. The ViewModel's whole job here is the handoff: build the
            // document, hand it over, and stay out of the filesystem - the write happens in the screen,
            // against a URI the user chose, so nothing is saved anywhere until they say where.
            val export = FakeDataExportRepository()
            val (viewModel, _) = viewModel(exportRepository = export)
            viewModel.uiState.first { !it.isLoading }

            viewModel.onExportDataRequested()
            val prepared = viewModel.uiState.first { it.pendingExport != null }.pendingExport!!

            assertEquals("tonic-export-1234.json", prepared.suggestedFileName)
            assertEquals(1, export.buildCount)
            assertNull(viewModel.uiState.value.exportResult, "no outcome until the picker returns")
        }

    @Test
    fun `every way the picker can end is reported back to the user`() =
        runBlocking {
            // docs/08-UI-SPEC.md §2a: a control that claims to do something must say what happened -
            // including when the answer is "nothing", which a cancelled picker is.
            val (viewModel, _) = viewModel()
            viewModel.uiState.first { !it.isLoading }

            for (outcome in ExportResult.entries) {
                viewModel.onExportDataRequested()
                viewModel.uiState.first { it.pendingExport != null }

                viewModel.onExportFinished(outcome)
                val state = viewModel.uiState.first { it.exportResult != null }
                assertEquals(outcome, state.exportResult)
                assertNull(state.pendingExport, "the payload is cleared however the picker ended")
            }
        }

    @Test
    fun `a debug jump reports where it landed, and clears its in-progress flag`() =
        runBlocking {
            val (viewModel, _) = viewModel()
            viewModel.uiState.first { !it.isLoading }

            viewModel.onDebugJumpRequested(SkillIds.M9_MODE_ID_CADENCE)
            val state = viewModel.uiState.first { it.debugJumpResult != null }

            assertEquals(SkillIds.M9_MODE_ID_CADENCE, state.debugJumpResult?.target)
            assertNull(state.debugJumpResult?.failure, "a valid target must not report a failure")
            assertFalse(state.debugJumpInProgress, "the in-progress flag must clear, or every later press is ignored")
        }

    @Test
    fun `a debug jump that fails says so instead of taking the app down`() =
        runBlocking {
            // The symptom a tester actually saw: a press that crashed the app, with nothing on screen
            // explaining it. Whatever goes wrong down there, it surfaces here as text.
            val (viewModel, _) = viewModel()
            viewModel.uiState.first { !it.isLoading }

            viewModel.onDebugJumpRequested(SkillIds.M2_INDEPENDENCE_CHECK)
            val state = viewModel.uiState.first { it.debugJumpResult != null }

            assertTrue(
                state.debugJumpResult?.failure != null,
                "an unreachable target must report a failure rather than throwing",
            )
            assertFalse(state.debugJumpInProgress)
        }
}
