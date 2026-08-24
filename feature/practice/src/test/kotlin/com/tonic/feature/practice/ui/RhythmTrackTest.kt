package com.tonic.feature.practice.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tonic.core.curriculum.graph.SkillGraph
import com.tonic.core.model.ids.SkillIds
import com.tonic.core.model.items.Item
import com.tonic.core.model.rhythm.RhythmCalibration
import com.tonic.core.model.rhythm.RhythmCalibrations
import com.tonic.core.model.rhythm.RhythmQuestion
import com.tonic.core.model.state.AppSettings
import com.tonic.core.model.state.PracticeTrack
import com.tonic.core.ui.components.PlaybackPhase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Rhythm, reachable — docs/40-PHASE-4-SPEC.md §2 and §7.2.
 *
 * Everything `M3` had before this stage was headless: the generator, the renderer, the scorer and the
 * mastery criteria all worked and no learner could reach any of it. So what these assert is the wiring
 * rather than the pedagogy — that asking for the rhythm track produces a rhythm item, that a
 * production item opens a tap window, and that taps entered in that window arrive at the attempt log
 * as a scored performance.
 */
@RunWith(AndroidJUnit4::class)
class RhythmTrackTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    /**
     * Calibrated, because since the production gate landed an uncalibrated learner never reaches a
     * rhythm item at all — which is the point of that gate and would make every test below wait
     * forever for a session that is deliberately refused.
     */
    private fun rhythmFixture() =
        PracticeFixture(
            AppSettings(
                module2IntroSeen = true,
                rhythmCalibrations =
                    RhythmCalibrations(
                        speaker = RhythmCalibration(offsetMs = 30.0, spreadMs = 10.0, tapsUsed = 12),
                    ),
            ),
            track = PracticeTrack.RHYTHM,
        )

    @Test
    fun `the rhythm track starts on a rhythm node, not a pitch one`() =
        runBlocking {
            val fixture = rhythmFixture()
            fixture.viewModel.startIfNeeded()

            val item =
                withTimeout(TIMEOUT_MS) {
                    fixture.viewModel.uiState
                        .first { it.item != null }
                        .item
                }
            assertTrue(item is Item.RhythmItem, "asked for rhythm and got ${item?.let { it::class.simpleName }}")
        }

    @Test
    fun `the pitch track is untouched by the argument existing`() =
        runBlocking {
            // The default, and what every session did before the track argument was added. A regression
            // here would mean the whole pitch curriculum had been rerouted by a nav parameter.
            val fixture = PracticeFixture(AppSettings(module2IntroSeen = true))
            fixture.viewModel.startIfNeeded()

            val item =
                withTimeout(TIMEOUT_MS) {
                    fixture.viewModel.uiState
                        .first { it.item != null }
                        .item
                }
            assertTrue(item is Item.FunctionalRecognitionItem)
        }

    @Test
    fun `a production item opens a tap window and scores what was tapped into it`() =
        runBlocking {
            val fixture = rhythmFixture()
            // The tap window is open for exactly as long as the backing plays, so the fake has to take
            // some time over it or there is no interval in which a tap can be entered.
            fixture.audioPlayer.playbackDurationMs = BACKING_MS
            fixture.viewModel.startIfNeeded()

            val listening =
                withTimeout(TIMEOUT_MS) {
                    fixture.viewModel.uiState.first { it.item is Item.RhythmItem }
                }
            val item = listening.item as Item.RhythmItem
            assertTrue(item.question is RhythmQuestion.TapItBack, "M3.BEAT_FIND is a production node")

            // Nothing may be answerable while the rhythm is still sounding - a tap during the
            // demonstration is not an answer, it is a learner playing along with the example.
            assertEquals(PlaybackPhase.LISTENING, listening.phase)
            fixture.viewModel.onRhythmTap(1_000L)
            assertEquals(0, fixture.viewModel.uiState.value.tapCount, "a tap before the window must not count")

            val open =
                withTimeout(TIMEOUT_MS) {
                    fixture.viewModel.uiState.first { it.inputEnabled }
                }
            assertEquals(PlaybackPhase.AWAITING_ANSWER, open.phase)

            repeat(TAPS) { fixture.viewModel.onRhythmTap(2_000L + it * 100L) }
            assertEquals(TAPS, fixture.viewModel.uiState.value.tapCount)

            val attempt =
                withTimeout(TIMEOUT_MS) {
                    fixture.attemptRepository
                        .recentAttempts(item.skill, limit = 10)
                        .first { it.isNotEmpty() }
                        .first()
                }
            val rhythm = assertNotNull(attempt.rhythm, "a tapped attempt must carry its taps")
            assertEquals(TAPS, rhythm.tapTimesMs.size)
            assertEquals(item.pattern.onsetCount, rhythm.expectedEventTimesMs.size)
        }

    @Test
    fun `M3 DOWNBEAT is never the node a learner is sent to`() =
        runBlocking {
            // It cannot be answered: plain identical beats, no downbeat accent, and a rotation that
            // never reaches the audio. Suspended in SkillGraph until its pattern carries a metrical
            // cue - see SkillGraph.ROUTING_SUSPENDED. Asserted here as well as there because the cost
            // of getting it wrong is a learner stuck on a question with no answer.
            assertTrue(SkillIds.M3_DOWNBEAT in SkillGraph.ROUTING_SUSPENDED)

            // And the chain still moves past it: mastering the node before must not leave the learner
            // parked, which is what a naive suspension would have caused.
            val mastered = setOf(SkillIds.M3_BEAT_FIND)
            val next = SkillGraph.currentRhythmNodeFor { it in mastered }
            assertEquals(SkillIds.M3_BEAT_DIV_RECOG, next)
        }

    private companion object {
        const val TIMEOUT_MS = 20_000L

        /** Enough to be unmistakably a real performance rather than a stray touch. */
        const val TAPS = 4

        /** Long enough for the taps below to be entered while the window is genuinely open. */
        const val BACKING_MS = 2_000L
    }
}
