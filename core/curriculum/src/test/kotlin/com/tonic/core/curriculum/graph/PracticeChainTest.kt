package com.tonic.core.curriculum.graph

import com.tonic.core.model.ids.SkillId
import com.tonic.core.model.ids.SkillIds
import com.tonic.core.model.items.DifficultyAxis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The one answer to "what is the learner working on" — Stage 2.8.
 *
 * Before this, Home, the progress screen and the practice loop each derived it separately, and they
 * had already diverged: Home and Progress walked `M2` alone while Practice walked the whole chain. A
 * learner past the major nodes would have seen Home report `M2.FULL_DIATONIC` indefinitely while their
 * sessions ran minor. Two screens disagreeing about one fact is worse than either being wrong alone,
 * because neither looks broken.
 *
 * `M9` was worse still: it had no node in this graph at all, while `M10.MIN_SET_1` declared
 * `M9.MODE_ID_TRIAD` as its prerequisite — a gate pointing at nothing, which is why the module was
 * unreachable no matter which chain a screen walked.
 */
class PracticeChainTest {
    // SkillId is a value class and so cannot be a vararg - lists throughout.
    private fun resolve(mastered: List<SkillId> = emptyList()): SkillId {
        val done = mastered.toSet()
        return SkillGraph.currentNodeFor { it in done }
    }

    /** Everything up to but not including [stop], in chain order. */
    private fun masteredUpTo(stop: SkillId): List<SkillId> =
        SkillGraph.practiceChain.map { it.id }.takeWhile { it != stop }

    @Test
    fun `a fresh learner starts at the first node`() {
        assertEquals(SkillIds.M2_DEG_SET_1, resolve())
    }

    @Test
    fun `every node in the chain is reachable by mastering the ones before it`() {
        // The property that makes the chain a curriculum rather than a list: walk it from empty,
        // mastering whatever the resolver hands back, and confirm it visits every node. A node that
        // can never be resolved is a node no learner can ever practice - which is exactly what M9 was.
        val mastered = mutableSetOf<SkillId>()
        val visited = mutableListOf<SkillId>()
        repeat(SkillGraph.practiceChain.size) {
            val next = SkillGraph.currentNodeFor { id -> id in mastered }
            visited += next
            mastered += next
        }

        assertEquals(
            SkillGraph.practiceChain.map { it.id }.toSet(),
            visited.toSet(),
            "unreachable nodes: ${SkillGraph.practiceChain.map { it.id } - visited.toSet()}",
        )
    }

    @Test
    fun `M9 is in the chain, and minor waits for it`() {
        // The dangling gate, closed. M10.MIN_SET_1's prerequisite is M9.MODE_ID_TRIAD, and until Stage
        // 2.8 nothing of that name existed here.
        assertTrue(SkillGraph.m9Nodes.map { it.id }.containsAll(SkillIds.M9_NODES_IN_ORDER))
        assertEquals(SkillIds.M9_MODE_ID_TRIAD, SkillGraph.node(SkillIds.M10_MIN_SET_1).prerequisite)

        // With the whole major chain done but no mode identification, the learner is sent to M9 -
        // never into minor, which they could not yet hear the mode of.
        assertEquals(SkillIds.M9_MODE_ID_CADENCE, resolve(masteredUpTo(SkillIds.M9_MODE_ID_CADENCE)))
    }

    @Test
    fun `MIXED_MODE waits for all three of its gates, not just the one above it in the list`() {
        // The reason the resolver checks alsoRequires rather than position: MIXED_MODE sits after the
        // minor chain in the list, but its gates are M2.FULL_DIATONIC, M10.MIN_NATURAL and
        // M9.MODE_ID_CADENCE. Position alone would have opened it on the wrong evidence.
        val node = SkillGraph.node(SkillIds.M10_MIXED_MODE)
        assertEquals(
            setOf(SkillIds.M2_FULL_DIATONIC, SkillIds.M10_MIN_NATURAL, SkillIds.M9_MODE_ID_CADENCE),
            SkillGraph.gatesFor(node).toSet(),
        )

        // Everything before it mastered *except* natural minor: it must not open.
        val allButMinNatural = masteredUpTo(SkillIds.M10_MIXED_MODE).filterNot { it == SkillIds.M10_MIN_NATURAL }
        assertTrue(
            resolve(allButMinNatural) != SkillIds.M10_MIXED_MODE,
            "MIXED_MODE opened without natural minor mastered",
        )
    }

