package com.tonic.feature.practice.ui.calibration

import android.os.SystemClock
import androidx.lifecycle.viewModelScope
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tonic.core.audio.route.OutputRouteMonitor
import com.tonic.core.model.rhythm.AudioOutputRoute
import com.tonic.core.model.rhythm.BlockReason
import com.tonic.core.model.rhythm.CalibrationFailure
import com.tonic.core.model.rhythm.CalibrationSlot
import com.tonic.core.model.rhythm.Tempo
import com.tonic.feature.practice.engine.FakeAudioPlayer
import com.tonic.feature.practice.ui.FakeSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.roundToLong
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The calibration run — docs/40-PHASE-4-SPEC.md §4.3 and §4.2.
 *
 * [com.tonic.core.model.rhythm.Calibrator] already has the arithmetic under test; what these cover is
 * everything around it that the pure function cannot see — that a blocked route refuses to run at all
 * rather than running and failing, that a measured constant reaches the slot for the route it was
 * measured on, and that a failed run stores nothing.
 */
@RunWith(AndroidJUnit4::class)
class CalibrationViewModelTest {
    private class FakeRouteMonitor(initial: AudioOutputRoute) : OutputRouteMonitor {
        private val flow = MutableStateFlow(initial)
        override val route: StateFlow<AudioOutputRoute> = flow.asStateFlow()
    }

    private lateinit var player: FakeAudioPlayer
    private lateinit var settings: FakeSettingsRepository

