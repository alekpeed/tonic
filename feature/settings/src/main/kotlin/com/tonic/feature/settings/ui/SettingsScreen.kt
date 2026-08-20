package com.tonic.feature.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tonic.core.model.state.AppSettings
import com.tonic.core.model.state.LabelStyle
import com.tonic.core.model.state.ThemeMode
import com.tonic.core.ui.theme.TonicSpacing
import com.tonic.core.ui.theme.TonicTheme
import com.tonic.feature.settings.R

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    if (!uiState.isLoading) {
        SettingsContent(
            settings = uiState.settings,
            onLabelStyleChanged = viewModel::onLabelStyleChanged,
            onReferenceA4HzChanged = viewModel::onReferenceA4HzChanged,
            onSessionLengthMinutesChanged = viewModel::onSessionLengthMinutesChanged,
            onHapticsEnabledChanged = viewModel::onHapticsEnabledChanged,
            onSoundEffectsEnabledChanged = viewModel::onSoundEffectsEnabledChanged,
            onThemeModeChanged = viewModel::onThemeModeChanged,
            onReduceMotionChanged = viewModel::onReduceMotionChanged,
            onDailyReminderChanged = viewModel::onDailyReminderChanged,
            discardResult = uiState.discardResult,
            onDiscardSavedSession = viewModel::onDiscardSavedSession,
        )
    }
}

@Composable
private fun SettingsContent(
    settings: AppSettings,
    onLabelStyleChanged: (LabelStyle) -> Unit,
    onReferenceA4HzChanged: (Float) -> Unit,
    onSessionLengthMinutesChanged: (Int) -> Unit,
    onHapticsEnabledChanged: (Boolean) -> Unit,
    onSoundEffectsEnabledChanged: (Boolean) -> Unit,
    onThemeModeChanged: (ThemeMode) -> Unit,
    onReduceMotionChanged: (Boolean) -> Unit,
    onDailyReminderChanged: (Boolean, String?) -> Unit,
    discardResult: DiscardResult? = null,
    onDiscardSavedSession: () -> Unit = {},
) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(TonicSpacing.md)) {
        item {
            Text(text = stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(TonicSpacing.lg))
        }

        item {
            ChoiceSection(
                heading = stringResource(R.string.settings_label_style_heading),
                options =
                    listOf(
                        LabelStyle.NUMBERS to stringResource(R.string.settings_label_style_numbers),
                        LabelStyle.SOLFEGE to stringResource(R.string.settings_label_style_solfege),
                    ),
                selected = settings.labelStyle,
                onSelected = onLabelStyleChanged,
                testTag = "settings_label_style",
            )
        }

        item {
            StepperSection(
                heading = stringResource(R.string.settings_tuning_heading),
                valueText = stringResource(R.string.settings_tuning_value, settings.referenceA4Hz.toInt()),
                onDecrement = { onReferenceA4HzChanged((settings.referenceA4Hz - 1f).coerceIn(A4_HZ_RANGE)) },
                onIncrement = { onReferenceA4HzChanged((settings.referenceA4Hz + 1f).coerceIn(A4_HZ_RANGE)) },
                testTag = "settings_reference_a4",
            )
        }

        item {
            StepperSection(
                heading = stringResource(R.string.settings_session_length_heading),
                valueText = stringResource(R.string.settings_session_length_value, settings.sessionLengthMinutes),
                onDecrement = {
                    onSessionLengthMinutesChanged((settings.sessionLengthMinutes - 1).coerceIn(SESSION_LENGTH_RANGE))
                },
                onIncrement = {
                    onSessionLengthMinutesChanged((settings.sessionLengthMinutes + 1).coerceIn(SESSION_LENGTH_RANGE))
                },
                testTag = "settings_session_length",
            )
        }

        item {
            ToggleSection(
                heading = stringResource(R.string.settings_haptics_heading),
                checked = settings.hapticsEnabled,
                onCheckedChange = onHapticsEnabledChanged,
                testTag = "settings_haptics",
            )
        }
        item {
            ToggleSection(
                heading = stringResource(R.string.settings_sound_effects_heading),
                checked = settings.soundEffectsEnabled,
                onCheckedChange = onSoundEffectsEnabledChanged,
                testTag = "settings_sound_effects",
            )
        }
        item {
            ToggleSection(
                heading = stringResource(R.string.settings_reduce_motion_heading),
                checked = settings.reduceMotion,
                onCheckedChange = onReduceMotionChanged,
                testTag = "settings_reduce_motion",
            )
        }

        item {
            ChoiceSection(
                heading = stringResource(R.string.settings_theme_heading),
                options =
                    listOf(
                        ThemeMode.SYSTEM to stringResource(R.string.settings_theme_system),
                        ThemeMode.LIGHT to stringResource(R.string.settings_theme_light),
                        ThemeMode.DARK to stringResource(R.string.settings_theme_dark),
                    ),
                selected = settings.themeMode,
                onSelected = onThemeModeChanged,
                testTag = "settings_theme",
            )
        }

        item {
            DailyReminderSection(
                enabled = settings.dailyReminderEnabled,
                time = settings.dailyReminderTime,
                onDailyReminderChanged = onDailyReminderChanged,
            )
        }

        item {
            SettingsSection(stringResource(R.string.settings_saved_session_heading)) {
                Text(
                    text = stringResource(R.string.settings_discard_session_body),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(TonicSpacing.sm))
                TextButton(
                    onClick = onDiscardSavedSession,
                    modifier = Modifier.testTag("settings_discard_session"),
                ) {
                    Text(stringResource(R.string.settings_discard_session))
                }
                // §2a: the press visibly did something, whichever way it went.
                discardResult?.let { result ->
                    Text(
                        text =
                            stringResource(
                                when (result) {
                                    DiscardResult.DISCARDED -> R.string.settings_discard_session_done
                                    DiscardResult.NOTHING_SAVED -> R.string.settings_discard_session_none
                                },
                            ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.testTag("settings_discard_result"),
                    )
                }
            }
        }
    }
}

