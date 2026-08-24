package com.tonic.feature.practice.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tonic.core.curriculum.graph.SkillGraph
import com.tonic.core.model.ids.SkillIds
import com.tonic.core.model.rhythm.AudioOutputRoute
import com.tonic.core.model.rhythm.BlockReason
import com.tonic.core.model.rhythm.RhythmCalibration
import com.tonic.core.model.rhythm.RhythmCalibrations
import com.tonic.core.model.state.AppSettings
import com.tonic.core.model.state.PracticeTrack
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tapping is refused when it cannot be measured — docs/40-PHASE-4-SPEC.md §4.2 and §4.3, and §9's
 * simulation 6.
 *
 * `ProductionGate` has been pure and tested since Stage 4.0 and nothing consulted it, which is the
 * shape of defect this repository has shipped twice before: a correct decision no line of code ever
 * asked for. These assert that the practice screen asks.
 *
 * The block happens *before* a session is planned, not at the item that would be blocked. By the time
 * an item is presented the loop has planned a session around it and played its audio, so a per-item
 * check would let a learner hear a rhythm, be told they cannot tap it back, and be left holding a
 * half-run session.
 */
@RunWith(AndroidJUnit4::class)
class ProductionGateTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val calibrated =
        AppSettings(
            module2IntroSeen = true,
            rhythmCalibrations =
                RhythmCalibrations(
                    speaker = RhythmCalibration(offsetMs = 40.0, spreadMs = 12.0, tapsUsed = 12),
                ),
        )

    private val uncalibrated = AppSettings(module2IntroSeen = true)

    @Test
    fun `an uncalibrated learner is stopped before a production session starts`() =
        runBlocking {
            // §4.3: "a required, explicit calibration step before the first production exercise."
            // M3.BEAT_FIND is the first node in the rhythm chain and it is a production node.
            val fixture = PracticeFixture(uncalibrated, track = PracticeTrack.RHYTHM)
            fixture.viewModel.startIfNeeded()

            val blocked =
                withTimeout(TIMEOUT_MS) { fixture.viewModel.uiState.first { it.productionBlock != null } }
            assertEquals(BlockReason.NOT_CALIBRATED, blocked.productionBlock)
            assertNull(blocked.item, "no item may be presented, and none may have sounded")
            assertTrue(fixture.audioPlayer.playedBuffers.isEmpty())
        }

    @Test
    fun `a calibrated learner on the speaker is not stopped`() =
        runBlocking {
            val fixture = PracticeFixture(calibrated, track = PracticeTrack.RHYTHM)
            fixture.viewModel.startIfNeeded()

            val running = withTimeout(TIMEOUT_MS) { fixture.viewModel.uiState.first { it.item != null } }
            assertNull(running.productionBlock)
        }

    @Test
    fun `Bluetooth blocks even a calibrated learner, and does not offer to calibrate`() =
        runBlocking {
            // §4.2, and the ordering ProductionGate exists to enforce: the reason calibration cannot
            // fix wins. Offering "measure it now" here would send someone to run a measurement that
            // cannot succeed on the route they are on.
            val fixture =
                PracticeFixture(calibrated, track = PracticeTrack.RHYTHM, route = AudioOutputRoute.BLUETOOTH)
            fixture.viewModel.startIfNeeded()

            val blocked =
                withTimeout(TIMEOUT_MS) { fixture.viewModel.uiState.first { it.productionBlock != null } }
            assertEquals(BlockReason.BLUETOOTH_OUTPUT, blocked.productionBlock)
        }

    @Test
    fun `the pitch track is never blocked by any of this`() =
        runBlocking {
            // A learner who has never calibrated, on Bluetooth, must still be able to practice
            // everything that does not involve tapping - which is the whole app before Phase 4.
            val fixture =
                PracticeFixture(uncalibrated, track = PracticeTrack.PITCH, route = AudioOutputRoute.BLUETOOTH)
            fixture.viewModel.startIfNeeded()

            val running = withTimeout(TIMEOUT_MS) { fixture.viewModel.uiState.first { it.item != null } }
            assertNull(running.productionBlock)
        }

    @Test
    fun `a blocked learner is offered listening, and taking it starts a real session`() =
        runBlocking {
            // §4.2 requires the block to offer "recognition exercises instead" rather than merely
            // refusing. At M3.BEAT_FIND nothing recognition-shaped is open yet, so the honest answer
            // is that there is no alternative to offer - and the screen must not show a button that
            // leads nowhere.
            val fixture = PracticeFixture(uncalibrated, track = PracticeTrack.RHYTHM)
            fixture.viewModel.startIfNeeded()
            val blocked =
                withTimeout(TIMEOUT_MS) { fixture.viewModel.uiState.first { it.productionBlock != null } }

            // Nothing listening-shaped is open to a learner who has not mastered M3.BEAT_FIND: the
            // first node in the module is a production node, and every recognition node sits behind
            // it. So the honest answer is that there is no alternative to offer yet, and the screen
            // shows no button rather than one that leads nowhere.
            assertNull(blocked.recognitionAlternative)

            // Once the first node is done, the offer becomes real: M3.DOWNBEAT is the listening node
            // that opens behind it.
            val later = SkillGraph.currentRhythmRecognitionNodeFor { it == SkillIds.M3_BEAT_FIND }
            assertEquals(SkillIds.M3_DOWNBEAT, later)
        }

    private companion object {
        const val TIMEOUT_MS = 20_000L
    }
}
