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
 * The first-run explanation for chromatic degrees — docs/20-PHASE-2-SPEC.md §5.1, which names exactly
 * two things it must cover: "what a note 'between' the familiar ones means, and why it has a new label."
 *
 * Both are harder than they sound, and for the same reason. Every session up to this point has taught,
 * implicitly, that a key contains seven notes and the ladder shows all of them. That belief is about to
 * be contradicted by the screen itself, and a learner who is not told why will read the new buttons as
 * the app having changed its mind about what the labels mean. So the first paragraph does the work: the
 * seven were never all the notes, they were the ones this key is *built* from, and the others have been
 * audible in music the whole time.
 *
 * The label question is the same one [M10IntroContent] answers for `♭3`, and is answered the same way
 * deliberately — a number is a distance from home, an accidental says which version of it. A learner
 * who met that idea in minor should recognize it here rather than meeting a second, unrelated system.
 *
 * No worked example button. The other three screens demonstrate a *new task shape*; this one is the
 * same task with a wider answer set, and the honest demonstration of "♯4 sits between 4 and 5" is the
 * ladder itself, one screen away, where 4 and 5 are visible on either side of it. Playing a chromatic
 * note in isolation before the learner has the ladder in front of them would demonstrate nothing they
 * could act on. This is the deviation from §5.1's "with a worked example" and it is recorded in
 * docs/20-PHASE-2-SPEC.md §8.4.
 */
@Composable
internal fun M11IntroContent(onStart: () -> Unit) {
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
            text = stringResource(R.string.m11_intro_title),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(TonicSpacing.lg))

        IntroParagraph(stringResource(R.string.m11_intro_between))
        IntroParagraph(stringResource(R.string.m11_intro_why_label))
        IntroParagraph(stringResource(R.string.m11_intro_where_on_ladder))
        IntroParagraph(stringResource(R.string.m11_intro_one_at_a_time))

        Spacer(modifier = Modifier.height(TonicSpacing.lg))
        Button(onClick = onStart, modifier = Modifier.testTag("m11_intro_start")) {
            Text(stringResource(R.string.m11_intro_start))
        }
    }
}

@Preview
@Composable
private fun M11IntroPreview() {
    TonicTheme(dynamicColor = true) {
        M11IntroContent(onStart = {})
    }
}