@Composable
private fun <T> ChoiceSection(
    heading: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelected: (T) -> Unit,
    testTag: String,
) {
    SettingsSection(heading) {
        Row(horizontalArrangement = Arrangement.spacedBy(TonicSpacing.sm)) {
            options.forEach { (value, label) ->
                FilterChip(
                    selected = value == selected,
                    onClick = { onSelected(value) },
                    label = { Text(label) },
                    modifier = Modifier.testTag("${testTag}_${value.toString().lowercase()}"),
                )
            }
        }
    }
}

@Composable
private fun StepperSection(
    heading: String,
    valueText: String,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    testTag: String,
) {
    SettingsSection(heading) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onDecrement, modifier = Modifier.testTag("${testTag}_decrement")) {
                Text("−", style = MaterialTheme.typography.titleLarge)
            }
            Text(
                text = valueText,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = TonicSpacing.md).testTag("${testTag}_value"),
            )
            IconButton(onClick = onIncrement, modifier = Modifier.testTag("${testTag}_increment")) {
                Text("+", style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}

@Composable
private fun ToggleSection(
    heading: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = TonicSpacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = heading, style = MaterialTheme.typography.titleMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange, modifier = Modifier.testTag(testTag))
    }
}

@Composable
private fun DailyReminderSection(
    enabled: Boolean,
    time: String?,
    onDailyReminderChanged: (Boolean, String?) -> Unit,
    discardResult: DiscardResult? = null,
    onDiscardSavedSession: () -> Unit = {},
) {
    val defaultTime = stringResource(R.string.settings_daily_reminder_time_morning)
    SettingsSection(stringResource(R.string.settings_daily_reminder_heading)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = stringResource(R.string.settings_daily_reminder_heading))
            Switch(
                checked = enabled,
                onCheckedChange = { checked -> onDailyReminderChanged(checked, time ?: defaultTime) },
                modifier = Modifier.testTag("settings_daily_reminder_toggle"),
            )
        }
        if (enabled) {
            Spacer(modifier = Modifier.height(TonicSpacing.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(TonicSpacing.sm)) {
                listOf(
                    stringResource(R.string.settings_daily_reminder_time_morning),
                    stringResource(R.string.settings_daily_reminder_time_afternoon),
                    stringResource(R.string.settings_daily_reminder_time_evening),
                ).forEach { preset ->
                    FilterChip(
                        selected = time == preset,
                        onClick = { onDailyReminderChanged(true, preset) },
                        label = { Text(preset) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(
    heading: String,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.padding(vertical = TonicSpacing.sm)) {
        Text(text = heading, style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(TonicSpacing.xs))
        content()
    }
}

private val A4_HZ_RANGE = 415f..466f
private val SESSION_LENGTH_RANGE = 3..15

@Preview(showBackground = true)
@Composable
private fun SettingsContentPreview() {
    TonicTheme(darkTheme = false) {
        SettingsContent(
            settings = AppSettings(),
            onLabelStyleChanged = {},
            onReferenceA4HzChanged = {},
            onSessionLengthMinutesChanged = {},
            onHapticsEnabledChanged = {},
            onSoundEffectsEnabledChanged = {},
            onThemeModeChanged = {},
            onReduceMotionChanged = {},
            onDailyReminderChanged = { _, _ -> },
        )
    }
}
