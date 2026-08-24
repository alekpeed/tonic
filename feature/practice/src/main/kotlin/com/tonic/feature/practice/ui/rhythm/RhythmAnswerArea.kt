package com.tonic.feature.practice.ui.rhythm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tonic.core.model.items.Item
import com.tonic.core.model.music.TimbreId
import com.tonic.core.model.rhythm.Meter
import com.tonic.core.model.rhythm.MetronomeFadeLevel
import com.tonic.core.model.rhythm.MetronomePlanner
import com.tonic.core.model.rhythm.RhythmPattern
import com.tonic.core.model.rhythm.RhythmQuestion
import com.tonic.core.model.rhythm.RhythmScore
import com.tonic.core.ui.theme.TonicSpacing
import com.tonic.core.ui.theme.TonicTheme
import com.tonic.feature.practice.R

/**
 * The answer control for an `M3` item — docs/40-PHASE-4-SPEC.md §7.2 and §3.3.
 *
 * Three shapes, because [RhythmQuestion] has three and they are genuinely different questions rather
 * than one question with different contents. Dispatching on the sealed type means adding a fourth is
 * a compile error here, which is the same reasoning `AnswerArea` and `PracticeItems` are built on.
 *
 * The two recognition shapes both come down to picking a numbered option, and they still do not share
 * a composable beyond [RhythmChoiceButtons]: the prompts are different questions in the learner's
 * head — "which of these did you hear" against "where was one" — and a shared prompt would have to be
 * vague enough to cover both.
 */
@Composable
internal fun RhythmAnswerArea(
    item: Item.RhythmItem,
    enabled: Boolean,
    selectedLabel: String?,
    correctLabel: String?,
    onLabelSelected: (String) -> Unit,
    onTap: (uptimeMillis: Long) -> Unit,
    modifier: Modifier = Modifier,
    hapticsEnabled: Boolean = true,
    reduceMotion: Boolean = false,
    audibleTaps: Boolean = false,
    tapCount: Int = 0,
    score: RhythmScore? = null,
) {
    when (val question = item.question) {
        is RhythmQuestion.TapItBack ->
            Column(modifier = modifier) {
                TapSurface(
                    enabled = enabled,
                    onTap = onTap,
                    hapticsEnabled = hapticsEnabled,
                    reduceMotion = reduceMotion,
                    audibleTaps = audibleTaps,
                    tapCount = tapCount,
                )
                // Only once the attempt has been scored. Showing where taps landed while the learner
                // is still tapping would turn the exercise into a game of chasing a mark, which is the
                // failure §7.4's "never a precision grade" is guarding against in a different form.
                if (score != null) {
                    Spacer(modifier = Modifier.height(TonicSpacing.md))
                    TapLandingStrip(score = score)
                }
            }

        is RhythmQuestion.WhichPattern ->
            RhythmChoiceButtons(
                prompt = stringResource(R.string.practice_rhythm_which_pattern_prompt),
                // Labelled by *position*, answered by figure. The choices are sounds played in order
                // and there is nothing else they could honestly be called - this app shows no notation
                // and §3.1 gives the learner syllables for positions inside a beat, not names for
                // whole patterns. What the attempt log stores is the figure at the beat where the
                // choices diverge (§8), so the button carries the position and hands back the figure.
                optionLabels = question.figureSignatures.indices.map { (it + 1).toString() },
                answerFor = { index -> question.figureSignatures[index] },
                enabled = enabled,
                selectedLabel = selectedLabel,
                correctLabel = correctLabel,
                onLabelSelected = onLabelSelected,
                modifier = modifier,
            )

        is RhythmQuestion.WhichBeatIsOne ->
            RhythmChoiceButtons(
                prompt = stringResource(R.string.practice_rhythm_which_beat_prompt),
                optionLabels = (1..question.beatsHeard).map { it.toString() },
                // The one place a position label *is* the answer: §3.4's skill is beat induction, so
                // "the third beat you heard" means the same thing from one item to the next.
                answerFor = { index -> (index + 1).toString() },
                enabled = enabled,
                selectedLabel = selectedLabel,
                correctLabel = correctLabel,
                onLabelSelected = onLabelSelected,
                modifier = modifier,
            )
    }
}

/**
 * A numbered row of choices, with the answer revealed once one is picked.
 *
 * The reveal follows docs/08-UI-SPEC.md §3: the chosen option is marked, and so is the right one, so a
 * learner who was wrong can see *what* the right answer was rather than only that they missed. Before
 * an answer, nothing is marked — [correctLabel] is null until feedback is in, which is what keeps the
 * answer off the screen while the learner is still deciding.
 */
@Composable
private fun RhythmChoiceButtons(
    prompt: String,
    optionLabels: List<String>,
    answerFor: (Int) -> String,
    enabled: Boolean,
    selectedLabel: String?,
    correctLabel: String?,
    onLabelSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(TonicSpacing.sm),
    ) {
        Text(
            text = prompt,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(TonicSpacing.xs))
        optionLabels.forEachIndexed { index, label ->
            val answer = answerFor(index)
            val isCorrect = correctLabel != null && answer == correctLabel
            val isChosen = selectedLabel != null && answer == selectedLabel
            Button(
                onClick = { onLabelSelected(answer) },
                enabled = enabled,
                colors =
                    when {
                        isCorrect ->
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                            )

                        isChosen ->
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            )

                        else -> ButtonDefaults.buttonColors()
                    },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(CHOICE_HEIGHT)
                        .testTag("$CHOICE_TAG_PREFIX$label"),
            ) {
                Text(label, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

internal const val CHOICE_TAG_PREFIX: String = "rhythm_choice_"

private val CHOICE_HEIGHT = 72.dp

private fun previewItem(question: RhythmQuestion) =
    Item.RhythmItem(
        skill = com.tonic.core.model.ids.SkillIds.M3_BEAT_FIND,
        meter = Meter.FOUR_FOUR,
        tempoBpm = 100,
        pattern =
            RhythmPattern(
                Meter.FOUR_FOUR,
                bars = 1,
                onsetTicks = listOf(0, Meter.TICKS_PER_BEAT, Meter.TICKS_PER_BEAT * 2, Meter.TICKS_PER_BEAT * 3),
            ),
        metronomePlan = MetronomePlanner.plan(MetronomeFadeLevel.L0, Meter.FOUR_FOUR, bars = 1),
        question = question,
        timbre = TimbreId.PURE,
        seed = 1L,
    )

@Preview(name = "Tap it back")
@Composable
private fun TapItBackPreview() {
    TonicTheme {
        RhythmAnswerArea(
            item = previewItem(RhythmQuestion.TapItBack),
            enabled = true,
            selectedLabel = null,
            correctLabel = null,
            onLabelSelected = {},
            onTap = {},
        )
    }
}

@Preview(name = "Where was one, answered wrongly")
@Composable
private fun WhichBeatIsOnePreview() {
    TonicTheme {
        RhythmAnswerArea(
            item = previewItem(RhythmQuestion.WhichBeatIsOne(beatsHeard = 4, downbeatPosition = 3)),
            enabled = false,
            selectedLabel = "2",
            correctLabel = "3",
            onLabelSelected = {},
            onTap = {},
        )
    }
}
