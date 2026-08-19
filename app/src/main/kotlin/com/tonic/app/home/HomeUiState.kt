package com.tonic.app.home

import com.tonic.core.model.music.ScaleDegree
import com.tonic.core.model.state.LabelStyle
import com.tonic.core.model.state.MasteryState

/** What docs/08-UI-SPEC.md §2's Home screen needs: "start session, current progress at a glance." */
data class HomeUiState(
    val isLoading: Boolean = true,
    /** True until the M0 diagnostic has ever completed - Home routes to it first, per its own placement contract. */
    val needsDiagnostic: Boolean = false,
    val labelStyle: LabelStyle = LabelStyle.NUMBERS,
    /** docs/08-UI-SPEC.md §7: passive, with forgiveness - never a countdown or an "at risk" framing. */
    val streakDays: Int = 0,
    val currentActiveDegrees: List<ScaleDegree> = emptyList(),
    val currentNodeMasteryState: MasteryState = MasteryState.LOCKED,
)
