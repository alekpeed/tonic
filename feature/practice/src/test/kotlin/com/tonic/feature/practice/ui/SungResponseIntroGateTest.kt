package com.tonic.feature.practice.ui

import com.tonic.core.model.ids.SkillIds
import com.tonic.core.model.state.AppSettings
import org.junit.Test
import kotlin.test.assertEquals

/**
 * When the sung-response explanation is due — [introKindFor]'s gate, docs/30-PHASE-3-SPEC.md §6.1–6.2.
 *
 * Deliberately a separate class from [SungResponseIntroTest], with no Compose rule. These four assert a
 * pure function over two arguments and need no composition at all, and holding a `createComposeRule`
 * over tests that never call `setContent` is what appears to have disturbed `Dispatchers.Main` for the
 * view-model tests sharing this JVM: run #14 had this module's three `Dispatchers.setMain` tests fail
 * inside `TestMainDispatcher` while the total test count was unchanged. Keeping the rule to tests that
 * actually compose something removes that entanglement and is the better structure regardless.
 */
class SungResponseIntroGateTest {
    /**
     * The gate. Singing is not tied to a node, so this screen could surface anywhere — but a learner who
     * has not yet been told what the exercise *is* must not first be told how to answer it by voice.
     */
    @Test
    fun `the module explanation wins when both are unseen`() {
        val settings =
            AppSettings(
                module2IntroSeen = false,
                sungResponseEnabled = true,
                sungResponseIntroSeen = false,
            )
        assertEquals(IntroKind.M2, introKindFor(SkillIds.M2_DEG_SET_1, settings))
    }

    @Test
    fun `the sung explanation appears once the module one is done and singing is on`() {
        val settings =
            AppSettings(
                module2IntroSeen = true,
                sungResponseEnabled = true,
                sungResponseIntroSeen = false,
            )
        assertEquals(IntroKind.SUNG, introKindFor(SkillIds.M2_DEG_SET_1, settings))
    }

    /** Default off, so a learner who never opts in never sees it — §6.1. */
    @Test
    fun `it never appears while singing is switched off`() {
        val settings = AppSettings(module2IntroSeen = true, sungResponseEnabled = false)
        assertEquals(IntroKind.NONE, introKindFor(SkillIds.M2_DEG_SET_1, settings))
    }

    @Test
    fun `it does not appear twice`() {
        val settings =
            AppSettings(
                module2IntroSeen = true,
                sungResponseEnabled = true,
                sungResponseIntroSeen = true,
            )
        assertEquals(IntroKind.NONE, introKindFor(SkillIds.M2_DEG_SET_1, settings))
    }
}
