package com.tonic.feature.diagnostic.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tonic.core.model.state.DiagnosticResult
import com.tonic.core.model.state.EntryPoint
import com.tonic.feature.diagnostic.engine.DiagnosticLoopEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.random.Random

/**
 * docs/04-ARCHITECTURE.md §3: thin by design. [DiagnosticLoopEngine] already owns every pedagogical
 * decision (which sub-test is next, how the staircases move, the placement calculation); this class
 * only starts a run, republishes its state as UI-shaped [DiagnosticUiState], and - the one thing worth
 * calling out - is the single point where [DiagnosticResult.amusiaIndicatorFlag] is read and discarded:
 * see [DiagnosticUiState]'s own KDoc.
 */
@HiltViewModel
class DiagnosticViewModel
    @Inject
    constructor(
        private val engine: DiagnosticLoopEngine,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(DiagnosticUiState())
        val uiState: StateFlow<DiagnosticUiState> = _uiState.asStateFlow()

        private var started = false

        /** Idempotent - re-collecting this ViewModel after a rotation must not start a second run. */
        fun begin() {
            if (started) return
            started = true
            _uiState.update { it.copy(hasStarted = true) }
            viewModelScope.launch { observeEngine() }
            viewModelScope.launch { engine.start(rootSeed = Random.nextLong()) }
        }

        private suspend fun observeEngine() {
            engine.state.collect { loopState ->
                _uiState.update {
                    it.copy(
                        currentItem = loopState.currentItem,
                        currentSubTest = loopState.currentSubTest,
                        subTestIndex = loopState.subTestIndex,
                        totalSubTests = loopState.totalSubTests,
                        inputEnabled = loopState.inputEnabled,
                        isFinished = loopState.isFinished,
                        outcome = loopState.result?.let(::outcomeFor),
                    )
                }
            }
        }

        fun onAnswerSelected(label: String) {
            viewModelScope.launch { engine.submitAnswer(label) }
        }

        fun onReplay() {
            viewModelScope.launch { engine.replay() }
        }

        override fun onCleared() {
            engine.close()
        }

        private fun outcomeFor(result: DiagnosticResult): DiagnosticOutcome =
            when (result.recommendedEntry) {
                EntryPoint.M2_STAGE_1 -> DiagnosticOutcome.PROCEED_TO_PRACTICE
                EntryPoint.M1_REMEDIATION -> DiagnosticOutcome.START_WITH_FUNDAMENTALS
            }
    }
