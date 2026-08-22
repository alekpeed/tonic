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
    private val singingOn = AppSettings(sungResponseEnabled = true, sungResponseIntroSeen = false)

    /**
     * The ordering. Singing is not tied to a node, so this screen could surface anywhere — but a learner
     * who has not yet been told what the exercise *is* must not first be told how to answer it by voice.
     * `alreadyShown` is what sequences them, now that the module half has no persisted flag to fall
     * through: nothing shown yet means the module's screen is what is due.
     */
    @Test
    fun `the module explanation wins when nothing has been shown yet`() {
        assertEquals(IntroKind.M2, introKindFor(SkillIds.M2_DEG_SET_1, singingOn, alreadyShown = emptySet()))
    }

    @Test
    fun `the sung explanation follows once the module one is on screen`() {
        assertEquals(
            IntroKind.SUNG,
            introKindFor(SkillIds.M2_DEG_SET_1, singingOn, alreadyShown = setOf(IntroKind.M2)),
        )
    }

    /** Default off, so a learner who never opts in never sees it — §6.1. */
    @Test
    fun `it never appears while singing is switched off`() {
        val settings = AppSettings(sungResponseEnabled = false)
        assertEquals(
            IntroKind.NONE,
            introKindFor(SkillIds.M2_DEG_SET_1, settings, alreadyShown = setOf(IntroKind.M2)),
        )
    }

    /**
     * The sung screen keeps its persisted flag where the module screens lost theirs: it explains a way
     * of *answering* rather than a module, so "every time you enter it" has nothing to hang on.
     */
    @Test
    fun `it does not appear twice, across sessions`() {
        val settings = singingOn.copy(sungResponseIntroSeen = true)
        assertEquals(
            IntroKind.NONE,
            introKindFor(SkillIds.M2_DEG_SET_1, settings, alreadyShown = setOf(IntroKind.M2)),
        )
    }

    /** And not twice within one visit either, before its flag has been written. */
    @Test
    fun `it does not appear twice within a single visit`() {
        assertEquals(
            IntroKind.NONE,
            introKindFor(SkillIds.M2_DEG_SET_1, singingOn, setOf(IntroKind.M2, IntroKind.SUNG)),
        )
    }
}
