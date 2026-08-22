package com.tonic.feature.practice.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tonic.core.audio.capture.CapturedAudio
import com.tonic.core.curriculum.graph.SkillGraph
import com.tonic.core.engine.debug.DebugMasterySeeder
import com.tonic.core.model.attempts.Attempt
import com.tonic.core.model.attempts.InputMethod
import com.tonic.core.model.ids.SkillId
import com.tonic.core.model.ids.SkillIds
import com.tonic.core.model.items.Item
import com.tonic.core.model.music.Tuning
import com.tonic.core.model.state.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import kotlin.math.pow
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Singing an answer at `M10` and `M11` — Stage 3.5's production-wiring trace.
 *
 * **The route, checked, because the route is what was missing last time.** Stage 3.5 needed no new
 * feature code: `M10` and `M11` both generate `FunctionalRecognitionItem` through `M2ItemGenerator`,
 * so `sungAvailableFor` already admits them and the analyzer already receives each item's own mode and
 * alphabet. That is a comfortable-sounding argument, and a comfortable-sounding argument is exactly
 * what let three earlier stages ship unreachable. So this drives a real session to a minor node and to
 * a chromatic one, sings into it, and reads the attempt back out of the log.
 *
 * The two nodes are chosen for the two things that could plausibly be wired wrong. `M10.MIN_NATURAL`
 * is the first place the analyzer is handed `Mode.MINOR` — a resolver given the wrong mode would place
 * every degree a semitone off and still answer confidently. `M11.CHROM_FULL` is the widest alphabet
 * the app has, where a sung answer resolves against twelve candidates a semitone apart.
 */
@RunWith(AndroidJUnit4::class)
class SungMinorAndChromaticTest {
    @Before fun setUp() = Dispatchers.setMain(Dispatchers.Default)

    @After fun tearDown() = Dispatchers.resetMain()

    private fun fixtureAt(target: SkillId): PracticeFixture {
        val fixture =
            PracticeFixture(
                AppSettings(
                    module2IntroSeen = true,
                    module10IntroSeen = true,
                    module11IntroSeen = true,
                    sungResponseEnabled = true,
                    sungResponseIntroSeen = true,
                ),
            )
        fixture.microphoneSource.isAvailable = true
        runBlocking {
            for (node in SkillGraph.practiceChain) {
                if (node.id == target) break
                fixture.skillStateRepository.update(DebugMasterySeeder.masteredStateFor(node.id, Instant.EPOCH))
            }
        }
        return fixture
    }

    /** A sustained tone at [midi], long enough to survive the analyzer's onset discard. */
    private fun tone(midi: Int): CapturedAudio {
        val frequency = Tuning.DEFAULT_A4_HZ * 2.0.pow((midi - A4_MIDI) / 12.0)
        val sampleRate = 48_000
        val samples =
            FloatArray((sampleRate * TONE_MS / 1000.0).toInt()) { i ->
                (kotlin.math.sin(2.0 * Math.PI * frequency * i / sampleRate) * 0.5).toFloat()
            }
        return CapturedAudio(samples, sampleRate)
    }

    /**
     * Runs one item at [target]: waits for a live recognition item on that node, sings its target
     * pitch, and returns the attempt that was written.
     */
    private fun singOneItemAt(target: SkillId): Pair<Item.FunctionalRecognitionItem, Attempt> =
        runBlocking {
            val fixture = fixtureAt(target)
            fixture.viewModel.startIfNeeded()
            withTimeout(TIMEOUT_MS) { fixture.viewModel.uiState.first { it.item != null } }

            val item =
                withTimeout(TIMEOUT_MS) {
                    var found: Item.FunctionalRecognitionItem? = null
                    while (found == null) {
                        if (fixture.viewModel.uiState.value.showIntro) fixture.viewModel.onIntroDismissed()
                        val state = fixture.viewModel.uiState.value
                        found =
                            state.recognitionItem
                                ?.takeIf { it.skill == target && state.inputEnabled && state.sungResponseAvailable }
                        if (found == null) delay(20)
                    }
                    found
                }

            // Exactly the pitch the item is asking about. Anything the analyzer does with mode or
            // alphabet that is wrong will move the answer off this degree.
            fixture.microphoneSource.nextCapture = tone(item.targetMidi)
            fixture.viewModel.onSingAnswer()

            withTimeout(TIMEOUT_MS) {
                while (fixture.attemptRepository.attemptsFor(target).isEmpty()) delay(10)
            }
            item to fixture.attemptRepository.attemptsFor(target).last()
        }

    /**
     * Minor, where the analyzer is handed `Mode.MINOR` for the first time.
     *
     * A resolver given the wrong mode would put `♭3` where `3` is and answer with complete confidence,
     * so "it resolved to something" proves nothing here — the assertion is that it resolved to the
     * degree the item actually asked for.
     */
    @Test
    fun `a sung answer is scored correctly on a minor node`() {
        val (item, attempt) = singOneItemAt(SkillIds.M10_MIN_NATURAL)

        assertEquals(InputMethod.SUNG, attempt.inputMethod)
        assertEquals(item.targetDegree.canonicalLabel, attempt.responseLabel)
        assertTrue(attempt.correct, "singing the target pitch of a minor item did not score as correct")
    }

    /**
     * Twelve degrees a semitone apart — the widest alphabet in the app.
     *
     * §5.5 measures this node as tolerating 44 cents of detuning, so a pitch sung exactly on target has
     * enormous headroom; what is being checked is the wiring, not the margin. The margin has its own
     * measurement in `SungToleranceMeasurementTest`, which is where a change to it will be caught.
     */
    @Test
    fun `a sung answer is scored correctly on the full chromatic node`() {
        val (item, attempt) = singOneItemAt(SkillIds.M11_CHROM_FULL)

        assertEquals(InputMethod.SUNG, attempt.inputMethod)
        assertEquals(item.targetDegree.canonicalLabel, attempt.responseLabel)
        assertTrue(attempt.correct, "singing the target pitch of a chromatic item did not score as correct")
        assertEquals(
            CHROMATIC_ALPHABET_SIZE,
            item.activeDegrees.size,
            "the chromatic node stopped offering twelve degrees, so this is no longer the widest-alphabet case",
        )
    }

    private companion object {
        const val TIMEOUT_MS = 30_000L
        const val A4_MIDI = 69
        const val TONE_MS = 1_200
        const val CHROMATIC_ALPHABET_SIZE = 12
    }
}
