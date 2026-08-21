package com.tonic.app.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tonic.core.curriculum.graph.SkillGraph
import com.tonic.core.data.repository.SessionRepository
import com.tonic.core.data.repository.SkillStateRepository
import com.tonic.core.data.settings.SettingsRepository
import com.tonic.core.engine.streak.StreakCalculator
import com.tonic.core.model.state.MasteryState
import com.tonic.core.model.state.SkillState
import com.tonic.core.model.time.Clock
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.ZoneId
import javax.inject.Inject

/**
 * docs/04-ARCHITECTURE.md §3: thin by design. Home is the hub `:feature:*` modules deliberately don't
 * depend on each other through (docs/04-ARCHITECTURE.md §2's "no feature depending on another feature"),
 * so this - and not any single feature module - is where "which node is the user working on" and "what
 * is their streak" get resolved; `:app` is allowed to depend on everything.
 */
@HiltViewModel
class HomeViewModel
    @Inject
    constructor(
        private val settingsRepository: SettingsRepository,
        private val skillStateRepository: SkillStateRepository,
        private val sessionRepository: SessionRepository,
        private val clock: Clock,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(HomeUiState())
        val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

        private var started = false

        /** Idempotent - a rotation re-collecting this ViewModel must not reload (and re-flicker) the screen. */
        fun loadIfNeeded() {
            if (started) return
            started = true
            viewModelScope.launch {
                settingsRepository.settings.collect { settings ->
                    _uiState.update {
                        it.copy(
                            labelStyle = settings.labelStyle,
                            needsOnboarding = !settings.onboardingCompleted,
                            needsDiagnostic = !settings.diagnosticCompleted,
                        )
                    }
                }
            }
            viewModelScope.launch { load() }
        }

        private suspend fun load() {
            val states = skillStateRepository.observeAll().first()
            // SkillGraph.currentNodeFor, not a chain of this screen's own: Home, the progress screen
            // and the practice loop each used to answer "what are you working on" separately, and two
            // of them walked M2 alone while the third walked everything. Home would have reported
            // M2.FULL_DIATONIC forever while sessions ran minor.
            val currentNodeId =
                SkillGraph.currentNodeFor { id -> states[id]?.masteryState == MasteryState.MASTERED }
            val currentState = states[currentNodeId] ?: SkillState.initial(currentNodeId)
            val activeDegrees = SkillGraph.activeDegreesFor(currentNodeId).sortedBy { it.degree }

            val recentSessions = sessionRepository.recentCompletedSessions(STREAK_LOOKBACK)
            val zone = ZoneId.systemDefault()
            val practiceDates = recentSessions.map { it.startedAt.atZone(zone).toLocalDate() }.toSet()
            val today = clock.now().atZone(zone).toLocalDate()
            val streak = StreakCalculator.currentStreak(practiceDates, today)

            _uiState.update {
                it.copy(
                    isLoading = false,
                    streakDays = streak,
                    currentActiveDegrees = activeDegrees,
                    currentNodeMasteryState = currentState.masteryState,
                )
            }
        }

        private companion object {
            /** Generous but bounded - see [StreakCalculator]'s own KDoc on why the walk terminates quickly in practice. */
            const val STREAK_LOOKBACK = 400
        }
    }
