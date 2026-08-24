package com.tonic.feature.practice.ui.rhythm

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tonic.core.model.rhythm.EventMatch
import com.tonic.core.model.rhythm.RhythmScore
import com.tonic.core.model.rhythm.TimingNote
import com.tonic.core.ui.theme.TonicSpacing
import com.tonic.core.ui.theme.TonicTheme
import com.tonic.feature.practice.R

/**
 * Where every tap landed, after a production item — docs/40-PHASE-4-SPEC.md §7.4.
 *
 * §7.4 singles this out: "a simple visual of where taps landed relative to where events were — this is
 * the most instructive feedback in the whole module, and worth designing properly rather than reducing
 * to a percentage."
 *
 * **One cell per expected sound, not one timeline.** A timeline is the obvious drawing and it does not
 * work: a tap 20 ms off in a pattern lasting 2.4 seconds is under one percent of the width, so every
 * mark would sit exactly on its event and the picture would say "perfect" to everyone. Each event
 * therefore gets its own cell with the beat down the middle and the tolerance window as the cell's
 * full width, which magnifies the only distance that matters to exactly the scale it matters at. It
 * also puts the right question on screen: not "how many milliseconds out were you" but "did you land
 * inside the window, and where in it".
 *
 * **No number appears anywhere**, which §7.4 requires in as many words: "never a precision grade or
 * score". The cells show position, the sentence underneath says a direction, and neither can be read
 * as a measurement to chase.
 *
 * A missed event is an empty cell with its beat line still drawn — the sound was expected and nothing
 * came, which is a different thing to see than a tap that arrived late. Extra taps are counted in the
 * sentence rather than placed, because a tap that matched no event belongs at no particular beat and
 * drawing it somewhere would invent a relationship it does not have.
 */
@Composable
internal fun TapLandingStrip(
    score: RhythmScore,
    modifier: Modifier = Modifier,
) {
    val note = TimingNote.of(score.matches.map { it.asynchronyMs }, score.toleranceHalfWidthMs)
    val description =
        stringResource(
            R.string.practice_rhythm_landing_description,
            score.matchedCount,
            score.matches.size,
            score.extraCount,
        )

    val beatColor = MaterialTheme.colorScheme.onSurfaceVariant
    val landedColor = MaterialTheme.colorScheme.primary
    val missedColor = MaterialTheme.colorScheme.error

    Column(
        modifier = modifier.fillMaxWidth().testTag(LANDING_STRIP_TAG),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(CELL_HEIGHT).semantics { contentDescription = description },
            horizontalArrangement = Arrangement.spacedBy(TonicSpacing.xs),
        ) {
            for (match in score.matches) {
                Canvas(modifier = Modifier.weight(1f).height(CELL_HEIGHT)) {
                    drawCell(match, score.toleranceHalfWidthMs, beatColor, landedColor, missedColor)
                }
            }
        }

        if (note != TimingNote.NONE) {
            Text(
                text = stringResource(timingNoteRes(note)),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = TonicSpacing.sm),
            )
        }
        if (score.extraCount > 0) {
            Text(
                text = stringResource(R.string.practice_rhythm_landing_extra),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = TonicSpacing.xs),
            )
        }
    }
}

/**
 * One event: the beat down the middle, the window as the cell, and the tap where it fell.
 *
 * The tap is clamped to the cell. It cannot genuinely fall outside — a tap beyond the window matched
 * no event and is an extra rather than a late one — but clamping means a future change to how matching
 * works cannot silently draw a mark off the edge of its own cell.
 */
private fun DrawScope.drawCell(
    match: EventMatch,
    toleranceHalfWidthMs: Double,
    beatColor: Color,
    landedColor: Color,
    missedColor: Color,
) {
    val midX = size.width / 2f
    drawLine(
        color = beatColor,
        start = Offset(midX, 0f),
        end = Offset(midX, size.height),
        strokeWidth = BEAT_LINE_WIDTH.toPx(),
    )

    val asynchrony = match.asynchronyMs
    if (asynchrony == null) {
        // Nothing came. An open ring rather than a filled dot, and in the error color: the shape says
        // "expected", the absence of fill says "and not played".
        drawCircle(
            color = missedColor,
            radius = MARK_RADIUS.toPx(),
            center = Offset(midX, size.height / 2f),
            style = Stroke(width = MISSED_RING_WIDTH.toPx()),
        )
        return
    }

    val share = (asynchrony / toleranceHalfWidthMs).coerceIn(-1.0, 1.0)
    val x = midX + (share * (size.width / 2f)).toFloat()
    drawCircle(color = landedColor, radius = MARK_RADIUS.toPx(), center = Offset(x, size.height / 2f))
}

private fun timingNoteRes(note: TimingNote): Int =
    when (note) {
        TimingNote.ON_BEAT -> R.string.practice_rhythm_timing_on_beat
        TimingNote.AHEAD -> R.string.practice_rhythm_timing_ahead
        TimingNote.BEHIND -> R.string.practice_rhythm_timing_behind
        // Never rendered - the caller checks first. Named rather than defaulted so that adding a
        // fifth note is a compile error here instead of a silently missing sentence.
        TimingNote.NONE -> R.string.practice_rhythm_timing_on_beat
    }

internal const val LANDING_STRIP_TAG: String = "rhythm_tap_landing"

private val CELL_HEIGHT = 56.dp
private val MARK_RADIUS = 7.dp
private val BEAT_LINE_WIDTH = 2.dp
private val MISSED_RING_WIDTH = 2.dp

@Preview(name = "Taps landed, slightly ahead")
@Composable
private fun TapLandingAheadPreview() {
    TonicTheme {
        TapLandingStrip(
            score =
                RhythmScore(
                    matches =
                        listOf(
                            EventMatch(0.0, -40.0),
                            EventMatch(600.0, -55.0),
                            EventMatch(1200.0, -30.0),
                            EventMatch(1800.0, -50.0),
                        ),
                    extraTaps = emptyList(),
                    toleranceHalfWidthMs = 150.0,
                    calibrationOffsetMs = 0.0,
                ),
        )
    }
}

@Preview(name = "One missed, one extra")
@Composable
private fun TapLandingMissedPreview() {
    TonicTheme {
        TapLandingStrip(
            score =
                RhythmScore(
                    matches =
                        listOf(
                            EventMatch(0.0, 10.0),
                            EventMatch(600.0, null),
                            EventMatch(1200.0, 25.0),
                            EventMatch(1800.0, -5.0),
                        ),
                    extraTaps = listOf(900.0),
                    toleranceHalfWidthMs = 150.0,
                    calibrationOffsetMs = 0.0,
                ),
        )
    }
}
