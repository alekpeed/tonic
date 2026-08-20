package com.tonic.feature.practice.engine

import com.tonic.core.engine.session.SkillWorkContext
import com.tonic.core.model.ids.SkillId
import com.tonic.core.model.ids.SkillIds
import com.tonic.core.model.items.AnswerAlphabet
import com.tonic.core.model.items.DifficultyAxis
import com.tonic.core.model.items.Item
import com.tonic.core.model.time.Clock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * An `M12` session driven end to end through the real loop — the production-wiring trace for Stage 2.6.
 *
 * The pieces are each tested where they live: the generator makes prediction items, the evaluator
 * applies d-prime and the gap criterion, the screen renders three buttons. What none of those proves
 * is that a learner in a session is handed a prediction item at all, that the audio it renders
 * actually contains the silent gap, or that a directional answer scores the way the node says it
 * should. That is what runs here.
 */
class PredictionSessionTest {
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

        suspend fun start(
            skill: SkillId,
            axes: Map<DifficultyAxis, Int> = DifficultyAxis.PREDICTION_AXES.associateWith { 0 },
        ) = engine.start(
            SkillWorkContext(skill, axes, totalAttempts = 0),
            dueReviews = emptyList(),
            sessionLengthMinutes = 10,
            rootSeed = 6_120L,
            now = now,
        )

        val current: Item.PredictionItem?
            get() = engine.state.value.currentItem as? Item.PredictionItem

        suspend fun answer(label: String) {
            now = now.plusSeconds(6)
            engine.submitAnswer(label)
        }
    }

    @Test
    fun `a prediction session hands out prediction items`() {
        runBlocking {
            val fixture = Fixture()
            fixture.start(SkillIds.M12_PREDICT_TRIAD)
            var seen = 0
            while (!fixture.engine.state.value.isFinished && seen < 20) {
                val item = fixture.current ?: break
                assertTrue(
                    item.statedDegree in item.activeDegrees,
                    "${item.statedDegree.canonicalLabel} was named but is not in the node's pool",
                )
                fixture.answer(item.correctLabel)
                seen++
            }
            fixture.engine.awaitPersistence()
            assertTrue(seen > 0, "the session produced no prediction items at all")
        }
    }

    @Test
    fun `the rendered audio actually contains the silent gap`() {
        runBlocking {
            // The gap is the PREDICT_GAP axis itself, not decoration, and it lives inside the buffer
            // rather than being a pause the screen takes between two plays - so it has to be *in* what
            // the loop hands the player, or the axis is measuring nothing.
            val shortGap =
                PracticeItems.renderAudio(
                    item(SkillIds.M12_PREDICT_DIATONIC, gap = 0),
                )
            val longGap =
                PracticeItems.renderAudio(
                    item(SkillIds.M12_PREDICT_DIATONIC, gap = 3),
                )
            val extraMs = longGap.durationMs - shortGap.durationMs
            assertTrue(
                extraMs > 3_500 && extraMs < 4_500,
                "gap level 3 minus level 0 should be 5000-1000ms of extra audio; measured ${extraMs}ms",
            )
        }
    }

    @Test
    fun `the introductory node accepts either direction, and the next node does not`() {
        runBlocking {
            // docs/20-PHASE-2-SPEC.md §8.1 decision 3, through the real scoring path. A learner who
            // hears that it was wrong but names the direction wrong is right at PREDICT_TRIAD and
            // wrong at PREDICT_DIATONIC.
            val triad = mismatched(SkillIds.M12_PREDICT_TRIAD)
            val opposite = flip(triad.correctLabel)
            assertTrue(
                PracticeItems.isCorrect(triad, opposite),
                "PREDICT_TRIAD must score a detected mismatch regardless of direction",
            )

            val diatonic = mismatched(SkillIds.M12_PREDICT_DIATONIC)
            assertTrue(
                !PracticeItems.isCorrect(diatonic, flip(diatonic.correctLabel)),
                "PREDICT_DIATONIC onward scores the direction",
            )
            assertTrue(PracticeItems.isCorrect(diatonic, diatonic.correctLabel))
        }
    }

    @Test
    fun `answering MATCHED on a mismatched item is wrong at every node`() {
        // The forgiveness at PREDICT_TRIAD is about direction only. Failing to notice a mismatch at all
        // is the thing the module measures, and is never excused.
        val triad = mismatched(SkillIds.M12_PREDICT_TRIAD)
        assertTrue(!PracticeItems.isCorrect(triad, AnswerAlphabet.MatchDirection.MATCHED))
    }

    @Test
    fun `a prediction attempt records the direction even where scoring ignores it`() {
        runBlocking {
            // Scoring and recording are different questions. §8.1 decision 3 added direction precisely
            // so the confusion data would become diagnostic; collapsing the recorded label at
            // PREDICT_TRIAD would throw that away at the one node where a learner's errors are most
            // informative.
            val fixture = Fixture()
            fixture.start(SkillIds.M12_PREDICT_TRIAD)
            var mismatches = 0
            while (!fixture.engine.state.value.isFinished && mismatches < 4) {
                val item = fixture.current ?: break
                if (!item.matches) mismatches++
                fixture.answer(AnswerAlphabet.MatchDirection.MATCHED)
            }
            fixture.engine.awaitPersistence()

            val recorded =
                fixture.attemptRepository.all
                    .map { it.targetLabel }
                    .toSet()
            assertTrue(
                recorded.any {
                    it == AnswerAlphabet.MatchDirection.TOO_LOW ||
                        it == AnswerAlphabet.MatchDirection.TOO_HIGH
                },
                "no directional target was ever recorded: $recorded",
            )
        }
    }

    @Test
    fun `a prediction node is scheduled over prediction axes only`() {
        runBlocking {
            val fixture = Fixture()
            fixture.start(SkillIds.M12_PREDICT_DIATONIC)
            repeat(12) {
                val item = fixture.current ?: return@repeat
                fixture.answer(item.correctLabel)
            }
            fixture.engine.awaitPersistence()

            val state =
                fixture.skillStateRepository
                    .observeAll()
                    .first()[SkillIds.M12_PREDICT_DIATONIC]
            assertTrue(state != null, "the node was never rebuilt from its attempts")
            assertEquals(
                DifficultyAxis.PREDICTION_AXES.toSet(),
                state.axisLevels.keys,
                "an M12 node must not carry a recognition axis - its items have no cadence to fade",
            )
        }
    }

    private fun item(
        skill: SkillId,
        gap: Int,
    ): Item.PredictionItem =
        com.tonic.core.curriculum.generators.M12ItemGenerator
            .generate(
                skill,
                mapOf(DifficultyAxis.PREDICT_GAP to gap, DifficultyAxis.PREDICT_DEVIATION to 0),
                seed = 31L,
            ).item

    /** The first generated item for [skill] whose sounded note is not the named one. */
    private fun mismatched(skill: SkillId): Item.PredictionItem =
        generateSequence(0L) { it + 1 }
            .take(64)
            .map {
                com.tonic.core.curriculum.generators.M12ItemGenerator
                    .generate(
                        skill,
                        DifficultyAxis.PREDICTION_AXES.associateWith { 0 },
                        seed = 900L + it,
                    ).item
            }.first { !it.matches }

    private fun flip(label: String): String =
        when (label) {
            AnswerAlphabet.MatchDirection.TOO_LOW -> AnswerAlphabet.MatchDirection.TOO_HIGH
            AnswerAlphabet.MatchDirection.TOO_HIGH -> AnswerAlphabet.MatchDirection.TOO_LOW
            else -> error("$label has no opposite direction")
        }
}
