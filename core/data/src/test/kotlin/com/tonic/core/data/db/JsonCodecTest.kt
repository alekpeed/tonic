package com.tonic.core.data.db

import com.tonic.core.model.ids.SkillIds
import com.tonic.core.model.items.DifficultyAxis
import com.tonic.core.model.state.ConfusionState
import com.tonic.core.model.state.PlannedSlot
import com.tonic.core.model.state.ResumeState
import com.tonic.core.model.state.SessionPlan
import com.tonic.core.model.state.StaircaseState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * docs/05-DATA-MODEL.md §2: "deserialization must tolerate unknown fields
 * ... and must have an explicit fallback path if schemaVersion is newer
 * than the code understands: do not crash, do not silently misinterpret -
 * degrade to defaults and log."
 */
class JsonCodecTest {
    @Test
    fun `axis levels round-trip through JSON exactly`() {
        val levels = mapOf(DifficultyAxis.CADENCE_FADE to 5, DifficultyAxis.KEY_SPREAD to 1)
        val decoded = JsonCodec.decodeAxisLevels(JsonCodec.encodeAxisLevels(levels))
        assertEquals(levels, decoded)
    }

    @Test
    fun `malformed axis levels JSON degrades to empty rather than crashing`() {
        assertEquals(emptyMap(), JsonCodec.decodeAxisLevels("not valid json at all"))
    }

    @Test
    fun `staircase states round-trip through JSON exactly, including reversal history`() {
        val states =
            mapOf(
                DifficultyAxis.CADENCE_FADE to
                    StaircaseState(level = 4, reversals = listOf(3, 5, 3, 5, 3, 5), stepSize = 1),
            )
        val decoded = JsonCodec.decodeStaircaseStates(JsonCodec.encodeStaircaseStates(states))
        assertEquals(states, decoded)
    }

    @Test
    fun `an unknown extra field in staircase state JSON is tolerated, not a crash`() {
        val raw =
            """{"CADENCE_FADE":{"level":3,"consecutiveCorrect":0,"reversals":[],"stepSize":2,"aFieldFromTheFuture":true}}"""
        val decoded = JsonCodec.decodeStaircaseStates(raw)
        assertEquals(3, decoded.getValue(DifficultyAxis.CADENCE_FADE).level)
    }

    @Test
    fun `a staircase state from a newer schema version degrades to just its level, not a crash or a misread`() {
        val raw =
            """{"CADENCE_FADE":{"schemaVersion":99,"level":6,"consecutiveCorrect":0,"reversals":[1,2,3,4,5,6],"stepSize":1}}"""
        val decoded = JsonCodec.decodeStaircaseStates(raw)
        val state = decoded.getValue(DifficultyAxis.CADENCE_FADE)
        assertEquals(6, state.level, "the level is still trustworthy - it's not opaque to future schema changes")
        assertTrue(
            state.reversals.isEmpty(),
            "reversal history from an unknown-shaped future schema must not be trusted",
        )
    }

    @Test
    fun `resume state round-trips through JSON exactly`() {
        val state =
            ResumeState(
                plan =
                    SessionPlan(
                        rootSeed = 42L,
                        plannedSlots =
                            listOf(
                                PlannedSlot(SkillIds.M2_DEG_SET_1, mapOf(DifficultyAxis.CADENCE_FADE to 2)),
                            ),
                    ),
                completedSlotIndex = 5,
            )
        val decoded = JsonCodec.decodeResumeState(JsonCodec.encodeResumeState(state))
        assertEquals(state, decoded)
    }

    @Test
    fun `a null resume state column decodes to null, not an exception`() {
        assertNull(JsonCodec.decodeResumeState(null))
    }

    @Test
    fun `a resume state from a newer schema version is discarded entirely rather than risk a bad resume`() {
        val raw = """{"schemaVersion":99,"plan":{"rootSeed":1,"plannedSlots":[]},"completedSlotIndex":0}"""
        assertNull(JsonCodec.decodeResumeState(raw))
    }

    @Test
    fun `confusion state round-trips through JSON exactly, including the recent-pairs window`() {
        val state =
            ConfusionState(
                skillId = SkillIds.M2_FULL_DIATONIC,
                recentPairs = listOf("1" to "1", "4" to "1", "1" to "1"),
                allTimeCounts = mapOf(("1" to "1") to 2, ("4" to "1") to 1),
            )
        val decoded = JsonCodec.decodeConfusionState(state.skillId, JsonCodec.encodeConfusionState(state))
        assertEquals(state, decoded)
    }

    @Test
    fun `malformed confusion state JSON degrades to a fresh empty state rather than crashing`() {
        val decoded = JsonCodec.decodeConfusionState(SkillIds.M2_FULL_DIATONIC, "{ this is not json")
        assertEquals(ConfusionState(SkillIds.M2_FULL_DIATONIC), decoded)
    }
}
