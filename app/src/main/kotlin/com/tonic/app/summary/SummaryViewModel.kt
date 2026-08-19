package com.tonic.app.summary

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tonic.core.data.repository.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** docs/08-UI-SPEC.md §2's `summary/{sessionId}` route - [sessionId] arrives as a nav argument via [SavedStateHandle]. */
@HiltViewModel
class SummaryViewModel
    @Inject
    constructor(
        private val sessionRepository: SessionRepository,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(SummaryUiState())
        val uiState: StateFlow<SummaryUiState> = _uiState.asStateFlow()

        init {
            val sessionId =
                checkNotNull(savedStateHandle.get<Long>(SESSION_ID_ARG)) { "summary route requires a sessionId" }
            viewModelScope.launch {
                val session = sessionRepository.findById(sessionId)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        itemsCompleted = session?.completedItemCount ?: 0,
                        itemsPlanned = session?.plannedItemCount ?: 0,
                    )
                }
            }
        }

        companion object {
            const val SESSION_ID_ARG = "sessionId"
        }
    }
