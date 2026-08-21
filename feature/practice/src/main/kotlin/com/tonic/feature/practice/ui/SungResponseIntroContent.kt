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
 * The sung-response explanation — docs/30-PHASE-3-SPEC.md §6.2, shown once before the first sung item.
 *
 * Singing is its own task shape under docs/08-UI-SPEC.md §3a: a different answer control and a
 * different thing to do, even though the question being asked is unchanged. So it gets its own screen
 * and its own seen-once flag rather than riding on any module's.
 *
 * Most of this screen's length goes to one job, because §3 names it as the phase's central risk: making
 * sure the learner understands that their *singing* is not what is being judged. A learner who believes
 * they are being marked on vocal accuracy will avoid the feature, or worse, will read a correct answer
 * scored correct as luck. The three points that carry that — any octave is fine, approximate is fine,
 * and unclear is never wrong — are stated plainly rather than left to be inferred from behavior.
 *
 * **Deviation: the worked example is text, not audio.** §6.2 asks for one, and every other explanation
 * screen in this app plays real audio through the same renderer the exercise uses. This one cannot yet:
 * an honest demonstration here is a *human voice* landing slightly off and being accepted, and there is
 * no recording of one. A synthesized stand-in would demonstrate the pipeline rather than the point,
 * since the reassurance being offered is about imperfect human singing specifically. Decided with the
 * maintainer on 2026-08-21 to ship the explanation with a worked example in prose and revisit once a
 * real clip can be recorded on a device.
 */
@Composable
internal fun SungResponseIntroContent(onStart: () -> Unit) {
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
            text = stringResource(R.string.sung_intro_title),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(TonicSpacing.lg))

        IntroParagraph(stringResource(R.string.sung_intro_what_happens))
        IntroParagraph(stringResource(R.string.sung_intro_any_octave))

        Spacer(modifier = Modifier.height(TonicSpacing.md))
        Text(
            text = stringResource(R.string.sung_intro_example_heading),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(TonicSpacing.sm))
        IntroParagraph(stringResource(R.string.sung_intro_example))

        Spacer(modifier = Modifier.height(TonicSpacing.md))
        IntroParagraph(stringResource(R.string.sung_intro_not_judged))
        IntroParagraph(stringResource(R.string.sung_intro_unclear))
        IntroParagraph(stringResource(R.string.sung_intro_tap_always))

        Spacer(modifier = Modifier.height(TonicSpacing.lg))
        Button(onClick = onStart, modifier = Modifier.testTag("sung_intro_start")) {
            Text(stringResource(R.string.sung_intro_start))
        }
        Spacer(modifier = Modifier.height(TonicSpacing.lg))
    }
}

@Preview(name = "Sung response intro", showBackground = true, heightDp = 900)
@Composable
private fun SungResponseIntroPreview() {
    TonicTheme(darkTheme = false) {
        SungResponseIntroContent {}
    }
}

@Preview(name = "Sung response intro - dark", showBackground = true, heightDp = 900)
@Composable
private fun SungResponseIntroDarkPreview() {
    TonicTheme(darkTheme = true) {
        SungResponseIntroContent {}
    }
}
