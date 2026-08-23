package com.tonic.feature.practice.engine

import com.tonic.core.engine.session.SkillWorkContext
import com.tonic.core.model.ids.SkillId
import com.tonic.core.model.ids.SkillIds
import com.tonic.core.model.items.DifficultyAxis
import com.tonic.core.model.items.Item
import com.tonic.core.model.rhythm.RhythmQuestion
import com.tonic.core.model.time.Clock
import kotlinx.coroutines.runBlocking
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `M3` through the practice loop — docs/40-PHASE-4-SPEC.md §5 and §6, Stage 4.5.
 *
 * Rhythm is the first module answered by something other than a label, so this asserts the shape of
 * that channel rather than only its happy path: taps are scored by the loop against the pattern it is
 * holding, the whole record reaches the attempt log, and a label answer cannot be smuggled into a
 * production item.
 */
class RhythmSessionTest {
    private class Fixture(
        var now: Instant = Instant.EPOCH,
    ) {
        val attemptRepository = FakeAttemptRepository()
        val skillStateRepository = FakeSkillStateRepository(attemptRepository)
        val confusionRepository = FakeConfusionRepository()
        val sessionRepository = FakeSessionRepository()
        val audioPlayer = FakeAudioPlayer()
        val audioInterruptions = FakeAudioInterruptions()
        val engine =
            PracticeLoopEngine(
                attemptRepository,
                skillStateRepository,
                confusionRepository,
                sessionRepository,
                audioPlayer,
                audioInterruptions,
                Clock { now },
            )

        suspend fun start(skill: SkillId) =
            engine.start(
                SkillWorkContext(skill, DifficultyAxis.RHYTHM_AXES.associateWith { 0 }, totalAttempts = 0),
                dueReviews = emptyList(),
                sessionLengthMinutes = 5,
                rootSeed = 4_040L,
                now = now,
            )

        val rhythmItem: Item.RhythmItem?
            get() = engine.state.value.currentItem as? Item.RhythmItem
    }

    /** Taps exactly on every onset — a learner who reproduced the pattern perfectly. */
    private fun perfectTaps(item: Item.RhythmItem) = item.onsetTimesMs

    @Test
    fun `a production session runs end to end, scoring taps rather than a label`() {
        runBlocking {
            val fixture = Fixture()
            fixture.start(SkillIds.M3_BEAT_FIND)

            var answered = 0
            while (!fixture.engine.state.value.isFinished && answered < 60) {
                val item = fixture.rhythmItem ?: break
                fixture.now = fixture.now.plusSeconds(5)
                fixture.engine.submitTaps(perfectTaps(item), calibrationOffsetMs = 0.0)
                answered++
            }
            fixture.engine.awaitPersistence()

            assertTrue(answered > 5, "the session should run a real number of items, got $answered")
            assertEquals(answered, fixture.attemptRepository.all.size, "every tapped answer is recorded")
            assertTrue(fixture.attemptRepository.all.all { it.correct })
            assertTrue(
                fixture.attemptRepository.all.all { it.rhythm != null },
                "a tapped attempt without its tap record cannot be re-scored, which is §4.4's whole promise",
            )
        }
    }

    @Test
    fun `the attempt carries everything a later verdict reads`() {
        runBlocking {
            val fixture = Fixture()
            fixture.start(SkillIds.M3_BEAT_FIND)
            val item = fixture.rhythmItem!!

            fixture.engine.submitTaps(perfectTaps(item), calibrationOffsetMs = 12.0)
            fixture.engine.awaitPersistence()

            val attempt = fixture.attemptRepository.all.single()
            val rhythm = assertNotNull(attempt.rhythm)
            assertEquals(item.onsetTimesMs, rhythm.expectedEventTimesMs)
            assertEquals(item.perEventFigures, rhythm.perEventFigures)
            assertEquals(12.0, rhythm.calibrationOffsetMs)
            assertTrue(rhythm.toleranceHalfWidthMs > 0.0, "the window actually applied must be recorded")
            assertEquals(RhythmQuestion.TapItBack.TAPPED, attempt.responseLabel)
            assertEquals(RhythmQuestion.TapItBack.TAPPED, attempt.targetLabel)
        }
    }

