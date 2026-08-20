package com.tonic.feature.progress.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tonic.core.curriculum.graph.SkillGraph
import com.tonic.core.data.repository.AttemptRepository
import com.tonic.core.data.repository.ConfusionRepository
import com.tonic.core.data.repository.SkillStateRepository
import com.tonic.core.data.settings.SettingsRepository
import com.tonic.core.engine.confusion.ConfusionTracker
import com.tonic.core.engine.mastery.IndependenceCheck
import com.tonic.core.engine.mastery.MasteryEvaluator
import com.tonic.core.model.attempts.Attempt
import com.tonic.core.model.ids.SkillId
import com.tonic.core.model.ids.SkillIds
import com.tonic.core.model.music.ScaleDegree
import com.tonic.core.model.state.MasteryState
import com.tonic.core.model.state.SkillState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * docs/04-ARCHITECTURE.md §3: thin by design. Every number shown here is recomputed from
 * [SkillStateRepository]/[AttemptRepository]/[ConfusionRepository] using the same pure `:core:engine`
 * algorithms the practice loop uses live ([MasteryEvaluator], [ConfusionTracker], [IndependenceCheck]) -
 * this screen has no pedagogical logic of its own, only presentation.
 */
@HiltViewModel
class ProgressViewModel
    @Inject
    constructor(
        private val skillStateRepository: SkillStateRepository,
        private val attemptRepository: AttemptRepository,
        private val confusionRepository: ConfusionRepository,
        private val settingsRepository: SettingsRepository,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(ProgressUiState())
        val uiState: StateFlow<ProgressUiState> = _uiState.asStateFlow()

        private var started = false

        /** Idempotent - a rotation re-collecting this ViewModel must not reload (and re-flicker) the screen. */
        fun loadIfNeeded() {
            if (started) return
            started = true
            viewModelScope.launch {
                settingsRepository.settings.collect { settings ->
                    _uiState.update { it.copy(labelStyle = settings.labelStyle) }
                }
            }
            viewModelScope.launch { load() }
        }

        /** Expands or collapses one mastery-map node's blocking-criterion detail. */
        fun onNodeToggled(skillId: SkillId) {
            _uiState.update { it.copy(expandedNode = if (it.expandedNode == skillId) null else skillId) }
        }

        private suspend fun load() {
            val states = skillStateRepository.observeAll().first()
            val masteryMap = SkillGraph.m2Nodes.map { node -> masteryMapNodeFor(node.id, states) }

            // "Per-degree accuracy" / "confusion view" are scoped to whichever node the user is actually
            // working on - the same "current node" resolution `:feature:practice`'s ViewModel uses
            // (docs/08-UI-SPEC.md §2/§6 both describe a single, current picture, not one per node).
            val currentNodeId =
                SkillGraph.m2Nodes.firstOrNull { states[it.id]?.masteryState != MasteryState.MASTERED }?.id
                    ?: SkillGraph.m2Nodes.last().id
            val matrix = confusionRepository.matrixFor(currentNodeId)
            val activeDegrees = SkillGraph.activeDegreesFor(currentNodeId).sortedBy { it.degree }
            val degreeAccuracy =
                activeDegrees.map { degree -> DegreeAccuracy(degree, matrix.accuracyFor(degree.canonicalLabel)) }
            val confusionStatements =
                ConfusionTracker.confusionPairs(matrix).map { cell ->
                    ConfusionStatement(ScaleDegree(cell.target.toInt()), ScaleDegree(cell.response.toInt()))
                }

            val independenceCheck = loadIndependenceCheck()

            _uiState.update {
                it.copy(
                    isLoading = false,
                    masteryMap = masteryMap,
                    degreeAccuracy = degreeAccuracy,
                    confusionStatements = confusionStatements,
                    independenceCheck = independenceCheck,
                )
            }
        }

        private suspend fun masteryMapNodeFor(
            skillId: SkillId,
            states: Map<SkillId, SkillState>,
        ): MasteryMapNode {
            val state = states[skillId] ?: SkillState.initial(skillId)
            val activeDegrees = SkillGraph.activeDegreesFor(skillId).sortedBy { it.degree }
            val verdict =
                when (state.masteryState) {
                    MasteryState.LOCKED, MasteryState.MASTERED -> null
                    MasteryState.AVAILABLE, MasteryState.IN_PROGRESS -> {
                        val window = masteryWindowFor(skillId)
                        MasteryEvaluator.evaluate(
                            window,
                            SkillGraph.activeDegreesFor(skillId),
                            state.axisLevels,
                            // The same six criteria the replayer applies, so the progress screen shows
                            // what is actually blocking rather than a subset of it.
                            focusDegree = SkillGraph.focusDegreeFor(skillId),
                        )
                    }
                }
            return MasteryMapNode(skillId, activeDegrees, state.masteryState, verdict)
        }

        /**
         * The same window [com.tonic.core.engine.replay.SkillStateReducer] accumulates live: the most
         * recent (up to) [MasteryEvaluator.WINDOW_SIZE] genuinely-answered, non-warm-up, non-independence-
         * check attempts, evaluated against the skill's *current* axis levels.
         */
        private suspend fun masteryWindowFor(skillId: SkillId): List<Attempt> =
            attemptRepository
                .windowFor(skillId, MasteryEvaluator.WINDOW_SIZE)
                .filter { !it.isWarmup && !it.isIndependenceCheckProbe && !it.isAbandoned }

        /**
         * Isolates the most recent [IndependenceCheck.REQUIRED_ITEMS] probe attempts (see
         * [Attempt.isIndependenceCheckProbe]'s KDoc) out of a generously bounded fetch, and re-runs the
         * same evaluation `:core:engine` already ran live - no separate persisted pass/fail state exists
         * (docs/09-BUILD-PLAN.md Stage 9's pending-tasks note), this recomputes it instead.
         */
        private suspend fun loadIndependenceCheck(): IndependenceCheckSummary? {
            val probes =
                attemptRepository
                    .windowFor(SkillIds.M2_FULL_DIATONIC, INDEPENDENCE_CHECK_LOOKBACK)
                    .filter { it.isIndependenceCheckProbe }
                    .takeLast(IndependenceCheck.REQUIRED_ITEMS)
            if (probes.isEmpty()) return null
            val result = IndependenceCheck.evaluate(probes)
            val status = if (result.passed) IndependenceCheckStatus.PASSED else IndependenceCheckStatus.FAILED
            return IndependenceCheckSummary(status, result.accuracy)
        }

        private companion object {
            const val INDEPENDENCE_CHECK_LOOKBACK = 500
        }
    }
