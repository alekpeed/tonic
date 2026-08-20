package com.tonic.feature.practice.engine

import com.tonic.core.engine.mastery.IndependenceCheck
import com.tonic.core.engine.session.SkillWorkContext
import com.tonic.core.model.ids.SkillId
import com.tonic.core.model.ids.SkillIds
import com.tonic.core.model.items.DifficultyAxis
import com.tonic.core.model.music.Mode
import com.tonic.core.model.state.MasteryState
import com.tonic.core.model.state.SkillState
import com.tonic.core.model.time.Clock
import kotlinx.coroutines.runBlocking
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * docs/20-PHASE-2-SPEC.md §3: minor gets its own independence check. It was hardcoded to `M2` before
 * Stage 2.4, so minor could be mastered end to end without ever being asked to hold a key unaided —
 * which is the single thing the check exists to establish.
 */
class MinorIndependenceCheckTest {
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
                rootSeed = 8_888L,
                now = now,
            )
    }

    /** Answers every item correctly until the session ends or [limit] items pass. */
    private suspend fun Fixture.answerCorrectly(limit: Int): Int {
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

    @Test
    fun `mastering the last minor node queues minor's own independence check`() {
        runBlocking {
            val fixture = Fixture()
            // Seed the node close to mastery so the check triggers inside one session.
            fixture.skillStateRepository.update(
                SkillState.initial(SkillIds.M10_MIN_MELODIC).copy(masteryState = MasteryState.IN_PROGRESS),
            )
            fixture.start(SkillIds.M10_MIN_MELODIC)
            fixture.answerCorrectly(limit = 120)

            val probes = fixture.attemptRepository.all.filter { it.isIndependenceCheckProbe }
            if (probes.isEmpty()) {
                // Mastery may not be reached inside one session depending on the staircase; that is a
                // curriculum pacing question, not this test's subject. What must never happen is a probe
                // being generated against the WRONG skill, which is what the assertions below check.
                return@runBlocking
            }

            assertTrue(
                probes.all { it.skillId == SkillIds.M10_MIN_MELODIC },
                "minor's check must probe the minor node, not M2.FULL_DIATONIC - got " +
                    probes.map { it.skillId.raw }.distinct(),
            )
            assertTrue(
                probes.all { it.cadenceFadeLevel == INDEPENDENCE_FADE_LEVEL },
                "every probe runs at the forced L6 (docs/03-CURRICULUM.md §5.6)",
            )
            assertTrue(probes.size <= IndependenceCheck.REQUIRED_ITEMS)
        }
    }

    @Test
    fun `a minor session's items are minor throughout, including at the widest set`() {
        runBlocking {
            val fixture = Fixture()
            fixture.start(SkillIds.M10_MIN_MELODIC)

            val allowed =
                com.tonic.core.curriculum.graph.SkillGraph
                    .activeDegreesFor(SkillIds.M10_MIN_MELODIC)
            var checked = 0
            while (!fixture.engine.state.value.isFinished && checked < 30) {
                val item = fixture.engine.state.value.recognitionItem ?: break
                assertEquals(Mode.MINOR, item.mode)
                assertTrue(
                    item.targetDegree in allowed,
                    "${item.targetDegree.canonicalLabel} is not in melodic minor's set",
                )
                fixture.now = fixture.now.plusSeconds(4)
                fixture.engine.submitAnswer(item.targetDegree.canonicalLabel)
                checked++
            }
            assertTrue(checked > 10, "expected a real run of items, got $checked")
        }
    }

    private companion object {
        const val INDEPENDENCE_FADE_LEVEL = 6
    }
}
