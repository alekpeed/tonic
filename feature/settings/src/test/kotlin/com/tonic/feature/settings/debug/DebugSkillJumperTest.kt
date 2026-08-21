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
        val debugProgressRepository =
            FakeDebugProgressRepository(attemptRepository, skillStateRepository)
        val jumper =
            DebugSkillJumper(
                debugProgressRepository,
                skillStateRepository,
                sessionRepository,
                Clock { now },
            )

        suspend fun masteredNodes() =
            skillStateRepository
                .observeAll()
                .first()
                .filterValues { it.masteryState == MasteryState.MASTERED }
                .keys

        suspend fun currentNode() = masteredNodes().let { done -> SkillGraph.currentNodeFor { it in done } }
    }

    @Test
    fun `jumping to a node masters everything before it and nothing after`() {
        runBlocking {
            val fixture = Fixture()
            val target = SkillIds.M11_CHROM_FLAT6

            val seeded = fixture.jumper.jumpTo(target)

            val expectedBefore = SkillGraph.practiceChain.map { it.id }.takeWhile { it != target }
            assertEquals(expectedBefore, seeded, "seeded the wrong nodes, or in the wrong order")
            assertEquals(expectedBefore.toSet(), fixture.masteredNodes(), "seeded but not actually mastered")
            assertEquals(target, fixture.currentNode())
        }
    }

    @Test
    fun `pressing an earlier node after a later one works instead of throwing`() {
        // A REAL BUG, and the one a tester hit within a minute of opening the screen. The first
        // version walked forward from wherever the learner already was, so a target at or behind the
        // current node was unsatisfiable: the walk ran to its step ceiling and threw, which inside
        // viewModelScope.launch crashed the app. Resetting first makes the operation absolute, so the
        // buttons work in the order a person actually presses them.
        runBlocking {
            val fixture = Fixture()
            fixture.jumper.jumpTo(SkillIds.M11_CHROM_FLAT2)

            val seeded = fixture.jumper.jumpTo(SkillIds.M2_DEG_SET_2)

            assertEquals(listOf(SkillIds.M2_DEG_SET_1), seeded)
            assertEquals(SkillIds.M2_DEG_SET_2, fixture.currentNode())
            assertEquals(setOf(SkillIds.M2_DEG_SET_1), fixture.masteredNodes(), "the later nodes must be gone")
        }
    }

    @Test
    fun `the same jump twice leaves the same state - it is absolute, not cumulative`() {
        runBlocking {
            val fixture = Fixture()
            val target = SkillIds.M10_MIXED_MODE

            val first = fixture.jumper.jumpTo(target)
            val attemptsAfterFirst = fixture.attemptRepository.all.size
            val second = fixture.jumper.jumpTo(target)

            assertEquals(first, second)
            assertEquals(target, fixture.currentNode())
            assertEquals(
                attemptsAfterFirst,
                fixture.attemptRepository.all.size,
                "attempts accumulated across jumps - the reset is not actually clearing the log",
            )
        }
    }

    @Test
    fun `jumping to the chain's first node seeds nothing and wipes what was there`() {
        runBlocking {
            val fixture = Fixture()
            fixture.jumper.jumpTo(SkillIds.M9_MODE_ID_CADENCE)

            val seeded = fixture.jumper.jumpTo(SkillGraph.practiceChain.first().id)

            assertEquals(emptyList(), seeded)
            assertTrue(fixture.masteredNodes().isEmpty())
            assertTrue(fixture.attemptRepository.all.isEmpty())
        }
    }

    @Test
    fun `every node in the chain is reachable by one press, from a state that is not fresh`() {
        // The claim the whole tool rests on, checked for every node rather than a chosen few - and
        // checked from dirty state each time, since that is the only state a tester's device is ever in.
        runBlocking {
            val fixture = Fixture()
            for (node in SkillGraph.practiceChain) {
                fixture.jumper.jumpTo(node.id)
                assertEquals(node.id, fixture.currentNode(), "${node.id.raw} was not reachable in one press")
            }
        }
    }
}
