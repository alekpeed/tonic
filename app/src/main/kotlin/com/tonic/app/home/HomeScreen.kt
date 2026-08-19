package com.tonic.app.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tonic.app.R
import com.tonic.core.model.music.ScaleDegree
import com.tonic.core.model.state.LabelStyle
import com.tonic.core.model.state.MasteryState
import com.tonic.core.ui.labels.displayLabel
import com.tonic.core.ui.theme.TonicSpacing
import com.tonic.core.ui.theme.TonicTheme

@Composable
fun HomeScreen(
    onNeedsDiagnostic: () -> Unit,
    onStartPractice: () -> Unit,
    onOpenProgress: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.loadIfNeeded() }

    if (uiState.isLoading) return

    if (uiState.needsDiagnostic) {
        LaunchedEffect(Unit) { onNeedsDiagnostic() }
        return
    }

    HomeContent(
        uiState = uiState,
        onStartPractice = onStartPractice,
        onOpenProgress = onOpenProgress,
        onOpenSettings = onOpenSettings,
    )
}

@Composable
private fun HomeContent(
    uiState: HomeUiState,
    onStartPractice: () -> Unit,
    onOpenProgress: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(TonicSpacing.lg),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // docs/08-UI-SPEC.md §7: "displayed passively" - a small, unemphasized line, not a badge or a
        // counter that competes visually with the Start action below it.
        if (uiState.streakDays > 0) {
            Text(
                text = pluralStringResource(R.plurals.home_streak, uiState.streakDays, uiState.streakDays),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag("home_streak"),
            )
            Spacer(modifier = Modifier.height(TonicSpacing.md))
        }

        Text(
            text = currentProgressText(uiState),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag("home_current_progress"),
        )

        Spacer(modifier = Modifier.height(TonicSpacing.xl))

        Button(onClick = onStartPractice, modifier = Modifier.testTag("home_start")) {
            Text(stringResource(R.string.home_start_practice))
        }

        Spacer(modifier = Modifier.height(TonicSpacing.lg))

        Row(horizontalArrangement = Arrangement.spacedBy(TonicSpacing.md)) {
            TextButton(onClick = onOpenProgress, modifier = Modifier.testTag("home_open_progress")) {
                Text(stringResource(R.string.home_open_progress))
            }
            TextButton(onClick = onOpenSettings, modifier = Modifier.testTag("home_open_settings")) {
                Text(stringResource(R.string.home_open_settings))
            }
        }
    }
}

@Composable
private fun currentProgressText(uiState: HomeUiState): String {
    val degrees = uiState.currentActiveDegrees.joinToString(", ") { it.displayLabel(uiState.labelStyle) }
    return when (uiState.currentNodeMasteryState) {
        MasteryState.MASTERED -> stringResource(R.string.home_progress_all_mastered)
        else -> stringResource(R.string.home_progress_working_on, degrees)
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeContentPreview() {
    TonicTheme(darkTheme = false, dynamicColor = false) {
        HomeContent(
            uiState =
                HomeUiState(
                    isLoading = false,
                    labelStyle = LabelStyle.NUMBERS,
                    streakDays = 4,
                    currentActiveDegrees = listOf(1, 2, 3, 5).map(::ScaleDegree),
                    currentNodeMasteryState = MasteryState.IN_PROGRESS,
                ),
            onStartPractice = {},
            onOpenProgress = {},
            onOpenSettings = {},
        )
    }
}

@Preview(name = "No streak yet", showBackground = true)
@Composable
private fun HomeContentNoStreakPreview() {
    TonicTheme(darkTheme = false, dynamicColor = false) {
        HomeContent(
            uiState =
                HomeUiState(
                    isLoading = false,
                    currentActiveDegrees = listOf(1, 3, 5).map(::ScaleDegree),
                    currentNodeMasteryState = MasteryState.AVAILABLE,
                ),
            onStartPractice = {},
            onOpenProgress = {},
            onOpenSettings = {},
        )
    }
}
