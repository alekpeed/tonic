package com.tonic.core.model.rhythm

/**
 * How a recognition item lays its playback out in time — docs/40-PHASE-4-SPEC.md §3.3.
 *
 * §3.3's question is "which of these three patterns did you *just hear*", which only means something
 * if the learner heard one first. So the audio is the target, then every choice in turn, and the
 * learner picks the choice that matched. The target is also one of the choices by construction
 * (`M3ItemGenerator` shuffles it in among its own distractors), so it does sound twice — that is the
 * comparison being asked for, not a duplication to remove.
 *
 * **One count-in, then all of it continuously**, decided by the maintainer at Stage 4.5. The
 * alternative shapes were a count-in before every segment, which runs to about thirty seconds an item
 * at `METRONOME_FADE` L0, and a count-in before the target only, which leaves the choices without a
 * beat to be placed against — harder in a way the node is not asking about. Continuous playback also
 * keeps `METRONOME_FADE` meaning one thing: the count-in is what the axis shortens, and at L4 and
 * above the whole sequence is bare, which is the same withdrawal production gets.
 *
 * Segments are separated by [SEPARATOR_BARS] of rest rather than butted together. Two patterns with no
 * gap are one longer pattern, and the learner would have to find the seam before they could compare
 * anything.
 */
public object ChoiceSequence {
    /**
     * A full bar of rest between segments.
     *
     * A bar rather than a fixed number of beats so the gap scales with the meter, and a whole one so
     * the metronome carries the learner across it in the same pulse — at low fade levels the clicks
     * continue through the rest, which is what makes the next segment land in a metric position the
     * learner can hear rather than merely after a pause.
     */
    public const val SEPARATOR_BARS: Int = 1

    /**
     * How many bars the whole sequence occupies, separators included.
     *
     * @param segmentCount the target plus every choice.
     * @param barsPerSegment the length of one pattern, from the `PATTERN_LENGTH` axis.
     */
    public fun totalBars(
        segmentCount: Int,
        barsPerSegment: Int,
    ): Int {
        require(segmentCount >= 1) { "A sequence needs at least the target, was $segmentCount" }
        require(barsPerSegment >= 1) { "A pattern is at least one bar, was $barsPerSegment" }
        return segmentCount * barsPerSegment + (segmentCount - 1) * SEPARATOR_BARS
    }

    /**
     * The tick the segment at [index] starts on, counting from the sequence's own start.
     *
     * Shared by the generator, which plans a metronome across the whole span, and the renderer, which
     * lays the patterns onto it. Two implementations of this arithmetic would put the clicks and the
     * sounds in different places, and the failure would be a metronome that drifts out of phase with
     * the exercise rather than anything that looks like a bug.
     */
    public fun segmentStartTick(
        index: Int,
        barsPerSegment: Int,
        ticksPerBar: Int,
    ): Int {
        require(index >= 0) { "Segment index must not be negative, was $index" }
        return index * (barsPerSegment + SEPARATOR_BARS) * ticksPerBar
    }
}
