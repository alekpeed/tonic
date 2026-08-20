package com.tonic.core.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * Practice screen's only progress signal — docs/08-UI-SPEC.md §4: "a thin, unlabeled bar showing
 * position in the session. No item count, no percentage, no score." Deliberately has no visible
 * numbers; [contentDescription] still states position for TalkBack, since screen-reader users need the
 * same information sighted users get from the bar's fill level.
 */
@Composable
fun MinimalProgressIndicator(
    itemsCompleted: Int,
    itemsPlanned: Int,
    modifier: Modifier = Modifier,
) {
    val fraction = if (itemsPlanned > 0) (itemsCompleted.toFloat() / itemsPlanned).coerceIn(0f, 1f) else 0f
    MinimalProgressIndicator(
        fraction = fraction,
        contentDescription = "$itemsCompleted of $itemsPlanned",
        modifier = modifier,
    )
}

/**
 * Fraction form: the practice session's bar draws elapsed *time* over the wall-clock budget rather
 * than an item count - the plan's item total is an estimate, so an item-based fill barely moved for a
 * deliberate learner while the session ran on, reading as a stuck timer. Time is what actually bounds
 * the session (docs/07-ADAPTIVE-ENGINE.md §8), so time is what the bar shows.
 */
@Composable
fun MinimalProgressIndicator(
    fraction: Float,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    LinearProgressIndicator(
        progress = { fraction },
        modifier =
            modifier
                .fillMaxWidth()
                .height(4.dp)
                .testTag("session_progress")
                .semantics { this.contentDescription = contentDescription },
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.surfaceVariant,
        gapSize = 0.dp,
        drawStopIndicator = {},
    )
}
