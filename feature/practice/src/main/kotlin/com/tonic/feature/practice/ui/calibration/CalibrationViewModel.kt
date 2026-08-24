package com.tonic.feature.practice.ui.calibration

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tonic.core.audio.player.AudioPlayer
import com.tonic.core.audio.rhythm.RhythmRenderer
import com.tonic.core.audio.route.OutputRouteMonitor
import com.tonic.core.data.settings.SettingsRepository
import com.tonic.core.model.rhythm.AudioOutputRoute
import com.tonic.core.model.rhythm.BlockReason
import com.tonic.core.model.rhythm.CalibrationFailure
import com.tonic.core.model.rhythm.CalibrationOutcome
import com.tonic.core.model.rhythm.Calibrator
import com.tonic.core.model.rhythm.Meter
import com.tonic.core.model.rhythm.MetronomeFadeLevel
import com.tonic.core.model.rhythm.MetronomePlanner
import com.tonic.core.model.rhythm.RhythmCalibration
import com.tonic.core.model.rhythm.RhythmPattern
import com.tonic.core.model.rhythm.RouteTiming
import com.tonic.core.model.rhythm.TapEvent
import com.tonic.core.model.rhythm.Tempo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToLong

/** Where a calibration run is — docs/40-PHASE-4-SPEC.md §4.3 and §7.1. */
enum class CalibrationStage {
    /**
     * The explanation, before anything sounds. §7.1 requires it by name and gives the wording to aim
     * at: "your phone has a small delay; this measures it". Not skippable and not shown after the
     * fact — a learner asked to tap along before being told why is being asked to do something
     * arbitrary.
     */
    EXPLANATION,

    /** The metronome is playing and taps are being recorded. */
    RUNNING,

    /** A usable constant was measured and stored. */
    DONE,

    /** Nothing storable came out. The learner is told what went wrong and offered another run. */
    FAILED,
}

/** What the calibration screen renders. */
data class CalibrationUiState(
    val stage: CalibrationStage = CalibrationStage.EXPLANATION,
    val route: AudioOutputRoute = AudioOutputRoute.UNKNOWN,
    /**
     * Non-null when this route cannot be calibrated at all — §4.2's Bluetooth case above all.
     *
     * Checked before the run rather than after it, because sending someone to tap along for ten
     * seconds on a route where the answer cannot be stable wastes their time and then blames them for
     * the result.
     */
    val blocked: BlockReason? = null,
    val tapCount: Int = 0,
    val failure: CalibrationFailure? = null,
    /** The stored constant, once one exists. Shown as reassurance, never as a number to improve. */
    val measured: RhythmCalibration? = null,
)

/**
 * The calibration run — docs/40-PHASE-4-SPEC.md §4.3's six steps, driven.
 *
 * The measurement itself is [Calibrator]'s and is pure; what lives here is the part that cannot be:
 * playing a metronome, collecting touches, and deciding which slot the result belongs to.
 *
 * ### The beat times this passes in, and why they are the scheduled ones
 *
 * §4.3 asks for the instants each beat was *heard*, from the output timebase, and this passes the
 * instants they were *scheduled* for. That is a deliberate deviation and it is about consistency
 * rather than accuracy.
 *
 * The constant exists to cancel the systematic difference between when a learner means to tap and
 * what the app records. That difference is output latency plus input latency plus the learner's own
 * bias. `PracticeViewModel` scores taps against the scheduled start of the pattern, so the error it
 * makes includes output latency — and a constant measured against *heard* beats would not contain
 * that term and would under-correct by exactly it. Measured this way the constant absorbs all three
 * and cancels cleanly.
 *
 * The version §4.3 describes is better and needs something this project does not have: §10 q2 asks
 * outright whether Android's reported output timing can be trusted, and nobody has answered it. When
 * it is answered, both sides move together — calibration and scoring have to agree, and which instant
 * they agree on matters less than that they do.
 *
 * ⚠️ The consequence, stated so it is not rediscovered: the stored constant is device-and-route
 * specific in a stronger sense than §4.3 implies, and it is not comparable across builds if the
 * scoring origin ever changes.
 */
