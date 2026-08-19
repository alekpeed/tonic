package com.tonic.core.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.tonic.core.ui.theme.TonicSpacing
import com.tonic.core.ui.theme.TonicTheme

/**
 * The name of the stage the user is currently in — docs/11-ONBOARDING-CLARITY.md §9.1: "Every screen
 * belongs to a named stage the user can see... a small persistent header or equivalent, not something
 * the user has to infer from context."
 *
 * Deliberately quiet: low emphasis, small type, no chrome. It is there to be *checkable* at a glance,
 * not to compete with the exercise. The gap it closes was reported from real use — a user moved from
 * the diagnostic through four sub-tests into live practice without any screen ever saying which of
 * those two things was happening.
 *
 * Marked as a heading for TalkBack so screen-reader users can jump to it rather than hearing it read
 * in sequence on every screen.
 */
@Composable
fun StageHeader(
    stageName: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = stageName,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier =
            modifier
                .fillMaxWidth()
                .padding(bottom = TonicSpacing.sm)
                .testTag("stage_header")
                .semantics { heading() },
    )
}

@Preview(showBackground = true)
@Composable
private fun StageHeaderPreview() {
    TonicTheme(darkTheme = false, dynamicColor = false) {
        StageHeader(stageName = "Calibration")
    }
}
