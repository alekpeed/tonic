package com.tonic.core.data.export

import com.tonic.core.data.dao.AttemptDao
import com.tonic.core.data.dao.ConfusionStateDao
import com.tonic.core.data.dao.DiagnosticResultDao
import com.tonic.core.data.dao.SessionDao
import com.tonic.core.data.dao.SkillStateDao
import com.tonic.core.model.time.Clock
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class DataExportRepositoryImpl
    @Inject
    constructor(
        private val attemptDao: AttemptDao,
        private val sessionDao: SessionDao,
        private val skillStateDao: SkillStateDao,
        private val confusionStateDao: ConfusionStateDao,
        private val diagnosticResultDao: DiagnosticResultDao,
        private val clock: Clock,
    ) : DataExportRepository {
        override suspend fun buildExport(): TonicExport =
            TonicExport(
                exportedAtEpochMs = clock.now().toEpochMilli(),
                attempts =
                    attemptDao.allAttempts().map { e ->
                        ExportedAttempt(
                            id = e.id,
                            skillId = e.skillId,
                            sessionId = e.sessionId,
                            itemSeed = e.itemSeed,
                            axisLevelsJson = e.axisLevelsJson,
                            targetLabel = e.targetLabel,
                            responseLabel = e.responseLabel,
                            correct = e.correct,
                            latencyMs = e.latencyMs,
                            replayCount = e.replayCount,
                            keyPitchClass = e.keyPitchClass,
                            targetMidi = e.targetMidi,
                            timbreId = e.timbreId,
                            cadenceFadeLevel = e.cadenceFadeLevel,
                            timestampEpochMs = e.timestamp,
                            isWarmup = e.isWarmup,
                            isAbandoned = e.isAbandoned,
                            isIndependenceCheckProbe = e.isIndependenceCheckProbe,
                        )
                    },
                sessions =
                    sessionDao.allSessions().map { e ->
                        ExportedSession(
                            id = e.id,
                            startedAtEpochMs = e.startedAt,
                            endedAtEpochMs = e.endedAt,
                            plannedItemCount = e.plannedItemCount,
                            completedItemCount = e.completedItemCount,
                            rootSeed = e.rootSeed,
                            // The scratchpad itself is not exported - see ExportedSession's KDoc - but
                            // whether one was open is a fact about the session worth keeping.
                            wasResumableAtExport = e.resumeStateJson != null,
                        )
                    },
                skillStates =
                    skillStateDao.allSkillStates().map { e ->
                        ExportedSkillState(
                            skillId = e.skillId,
                            axisLevelsJson = e.axisLevelsJson,
                            staircaseStateJson = e.staircaseStateJson,
                            activeAxis = e.activeAxis,
                            masteryStatus = e.masteryStatus,
                            masteredAtEpochMs = e.masteredAt,
                            fsrsStability = e.fsrsStability,
                            fsrsDifficulty = e.fsrsDifficulty,
                            fsrsLastReviewEpochMs = e.fsrsLastReview,
                            fsrsDueEpochMs = e.fsrsDue,
                            fsrsReps = e.fsrsReps,
                            fsrsLapses = e.fsrsLapses,
                            totalAttempts = e.totalAttempts,
                            updatedAtEpochMs = e.updatedAt,
                        )
                    },
                confusionStates =
                    confusionStateDao.allConfusionStates().map { e ->
                        ExportedConfusionState(
                            skillId = e.skillId,
                            stateJson = e.stateJson,
                            updatedAtEpochMs = e.updatedAt,
                        )
                    },
                diagnosticResults =
                    diagnosticResultDao.allResults().map { e ->
                        // amusiaIndicatorFlag is read here and deliberately never carried across - see
                        // ExportedDiagnosticResult's KDoc. The type has no field for it, so this is
                        // enforced by the compiler rather than by remembering.
                        ExportedDiagnosticResult(
                            id = e.id,
                            pitchDirectionThresholdCents = e.pitchDirectionThresholdCents,
                            discriminationDPrime = e.discriminationDPrime,
                            tonalMemorySpan = e.tonalMemorySpan,
                            recommendedEntry = e.recommendedEntry,
                            initialAxisLevelsJson = e.initialAxisLevelsJson,
                            seed = e.seed,
                            completedAtEpochMs = e.completedAt,
                        )
                    },
            )

        override suspend fun exportToJson(): String = EXPORT_JSON.encodeToString(buildExport())

        override suspend fun suggestedFileName(): String = "tonic-export-${clock.now().toEpochMilli()}.json"

        private companion object {
            /**
             * `prettyPrint` because the file is meant to be readable by the person who owns it, and
             * `encodeDefaults` so a reader never has to know what a default was — an exported record
             * states every field it has.
             */
            val EXPORT_JSON =
                Json {
                    prettyPrint = true
                    encodeDefaults = true
                }
        }
    }
