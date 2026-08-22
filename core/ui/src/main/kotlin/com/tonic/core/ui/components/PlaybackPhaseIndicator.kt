package com.tonic.core.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** What the audio is currently doing — docs/08-UI-SPEC.md §4: "a user needs to know whether the app is still playing the setup or has moved to the question." */
enum class PlaybackPhase {
    REFERENCE,
    TARGET,
    AWAITING_ANSWER,

    /**
     * `M12` only — the silent window in which the learner builds the named degree internally
     * (docs/20-PHASE-2-SPEC.md §2.3). Distinct from [REFERENCE] and [TARGET] because *nothing is
     * playing*, and the learner has to know that the silence is the exercise rather than a fault.
     * §5.3 rules out a countdown here explicitly, so this shares the calm dot treatment with every
     * other phase and shows no elapsed or remaining time at all.
     */
    AUDIATION_GAP,
}

/**
 * A large, calm, non-verbal indicator of [phase] — deliberately not a countdown or timer
 * (docs/08-UI-SPEC.md §1: "nothing that creates time pressure"). Reference and target are
 * distinguished by dot count and motion, not just color, and collapse to their final resting frame
 * instantly when [reduceMotion] is set rather than animating.
 */
@Composable
fun PlaybackPhaseIndicator(
    phase: PlaybackPhase,
    modifier: Modifier = Modifier,
    reduceMotion: Boolean = false,
) {
    val label =
        when (phase) {
            PlaybackPhase.REFERENCE -> "Setting the key"
            PlaybackPhase.TARGET -> "Playing the note"
            PlaybackPhase.AWAITING_ANSWER -> "Your turn"
            // Says what to do, not how long is left. docs/20-PHASE-2-SPEC.md §5.3 and
            // docs/02-PEDAGOGY.md §6: no time pressure, ever.
            PlaybackPhase.AUDIATION_GAP -> "Hear it in your head"
        }
    Column(
        modifier = modifier.testTag("playback_phase").semantics { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PhaseGlyph(phase, reduceMotion)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.semantics { contentDescription = label },
        )
    }
}

@Composable
private fun PhaseGlyph(
    phase: PlaybackPhase,
    reduceMotion: Boolean,
) {
    val color = MaterialTheme.colorScheme.primary
    val dotCount =
        when (phase) {
            PlaybackPhase.REFERENCE -> 3
            PlaybackPhase.TARGET -> 1
            PlaybackPhase.AWAITING_ANSWER -> 0
            // Two dots, drifting - visibly "something is coming", visibly not a countdown.
            PlaybackPhase.AUDIATION_GAP -> 2
        }

    val pulse =
        if (!reduceMotion && phase != PlaybackPhase.AWAITING_ANSWER) {
            val transition = rememberInfiniteTransition(label = "phase_pulse")
            transition
                .animateFloat(
                    initialValue = 0.5f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
                    label = "phase_pulse_alpha",
                ).value
        } else {
            1f
        }

    Canvas(
        modifier = Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 24.dp),
    ) {
        if (dotCount == 0) {
            // AWAITING_ANSWER: an open ring rather than a filled dot - "it's your move," not "something
            // is happening."
            drawCircle(
                color = color.copy(alpha = 0.9f),
                radius = size.height / 3,
                center = center,
                style = Stroke(width = 4.dp.toPx()),
            )
        } else {
            val spacing = size.width / (dotCount + 1)
            for (i in 1..dotCount) {
                drawCircle(
                    color = color.copy(alpha = pulse),
                    radius = size.height / 4,
                    center = Offset(spacing * i, size.height / 2),
                )
            }
        }
    }
}
