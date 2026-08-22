package com.tonic.feature.settings.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import com.tonic.core.ui.theme.TonicSpacing
import com.tonic.core.ui.theme.TonicTheme
import com.tonic.feature.settings.R

/**
 * The opt-in for answering by singing, and the only place microphone permission is ever requested —
 * docs/30-PHASE-3-SPEC.md §6.1.
 *
 * **This screen is why the sung response was unreachable.** `sungResponseEnabled` has existed in
 * `AppSettings` since Stage 3.3, defaulting to false, and until now nothing in the app could change
 * it — so the analyzer, the sung answer path and the audiation capture were all built, tested and
 * shipped behind a setting no learner could reach. The control belongs here rather than in the
 * practice screen for the reason §6.1 gives: the request must come when someone actively opts in,
 * never at install and never at launch, and Settings is the one surface a person arrives at on
 * purpose.
 *
 * The order is deliberate and is the spec's: explanation first, system dialog second. §6.1 requires
 * the request be "preceded by a plain explanation: what the mic is used for, that audio never leaves
 * the device, that it's optional, and that everything works without it." A system permission dialog
 * arriving before any of that has been said is a request the learner cannot evaluate.
 *
 * **Denial ends it.** §6.1: "denying or revoking permission silently disables singing. No nagging, no
 * repeat prompts, no degraded experience elsewhere." So a refusal leaves the toggle off and says
 * nothing further — there is no second ask, no explanation of what they are missing, and no path in
 * this file that re-raises the dialog on its own.
 */
@Composable
internal fun SungResponseSection(
    enabled: Boolean,
    octaveAgnostic: Boolean,
    onEnabledChanged: (Boolean) -> Unit,
    onOctaveAgnosticChanged: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    var explaining by remember { mutableStateOf(false) }

    // Read on each composition rather than held in state: the learner can revoke this in system
    // settings and come back, and a cached value would leave the screen describing a permission the
    // app no longer has.
    val granted =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    val requestPermission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
            // Only a grant turns singing on. A refusal is not an error state and gets no message —
            // the toggle simply stays where it was.
            if (allowed) onEnabledChanged(true)
        }

    SettingsSection(stringResource(R.string.settings_sung_heading)) {
        Column(verticalArrangement = Arrangement.spacedBy(TonicSpacing.sm)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.settings_sung_toggle),
                    style = MaterialTheme.typography.titleMedium,
                )
                Switch(
                    checked = enabled && granted,
                    onCheckedChange = { wanted ->
                        when {
                            !wanted -> onEnabledChanged(false)
                            granted -> onEnabledChanged(true)
                            // Not the system dialog yet — §6.1's explanation has to come first.
                            else -> explaining = true
                        }
                    },
                    modifier = Modifier.testTag("settings_sung_toggle"),
                )
            }

            Text(
                text = stringResource(R.string.settings_sung_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // The one case worth naming out loud: the learner turned singing on, then revoked the
            // permission later. Saying so here is not nagging — they came to this screen to look at
            // this setting, and a toggle that silently refuses to move would be the worse answer.
            if (enabled && !granted) {
                Text(
                    text = stringResource(R.string.settings_sung_permission_missing),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("settings_sung_permission_missing"),
                )
            }

            if (enabled && granted) {
                Spacer(modifier = Modifier.height(TonicSpacing.xs))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.settings_sung_octave_agnostic),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Switch(
                        checked = octaveAgnostic,
                        onCheckedChange = onOctaveAgnosticChanged,
                        modifier = Modifier.testTag("settings_sung_octave_toggle"),
                    )
                }
                Text(
                    text = stringResource(R.string.settings_sung_octave_agnostic_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (explaining) {
        MicrophoneExplanationDialog(
            onDismiss = { explaining = false },
            onContinue = {
                explaining = false
                requestPermission.launch(Manifest.permission.RECORD_AUDIO)
            },
        )
    }
}

/**
 * §6.1's four required statements, before the system dialog and in plain words.
 *
 * What the microphone is for, that nothing recorded leaves the device, that it is optional, and that
 * everything works without it. Nothing here recommends singing or frames it as the better way to
 * answer — §2 forbids copy that makes it a tier rather than an option — and the dismissing action is
 * phrased as a real choice rather than as a retreat.
 */
@Composable
private fun MicrophoneExplanationDialog(
    onDismiss: () -> Unit,
    onContinue: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_sung_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(TonicSpacing.sm)) {
                Text(stringResource(R.string.settings_sung_dialog_what))
                Text(stringResource(R.string.settings_sung_dialog_private))
                Text(stringResource(R.string.settings_sung_dialog_optional))
            }
        },
        confirmButton = {
            TextButton(
                onClick = onContinue,
                modifier = Modifier.testTag("settings_sung_dialog_continue"),
            ) { Text(stringResource(R.string.settings_sung_dialog_continue)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_sung_dialog_not_now)) }
        },
        modifier = Modifier.testTag("settings_sung_dialog"),
    )
}

@Preview(name = "Sung response - off", showBackground = true)
@Composable
private fun SungResponseOffPreview() {
    TonicTheme(darkTheme = false) {
        SungResponseSection(
            enabled = false,
            octaveAgnostic = true,
            onEnabledChanged = {},
            onOctaveAgnosticChanged = {},
        )
    }
}

@Preview(name = "Sung response - explanation", showBackground = true)
@Composable
private fun SungResponseDialogPreview() {
    TonicTheme(darkTheme = false) {
        MicrophoneExplanationDialog(onDismiss = {}, onContinue = {})
    }
}
