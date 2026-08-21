package com.tonic.feature.settings.debug

import com.tonic.core.curriculum.graph.SkillGraph
import com.tonic.core.model.ids.SkillIds
import com.tonic.core.model.state.MasteryState
import com.tonic.core.model.time.Clock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The production-wiring trace for the "I can't debug it if I can't get through the level" tool: a
 * tester should be able to reach any node in [SkillGraph.practiceChain] without playing every node
 * before it for real. What [com.tonic.core.engine.debug.DebugMasterySeederTest] proves in isolation —
 * that a seeded attempt log mastery-clears one node — this proves end to end: recorded through the real
 * repositories, rebuilt through the real replayer, and left exactly where a genuine learner who had
 * just cleared that node would be.
 */
class DebugSkillJumperTest {
    private class Fixture(
        var now: Instant = Instant.EPOCH,
    ) {
        val attemptRepository = FakeAttemptRepository()
        val skillStateRepository = FakeSkillStateRepository(attemptRepository)
        val sessionRepository = FakeSessionRepository()
        val jumper =
            DebugSkillJumper(attemptRepository, skillStateRepository, sessionRepository, Clock { now })
    }

    @Test
    fun `jumping to a node masters everything before it and nothing after`() {
        runBlocking {
            val fixture = Fixture()
            val target = SkillIds.M11_CHROM_FLAT6

            val seeded = fixture.jumper.jumpTo(target)

            val expectedBefore = SkillGraph.practiceChain.map { it.id }.takeWhile { it != target }
            assertEquals(expectedBefore, seeded, "seeded the wrong nodes, or in the wrong order")

            val states = fixture.skillStateRepository.observeAll().first()
            for (id in expectedBefore) {
                assertEquals(
                    MasteryState.MASTERED,
                    states[id]?.masteryState,
                    "${id.raw} was seeded but never actually mastered",
                )
            }
            assertTrue(
                target !in states || states.getValue(target).masteryState != MasteryState.MASTERED,
                "the target itself must be left unmastered - the tester practices it for real",
            )

            assertEquals(
                target,
                SkillGraph.currentNodeFor {
                    it in states.keys &&
                        states.getValue(it).masteryState == MasteryState.MASTERED
                },
            )
        }
    }

    @Test
    fun `jumping to the current node is a no-op`() {
        runBlocking {
            val fixture = Fixture()
            val current = SkillGraph.currentNodeFor { false }

            val seeded = fixture.jumper.jumpTo(current)

            assertEquals(emptyList(), seeded)
            assertTrue(
                fixture.skillStateRepository
                    .observeAll()
                    .first()
                    .isEmpty(),
            )
        }
    }

    @Test
    fun `jumping twice in the same session reaches a later target from where the first jump left off`() {
        runBlocking {
            val fixture = Fixture()
            fixture.jumper.jumpTo(SkillIds.M9_MODE_ID_CADENCE)
            val secondSeeded = fixture.jumper.jumpTo(SkillIds.M10_MIXED_MODE)

            // Nothing already mastered by the first jump should be re-seeded by the second.
            assertTrue(SkillIds.M2_DEG_SET_1 !in secondSeeded)
            val states = fixture.skillStateRepository.observeAll().first()
            assertEquals(
                SkillIds.M10_MIXED_MODE,
                SkillGraph.currentNodeFor { states[it]?.masteryState == MasteryState.MASTERED },
            )
        }
    }
}
