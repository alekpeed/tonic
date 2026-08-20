package com.tonic.core.model.state

import com.tonic.core.model.ids.SkillId
import com.tonic.core.model.ids.SkillIds
import com.tonic.core.model.items.DifficultyAxis
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.put
import kotlinx.serialization.serializer
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * docs/09-BUILD-PLAN.md Stage 1 acceptance: "Serialization round-trips for
 * every @Serializable type, including unknown-field tolerance." Every type
 * that lands in one of the JSON columns from docs/05-DATA-MODEL.md §2 is
 * covered here, structurally (not just by hash — a hash test tells you it
 * broke, not how, per docs/10-TESTING.md §3).
 */
class SerializationRoundTripTest {
    // docs/05-DATA-MODEL.md §2: deserialization must tolerate unknown fields.
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `SkillId round trips`() {
        val original = SkillIds.M2_DEG_SET_2
        val encoded = json.encodeToString(original)
        assertEquals(original, json.decodeFromString<SkillId>(encoded))
    }

    @Test
    fun `axisLevelsJson shape - Map of DifficultyAxis to Int - round trips`() {
        val original: Map<DifficultyAxis, Int> = DifficultyAxis.entries.associateWith { it.maxLevel - 1 }
        val encoded = json.encodeToString(original)
        val decoded: Map<DifficultyAxis, Int> = json.decodeFromString(encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `StaircaseState round trips`() {
        val original =
            StaircaseState(
                level = 3,
                consecutiveCorrect = 1,
                reversals = listOf(4, 2, 5, 1),
                stepSize = 1,
                lastDirection = Direction.UP,
            )
        val encoded = json.encodeToString(original)
        assertEquals(original, json.decodeFromString<StaircaseState>(encoded))
    }

    @Test
    fun `StaircaseState with null lastDirection round trips`() {
        val original = StaircaseState(level = 0)
        val encoded = json.encodeToString(original)
        assertEquals(original, json.decodeFromString<StaircaseState>(encoded))
    }

    @Test
    fun `staircaseStateJson shape - Map of DifficultyAxis to StaircaseState - round trips`() {
        val original: Map<DifficultyAxis, StaircaseState> =
            mapOf(
                DifficultyAxis.CADENCE_FADE to StaircaseState(level = 4),
                DifficultyAxis.KEY_SPREAD to StaircaseState(level = 1, lastDirection = Direction.DOWN),
            )
        val encoded = json.encodeToString(original)
        val decoded: Map<DifficultyAxis, StaircaseState> = json.decodeFromString(encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `SessionPlan and PlannedSlot round trip`() {
        val original =
            SessionPlan(
                rootSeed = 123456789L,
                plannedSlots =
                    listOf(
                        PlannedSlot(SkillIds.M2_DEG_SET_1, mapOf(DifficultyAxis.CADENCE_FADE to 2), isWarmup = true),
                        PlannedSlot(SkillIds.M2_DEG_SET_1, mapOf(DifficultyAxis.CADENCE_FADE to 2), isReview = true),
                    ),
            )
        val encoded = json.encodeToString(original)
        assertEquals(original, json.decodeFromString<SessionPlan>(encoded))
    }

    @Test
    fun `ResumeState - resumeStateJson shape - round trips`() {
        val plan =
            SessionPlan(
                rootSeed = 42L,
                plannedSlots = listOf(PlannedSlot(SkillIds.M2_FULL_DIATONIC, emptyMap())),
            )
        val original = ResumeState(plan = plan, completedSlotIndex = 0)
        val encoded = json.encodeToString(original)
        assertEquals(original, json.decodeFromString<ResumeState>(encoded))
    }

    @Test
    fun `unknown fields are tolerated rather than crashing`() {
        val withExtraField =
            buildJsonObject {
                put("schemaVersion", 1)
                put("level", 3)
                put("consecutiveCorrect", 0)
                put("stepSize", 1)
                put("reversals", buildJsonArray {})
                // A field from a future schema version this code doesn't know about.
                put("aFieldFromTheFuture", "should be ignored, not crash")
            }
        val decoded = json.decodeFromJsonElement(StaircaseState.serializer(), withExtraField)
        assertEquals(3, decoded.level)
    }

    private inline fun <reified T> Json.decodeFromString(s: String): T = this.decodeFromString(serializer(), s)
}
