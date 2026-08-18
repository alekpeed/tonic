package com.tonic.core.ui.theme

import androidx.compose.ui.unit.dp

/** Spacing tokens — docs/04-ARCHITECTURE.md §3's "design system: ... spacing tokens." */
object TonicSpacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp

    /** docs/08-UI-SPEC.md §1: "large touch targets (minimum 56 dp)." */
    val minTouchTarget = 56.dp
}
