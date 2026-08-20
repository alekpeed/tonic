package com.tonic.feature.practice.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
 * The Module 2 explanation screen — docs/11-ONBOARDING-CLARITY.md §3, whose six required content points
 * appear here in the order that section specifies: what plays first and that it centers on home, what
 * plays second, what the task is, what the buttons mean tied to what they'll sound like, one worked
 * playable example with the answer revealed, and a single Start.
 *
 * No comprehension check and nothing gated: §1 is explicit that this "is not a tutorial mode the user
 * must complete correctly to proceed." The example can be played any number of times or not at all, and
 * Start is live from the first frame.
 *
 * The reveal is user-driven rather than automatic so the answer can't be read before the sound has been
 * heard — an answer shown up front turns the worked example back into prose.
 */
@Composable
internal fun M2IntroContent(
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
            text = stringResource(R.string.m2_intro_title),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(TonicSpacing.lg))

        IntroParagraph(stringResource(R.string.m2_intro_what_plays_first))
        IntroParagraph(stringResource(R.string.m2_intro_what_plays_second))
        IntroParagraph(stringResource(R.string.m2_intro_the_task))
        IntroParagraph(stringResource(R.string.m2_intro_buttons))

        Spacer(modifier = Modifier.height(TonicSpacing.md))
        Text(
            text = stringResource(R.string.m2_intro_example_heading),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(TonicSpacing.sm))
        IntroParagraph(stringResource(R.string.m2_intro_example_prompt))

        Button(onClick = onPlayExample, modifier = Modifier.testTag("m2_intro_play_example")) {
            Text(
                stringResource(
                    if (answerRevealed) R.string.m2_intro_example_replay else R.string.m2_intro_example_play,
                ),
            )
        }
        Spacer(modifier = Modifier.height(TonicSpacing.sm))

        if (answerRevealed) {
            IntroParagraph(stringResource(R.string.m2_intro_example_answer, answerLabel))
            IntroParagraph(
                stringResource(
                    if (answerLabel == HOME_DEGREE_LABEL) {
                        R.string.m2_intro_example_answer_home
                    } else {
                        R.string.m2_intro_example_answer_above
                    },
                ),
            )
        } else {
            TextButton(onClick = onRevealAnswer, modifier = Modifier.testTag("m2_intro_reveal")) {
                Text(stringResource(R.string.m2_intro_example_reveal))
            }
        }

        Spacer(modifier = Modifier.height(TonicSpacing.md))
        IntroParagraph(stringResource(R.string.m2_intro_no_wrong))

        Spacer(modifier = Modifier.height(TonicSpacing.lg))
        Button(onClick = onStart, modifier = Modifier.testTag("m2_intro_start")) {
            Text(stringResource(R.string.m2_intro_start))
        }
        Spacer(modifier = Modifier.height(TonicSpacing.lg))
    }
}

@Composable
private fun IntroParagraph(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(bottom = TonicSpacing.md),
    )
}

/** docs/02-PEDAGOGY.md §1: scale degree 1 is the tonic, i.e. "home" itself. */
private const val HOME_DEGREE_LABEL = "1"

@Preview(name = "M2 intro - before reveal", showBackground = true, heightDp = 900)
@Composable
private fun M2IntroPreview() {
    TonicTheme(darkTheme = false) {
        M2IntroContent("3", answerRevealed = false, {}, {}, {})
    }
}

@Preview(name = "M2 intro - answer revealed", showBackground = true, heightDp = 900)
@Composable
private fun M2IntroRevealedPreview() {
    TonicTheme(darkTheme = false) {
        M2IntroContent("3", answerRevealed = true, {}, {}, {})
    }
}
