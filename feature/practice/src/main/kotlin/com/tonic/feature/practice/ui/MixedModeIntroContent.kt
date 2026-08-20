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
import androidx.compose.material3.TextButton
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
 * The first-run explanation for `M10.MIXED_MODE` — docs/08-UI-SPEC.md §3a.
 *
 * Not one of the four screens docs/20-PHASE-2-SPEC.md §5.1 names, and required anyway. §3a's test for
 * a new task shape is "a different question, a different answer control, **or a different thing to
 * listen for**," and this node is the third: the question and the ladder are the ones the learner
 * knows, but they now have to hear *which key they are in* before the degree means anything. That is a
 * genuinely new listening task, and it arrives alongside a ladder that has silently grown from seven
 * buttons to ten.
 *
 * The screen's job is to make the extra buttons make sense before they are met, rather than after. Its
 * worked example is deliberately a minor item: a major one would demonstrate a flow indistinguishable
 * from ordinary practice and teach nothing about why the ladder changed.
 */
@Composable
internal fun MixedModeIntroContent(
    answerLabel: String,
    answerRevealed: Boolean,
    onPlayExample: () -> Unit,
    onRevealAnswer: () -> Unit,
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
            text = stringResource(R.string.mixed_intro_title),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(TonicSpacing.lg))

        IntroParagraph(stringResource(R.string.mixed_intro_no_warning))
        IntroParagraph(stringResource(R.string.mixed_intro_all_ten))
        IntroParagraph(stringResource(R.string.mixed_intro_two_steps))

        Spacer(modifier = Modifier.height(TonicSpacing.md))
        Text(
            text = stringResource(R.string.mixed_intro_example_heading),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(modifier = Modifier.height(TonicSpacing.sm))
        IntroParagraph(stringResource(R.string.mixed_intro_example_prompt))

        Button(onClick = onPlayExample, modifier = Modifier.testTag("mixed_intro_play_example")) {
            Text(stringResource(R.string.m2_intro_example_play))
        }
        Spacer(modifier = Modifier.height(TonicSpacing.sm))

        if (answerRevealed) {
            IntroParagraph(stringResource(R.string.mixed_intro_answer, answerLabel))
        } else {
            TextButton(onClick = onRevealAnswer, modifier = Modifier.testTag("mixed_intro_reveal")) {
                Text(stringResource(R.string.mixed_intro_reveal))
            }
        }

        IntroParagraph(stringResource(R.string.mixed_intro_told_after))

        Spacer(modifier = Modifier.height(TonicSpacing.lg))
        Button(onClick = onStart, modifier = Modifier.testTag("mixed_intro_start")) {
            Text(stringResource(R.string.mixed_intro_start))
        }
    }
}

@Preview
@Composable
private fun MixedModeIntroPreview() {
    TonicTheme(dynamicColor = true) {
        MixedModeIntroContent(
            answerLabel = "♭3",
            answerRevealed = true,
            onPlayExample = {},
            onRevealAnswer = {},
            onStart = {},
        )
    }
}
