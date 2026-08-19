package com.tonic.app.onboarding

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.tonic.app.R
import com.tonic.core.ui.theme.TonicSpacing
import com.tonic.core.ui.theme.TonicTheme

/**
 * Shown exactly once, before the M0 diagnostic or any practice - CLAUDE.md's target user has "no
 * musical background, no instrument, and no notation literacy," and neither the diagnostic's binary
 * choices nor the practice loop's degree ladder are self-explanatory to that person on first sight.
 * This is where the app's one recurring mechanic - reference establishes "home," then one note, then
 * you place it relative to home - gets said in plain words a single time, so every screen after this
 * one can stay wordless per docs/08-UI-SPEC.md.
 */
@Composable
fun OnboardingScreen(
    onDone: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    OnboardingContent(onContinue = { viewModel.onDone(onDone) })
}

@Composable
private fun OnboardingContent(onContinue: () -> Unit) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(TonicSpacing.lg),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.onboarding_title),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(TonicSpacing.lg))

        OnboardingPoint(stringResource(R.string.onboarding_what_it_is))
        OnboardingPoint(stringResource(R.string.onboarding_how_it_works))
        OnboardingPoint(stringResource(R.string.onboarding_the_question))
        OnboardingPoint(stringResource(R.string.onboarding_replay))
        OnboardingPoint(stringResource(R.string.onboarding_first_up))

        Spacer(modifier = Modifier.height(TonicSpacing.lg))
        Button(onClick = onContinue, modifier = Modifier.testTag("onboarding_continue")) {
            Text(stringResource(R.string.onboarding_continue))
        }
    }
}

@Composable
private fun OnboardingPoint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(bottom = TonicSpacing.md),
    )
}

@Preview(showBackground = true)
@Composable
private fun OnboardingPreview() {
    TonicTheme(darkTheme = false) {
        OnboardingContent(onContinue = {})
    }
}
