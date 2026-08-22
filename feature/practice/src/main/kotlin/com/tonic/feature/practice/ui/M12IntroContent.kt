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
 * The first-run explanation for audiation — docs/20-PHASE-2-SPEC.md §5.1, the one screen the spec
 * calls out as non-optional: "the user must understand they are meant to *imagine* the note before it
 * plays. A worked example is not optional here; it is the only way this task is comprehensible."
 *
 * Three things this has to do that no other explanation screen does.
 *
 * **Say that the task runs backwards.** Every session so far has been sound-then-name. A learner who
 * arrives expecting that will treat the named degree as a hint and the silence as a bug.
 *
 * **Say that the silence is the exercise.** Several seconds of nothing, on a screen that has never
 * been silent before, reads as the app having stopped. §5.3 forbids a countdown, so the words have to
 * carry it.
 *
 * **Say what "hear it in your head" means**, for a learner who has never been asked to do it and may
 * well believe they cannot. The screen's answer is the song-in-your-head analogy: nearly everyone has
 * done this involuntarily, and naming that as the same skill is what makes the instruction actionable
 * rather than mystical.
 *
 * The reveal is user-driven and states the *direction* of the mismatch when there is one, because
 * direction is what the three buttons ask for (§8.1 decision 3) and an example that only said "not a
 * match" would leave the third button unexplained.
 */
@Composable
internal fun M12IntroContent(
    statedLabel: String,
    matched: Boolean,
    soundedWasLower: Boolean,
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
            text = stringResource(R.string.m12_intro_title),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(TonicSpacing.lg))

        IntroParagraph(stringResource(R.string.m12_intro_backwards))
        IntroParagraph(stringResource(R.string.m12_intro_how_it_goes))
        IntroParagraph(stringResource(R.string.m12_intro_silence_is_the_exercise))
        IntroParagraph(stringResource(R.string.m12_intro_what_holding_means))

        Spacer(modifier = Modifier.height(TonicSpacing.md))
        Text(
            text = stringResource(R.string.m12_intro_example_heading),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(modifier = Modifier.height(TonicSpacing.sm))
        IntroParagraph(stringResource(R.string.m12_intro_example_prompt, statedLabel))

        Button(onClick = onPlayExample, modifier = Modifier.testTag("m12_intro_play_example")) {
            Text(stringResource(R.string.m12_intro_example_play))
        }
        Spacer(modifier = Modifier.height(TonicSpacing.sm))

        if (answerRevealed) {
            IntroParagraph(
                if (matched) {
                    stringResource(R.string.m12_intro_answer_matched, statedLabel)
                } else {
                    stringResource(
                        R.string.m12_intro_answer_mismatched,
                        stringResource(
                            if (soundedWasLower) {
                                R.string.m12_intro_answer_lower
                            } else {
                                R.string.m12_intro_answer_higher
                            },
                        ),
                        statedLabel,
                    )
                },
            )
        } else {
            TextButton(onClick = onRevealAnswer, modifier = Modifier.testTag("m12_intro_reveal")) {
                Text(stringResource(R.string.m12_intro_reveal))
            }
        }

        IntroParagraph(stringResource(R.string.m12_intro_three_buttons))

        Spacer(modifier = Modifier.height(TonicSpacing.lg))
        Button(onClick = onStart, modifier = Modifier.testTag("m12_intro_start")) {
            Text(stringResource(R.string.m12_intro_start))
        }
    }
}

@Preview
@Composable
private fun M12IntroPreview() {
    TonicTheme(dynamicColor = true) {
        M12IntroContent(
            statedLabel = "5",
            matched = false,
            soundedWasLower = true,
            answerRevealed = true,
            onPlayExample = {},
            onRevealAnswer = {},
            onStart = {},
        )
    }
}
