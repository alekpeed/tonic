package com.tonic.feature.practice.engine

import com.tonic.core.curriculum.graph.SkillGraph
import com.tonic.core.engine.session.SkillWorkContext
import com.tonic.core.model.ids.SkillId
import com.tonic.core.model.ids.SkillIds
import com.tonic.core.model.items.DifficultyAxis
import com.tonic.core.model.music.Mode
import com.tonic.core.model.music.ScaleDegree
import com.tonic.core.model.time.Clock
import kotlinx.coroutines.runBlocking
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * An `M11` session driven end to end through the real loop — docs/20-PHASE-2-SPEC.md §2.2/§3.
 *
 * The production-wiring trace for Stage 2.5. Everything else in this stage is checked at the level of
 * the piece that owns it: the graph knows the chromatic nodes, the generator draws from their sets, the
 * evaluator applies the sixth criterion, the ladder measures at twelve positions. What no unit of those
 * proves is that a learner sitting in a session actually gets chromatic items, with the new note heard
 * often enough for the criterion judging them to have a real sample behind it.
 */
class ChromaticSessionTest {
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
                SkillWorkContext(skill, DifficultyAxis.RECOGNITION_AXES.associateWith { 0 }, totalAttempts = 0),
                dueReviews = emptyList(),
                sessionLengthMinutes = 10,
                rootSeed = 5_150L,
                now = now,
            )

        suspend fun answerCorrectly(limit: Int): Int {
            var answered = 0
            while (!engine.state.value.isFinished && answered < limit) {
                val item = engine.state.value.recognitionItem ?: break
                now = now.plusSeconds(4)
                engine.submitAnswer(item.targetDegree.canonicalLabel)
                answered++
            }
            engine.awaitPersistence()
            return answered
        }
    }

    @Test
    fun `a chromatic session asks about the note the node introduces, often`() {
        runBlocking {
            val fixture = Fixture()
            fixture.start(SkillIds.M11_CHROM_SHARP4)
            val answered = fixture.answerCorrectly(limit = 60)
            assertTrue(answered > 0, "the session produced no items at all")

            val logged = fixture.attemptRepository.all.filterNot { it.isIndependenceCheckProbe }
            val focus = SkillGraph.focusDegreeFor(SkillIds.M11_CHROM_SHARP4)!!
            val focusShare = logged.count { it.targetLabel == focus.canonicalLabel }.toDouble() / logged.size

            // Uniform across 8 degrees would be 0.125. The weighting exists so the mastery criterion has
            // something to judge; if this drops back to uniform the criterion becomes unreachable and
            // the node silently un-masterable, which is a failure with no visible symptom.
            assertTrue(
                focusShare > 1.0 / SkillGraph.activeDegreesFor(SkillIds.M11_CHROM_SHARP4).size,
                "${focus.canonicalLabel} took ${"%.2f".format(focusShare)} of the session - no better " +
                    "than uniform, so SkillGraph.degreeWeightsFor is not reaching the live loop",
            )
        }
    }

    @Test
    fun `every answer offered in a chromatic session is one the learner has a button for`() {
        runBlocking {
            val fixture = Fixture()
            fixture.start(SkillIds.M11_CHROM_FULL)
            fixture.answerCorrectly(limit = 40)

            val allowed = SkillGraph.activeDegreesFor(SkillIds.M11_CHROM_FULL)
            val logged = fixture.attemptRepository.all.filterNot { it.isIndependenceCheckProbe }
            assertTrue(logged.isNotEmpty())
            for (attempt in logged) {
                assertTrue(
                    allowed.any { it.canonicalLabel == attempt.targetLabel },
                    "${attempt.targetLabel} was asked but is not in CHROM_FULL's set",
                )
            }
            // docs/20-PHASE-2-SPEC.md §8.1 decision 4's rule, restated for chromatic major: the answer
            // alphabet and the sounded pitches are the same set, so nothing is ever heard that has no
            // button. All twelve are in play here, so the property is trivially true - asserted anyway,
            // because it stops being trivial the moment anything narrows the ladder.
            assertEquals(12, allowed.size)
        }
    }

    @Test
    fun `chromatic items stay in major and target the twelve-tone set`() {
        runBlocking {
            val fixture = Fixture()
            fixture.start(SkillIds.M11_CHROM_FLAT6)
            var checked = 0
            while (!fixture.engine.state.value.isFinished && checked < 25) {
                val item = fixture.engine.state.value.recognitionItem ?: break
                assertEquals(Mode.MAJOR, item.mode, "M11 is chromatic degrees *in major* (§2.2)")
                assertTrue(item.targetDegree in ScaleDegree.ALL_CHROMATIC)
                fixture.now = fixture.now.plusSeconds(4)
                fixture.engine.submitAnswer(item.targetDegree.canonicalLabel)
                checked++
            }
            fixture.engine.awaitPersistence()
            assertTrue(checked > 0)
        }
    }
}
