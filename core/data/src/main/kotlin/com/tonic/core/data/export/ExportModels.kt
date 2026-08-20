package com.tonic.core.data.export

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The on-disk shape of a data export — docs/20-PHASE-2-SPEC.md §6.
 *
 * Deliberately its own set of types rather than the domain models or the Room entities. An exported
 * file is a long-lived artifact that leaves the device and outlives any given build, so its format is a
 * published contract: renaming an internal field must not silently rewrite what a user's saved export
 * looks like, and the compiler cannot warn about that if the two are the same class. The DTOs below are
 * flat and boring on purpose — a person opening the JSON in a text editor is a supported use.
 *
 * `@SerialName` is set explicitly on every field for the same reason: the wire name is chosen, not
 * inherited from whatever the Kotlin property happens to be called this month.
 */
@Serializable
data class TonicExport(
    /**
     * Bumped whenever the *meaning* of a field changes or a field is removed — never for a purely
     * additive change, which older readers tolerate by ignoring what they don't know.
     */
    @SerialName("schema_version") val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    @SerialName("exported_at_epoch_ms") val exportedAtEpochMs: Long,
    @SerialName("app_note") val appNote: String = EXPORT_NOTE,
    @SerialName("attempts") val attempts: List<ExportedAttempt>,
    @SerialName("sessions") val sessions: List<ExportedSession>,
    @SerialName("skill_states") val skillStates: List<ExportedSkillState>,
    @SerialName("confusion_states") val confusionStates: List<ExportedConfusionState>,
    @SerialName("diagnostic_results") val diagnosticResults: List<ExportedDiagnosticResult>,
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1

        /**
         * Written into every file. The attempt log is the source of truth (docs/05-DATA-MODEL.md §1),
         * so this states the one thing a reader most needs to know about what they are holding.
         */
        const val EXPORT_NOTE =
            "Tonic practice data. The attempts list is the source of truth - every other section is " +
                "derived from it and can be rebuilt. Times are milliseconds since the Unix epoch, UTC."
    }
}

/** One answered, skipped, or abandoned item. */
@Serializable
data class ExportedAttempt(
    @SerialName("id") val id: Long,
    @SerialName("skill_id") val skillId: String,
    @SerialName("session_id") val sessionId: Long,
    @SerialName("item_seed") val itemSeed: Long,
    @SerialName("axis_levels") val axisLevelsJson: String,
    @SerialName("target_label") val targetLabel: String,
    /** Null when the item was skipped or abandoned rather than answered. */
    @SerialName("response_label") val responseLabel: String?,
    @SerialName("correct") val correct: Boolean,
    @SerialName("latency_ms") val latencyMs: Long,
    @SerialName("replay_count") val replayCount: Int,
    @SerialName("key_pitch_class") val keyPitchClass: Int,
    @SerialName("target_midi") val targetMidi: Int,
    @SerialName("timbre_id") val timbreId: String,
    @SerialName("cadence_fade_level") val cadenceFadeLevel: Int,
    @SerialName("timestamp_epoch_ms") val timestampEpochMs: Long,
    @SerialName("is_warmup") val isWarmup: Boolean,
    @SerialName("is_abandoned") val isAbandoned: Boolean,
    @SerialName("is_independence_check_probe") val isIndependenceCheckProbe: Boolean,
)

@Serializable
data class ExportedSession(
    @SerialName("id") val id: Long,
    @SerialName("started_at_epoch_ms") val startedAtEpochMs: Long,
    @SerialName("ended_at_epoch_ms") val endedAtEpochMs: Long?,
    @SerialName("planned_item_count") val plannedItemCount: Int,
    @SerialName("completed_item_count") val completedItemCount: Int,
    @SerialName("root_seed") val rootSeed: Long,
    /**
     * Deliberately omitted from export even though the column exists: it is a mid-session scratchpad
     * for resuming an interrupted run, not progress, and it is cleared the moment a session completes.
     * Nothing about a user's history is lost by leaving it out.
     */
    @SerialName("was_resumable_at_export") val wasResumableAtExport: Boolean,
)

@Serializable
data class ExportedSkillState(
    @SerialName("skill_id") val skillId: String,
    @SerialName("axis_levels") val axisLevelsJson: String,
    @SerialName("staircase_state") val staircaseStateJson: String,
    @SerialName("active_axis") val activeAxis: String?,
    @SerialName("mastery_status") val masteryStatus: String,
    @SerialName("mastered_at_epoch_ms") val masteredAtEpochMs: Long?,
    @SerialName("fsrs_stability") val fsrsStability: Double,
    @SerialName("fsrs_difficulty") val fsrsDifficulty: Double,
    @SerialName("fsrs_last_review_epoch_ms") val fsrsLastReviewEpochMs: Long?,
    @SerialName("fsrs_due_epoch_ms") val fsrsDueEpochMs: Long?,
    @SerialName("fsrs_reps") val fsrsReps: Int,
    @SerialName("fsrs_lapses") val fsrsLapses: Int,
    @SerialName("total_attempts") val totalAttempts: Int,
    @SerialName("updated_at_epoch_ms") val updatedAtEpochMs: Long,
)

@Serializable
data class ExportedConfusionState(
    @SerialName("skill_id") val skillId: String,
    @SerialName("state") val stateJson: String,
    @SerialName("updated_at_epoch_ms") val updatedAtEpochMs: Long,
)

/**
 * A completed diagnostic run.
 *
 * **`amusiaIndicatorFlag` is deliberately absent, permanently** — docs/20-PHASE-2-SPEC.md §6. It is
 * internal routing state that sends a user down a longer discrimination-training path and nothing else;
 * docs/02-PEDAGOGY.md §8 forbids ever surfacing it evaluatively, and a shareable file with a column
 * legible as "amusia" is exactly that, whatever the value happens to be. There is no upside to
 * including it: a user inspecting their own progress learns nothing from it, and the app re-derives its
 * routing from the measurements that *are* here. Adding it back is a pedagogy-safety regression, not a
 * feature.
 */
@Serializable
data class ExportedDiagnosticResult(
    @SerialName("id") val id: Long,
    @SerialName("pitch_direction_threshold_cents") val pitchDirectionThresholdCents: Int,
    @SerialName("discrimination_d_prime") val discriminationDPrime: Double,
    @SerialName("tonal_memory_span") val tonalMemorySpan: Int,
    @SerialName("recommended_entry") val recommendedEntry: String,
    @SerialName("initial_axis_levels") val initialAxisLevelsJson: String,
    @SerialName("seed") val seed: Long,
    @SerialName("completed_at_epoch_ms") val completedAtEpochMs: Long,
)
