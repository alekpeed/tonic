package com.tonic.app.summary

/** docs/08-UI-SPEC.md §2: "what happened, what's next" - deliberately just the session's own row; no score, no grade. */
data class SummaryUiState(
    val isLoading: Boolean = true,
    val itemsCompleted: Int = 0,
    val itemsPlanned: Int = 0,
)
