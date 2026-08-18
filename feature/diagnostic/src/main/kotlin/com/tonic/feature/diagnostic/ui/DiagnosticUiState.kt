package com.tonic.feature.diagnostic.ui

import com.tonic.core.model.items.Item
import com.tonic.feature.diagnostic.engine.M0SubTest

/**
 * Where a flagged user ends up starting — deliberately the *only* thing this screen carries forward
 * from a completed [com.tonic.core.model.state.DiagnosticResult]. `amusiaIndicatorFlag` and every raw
 * measurement stop at the ViewModel: docs/02-PEDAGOGY.md §8 / docs/09-BUILD-PLAN.md Stage 8's "the
 * amusia flag is not reachable from any composable" is enforced structurally here, not just by
 * convention - there is no field on [DiagnosticUiState] a composable could read it through.
 */
enum class DiagnosticOutcome { PROCEED_TO_PRACTICE, START_WITH_FUNDAMENTALS }

/** What docs/08-UI-SPEC.md §5's diagnostic screen needs to render one moment of a run. */
data class DiagnosticUiState(
    val hasStarted: Boolean = false,
    val currentItem: Item? = null,
    val currentSubTest: M0SubTest? = null,
    val subTestIndex: Int = 0,
    val totalSubTests: Int = 4,
    val inputEnabled: Boolean = false,
    val isFinished: Boolean = false,
    val outcome: DiagnosticOutcome? = null,
)
