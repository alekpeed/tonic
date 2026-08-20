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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import com.tonic.core.ui.theme.TonicSpacing
import com.tonic.core.ui.theme.TonicTheme

/**
 * The name of the stage the user is currently in — docs/11-ONBOARDING-CLARITY.md §9.1: "Every screen
 * belongs to a named stage the user can see... a small persistent header or equivalent, not something
 * the user has to infer from context."
 *
 * Quiet but *legible*. The first version of this used `labelMedium` (12sp, the smallest style in the
 * system) in `onSurfaceVariant` (the lowest-emphasis on-colour), aiming not to compete with the
 * exercise — and overshot into invisible: a user looking at a full practice screen still could not tell
 * which mode they were in, which is the exact failure §9.1 exists to prevent. That section's bar is
 * legibility, not mere presence; a label nobody notices fails it as surely as no label at all.
 *
 * So: 16sp semi-bold, tracked out slightly, in `onSurface` — the role every Material scheme, dynamic
 * ones included, guarantees is legible against the surface behind it. The size is placed deliberately
 * in the hierarchy: above every incidental 14sp label on the screen (skip, help, the phase caption) so
 * it is no longer joint-last, and below the 20sp ladder labels and 22sp prompt so it still doesn't
 * compete with the exercise. Still no chrome and no colour of its own — a label, not a banner.
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
        style =
            MaterialTheme.typography.bodyLarge.copy(
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.8.sp,
            ),
        color = MaterialTheme.colorScheme.onSurface,
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
    TonicTheme(darkTheme = false) {
        StageHeader(stageName = "Calibration")
    }
}