    @Test
    fun `the calibration constant is applied, not merely recorded`() {
        runBlocking {
            val fixture = Fixture()
            fixture.start(SkillIds.M3_BEAT_FIND)
            val item = fixture.rhythmItem!!

            // A learner uniformly a full beat late. Corrected they are perfect; uncorrected they have
            // played a different rhythm. If the offset were stored and not subtracted, this would be
            // scored wrong - the exact defect RhythmScorer.scoreRelative was fixed for at Stage 4.3.
            val beatMs = 60_000.0 / item.tempoBpm
            fixture.engine.submitTaps(item.onsetTimesMs.map { it + beatMs }, calibrationOffsetMs = beatMs)
            fixture.engine.awaitPersistence()

            val corrected = fixture.attemptRepository.all.single()
            assertTrue(corrected.correct)
        }
    }

    @Test
    fun `taps that land on nothing are recorded as extra, not as misses`() {
        runBlocking {
            val fixture = Fixture()
            fixture.start(SkillIds.M3_BEAT_FIND)
            val item = fixture.rhythmItem!!

            // Every onset struck, plus one stray halfway between the first two - far enough from both
            // to match neither. §6.2: extras and misses "mean different things pedagogically".
            val stray = (item.onsetTimesMs[0] + item.onsetTimesMs[1]) / 2.0
            fixture.engine.submitTaps((item.onsetTimesMs + stray).sorted(), calibrationOffsetMs = 0.0)
            fixture.engine.awaitPersistence()

            val attempt = fixture.attemptRepository.all.single()
            val rhythm = assertNotNull(attempt.rhythm)
            assertEquals(1, rhythm.extraTaps)
            assertEquals(0, rhythm.missedTaps)
            assertFalse(attempt.correct)
        }
    }

    @Test
    fun `the score of the last tapped answer reaches the loop state whole`() {
        runBlocking {
            val fixture = Fixture()
            fixture.start(SkillIds.M3_BEAT_FIND)
            val item = fixture.rhythmItem!!
            assertNull(fixture.engine.state.value.lastRhythmScore, "nothing has been tapped yet")

            fixture.engine.submitTaps(perfectTaps(item), calibrationOffsetMs = 0.0, autoAdvance = false)

            // §7.4 forbids reducing this to a percentage, so the per-event detail has to survive the
            // trip out of the loop.
            val score = assertNotNull(fixture.engine.state.value.lastRhythmScore)
            assertEquals(item.pattern.onsetCount, score.matches.size)
            assertTrue(score.matches.all { it.asynchronyMs != null })
        }
    }

    @Test
    fun `a recognition rhythm item is still answered with a label`() {
        runBlocking {
            val fixture = Fixture()
            fixture.start(SkillIds.M3_DOWNBEAT)
            val item = fixture.rhythmItem!!
            val question = item.question as RhythmQuestion.WhichBeatIsOne

            fixture.engine.submitAnswer(question.correctLabel)
            fixture.engine.awaitPersistence()

            val recognized = fixture.attemptRepository.all.single()
            assertTrue(recognized.correct)
            assertNull(recognized.rhythm, "nothing was tapped, so there is no tap record to invent")
        }
    }

    @Test
    fun `submitTaps refuses an item that is not tapped back`() {
        runBlocking {
            val fixture = Fixture()
            fixture.start(SkillIds.M3_DOWNBEAT)

            val failure =
                runCatching { fixture.engine.submitTaps(listOf(0.0), calibrationOffsetMs = 0.0) }
                    .exceptionOrNull()
            assertTrue(
                failure is IllegalArgumentException,
                "answering a recognition item with taps must fail loudly rather than record a " +
                    "meaningless perfect score, got $failure",
            )
        }
    }
}
