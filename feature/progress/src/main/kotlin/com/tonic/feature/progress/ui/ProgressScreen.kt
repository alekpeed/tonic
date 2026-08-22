package com.tonic.feature.progress.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tonic.core.model.ids.SkillId
import com.tonic.core.model.state.LabelStyle
import com.tonic.core.model.state.MasteryState
import com.tonic.core.ui.labels.displayLabel
import com.tonic.core.ui.theme.TonicSpacing
import com.tonic.core.ui.theme.TonicTheme
import com.tonic.feature.progress.R

@Composable
fun ProgressScreen(viewModel: ProgressViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.loadIfNeeded() }

    if (!uiState.isLoading) {
        ProgressContent(uiState = uiState, onNodeToggled = viewModel::onNodeToggled)
    }
}

@Composable
private fun ProgressContent(
    uiState: ProgressUiState,
    onNodeToggled: (SkillId) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(TonicSpacing.md)) {
        item {
            Text(text = stringResource(R.string.progress_title), style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(TonicSpacing.lg))
        }

        item { IndependenceCheckCard(uiState.independenceCheck) }
        item { Spacer(modifier = Modifier.height(TonicSpacing.lg)) }

        item {
            Text(
                text = stringResource(R.string.progress_mastery_map_heading),
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(modifier = Modifier.height(TonicSpacing.sm))
        }
        items(uiState.masteryMap) { node ->
            MasteryMapRow(
                node = node,
                labelStyle = uiState.labelStyle,
                expanded = uiState.expandedNode == node.skillId,
                onToggle = { onNodeToggled(node.skillId) },
            )
            Spacer(modifier = Modifier.height(TonicSpacing.sm))
        }

        item { Spacer(modifier = Modifier.height(TonicSpacing.md)) }
        item {
            Text(
                text = stringResource(R.string.progress_degree_accuracy_heading),
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(modifier = Modifier.height(TonicSpacing.sm))
        }
        items(uiState.degreeAccuracy) { entry -> DegreeAccuracyRow(entry, uiState.labelStyle) }

        item { Spacer(modifier = Modifier.height(TonicSpacing.md)) }
        item {
            Text(
                text = stringResource(R.string.progress_confusion_heading),
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(modifier = Modifier.height(TonicSpacing.sm))
        }
        if (uiState.confusionStatements.isEmpty()) {
            item { Text(text = stringResource(R.string.progress_confusion_empty)) }
        } else {
            items(uiState.confusionStatements) { statement -> ConfusionRow(statement, uiState.labelStyle) }
        }
    }
}

@Composable
private fun IndependenceCheckCard(summary: IndependenceCheckSummary?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(TonicSpacing.md)) {
            Text(
                text = stringResource(R.string.progress_independence_heading),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(TonicSpacing.xs))
            val bodyRes =
                when (summary?.status) {
                    null, IndependenceCheckStatus.NOT_YET_ATTEMPTED -> R.string.progress_independence_not_yet
                    IndependenceCheckStatus.PASSED -> R.string.progress_independence_passed
                    IndependenceCheckStatus.FAILED -> R.string.progress_independence_failed
                }
            Text(text = stringResource(bodyRes), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun MasteryMapRow(
    node: MasteryMapNode,
    labelStyle: LabelStyle,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(enabled = node.verdict != null, onClick = onToggle),
    ) {
        Column(modifier = Modifier.padding(TonicSpacing.md)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = node.activeDegrees.joinToString(", ") { it.displayLabel(labelStyle) },
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(statusLabelRes(node.masteryState)),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            val blocking = node.verdict?.blockingCriterion
            if (expanded && blocking != null) {
                Spacer(modifier = Modifier.height(TonicSpacing.sm))
                val copy = copyFor(blocking, labelStyle)
                Text(
                    text = stringResource(copy.textRes, *copy.args.toTypedArray()),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

private fun statusLabelRes(masteryState: MasteryState): Int =
    when (masteryState) {
        MasteryState.LOCKED -> R.string.progress_status_locked
        MasteryState.AVAILABLE -> R.string.progress_status_available
        MasteryState.IN_PROGRESS -> R.string.progress_status_in_progress
        MasteryState.MASTERED -> R.string.progress_status_mastered
    }

@Composable
private fun DegreeAccuracyRow(
    entry: DegreeAccuracy,
    labelStyle: LabelStyle,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = TonicSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = entry.degree.displayLabel(labelStyle), modifier = Modifier.width(32.dp))
        Spacer(modifier = Modifier.width(TonicSpacing.sm))
        if (entry.accuracy != null) {
            LinearProgressIndicator(
                progress = { entry.accuracy.toFloat() },
                modifier = Modifier.weight(1f).height(8.dp),
            )
            Spacer(modifier = Modifier.width(TonicSpacing.sm))
            Text(text = stringResource(R.string.progress_degree_accuracy_value, (entry.accuracy * 100).toInt()))
        } else {
            Text(
                text = stringResource(R.string.progress_degree_accuracy_no_data),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun ConfusionRow(
    statement: ConfusionStatement,
    labelStyle: LabelStyle,
) {
    Text(
        text =
            stringResource(
                R.string.progress_confusion_statement,
                statement.target.displayLabel(labelStyle),
                statement.response.displayLabel(labelStyle),
            ),
        modifier = Modifier.fillMaxWidth().padding(vertical = TonicSpacing.xs),
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Preview(showBackground = true)
@Composable
private fun ProgressContentPreview() {
    TonicTheme(darkTheme = false) {
        ProgressContent(uiState = PreviewStates.sample, onNodeToggled = {})
    }
}