    /**
     * Every view model built during a test, so their scopes are cancelled before Main is put back.
     *
     * The same ordering `MainDispatcherRule` exists for, and for the same reason: a view model whose
     * scope is never cancelled keeps running coroutines on Main after its test returns, and
     * `resetMain()` then swaps the dispatcher out from under them. That has failed CI in this
     * repository before and was called a flake the first time.
     */
    private val built = mutableListOf<CalibrationViewModel>()

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Default)
        player = FakeAudioPlayer()
        settings = FakeSettingsRepository()
        // Long enough that the run is genuinely in progress while taps are entered, short enough not
        // to slow the suite. Tap instants are data rather than wall-clock events, so they need not be
        // spread over the real duration.
        player.playbackDurationMs = PLAYBACK_MS
    }

    @After
    fun tearDown() {
        runBlocking {
            for (viewModel in built) {
                viewModel.viewModelScope.cancel()
                // Bounded, per docs/21-HANDOFF.md §8 on unbounded waits in this repository's tests: a
                // scope that will not finish a second after cancellation is a bug to fail on rather
                // than hang on.
                withTimeout(CANCEL_TIMEOUT_MS) { viewModel.viewModelScope.coroutineContext.job.join() }
            }
        }
        built.clear()
        Dispatchers.resetMain()
    }

    private fun viewModel(route: AudioOutputRoute = AudioOutputRoute.SPEAKER) =
        CalibrationViewModel(player, settings, FakeRouteMonitor(route)).also { built += it }

    /** Taps [offsetMs] after every beat of a run that started at [startedAt]. */
    private fun tapAlong(
        viewModel: CalibrationViewModel,
        startedAt: Long,
        offsetMs: Long,
        beats: Int = CalibrationViewModel.BEATS,
    ) {
        val msPerBeat = Tempo.msPerBeat(CalibrationViewModel.TEMPO_BPM)
        for (beat in 0 until beats) {
            viewModel.onTap(startedAt + (beat * msPerBeat).roundToLong() + offsetMs)
        }
    }

    @Test
    fun `it opens on the explanation, before anything sounds`() {
        // §7.1 lists calibration among the three things that must be explained before they happen.
        val viewModel = viewModel()
        assertEquals(CalibrationStage.EXPLANATION, viewModel.uiState.value.stage)
        assertTrue(player.playedBuffers.isEmpty(), "nothing may play before the learner has read why")
    }

    @Test
    fun `a steady run stores a constant against the route it was measured on`() =
        runBlocking {
            val viewModel = viewModel(AudioOutputRoute.SPEAKER)
            withTimeout(TIMEOUT_MS) { viewModel.uiState.first { it.route == AudioOutputRoute.SPEAKER } }

            val startedAt = SystemClock.uptimeMillis()
            viewModel.onStart()
            withTimeout(TIMEOUT_MS) { viewModel.uiState.first { it.stage == CalibrationStage.RUNNING } }
            tapAlong(viewModel, startedAt, offsetMs = LATE_MS)

            val done =
                withTimeout(TIMEOUT_MS) {
                    viewModel.uiState.first { it.stage != CalibrationStage.RUNNING }
                }
            assertEquals(CalibrationStage.DONE, done.stage, "a steady tapper must produce a usable constant")

            val stored = settings.settings.first().rhythmCalibrations
            val speaker = assertNotNull(stored.forSlot(CalibrationSlot.SPEAKER))
            assertTrue(speaker.offsetMs > 0.0, "a consistently late tapper has a positive offset")
            assertNull(stored.forSlot(CalibrationSlot.WIRED), "§4.3 stores per route, and one route ran")
        }

    @Test
    fun `a run with almost no taps stores nothing and says why`() =
        runBlocking {
            val viewModel = viewModel()
            val startedAt = SystemClock.uptimeMillis()
            viewModel.onStart()
            withTimeout(TIMEOUT_MS) { viewModel.uiState.first { it.stage == CalibrationStage.RUNNING } }
            tapAlong(viewModel, startedAt, offsetMs = LATE_MS, beats = 3)

            val failed =
                withTimeout(TIMEOUT_MS) {
                    viewModel.uiState.first { it.stage != CalibrationStage.RUNNING }
                }
            assertEquals(CalibrationStage.FAILED, failed.stage)
            assertEquals(CalibrationFailure.NOT_ENOUGH_TAPS, failed.failure)
            // §4.3's sanity bounds: "re-prompt rather than storing garbage."
            assertNull(settings.settings.first().rhythmCalibrations.forSlot(CalibrationSlot.SPEAKER))
        }

    @Test
    fun `Bluetooth blocks the run rather than failing it`() =
        runBlocking {
            // §4.2 wants "an actual mode change", not a warning. Starting anyway and reporting a bad
            // measurement would be the dismissible version with extra steps - and would blame the
            // learner for a result their route made impossible.
            val viewModel = viewModel(AudioOutputRoute.BLUETOOTH)
            val blocked =
                withTimeout(TIMEOUT_MS) { viewModel.uiState.first { it.blocked != null } }
            assertEquals(BlockReason.BLUETOOTH_OUTPUT, blocked.blocked)

            viewModel.onStart()
            assertEquals(CalibrationStage.EXPLANATION, viewModel.uiState.value.stage)
            assertTrue(player.playedBuffers.isEmpty(), "a blocked route must not start a metronome")
        }

    @Test
    fun `retrying returns to the explanation rather than straight to tapping`() =
        runBlocking {
            val viewModel = viewModel()
            val startedAt = SystemClock.uptimeMillis()
            viewModel.onStart()
            withTimeout(TIMEOUT_MS) { viewModel.uiState.first { it.stage == CalibrationStage.RUNNING } }
            tapAlong(viewModel, startedAt, offsetMs = LATE_MS, beats = 2)
            withTimeout(TIMEOUT_MS) { viewModel.uiState.first { it.stage == CalibrationStage.FAILED } }

            viewModel.onRetry()
            val retried = viewModel.uiState.value
            assertEquals(CalibrationStage.EXPLANATION, retried.stage)
            assertNull(retried.failure, "the previous failure must not still be on screen")
            assertEquals(0, retried.tapCount)
        }

    private companion object {
        const val TIMEOUT_MS = 20_000L
        const val CANCEL_TIMEOUT_MS = 1_000L
        const val PLAYBACK_MS = 1_000L

        /** A tapper consistently behind the beat, well inside what §4.3 considers plausible. */
        const val LATE_MS = 45L
    }
}
