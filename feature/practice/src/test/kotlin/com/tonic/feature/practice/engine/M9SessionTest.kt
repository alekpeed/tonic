package com.tonic.feature.practice.engine

import com.tonic.core.engine.mastery.BinaryMasteryEvaluator
import com.tonic.core.engine.session.SkillWorkContext
import com.tonic.core.model.ids.SkillIds
import com.tonic.core.model.items.AnswerAlphabet
import com.tonic.core.model.items.DifficultyAxis
import com.tonic.core.model.items.Item
import com.tonic.core.model.music.Mode
import com.tonic.core.model.time.Clock
import kotlinx.coroutines.runBlocking
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The point of the loop refactor: [PracticeLoopEngine] can now run a session of a non-`M2` item type.
 * Before this, the loop was typed on `Item.FunctionalRecognitionItem` end to end and `M9` could be
 * generated but never played.
 */
class M9SessionTest {
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

        suspend fun start(skill: com.tonic.core.model.ids.SkillId) =
            engine.start(
                SkillWorkContext(skill, DifficultyAxis.RECOGNITION_AXES.associateWith { 0 }, totalAttempts = 0),
                dueReviews = emptyList(),
                sessionLengthMinutes = 5,
                rootSeed = 31_337L,
                now = now,
            )

        val modeItem: Item.ModeIdentificationItem?
            get() = engine.state.value.currentItem as? Item.ModeIdentificationItem
    }

    @Test
    fun `a full M9 session runs end to end, scoring every answer`() {
        runBlocking {
            val fixture = Fixture()
            fixture.start(SkillIds.M9_MODE_ID_TRIAD)

            var answered = 0
            while (!fixture.engine.state.value.isFinished && answered < 60) {
                val item = fixture.modeItem ?: break
                // A learner who genuinely hears it.
                fixture.now = fixture.now.plusSeconds(5)
                fixture.engine.submitAnswer(item.correctLabel)
                answered++
            }
            fixture.engine.awaitPersistence()

            assertTrue(answered > 10, "the session should run a real number of items, got $answered")
            assertEquals(answered, fixture.attemptRepository.all.size, "every answer is recorded")
            assertTrue(
                fixture.attemptRepository.all.all { it.correct },
                "a learner answering correctly must be scored correct - the loop is comparing against " +
                    "the mode label, not against a scale degree",
            )
        }
    }

    @Test
    fun `M9 attempts record the mode as the answer, and audio actually played`() {
        runBlocking {
            val fixture = Fixture()
            fixture.start(SkillIds.M9_MODE_ID_CADENCE)

            val item = fixture.modeItem!!
            val expected = if (item.mode == Mode.MAJOR) "MAJOR" else "MINOR"

            fixture.engine.submitAnswer(AnswerAlphabet.MajorMinor.MINOR)
            fixture.engine.awaitPersistence()

            val attempt = fixture.attemptRepository.all.first()
            assertEquals(expected, attempt.targetLabel, "the target label is the mode, not a degree")
            assertEquals(AnswerAlphabet.MajorMinor.MINOR, attempt.responseLabel)
            assertEquals(expected == "MINOR", attempt.correct)
            assertEquals(SkillIds.M9_MODE_ID_CADENCE, attempt.skillId)

            assertTrue(
                fixture.audioPlayer.playedBuffers
                    .first()
                    .samples
                    .isNotEmpty(),
                "an M9 item must render real audio through the loop, not an empty buffer",
            )
        }
    }

    @Test
    fun `a learner who answers major to everything is not certified by the loop's own record`() {
        // End-to-end version of the spec's biased-responder simulation: driven through the real loop
        // rather than a hand-built attempt list, so it also proves the loop records the fields
        // BinaryMasteryEvaluator needs in the form it expects.
        runBlocking {
            val fixture = Fixture()
            fixture.start(SkillIds.M9_MODE_ID_TRIAD)

            var answered = 0
            while (!fixture.engine.state.value.isFinished && answered < 40) {
                fixture.modeItem ?: break
                fixture.now = fixture.now.plusSeconds(5)
                fixture.engine.submitAnswer(AnswerAlphabet.MajorMinor.MAJOR)
                answered++
            }
            fixture.engine.awaitPersistence()

            val window = fixture.attemptRepository.all.takeLast(BinaryMasteryEvaluator.WINDOW_SIZE)
            val verdict = BinaryMasteryEvaluator.evaluate(window, signalLabel = AnswerAlphabet.MajorMinor.MINOR)

            assertFalse(verdict.isMastered, "answering one label to everything must never certify")
        }
    }

    @Test
    fun `replaying an M9 item repeats it unchanged - there is no home to restore`() {
        // The home-reminder mechanic is a recognition-item notion. An M9 item has no target note that
        // could have lost its reference, so replay must be the plain repeat it always was, not a crash
        // and not a silently different stimulus.
        runBlocking {
            val fixture = Fixture()
            fixture.start(SkillIds.M9_MODE_ID_MELODIC)

            val first = fixture.audioPlayer.playedBuffers.last()
            fixture.engine.replay()

            assertEquals(first, fixture.audioPlayer.playedBuffers.last())
        }
    }
}
