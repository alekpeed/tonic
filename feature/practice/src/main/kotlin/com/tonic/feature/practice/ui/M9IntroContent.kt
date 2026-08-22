package com.tonic.feature.practice.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.tonic.core.ui.theme.TonicSpacing
import com.tonic.core.ui.theme.TonicTheme
import com.tonic.feature.practice.R

/**
 * The Module 9 first-run explanation — docs/08-UI-SPEC.md §3a and docs/20-PHASE-2-SPEC.md §5.1.
 *
 * Structurally the same three steps as [M2IntroContent] (explain, demonstrate, let go), with one
 * deliberate difference: it demonstrates **two** examples rather than one. Major and minor are a
 * contrast, not two independently recognizable things, and a learner meeting them for the first time
 * cannot calibrate either alone — see [M9WorkedExample]. Both play through the real generator and the
 * real audio path.
 *
 * Stateless, like every other composable here (CLAUDE.md §6): the caller owns playback and dismissal.
 */
@Composable
internal fun M9IntroContent(
    onPlayMajor: () -> Unit,
    onPlayMinor: () -> Unit,
    onStart: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(TonicSpacing.lg)
                .testTag("m9_intro"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.m9_intro_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(modifier = Modifier.height(TonicSpacing.lg))

        // §3a's ordering: what is changing, then what the two words mean, then what is being asked.
        // Every term is defined in the breath it is first used.
        for (
        line in
        listOf(
            R.string.m9_intro_what_changes,
            R.string.m9_intro_the_two,
            R.string.m9_intro_the_task,
            R.string.m9_intro_dont_overthink,
        )
        ) {
            Text(
                text = stringResource(line),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.fillMaxWidth().padding(bottom = TonicSpacing.md),
            )
        }

        Text(
            text = stringResource(R.string.m9_intro_example_heading),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = TonicSpacing.sm),
        )
        Spacer(modifier = Modifier.height(TonicSpacing.sm))
        Text(
            text = stringResource(R.string.m9_intro_example_hint),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.fillMaxWidth().padding(bottom = TonicSpacing.md),
        )

        // Side by side, and both always available: switching between them is the demonstration, so
        // neither is gated behind the other.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(TonicSpacing.sm),
        ) {
            OutlinedButton(
                onClick = onPlayMajor,
                modifier = Modifier.weight(1f).testTag("m9_intro_play_major"),
            ) {
                Text(stringResource(R.string.m9_intro_play_major))
            }
            OutlinedButton(
                onClick = onPlayMinor,
                modifier = Modifier.weight(1f).testTag("m9_intro_play_minor"),
            ) {
                Text(stringResource(R.string.m9_intro_play_minor))
            }
        }

        Spacer(modifier = Modifier.height(TonicSpacing.lg))
        Text(
            text = stringResource(R.string.m9_intro_no_wrong),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.fillMaxWidth().padding(bottom = TonicSpacing.lg),
        )

        // §3a: a dismiss button and nothing else. No comprehension check, no forced repeat viewing.
        Button(onClick = onStart, modifier = Modifier.testTag("m9_intro_start")) {
            Text(stringResource(R.string.m9_intro_start))
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun M9IntroPreview() {
    TonicTheme(dynamicColor = true) {
        M9IntroContent(onPlayMajor = {}, onPlayMinor = {}, onStart = {})
    }
}
