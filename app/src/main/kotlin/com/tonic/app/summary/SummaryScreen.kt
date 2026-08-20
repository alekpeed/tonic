package com.tonic.app.summary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tonic.app.R
import com.tonic.core.ui.components.StageHeader
import com.tonic.core.ui.theme.TonicSpacing
import com.tonic.core.ui.theme.TonicTheme

@Composable
fun SummaryScreen(
    onDone: () -> Unit,
    viewModel: SummaryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    if (!uiState.isLoading) {
        SummaryContent(uiState = uiState, onDone = onDone)
    }
}

@Composable
private fun SummaryContent(
    uiState: SummaryUiState,
    onDone: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(TonicSpacing.lg),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        StageHeader(stringResource(R.string.summary_stage_name))
        Text(
            text = stringResource(R.string.summary_title),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(TonicSpacing.md))
        Text(
            text = stringResource(R.string.summary_items_completed, uiState.itemsCompleted, uiState.itemsPlanned),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag("summary_items_completed"),
        )
        Spacer(modifier = Modifier.height(TonicSpacing.xl))
        Button(onClick = onDone, modifier = Modifier.testTag("summary_done")) {
            Text(stringResource(R.string.summary_done))
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SummaryContentPreview() {
    TonicTheme(darkTheme = false) {
        SummaryContent(uiState = SummaryUiState(isLoading = false, itemsCompleted = 42, itemsPlanned = 50), onDone = {})
    }
}
