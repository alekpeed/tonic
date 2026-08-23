package com.tonic.core.data.db

import android.util.Log
import com.tonic.core.model.ids.SkillId
import com.tonic.core.model.items.DifficultyAxis
import com.tonic.core.model.state.ConfusionState
import com.tonic.core.model.state.ResumeState
import com.tonic.core.model.state.StaircaseState
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Encodes/decodes the JSON-blob columns described in docs/05-DATA-MODEL.md
 * §2. `ignoreUnknownKeys = true` plus an explicit newer-schema fallback
 * satisfy that section's tolerance rule: "deserialization must tolerate
 * unknown fields and must have an explicit fallback path if schemaVersion
 * is newer than the code understands: do not crash, do not silently
 * misinterpret - degrade to defaults and log."
 *
 * `DifficultyAxis` map keys are encoded via [DifficultyAxis.name] rather
 * than relying on kotlinx.serialization's enum-key-as-JSON-object-key
 * support directly, so the on-disk shape (`{"CADENCE_FADE": 5, ...}`) is
 * explicit and doesn't depend on a serialization-library version detail.
 */
internal object JsonCodec {
    private const val TAG = "TonicJsonCodec"

    private val json = Json { ignoreUnknownKeys = true }

    fun encodeAxisLevels(levels: Map<DifficultyAxis, Int>): String =
        json.encodeToString(
            MapSerializer(String.serializer(), Int.serializer()),
            levels.mapKeys { it.key.name },
        )

    fun decodeAxisLevels(raw: String): Map<DifficultyAxis, Int> =
        runCatching {
            json
                .decodeFromString(MapSerializer(String.serializer(), Int.serializer()), raw)
                .mapKeys { DifficultyAxis.valueOf(it.key) }
        }.getOrElse {
            Log.w(TAG, "Failed to decode axis levels JSON, defaulting to empty: $raw", it)
            emptyMap()
        }

    fun encodeStaircaseStates(states: Map<DifficultyAxis, StaircaseState>): String =
        json.encodeToString(
            MapSerializer(String.serializer(), StaircaseState.serializer()),
            states.mapKeys { it.key.name },
        )

    fun decodeStaircaseStates(raw: String): Map<DifficultyAxis, StaircaseState> =
        runCatching {
            json
                .decodeFromString(MapSerializer(String.serializer(), StaircaseState.serializer()), raw)
                .mapKeys { DifficultyAxis.valueOf(it.key) }
                .mapValues { (_, state) -> degradeStaircaseIfFutureSchema(state) }
        }.getOrElse {
            Log.w(TAG, "Failed to decode staircase state JSON, defaulting to empty: $raw", it)
            emptyMap()
        }

    private fun degradeStaircaseIfFutureSchema(state: StaircaseState): StaircaseState =
        if (state.schemaVersion > StaircaseState.CURRENT_SCHEMA_VERSION) {
            Log.w(
                TAG,
                "StaircaseState schemaVersion ${state.schemaVersion} is newer than this build " +
                    "understands (${StaircaseState.CURRENT_SCHEMA_VERSION}); keeping only its level.",
            )
            StaircaseState(level = state.level)
        } else {
            state
        }

    /**
     * The tap list and the per-event asynchronies of a rhythm attempt — docs/40-PHASE-4-SPEC.md §8.
     *
     * A JSON array of numbers, in the same column-of-text style every other list here uses. The
     * asynchrony list is *nullable per element*, because a missed event has no asynchrony and a zero
     * would read as a tap that landed exactly on it — the same distinction `sungCents` draws between
     * "not measured" and "measured at zero".
     */
    fun encodeDoubles(values: List<Double>): String = json.encodeToString(values)

    fun decodeDoubles(raw: String): List<Double> =
        runCatching { json.decodeFromString<List<Double>>(raw) }.getOrDefault(emptyList())

    fun encodeNullableDoubles(values: List<Double?>): String = json.encodeToString(values)

    fun decodeNullableDoubles(raw: String): List<Double?> =
        runCatching { json.decodeFromString<List<Double?>>(raw) }.getOrDefault(emptyList())

    /**
     * The Takadimi figure each expected event belongs to — docs/40-PHASE-4-SPEC.md §5.3 criterion 5.
     *
     * Strings rather than an enum ordinal, because a figure is not a closed set: it is whatever
     * syllables fill one beat, and `M3.COMPOUND` adds more of them at Stage 4.6. An ordinal would
     * renumber the day the set grew and silently relabel every stored attempt.
     */
    fun encodeStrings(values: List<String>): String = json.encodeToString(values)

    fun decodeStrings(raw: String): List<String> =
        runCatching { json.decodeFromString<List<String>>(raw) }.getOrDefault(emptyList())

    fun encodeResumeState(state: ResumeState): String = json.encodeToString(state)

    fun decodeResumeState(raw: String?): ResumeState? {
        if (raw == null) return null
        return runCatching { json.decodeFromString<ResumeState>(raw) }
            .map { degradeResumeIfFutureSchema(it) }
            .getOrElse {
                Log.w(TAG, "Failed to decode resume state JSON, discarding: $raw", it)
                null
            }
    }

    private fun degradeResumeIfFutureSchema(state: ResumeState): ResumeState? =
        if (state.schemaVersion > ResumeState.CURRENT_SCHEMA_VERSION) {
            Log.w(
                TAG,
                "ResumeState schemaVersion ${state.schemaVersion} is newer than this build " +
                    "understands (${ResumeState.CURRENT_SCHEMA_VERSION}); discarding rather than risk " +
                    "misinterpreting it - the session simply won't offer to resume.",
            )
            null
        } else {
            state
        }

    fun encodeConfusionState(state: ConfusionState): String =
        json.encodeToString(
            ConfusionStateJson(
                recentPairs = state.recentPairs.map { PairEntry(it.first, it.second) },
                allTimeCounts = state.allTimeCounts.map { (pair, count) -> CountEntry(pair.first, pair.second, count) },
            ),
        )

    fun decodeConfusionState(
        skillId: SkillId,
        raw: String,
    ): ConfusionState =
        runCatching {
            val dto = json.decodeFromString<ConfusionStateJson>(raw)
            ConfusionState(
                skillId = skillId,
                recentPairs = dto.recentPairs.map { it.target to it.response },
                allTimeCounts = dto.allTimeCounts.associate { (it.target to it.response) to it.count },
            )
        }.getOrElse {
            Log.w(TAG, "Failed to decode confusion state JSON, defaulting to empty: $raw", it)
            ConfusionState(skillId)
        }

    @Serializable
    private data class PairEntry(
        val target: String,
        val response: String,
    )

    @Serializable
    private data class CountEntry(
        val target: String,
        val response: String,
        val count: Int,
    )

    @Serializable
    private data class ConfusionStateJson(
        val recentPairs: List<PairEntry>,
        val allTimeCounts: List<CountEntry>,
    )
}
