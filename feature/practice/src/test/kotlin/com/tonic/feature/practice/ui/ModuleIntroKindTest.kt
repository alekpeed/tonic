package com.tonic.feature.practice.ui

import com.tonic.core.curriculum.graph.SkillGraph
import com.tonic.core.model.ids.SkillIds
import com.tonic.core.model.state.AppSettings
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * [moduleIntroKindFor] — the node-to-explanation mapping the help affordance recalls, and the module
 * half of [introKindFor]'s first-encounter gate.
 *
 * Pure-function tests with no Compose rule, for the same reason [SungResponseIntroGateTest] has none.
 * Exists because of a live-use report: recall on an `M12` item produced the *major* explanation,
 * because [PracticeViewModel.onOpenIntro] reused whatever kind the session had opened with instead of
 * asking which node was on screen. From the learner's side that read as `M12`'s instruction screen
 * having been deleted. These pin the mapping the fixed recall path computes.
 */
class ModuleIntroKindTest {
    /** Every node covered, not just one per module — walking [SkillGraph.allNodes] leaves no gap to regress into. */
    @Test
    fun `every node in the graph maps to its own module's explanation`() {
        SkillGraph.allNodes.forEach { node ->
            val expected =
                when {
                    node.id == SkillIds.M10_MIXED_MODE -> IntroKind.MIXED_MODE
                    node.id.raw.startsWith("M12.") -> IntroKind.M12
                    node.id.raw.startsWith("M11.") -> IntroKind.M11
                    node.id.raw.startsWith("M10.") -> IntroKind.M10
                    node.id.raw.startsWith("M9.") -> IntroKind.M9
                    else -> IntroKind.M2
                }
            assertEquals(expected, moduleIntroKindFor(node.id), "wrong explanation for ${node.id.raw}")
            assertNotEquals(IntroKind.NONE, moduleIntroKindFor(node.id), "recall must never be a dead button")
        }
    }

    /**
     * The first-encounter gate for the screen this fix un-orphaned: `M9IntroContent` had existed since
     * Phase 2 Stage 2.2 with nothing dispatching it, so an `M9` node's first item arrived with either
     * the major explanation or none.
     */
    @Test
    fun `a mode-identification node owes the mode explanation until it has been seen`() {
        val unseen = AppSettings(module2IntroSeen = true, module9IntroSeen = false)
        assertEquals(IntroKind.M9, introKindFor(SkillIds.M9_MODE_ID_CADENCE, unseen))

        val seen = unseen.copy(module9IntroSeen = true)
        assertEquals(IntroKind.NONE, introKindFor(SkillIds.M9_MODE_ID_CADENCE, seen))
    }

    /**
     * The mid-session half of the reported bug, at the gate level: an `M12` node owes its explanation
     * regardless of which module's screen the session opened with — the major one having been seen
     * must not swallow it.
     */
    @Test
    fun `an audiation node owes its explanation even when the major one is long seen`() {
        val settings = AppSettings(module2IntroSeen = true, module12IntroSeen = false)
        assertEquals(IntroKind.M12, introKindFor(SkillIds.M12_PREDICT_TRIAD, settings))
    }
}
