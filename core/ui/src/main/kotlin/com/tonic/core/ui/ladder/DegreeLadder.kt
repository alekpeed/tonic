package com.tonic.core.ui.ladder

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tonic.core.model.music.ScaleDegree
import com.tonic.core.model.state.LabelStyle
import com.tonic.core.ui.labels.displayLabel
import com.tonic.core.ui.theme.TonicSpacing
import com.tonic.core.ui.theme.TonicTheme

/** One button's visual treatment — docs/08-UI-SPEC.md §3: "idle, pressed, correct (brief), incorrect (brief), disabled." Pressed is Compose's own ripple/interaction state, not modeled here. */
enum class DegreeButtonState { IDLE, CORRECT, INCORRECT, DISABLED }

/**
 * The answer input for all of Module 2 — docs/08-UI-SPEC.md §3, "the key widget." A vertical column,
 * lowest scale degree at the bottom, ascending upward: the spatial metaphor reinforces that these are
 * ordered positions in a scale, not arbitrary labels. Every one of the seven diatonic positions always
 * renders — degrees outside [activeDegrees] show as dimmed, non-interactive gaps, so the ladder's shape
 * never reshuffles as new degrees are introduced (docs/03-CURRICULUM.md §5.2).
 *
 * Pure and stateless (docs/04-ARCHITECTURE.md §3: `:core:ui` may depend only on `:core:model`) — the
 * caller decides which degree was chosen, which is correct, and whether input is currently accepted.
 */
@Composable
fun DegreeLadder(
    activeDegrees: List<ScaleDegree>,
    labelStyle: LabelStyle,
    enabled: Boolean,
    selectedDegree: ScaleDegree?,
    correctDegree: ScaleDegree?,
    onDegreeSelected: (ScaleDegree) -> Unit,
    modifier: Modifier = Modifier,
    reduceMotion: Boolean = false,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(TonicSpacing.sm),
    ) {
        // Degree 7 first (top of the column) down to degree 1 last (bottom) - ascending pitch maps to
        // ascending screen position.
        for (degree in 7 downTo 1) {
            val scaleDegree = ScaleDegree(degree)
            if (scaleDegree in activeDegrees) {
                val state =
                    when {
                        correctDegree == scaleDegree -> DegreeButtonState.CORRECT
                        selectedDegree == scaleDegree && correctDegree != null -> DegreeButtonState.INCORRECT
                        !enabled -> DegreeButtonState.DISABLED
                        else -> DegreeButtonState.IDLE
                    }
                DegreeButton(
                    degree = scaleDegree,
                    label = scaleDegree.displayLabel(labelStyle),
                    state = state,
                    reduceMotion = reduceMotion,
                    onClick = { onDegreeSelected(scaleDegree) },
                )
            } else {
                InactiveDegreeGap()
            }
        }
    }
}

@Composable
private fun DegreeButton(
    degree: ScaleDegree,
    label: String,
    state: DegreeButtonState,
    reduceMotion: Boolean,
    onClick: () -> Unit,
) {
    val extended = TonicTheme.extendedColors
    val targetContainer =
        when (state) {
            DegreeButtonState.CORRECT -> extended.correct.copy(alpha = 0.22f)
            DegreeButtonState.INCORRECT -> extended.incorrect.copy(alpha = 0.22f)
            DegreeButtonState.IDLE -> MaterialTheme.colorScheme.primaryContainer
            DegreeButtonState.DISABLED -> MaterialTheme.colorScheme.surfaceVariant
        }
    val targetBorder =
        when (state) {
            DegreeButtonState.CORRECT -> extended.correct
            DegreeButtonState.INCORRECT -> extended.incorrect
            else -> Color.Transparent
        }
    // Color is never the only signal (docs/08-UI-SPEC.md §8) - CORRECT/INCORRECT also get a border and
    // a leading glyph (below), so the state still reads under a red/green color-vision deficiency.
    val container =
        if (reduceMotion) {
            targetContainer
        } else {
            animateColorAsState(
                targetContainer,
                tween(150),
                label = "container",
            ).value
        }
    val border =
        if (reduceMotion) {
            targetBorder
        } else {
            animateColorAsState(
                targetBorder,
                tween(150),
                label = "border",
            ).value
        }

    val description =
        when (state) {
            DegreeButtonState.CORRECT -> "$label, scale degree ${degree.degree}, correct answer"
            DegreeButtonState.INCORRECT -> "$label, scale degree ${degree.degree}, your answer, incorrect"
            else -> "$label, scale degree ${degree.degree}"
        }

    TextButton(
        onClick = onClick,
        enabled = state != DegreeButtonState.DISABLED,
        modifier =
            Modifier
                .fillMaxWidth()
                .height(TonicSpacing.minTouchTarget)
                .testTag("degree_button_${degree.degree}")
                .semantics { contentDescription = description }
                .background(container, RoundedCornerShape(TonicSpacing.sm))
                .border(2.dp, border, RoundedCornerShape(TonicSpacing.sm)),
    ) {
        val glyph =
            when (state) {
                DegreeButtonState.CORRECT -> "✓ "
                DegreeButtonState.INCORRECT -> "✕ "
                else -> ""
            }
        Text(text = glyph + label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

/**
 * A non-interactive placeholder holding the ladder's shape for a degree not in the current active set —
 * docs/08-UI-SPEC.md §3's "dimmed, non-interactive gaps."
 *
 * Distinguished from a real button by **shape, not fill**. It occupies the same vertical slot so the
 * ladder never reshuffles as degrees are introduced, but draws only a short, thin, centered rule — no
 * full-width footprint, no rounded-button silhouette, nothing that reads as a control with a missing
 * label. That distinction was previously carried entirely by a fill-colour difference, which measured
 * 1.54:1 in the fallback dark scheme and 1.18:1 in light, and which docs/08-UI-SPEC.md §8's
 * dynamic-colour requirement puts outside our control anyway: `primaryContainer` and `surfaceVariant`
 * come from the user's wallpaper on Android 12+, and Material guarantees contrast *within* a role pair,
 * never *between* two roles we happened to compare. A user reported exactly the predicted failure —
 * blank boxes indistinguishable from buttons. Geometry survives any palette; a colour delta does not.
 *
 * [Modifier.clearAndSetSemantics] with an empty block removes it from the accessibility tree entirely -
 * docs/08-UI-SPEC.md §9's TalkBack requirement is about the real buttons; a screen-reader user gains
 * nothing from tabbing onto silent placeholders and loses time doing it.
 */
@Composable
private fun InactiveDegreeGap() {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(TonicSpacing.minTouchTarget)
                .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth(GAP_RULE_WIDTH_FRACTION)
                    .height(GAP_RULE_THICKNESS)
                    .background(
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = GAP_RULE_ALPHA),
                        RoundedCornerShape(GAP_RULE_THICKNESS / 2),
                    ),
        )
    }
}

/** Narrow enough that it cannot be mistaken for the full-width buttons above and below it. */
private const val GAP_RULE_WIDTH_FRACTION = 0.18f

/** A hairline: present enough to hold the ladder's rhythm, far too thin to read as a tappable surface. */
private val GAP_RULE_THICKNESS = 2.dp

/**
 * Drawn from `onSurfaceVariant` rather than a container colour: it is *ink*, and every Material scheme -
 * dynamic ones included - guarantees `onSurfaceVariant` is legible against the surface behind it.
 */
private const val GAP_RULE_ALPHA = 0.5f
