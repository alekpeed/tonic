package com.tonic.core.data.repository

import android.util.Log
import com.tonic.core.data.db.JsonCodec
import com.tonic.core.data.entity.AttemptEntity
import com.tonic.core.data.entity.ConfusionStateEntity
import com.tonic.core.data.entity.DiagnosticResultEntity
import com.tonic.core.data.entity.SessionEntity
import com.tonic.core.data.entity.SkillStateEntity
import com.tonic.core.model.attempts.Attempt
import com.tonic.core.model.attempts.InputMethod
import com.tonic.core.model.attempts.RhythmAttemptData
import com.tonic.core.model.ids.SkillId
import com.tonic.core.model.items.DifficultyAxis
import com.tonic.core.model.state.ConfusionState
import com.tonic.core.model.state.DiagnosticResult
import com.tonic.core.model.state.EntryPoint
import com.tonic.core.model.state.FsrsState
import com.tonic.core.model.state.MasteryState
import com.tonic.core.model.state.Session
import com.tonic.core.model.state.SkillState
import java.time.Instant

// Room entities are `internal` and never escape this module - docs/05-DATA-MODEL.md §5 /
// docs/04-ARCHITECTURE.md §3. Every mapper here is the one place an entity's shape meets a
// domain type's shape; keeping them together makes it easy to see the two stay in sync.

internal fun Attempt.toEntity(): AttemptEntity =
    AttemptEntity(
        id = id ?: 0,
        skillId = skillId.raw,
        sessionId = sessionId,
        itemSeed = itemSeed,
        axisLevelsJson = JsonCodec.encodeAxisLevels(axisLevels),
        targetLabel = targetLabel,
        responseLabel = responseLabel,
        correct = correct,
        latencyMs = latencyMs,
        replayCount = replayCount,
        keyPitchClass = keyPitchClass,
        targetMidi = targetMidi,
        timbreId = timbreId,
        cadenceFadeLevel = cadenceFadeLevel,
        timestamp = timestamp.toEpochMilli(),
        isWarmup = isWarmup,
        isAbandoned = isAbandoned,
        isIndependenceCheckProbe = isIndependenceCheckProbe,
        inputMethod = inputMethod.name,
        sungCents = sungCents,
        tapTimestampsMs = rhythm?.let { JsonCodec.encodeDoubles(it.tapTimesMs) },
        calibrationOffsetUsedMs = rhythm?.calibrationOffsetMs,
        toleranceUsedMs = rhythm?.toleranceHalfWidthMs,
        expectedEventTimesMs = rhythm?.let { JsonCodec.encodeDoubles(it.expectedEventTimesMs) },
        perEventFigures = rhythm?.let { JsonCodec.encodeStrings(it.perEventFigures) },
        perEventAsynchronyMs = rhythm?.let { JsonCodec.encodeNullableDoubles(it.perEventAsynchronyMs) },
        extraTaps = rhythm?.extraTaps,
        missedTaps = rhythm?.missedTaps,
    )

internal fun AttemptEntity.toDomain(): Attempt =
    Attempt(
        id = id,
        skillId = SkillId(skillId),
        sessionId = sessionId,
        itemSeed = itemSeed,
        axisLevels = JsonCodec.decodeAxisLevels(axisLevelsJson),
        targetLabel = targetLabel,
        responseLabel = responseLabel,
        correct = correct,
        latencyMs = latencyMs,
        replayCount = replayCount,
        keyPitchClass = keyPitchClass,
        targetMidi = targetMidi,
        timbreId = timbreId,
        cadenceFadeLevel = cadenceFadeLevel,
        timestamp = Instant.ofEpochMilli(timestamp),
        isWarmup = isWarmup,
        isAbandoned = isAbandoned,
        isIndependenceCheckProbe = isIndependenceCheckProbe,
        // An unrecognized value can only mean a row from a newer build read by an older one, which
        // this app has no path to produce. Falling back to TAP keeps the log readable rather than
        // throwing on a field nothing scores.
        inputMethod =
            runCatching { InputMethod.valueOf(inputMethod) }.getOrDefault(InputMethod.TAP),
        sungCents = sungCents,
        rhythm = rhythmOrNull(),
    )

/**
 * The rhythm half of a row, or null when the row has none or cannot be read as one.
 *
 * Keyed on the tap list being present rather than on all eight columns: they are written together or
 * not at all, so a row with taps and a missing scalar is a partial write rather than a pitch attempt,
 * and defaulting a scalar beats discarding the taps.
 *
 * The three per-event lists are different. They are only meaningful *aligned* — entry `i` of each
 * describes one expected event — so a row where they disagree about how many events there were is not
 * a row with a small gap in it, it is a row that cannot say which tap belongs to which beat. That is
 * discarded and logged rather than reconciled by padding, per docs/05-DATA-MODEL.md §2: "do not crash,
 * do not silently misinterpret." Reconciling would produce a plausible attempt describing a
 * performance nobody gave.
 */
