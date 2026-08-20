package com.tonic.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.tonic.core.ui.theme.TonicSpacing

/**
 * The M0 diagnostic's answer input — docs/08-UI-SPEC.md §5: "same structural language as Practice,
 * simpler answer widgets (two large buttons for binary choices)." Stacked vertically, like the degree
 * ladder, for the same one-handed-reach reasoning, but only ever two options.
 */
@Composable
fun BinaryChoiceButtons(
    firstLabel: String,
    secondLabel: String,
    enabled: Boolean,
    onFirstSelected: () -> Unit,
    onSecondSelected: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(TonicSpacing.md),
    ) {
        Button(
            onClick = onFirstSelected,
            enabled = enabled,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(LARGE_BUTTON_HEIGHT)
                    .testTag("binary_choice_first"),
        ) {
            Text(firstLabel, style = MaterialTheme.typography.titleMedium)
        }
        Button(
            onClick = onSecondSelected,
            enabled = enabled,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(LARGE_BUTTON_HEIGHT)
                    .testTag("binary_choice_second"),
        ) {
            Text(secondLabel, style = MaterialTheme.typography.titleMedium)
        }
    }
}

private val LARGE_BUTTON_HEIGHT = 72.dp