    @Test
    fun `a learner who has finished everything still has something to practice`() {
        val everything = SkillGraph.practiceChain.map { it.id }
        assertEquals(SkillGraph.practiceChain.last().id, resolve(everything))
    }

    @Test
    fun `every gate names a node the chain actually contains`() {
        // What would have caught M9's dangling prerequisite the day it was written. A gate pointing at
        // something outside the chain is a node that can never open, and it fails silently.
        val inChain = SkillGraph.practiceChain.map { it.id }.toSet()
        for (node in SkillGraph.practiceChain) {
            for (gate in SkillGraph.gatesFor(node)) {
                assertTrue(
                    gate in inChain,
                    "${node.id.raw} is gated on ${gate.raw}, which is not in the practice chain - the " +
                        "node can never open",
                )
            }
        }
    }

    @Test
    fun `M11 is reachable even though its declared gate is an assessment`() {
        // A REAL BUG this test found. CHROM_SHARP4's declared prerequisite is M2.INDEPENDENCE_CHECK,
        // which docs/03-CURRICULUM.md §5.6 calls "a separate, non-blocking assessment" - never a node a
        // learner is routed to, so never mastered, so under literal gate-checking every M11 node was
        // permanently unreachable and the resolver skipped straight to M12. The practice loop had been
        // walking its chain by position and ignoring prerequisites, so M11 was reachable by accident;
        // checking gates properly is what exposed it. SkillGraph.gatesFor now resolves an independence
        // check to the node whose mastery fires it, which is what the prerequisite always meant.
        assertEquals(
            SkillIds.M2_INDEPENDENCE_CHECK,
            SkillGraph.node(SkillIds.M11_CHROM_SHARP4).prerequisite,
            "the declared prerequisite stays as the spec writes it - only its resolution is interpreted",
        )
        assertEquals(listOf(SkillIds.M2_FULL_DIATONIC), SkillGraph.gatesFor(SkillGraph.node(SkillIds.M11_CHROM_SHARP4)))
        assertEquals(SkillIds.M11_CHROM_SHARP4, resolve(masteredUpTo(SkillIds.M11_CHROM_SHARP4)))
    }

    @Test
    fun `every node has a scope, and the scopes match the modules that own them`() {
        for (skill in SkillIds.M9_NODES_IN_ORDER) {
            assertEquals(DifficultyAxis.Scope.MODE_ID, SkillGraph.scopeFor(skill))
            assertTrue(
                DifficultyAxis.axesFor(DifficultyAxis.Scope.MODE_ID).isEmpty(),
                "M9 has no difficulty axes - its three nodes are its progression (§3)",
            )
        }
        for (skill in SkillIds.M12_NODES_IN_ORDER) {
            assertEquals(DifficultyAxis.Scope.PREDICTION, SkillGraph.scopeFor(skill))
        }
        for (skill in listOf(SkillIds.M2_DEG_SET_1, SkillIds.M10_MIN_NATURAL, SkillIds.M10_MIXED_MODE)) {
            assertEquals(DifficultyAxis.Scope.RECOGNITION, SkillGraph.scopeFor(skill))
        }
    }

    @Test
    fun `the chain contains every node the graph knows, and nothing twice`() {
        val ids = SkillGraph.practiceChain.map { it.id }
        assertEquals(ids.size, ids.toSet().size, "a node appears in the chain more than once")
        assertEquals(
            SkillGraph.allNodes.map { it.id }.toSet(),
            ids.toSet(),
            "the chain and the graph disagree about which nodes exist",
        )
    }
}
