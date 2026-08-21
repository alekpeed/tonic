package com.tonic.core.engine.debug

import com.tonic.core.curriculum.graph.SkillGraph
import com.tonic.core.engine.replay.SkillStateReducer
import com.tonic.core.model.state.MasteryState
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The production-wiring trace for the debug "jump to node" tool: every node this graph knows must
 * actually reach [MasteryState.MASTERED] when its seeded attempts are folded through the real
 * [SkillStateReducer] — the same reducer `:core:data`'s `rebuildFromAttempts` calls for genuine play.
 * A seeder that only satisfies its own hand-rolled notion of "mastered" would be worthless the moment
 * [com.tonic.core.engine.mastery.MasteryEvaluator] changed and it didn't.
 */
class DebugMasterySeederTest {
    @Test
    fun `every node in the practice chain masters from its seeded attempts, via the real reducer`() {
        val failures = mutableListOf<String>()
        for (node in SkillGraph.practiceChain) {
            val attempts = DebugMasterySeeder.attemptsToMaster(node.id, sessionId = 1L, startAt = Instant.EPOCH)
            val state = SkillStateReducer.replay(node.id, attempts)
            if (state.masteryState != MasteryState.MASTERED) {
                failures += "${node.id.raw}: ${attempts.size} attempts, ended ${state.masteryState}"
            }
        }
        assertEquals(emptyList<String>(), failures)
    }

    @Test
    fun `seeding is deterministic - same inputs, byte-identical attempts`() {
        val skill = SkillGraph.practiceChain.first().id
        val a = DebugMasterySeeder.attemptsToMaster(skill, sessionId = 7L, startAt = Instant.EPOCH)
        val b = DebugMasterySeeder.attemptsToMaster(skill, sessionId = 7L, startAt = Instant.EPOCH)
        assertEquals(a, b)
    }

    @Test
    fun `a chromatic node's focus degree clears the FOCUS_DEGREE criterion, not just the others`() {
        // The case plain round-robin cannot satisfy on its own - see the class doc on
        // DebugMasterySeeder.recognitionAttempts. If this regresses, the seeder would hang or error
        // on every M11 node rather than quietly under-covering one degree.
        val focusNode =
            SkillGraph.m11Nodes.firstOrNull { SkillGraph.focusDegreeFor(it.id) != null }
                ?: error("no M11 node declares a focus degree - test is stale")
        val attempts = DebugMasterySeeder.attemptsToMaster(focusNode.id, sessionId = 1L, startAt = Instant.EPOCH)
        val state = SkillStateReducer.replay(focusNode.id, attempts)
        assertEquals(MasteryState.MASTERED, state.masteryState)
    }
}
