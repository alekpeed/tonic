package com.tonic.feature.practice.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.tonic.core.ui.components.StageHeader
import com.tonic.core.ui.theme.TonicSpacing
import com.tonic.core.ui.theme.TonicTheme
import com.tonic.feature.practice.R

/**
 * The first-run explanation for minor — docs/20-PHASE-2-SPEC.md §5.1, under docs/08-UI-SPEC.md §3a's
 * rule that no new task shape reaches a first-time user without one.
 *
 * §5.1 names two things it must cover: what changed from major, and why `♭3` is labeled the way it is.
 * Both are here, and the second matters more than it looks — a learner who has spent every session so
 * far pressing "3" is about to be shown a button that is *nearly* that and is not it. Left unexplained,
 * the obvious reading is that the app renamed something.
 *
 * Deliberately shorter than the `M2` screen: the mechanic is unchanged, so re-teaching it would be the
 * kind of interruption §3a warns against. What is new is the sound and one button.
 */
@Composable
internal fun M10IntroContent(
    onPlayExample: () -> Unit,
    onStart: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(TonicSpacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        StageHeader(stringResource(R.string.practice_stage_name))

        Text(
            text = stringResource(R.string.m10_intro_title),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(TonicSpacing.lg))

        IntroParagraph(stringResource(R.string.m10_intro_what_changed))
        IntroParagraph(stringResource(R.string.m10_intro_the_sound))

        Button(onClick = onPlayExample, modifier = Modifier.testTag("m10_intro_play_example")) {
            Text(stringResource(R.string.m2_intro_example_play))
        }
        Spacer(modifier = Modifier.height(TonicSpacing.md))

        IntroParagraph(stringResource(R.string.m10_intro_flat_three))
        IntroParagraph(stringResource(R.string.m10_intro_why_flat))

        Spacer(modifier = Modifier.height(TonicSpacing.lg))
        Button(onClick = onStart, modifier = Modifier.testTag("m10_intro_start")) {
            Text(stringResource(R.string.m10_intro_start))
        }
    }
}

@Preview
@Composable
private fun M10IntroPreview() {
    TonicTheme(dynamicColor = true) {
        M10IntroContent(onPlayExample = {}, onStart = {})
    }
}