private fun AttemptEntity.rhythmOrNull(): RhythmAttemptData? {
    val taps = tapTimestampsMs ?: return null
    val asynchronies = perEventAsynchronyMs?.let(JsonCodec::decodeNullableDoubles) ?: emptyList()
    val eventTimes = expectedEventTimesMs?.let(JsonCodec::decodeDoubles) ?: emptyList()
    val figures = perEventFigures?.let(JsonCodec::decodeStrings) ?: emptyList()
    if (eventTimes.size != asynchronies.size || figures.size != asynchronies.size) {
        Log.w(
            MAPPER_TAG,
            "Attempt $id has ${asynchronies.size} asynchronies, ${eventTimes.size} event times and " +
                "${figures.size} figures; they must line up, so its rhythm record is being dropped.",
        )
        return null
    }
    return RhythmAttemptData(
        tapTimesMs = JsonCodec.decodeDoubles(taps),
        calibrationOffsetMs = calibrationOffsetUsedMs ?: 0.0,
        toleranceHalfWidthMs = toleranceUsedMs ?: 0.0,
        expectedEventTimesMs = eventTimes,
        perEventFigures = figures,
        perEventAsynchronyMs = asynchronies,
        extraTaps = extraTaps ?: 0,
        // Derived, never read back from its column. The column stays because it is what a
        // human reading the exported log wants to see without counting nulls, but two sources
        // for one fact is one source too many - and the domain type asserts they agree.
        missedTaps = asynchronies.count { it == null },
    )
}

private const val MAPPER_TAG = "TonicMappers"

internal fun SkillState.toEntity(): SkillStateEntity =
    SkillStateEntity(
        skillId = skillId.raw,
        axisLevelsJson = JsonCodec.encodeAxisLevels(axisLevels),
        staircaseStateJson = JsonCodec.encodeStaircaseStates(staircaseStates),
        activeAxis = activeAxis?.name,
        masteryStatus = masteryState.name,
        masteredAt = masteredAt?.toEpochMilli(),
        fsrsStability = fsrs.stability,
        fsrsDifficulty = fsrs.difficulty,
        fsrsLastReview = fsrs.lastReview?.toEpochMilli(),
        fsrsDue = fsrs.due?.toEpochMilli(),
        fsrsReps = fsrs.reps,
        fsrsLapses = fsrs.lapses,
        totalAttempts = totalAttempts,
        updatedAt = updatedAt.toEpochMilli(),
    )

internal fun SkillStateEntity.toDomain(): SkillState =
    SkillState(
        skillId = SkillId(skillId),
        axisLevels = JsonCodec.decodeAxisLevels(axisLevelsJson),
        staircaseStates = JsonCodec.decodeStaircaseStates(staircaseStateJson),
        activeAxis = activeAxis?.let { DifficultyAxis.valueOf(it) },
        masteryState = MasteryState.valueOf(masteryStatus),
        masteredAt = masteredAt?.let { Instant.ofEpochMilli(it) },
        fsrs =
            FsrsState(
                stability = fsrsStability,
                difficulty = fsrsDifficulty,
                lastReview = fsrsLastReview?.let { Instant.ofEpochMilli(it) },
                due = fsrsDue?.let { Instant.ofEpochMilli(it) },
                reps = fsrsReps,
                lapses = fsrsLapses,
            ),
        totalAttempts = totalAttempts,
        updatedAt = Instant.ofEpochMilli(updatedAt),
    )

internal fun ConfusionState.toEntity(): ConfusionStateEntity =
    ConfusionStateEntity(
        skillId = skillId.raw,
        stateJson = JsonCodec.encodeConfusionState(this),
        updatedAt = updatedAt.toEpochMilli(),
    )

internal fun ConfusionStateEntity.toDomain(): ConfusionState =
    JsonCodec.decodeConfusionState(SkillId(skillId), stateJson)

internal fun DiagnosticResultEntity.toDomain(): DiagnosticResult =
    DiagnosticResult(
        pitchDirectionThresholdCents = pitchDirectionThresholdCents,
        discriminationDPrime = discriminationDPrime,
        tonalMemorySpan = tonalMemorySpan,
        amusiaIndicatorFlag = amusiaIndicatorFlag,
        recommendedEntry = EntryPoint.valueOf(recommendedEntry),
        initialAxisLevels = JsonCodec.decodeAxisLevels(initialAxisLevelsJson),
        completedAt = Instant.ofEpochMilli(completedAt),
        seed = seed,
    )

internal fun DiagnosticResult.toEntity(): DiagnosticResultEntity =
    DiagnosticResultEntity(
        pitchDirectionThresholdCents = pitchDirectionThresholdCents,
        discriminationDPrime = discriminationDPrime,
        tonalMemorySpan = tonalMemorySpan,
        amusiaIndicatorFlag = amusiaIndicatorFlag,
        recommendedEntry = recommendedEntry.name,
        initialAxisLevelsJson = JsonCodec.encodeAxisLevels(initialAxisLevels),
        seed = seed,
        completedAt = completedAt.toEpochMilli(),
    )

internal fun Session.toEntity(): SessionEntity =
    SessionEntity(
        id = id ?: 0,
        startedAt = startedAt.toEpochMilli(),
        endedAt = endedAt?.toEpochMilli(),
        plannedItemCount = plannedItemCount,
        completedItemCount = completedItemCount,
        rootSeed = rootSeed,
        resumeStateJson = resumeState?.let { JsonCodec.encodeResumeState(it) },
    )

internal fun SessionEntity.toDomain(): Session =
    Session(
        id = id,
        startedAt = Instant.ofEpochMilli(startedAt),
        endedAt = endedAt?.let { Instant.ofEpochMilli(it) },
        plannedItemCount = plannedItemCount,
        completedItemCount = completedItemCount,
        rootSeed = rootSeed,
        resumeState = JsonCodec.decodeResumeState(resumeStateJson),
    )
