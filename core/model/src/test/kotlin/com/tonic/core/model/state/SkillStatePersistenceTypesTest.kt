package com.tonic.core.model.state

import com.tonic.core.model.ids.SkillIds
import com.tonic.core.model.items.DifficultyAxis
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The `:core:data`-facing types added for docs/09-BUILD-PLAN.md Stage 5. */
class SkillStatePersistenceTypesTest {
    @Test
    fun `SkillState initial is LOCKED, has every axis at 0, and no history`() {
        val state = SkillState.initial(SkillIds.M2_DEG_SET_1)
        assertEquals(MasteryState.LOCKED, state.masteryState)
        // Changed in Phase 2 Stage 2.0. Old: every axis at 0. New: every *recognition* axis at 0, which
        // for M2_DEG_SET_1 is the same six axes and the same map Phase 1 produced - byte-identical.
        // Reason: SkillState.initial is now scope-aware so an M12 node carries its two prediction axes
        // instead of six recognition axes it can never move (docs/20-PHASE-2-SPEC.md §4 change 3).
        assertEquals(DifficultyAxis.RECOGNITION_AXES.associateWith { 0 }, state.axisLevels)
        assertTrue(state.staircaseStates.isEmpty())
        assertNull(state.activeAxis)
        assertNull(state.masteredAt)
        assertEquals(0, state.totalAttempts)
        assertEquals(Instant.EPOCH, state.updatedAt)
    }

    @Test
    fun `Session defaults to no id and no resume state, and copy() updates just what changes`() {
        val session =
            Session(
                startedAt = Instant.EPOCH,
                endedAt = null,
                plannedItemCount = 20,
                completedItemCount = 0,
                rootSeed = 5L,
                resumeState = null,
            )
        assertNull(session.id)
        assertNull(session.resumeState)

        val persisted = session.copy(id = 1L)
        assertEquals(1L, persisted.id)
        assertEquals(session.rootSeed, persisted.rootSeed, "copy() must not disturb the other fields")
    }

    @Test
    fun `AppSettings defaults match docs 05-DATA-MODEL 3 exactly`() {
        val defaults = AppSettings()
        assertEquals(LabelStyle.NUMBERS, defaults.labelStyle)
        assertEquals(440.0f, defaults.referenceA4Hz)
        assertEquals(5, defaults.sessionLengthMinutes)
        assertTrue(defaults.hapticsEnabled)
        assertTrue(defaults.soundEffectsEnabled)
        assertEquals(ThemeMode.SYSTEM, defaults.themeMode)
        assertEquals(false, defaults.reduceMotion)
        assertEquals(false, defaults.onboardingCompleted)
        assertEquals(false, defaults.diagnosticCompleted)
        assertEquals(false, defaults.dailyReminderEnabled, "opt-in only")
        assertNull(defaults.dailyReminderTime)
    }

    @Test
    fun `ConfusionState defaults to an empty window and count map for a given skill`() {
        val state = ConfusionState(SkillIds.M2_FULL_DIATONIC)
        assertEquals(SkillIds.M2_FULL_DIATONIC, state.skillId)
        assertTrue(state.recentPairs.isEmpty())
        assertTrue(state.allTimeCounts.isEmpty())
        assertEquals(Instant.EPOCH, state.updatedAt)
    }
}