@HiltViewModel
class CalibrationViewModel
    @Inject
    constructor(
        private val audioPlayer: AudioPlayer,
        private val settingsRepository: SettingsRepository,
        private val routeMonitor: OutputRouteMonitor,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(CalibrationUiState())
        val uiState: StateFlow<CalibrationUiState> = _uiState.asStateFlow()

        /** Raw touch instants for the run in progress, on the clock the pointer events carry. */
        private val taps = mutableListOf<Long>()

        init {
            viewModelScope.launch {
                routeMonitor.route.collect { route ->
                    _uiState.update { it.copy(route = route, blocked = blockFor(route)) }
                }
            }
        }

        /**
         * Starts the run: the metronome plays, and every touch until it ends is recorded.
         *
         * Refuses on a blocked route. §4.2 calls for "an actual mode change" rather than a dismissible
         * warning, and a start button that worked anyway on Bluetooth would be the dismissible version
         * with extra steps.
         */
        fun onStart() {
            if (_uiState.value.blocked != null) return
            if (_uiState.value.stage == CalibrationStage.RUNNING) return

            taps.clear()
            _uiState.update {
                it.copy(stage = CalibrationStage.RUNNING, tapCount = 0, failure = null, measured = null)
            }

            viewModelScope.launch {
                val startedAtUptimeMs = android.os.SystemClock.uptimeMillis()
                val handle = audioPlayer.play(metronome())
                handle.awaitCompletion()
                finish(startedAtUptimeMs)
            }
        }

        /** One touch. Recorded raw; the arithmetic happens once, at the end of the run. */
        fun onTap(uptimeMillis: Long) {
            if (_uiState.value.stage != CalibrationStage.RUNNING) return
            taps += uptimeMillis
            _uiState.update { it.copy(tapCount = taps.size) }
        }

        private suspend fun finish(startedAtUptimeMs: Long) {
            val msPerBeat = Tempo.msPerBeat(TEMPO_BPM)
            val beatNanos =
                (0 until BEATS).map { beat ->
                    (startedAtUptimeMs + (beat * msPerBeat).roundToLong()) * NANOS_PER_MS
                }

            when (val outcome = Calibrator.measure(taps.map { TapEvent(it * NANOS_PER_MS) }, beatNanos)) {
                is CalibrationOutcome.Measured -> {
                    val slot = (_uiState.value.route.timing as? RouteTiming.Calibratable)?.slot
                    if (slot == null) {
                        // The route changed to an uncalibratable one mid-run. Storing the result
                        // against the slot it *was* on would attach a constant measured through
                        // headphones to the speaker, which is precisely what §4.3's per-route rule
                        // exists to prevent. Failed with no CalibrationFailure, because none of them
                        // is true: the measurement was fine and there is nowhere honest to put it.
                        _uiState.update { it.copy(stage = CalibrationStage.FAILED, failure = null) }
                        return
                    }
                    settingsRepository.setRhythmCalibration(slot, outcome.calibration)
                    _uiState.update {
                        it.copy(stage = CalibrationStage.DONE, measured = outcome.calibration)
                    }
                }

                is CalibrationOutcome.Failed ->
                    // Nothing is stored, per §4.3's sanity bounds: "re-prompt rather than storing
                    // garbage." outcome.rejected is deliberately not kept either.
                    _uiState.update { it.copy(stage = CalibrationStage.FAILED, failure = outcome.reason) }
            }
        }

        /** Back to the explanation, so a second run starts the same way the first did. */
        fun onRetry() {
            _uiState.update {
                it.copy(stage = CalibrationStage.EXPLANATION, tapCount = 0, failure = null)
            }
        }

        /**
         * Deliberately not [com.tonic.core.model.rhythm.ProductionGate.evaluate].
         *
         * That function blocks an uncalibrated route, which is right for tapping and exactly wrong
         * here: not being calibrated is the reason to run this screen, not a reason to refuse it. Only
         * the route itself can disqualify a run.
         */
        private fun blockFor(route: AudioOutputRoute): BlockReason? =
            when (val timing = route.timing) {
                is RouteTiming.Unusable -> timing.reason
                is RouteTiming.Calibratable -> null
            }

        /**
         * A steady metronome for the run — §4.3 step 1, "audible and unambiguous".
         *
         * `L1` is a click on every beat with no count-in: a count-in would be beats the learner is
         * told not to tap on, and this run has no pattern to lead into. The pattern handed to the
         * renderer sounds nothing and exists only to give the span a length, which is the one awkward
         * corner of sharing the practice renderer — and sharing it is the point, because §4.3's
         * constant has to be measured against the clicks practice will actually use.
         */
        private fun metronome() =
            RhythmRenderer.renderMetronome(
                plan = MetronomePlanner.plan(MetronomeFadeLevel.L1, METER, BARS),
                tempoBpm = TEMPO_BPM,
                pattern =
                    RhythmPattern(
                        METER,
                        BARS,
                        (0 until BEATS).map { it * Meter.TICKS_PER_BEAT },
                    ),
            )

        internal companion object {
            /**
             * Comfortable, and the centre of `TEMPO_DEVIATION`. Calibration should measure a learner
             * at a tempo they can hold without effort, not at one that is itself a test.
             */
            const val TEMPO_BPM = 100

            val METER = Meter.FOUR_FOUR
            const val BARS = 4

            /** §4.3's "bounded number of beats": four bars, of which the first two are discarded. */
            val BEATS = BARS * METER.beatsPerBar

            const val NANOS_PER_MS = 1_000_000L
        }
    }
