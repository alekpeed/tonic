package com.tonic.feature.practice.ui.rhythm

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tonic.core.ui.theme.TonicSpacing
import com.tonic.core.ui.theme.TonicTheme
import com.tonic.feature.practice.R
import kotlinx.coroutines.delay

/**
 * Where the learner taps a rhythm back — docs/40-PHASE-4-SPEC.md §7.2.
 *
 * §7.2's three requirements, in order:
 *
 * **One large target, not a small button.** The whole area, because this is tapped dozens of times in
 * a row and a thumb that has to aim is a thumb that is late. [MIN_HEIGHT] is far above
 * docs/08-UI-SPEC.md §1's 56 dp minimum, and for a different reason than that minimum exists: 56 dp
 * makes a control hittable, and this has to be hittable without looking at it.
 *
 * **Immediate visual and haptic response on every tap, independent of scoring.** Both fire here, in
 * the pointer handler, and neither waits for or consults a score. That is a deliberate departure from
 * every other haptic in this feature, which `PracticeViewModel` emits and the screen collects: that is
 * right for a haptic that *means* something about an answer, and wrong for one whose whole job is to
 * say "the glass registered your finger." Routing this through the view model would put a state
 * round-trip between the touch and the feeling of it, on the one screen in the app where a few
 * milliseconds of lag is the subject matter.
 *
 * **Audible feedback off by default.** §7.2 calls it "genuinely double-edged": it helps a learner hear
 * their own timing against the metronome, and it also adds output latency to their own feedback loop
 * and can mask the pattern. So [audibleTaps] defaults to false, and it is a setting rather than a
 * judgment made here.
 *
 * The timestamp handed up is the *pointer event's* own, not a clock read inside the handler. A handler
 * runs when the frame reaches it, which on a busy frame is tens of milliseconds after the finger
 * landed — the same order as the tolerance window itself. `uptimeMillis` is when the touch actually
 * happened, on `CLOCK_MONOTONIC`, which is also the clock `AudioTrack.getTimestamp` reports playback
 * position against. Both sides of the subtraction therefore share a base, which is the whole of what
 * §4.1 asks for.
 *
 * @param onTap called on every touch-*down* with that touch's own monotonic timestamp in
 *   milliseconds. Never on touch-up: a tap is an onset, and letting go is not a musical event.
 * @param tapCount how many taps have been recorded for this item, shown as reassurance that the
 *   surface is listening. Not a score and not a target — §7.4 forbids a precision grade, and a running
 *   count of taps against an unstated expected number would be one.
 */
@Composable
internal fun TapSurface(
    enabled: Boolean,
    onTap: (uptimeMillis: Long) -> Unit,
    modifier: Modifier = Modifier,
    hapticsEnabled: Boolean = true,
    reduceMotion: Boolean = false,
    audibleTaps: Boolean = false,
    onAudibleTap: () -> Unit = {},
    tapCount: Int = 0,
) {
    val haptics = LocalHapticFeedback.current
    val flash = remember { Animatable(0f) }

    // Bumped on every touch so the flash restarts even when a tap arrives before the previous one has
    // faded, which at any real tempo is most of them. Without a changing key the effect would not
    // relaunch and a run of taps would read as one long smear.
    val flashKey = remember { mutableIntStateOf(0) }
    LaunchedEffect(flashKey.intValue) {
        if (flashKey.intValue == 0) return@LaunchedEffect
        flash.snapTo(1f)
        if (reduceMotion) {
            // The response is not optional when motion is reduced - §7.2 requires it on every tap.
            // Only the movement is: a held tint for the same span says the same thing without it.
            delay(FLASH_FADE_MS.toLong())
            flash.snapTo(0f)
        } else {
            flash.animateTo(0f, tween(durationMillis = FLASH_FADE_MS))
        }
    }

    val base = MaterialTheme.colorScheme.secondaryContainer
    val lit = MaterialTheme.colorScheme.primary
    val description = stringResource(R.string.practice_rhythm_tap_surface_description)

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = MIN_HEIGHT)
                .clip(RoundedCornerShape(TonicSpacing.md))
                .background(blend(base, lit, flash.value))
                .semantics { contentDescription = description }
                .testTag(TAP_SURFACE_TAG)
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    awaitPointerEventScope {
                        while (true) {
                            // The initial pass, so a tap is timestamped and answered before anything
                            // downstream can consume it.
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val down =
                                event.changes.firstOrNull { it.pressed && !it.previousPressed }
                                    ?: continue
                            onTap(down.uptimeMillis)
                            flashKey.intValue++
                            if (hapticsEnabled) {
                                haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                            }
                            if (audibleTaps) onAudibleTap()
                        }
                    }
                },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text =
                if (tapCount == 0) {
                    stringResource(R.string.practice_rhythm_tap_prompt)
                } else {
                    stringResource(R.string.practice_rhythm_tap_counted, tapCount)
                },
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(TonicSpacing.md),
        )
    }
}

/**
 * A straight blend between two colors.
 *
 * Written out rather than reached for from the graphics library so that the flash's brightness is a
 * value this file states, and so a preview, a test and a device all see the same arithmetic.
 */
private fun blend(
    from: Color,
    to: Color,
    fraction: Float,
): Color {
    val f = fraction.coerceIn(0f, 1f) * MAX_TINT
    return Color(
        red = from.red + (to.red - from.red) * f,
        green = from.green + (to.green - from.green) * f,
        blue = from.blue + (to.blue - from.blue) * f,
        alpha = from.alpha + (to.alpha - from.alpha) * f,
    )
}

internal const val TAP_SURFACE_TAG: String = "rhythm_tap_surface"

private val MIN_HEIGHT = 220.dp

/** Long enough to see at a glance, short enough that two taps a sixteenth apart still read as two. */
private const val FLASH_FADE_MS = 120

/** How far toward the lit color a tap goes. A full replacement reads as a strobe over a fast pattern. */
private const val MAX_TINT = 0.7f

@Preview(name = "Tap surface, waiting")
@Composable
private fun TapSurfaceIdlePreview() {
    TonicTheme {
        TapSurface(enabled = true, onTap = {})
    }
}

@Preview(name = "Tap surface, mid-pattern")
@Composable
private fun TapSurfaceCountingPreview() {
    TonicTheme {
        TapSurface(enabled = true, onTap = {}, tapCount = 5)
    }
}
