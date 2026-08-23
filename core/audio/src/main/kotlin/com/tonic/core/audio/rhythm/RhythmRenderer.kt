package com.tonic.core.audio.rhythm

import com.tonic.core.audio.synth.Dsp
import com.tonic.core.audio.synth.PcmBuffer
import com.tonic.core.audio.synth.SynthEngine
import com.tonic.core.model.items.Item
import com.tonic.core.model.music.TimbreId
import com.tonic.core.model.rhythm.ChoiceSequence
import com.tonic.core.model.rhythm.ClickAccent
import com.tonic.core.model.rhythm.MetronomePlan
import com.tonic.core.model.rhythm.RhythmPattern
import com.tonic.core.model.rhythm.RhythmQuestion
import com.tonic.core.model.rhythm.Tempo
import kotlin.math.roundToInt

/**
 * Renders a rhythm item to PCM — docs/40-PHASE-4-SPEC.md §9, Stage 4.2: "All 8 `METRONOME_FADE` levels
 * render correctly."
 *
 * **Mixed onto one timeline, not concatenated.** Every other renderer in this project lays sounds end
 * to end, because in the pitch track nothing ever overlaps. Here the metronome and the pattern sound
 * *at the same time* and their alignment is the entire exercise, so events are written into a single
 * buffer at sample offsets computed from their ticks. Concatenating would make the metronome and the
 * pattern each individually correct and jointly meaningless.
 *
 * Pure `FloatArray` in, `FloatArray` out, like the rest of `synth` (docs/06-AUDIO-ENGINE.md §1), so
 * every level of the fade is checkable on the JVM without a device. What still needs a device is
 * whether the result *sounds* like a metronome someone can play along with, which is Stage 4.7's
 * listening gate.
 */
public object RhythmRenderer {
    /**
     * The whole item: count-in, then whatever that item actually sounds.
     *
     * For a production item and for `M3.DOWNBEAT` that is one pattern. For a `WhichPattern` item it is
     * the target followed by every choice, laid out by [ChoiceSequence] over one continuous metronome —
     * §3.3's question is "which of these did you *just hear*", which needs the learner to have heard
     * one first.
     *
     * The returned buffer starts at the first count-in click, so a caller that plays it from sample
     * zero gets the item as designed. [RenderedRhythm.patternStartSample] says where the count-in ends
     * — the offset a tap timeline is measured from (§4.4).
     */
    public fun render(
        item: Item.RhythmItem,
        sampleRate: Int = PcmBuffer.DEFAULT_SAMPLE_RATE,
    ): RenderedRhythm = mix(item, sampleRate, sounding = true)

    /**
     * The metronome this item gives the learner to tap into, with the pattern itself silent —
     * docs/40-PHASE-4-SPEC.md §3.2.
     *
     * This is the second half of a production item and the half the fade axis is actually about. §3.2's
     * table describes what the learner has *while producing*: at L1 a click on every beat, at L4
     * "metronome stops for the pattern", at L6 two beats of count-in and then nothing. Reading those
     * levels as describing only the demonstration would make the axis a property of what the learner
     * hears rather than of what they have to hold, which is the opposite of the pitch track's cadence
     * fade it is modelled on.
     *
     * Same plan, same span, same offsets as [render] — the learner taps into a timeline identical to
     * the one they just heard, minus the sounds they are reproducing.
     */
    public fun renderBacking(
        item: Item.RhythmItem,
        sampleRate: Int = PcmBuffer.DEFAULT_SAMPLE_RATE,
    ): RenderedRhythm = mix(item, sampleRate, sounding = false)

    /**
     * Where each segment of a `WhichPattern` item begins, in milliseconds from the start of playback.
     *
     * The screen needs these to say which option is sounding right now. Without it the learner hears
     * four patterns and has to count them to know which button "2" refers to, which is a memory test
     * the node is not asking for.
     *
     * Empty for an item that sounds one pattern.
     */
    public fun segmentStartsMs(item: Item.RhythmItem): List<Double> {
        val question = item.question as? RhythmQuestion.WhichPattern ?: return emptyList()
        val msPerTick = Tempo.msPerTick(item.tempoBpm)
        val leadInTicks = -firstTickOf(item)
        return (0..question.choices.size).map { index ->
            (ChoiceSequence.segmentStartTick(index, item.pattern.bars, item.meter.ticksPerBar) + leadInTicks) *
                msPerTick
        }
    }

    /**
     * The one place ticks become samples for a rhythm item.
     *
     * @param sounding false renders the metronome alone — see [renderBacking]. The span, the offsets
     *   and the clicks are identical either way, which is what lets a learner tap into the same
     *   timeline they were shown.
     */
    private fun mix(
        item: Item.RhythmItem,
        sampleRate: Int,
        sounding: Boolean,
    ): RenderedRhythm {
        val msPerTick = Tempo.msPerTick(item.tempoBpm)
        val plan = item.metronomePlan
        val firstTick = firstTickOf(item)
        val segments = segmentsOf(item)

        // A tail so the last sound is not cut off mid-decay. Not cosmetic: an onset clipped at the
        // buffer's end is a click, and a click at the end of a rhythm is a sound the learner did not
        // play and might reasonably tap to.
        val lastPatternTick = segments.maxOfOrNull { (start, pattern) -> start + pattern.totalTicks } ?: 0
        val lastTick = maxOf(lastPatternTick, plan.clicks.maxOfOrNull { it.tick } ?: 0)
        val totalMs = (lastTick - firstTick) * msPerTick + TAIL_MS
        val mix = FloatArray(msToSamples(totalMs, sampleRate))

        fun offsetOf(tick: Int) = msToSamples((tick - firstTick) * msPerTick, sampleRate)

        for (click in plan.clicks) {
            mixInto(mix, clickBuffer(click.accent, sampleRate).samples, offsetOf(click.tick), gainFor(click.accent))
        }
        if (sounding) {
            for ((start, pattern) in segments) {
                for (tick in pattern.onsetTicks) {
                    mixInto(mix, patternBuffer(item.timbre, sampleRate).samples, offsetOf(start + tick), PATTERN_GAIN)
                }
            }
        }

        return RenderedRhythm(
            buffer = PcmBuffer(Dsp.softLimit(mix), sampleRate),
            countInDurationMs = -firstTick * msPerTick,
            patternStartSample = offsetOf(0),
        )
    }

    /**
     * Every pattern this item sounds, paired with the tick it starts on.
     *
     * One entry for a production item. For a `WhichPattern` item, the target first and then the
     * choices in the order they are offered — the target is also among the choices by construction, so
     * it sounds twice, which is the comparison the item is asking for rather than a duplication.
     */
    private fun segmentsOf(item: Item.RhythmItem): List<Pair<Int, RhythmPattern>> {
        val question = item.question
        if (question !is RhythmQuestion.WhichPattern) return listOf(0 to item.pattern)
        val patterns = listOf(item.pattern) + question.choices
        return patterns.mapIndexed { index, pattern ->
            ChoiceSequence.segmentStartTick(index, item.pattern.bars, item.meter.ticksPerBar) to pattern
        }
    }

    /**
     * How long [render] and [renderBacking] will be, without rendering either.
     *
     * The screen needs the length to know when playback ends, and rendering a second buffer to find
     * out costs a few megabytes of `FloatArray` per item to read one number off it. Pure arithmetic
     * over the same span both renders use, so it cannot disagree with them.
     */
    public fun durationMs(item: Item.RhythmItem): Double {
        val msPerTick = Tempo.msPerTick(item.tempoBpm)
        val firstTick = firstTickOf(item)
        val lastPatternTick = segmentsOf(item).maxOf { (start, pattern) -> start + pattern.totalTicks }
        val lastTick = maxOf(lastPatternTick, item.metronomePlan.clicks.maxOfOrNull { it.tick } ?: 0)
        return (lastTick - firstTick) * msPerTick + TAIL_MS
    }

    /** The earliest tick anything happens on — the count-in's first click, or zero if there is none. */
    private fun firstTickOf(item: Item.RhythmItem): Int =
        minOf(item.metronomePlan.clicks.minOfOrNull { it.tick } ?: 0, 0)

    /**
     * The metronome alone, with no pattern — what a calibration run plays (§4.3 step 1: "a steady
     * metronome plays, audible and unambiguous").
     *
     * Shares this renderer rather than growing its own so that calibration measures the learner against
     * exactly the clicks practice will use. A calibration metronome rendered by different code is a
     * constant measured against a sound the learner never hears again.
     */
    public fun renderMetronome(
        plan: MetronomePlan,
        tempoBpm: Int,
        pattern: RhythmPattern,
        sampleRate: Int = PcmBuffer.DEFAULT_SAMPLE_RATE,
    ): PcmBuffer {
        val msPerTick = Tempo.msPerTick(tempoBpm)
        val firstTick = minOf(plan.clicks.minOfOrNull { it.tick } ?: 0, 0)
        val lastTick = maxOf(pattern.totalTicks, plan.clicks.maxOfOrNull { it.tick } ?: 0)
        val mix = FloatArray(msToSamples((lastTick - firstTick) * msPerTick + TAIL_MS, sampleRate))
        for (click in plan.clicks) {
            val offset = msToSamples((click.tick - firstTick) * msPerTick, sampleRate)
            mixInto(mix, clickBuffer(click.accent, sampleRate).samples, offset, gainFor(click.accent))
        }
        return PcmBuffer(Dsp.softLimit(mix), sampleRate)
    }

    /**
     * A click, as a short pitched blip.
     *
     * Pitched rather than a noise burst, and the three accents are three pitches an octave and a fifth
     * apart. A metronome whose accents differ only in loudness is one whose downbeat disappears the
     * moment a learner turns the volume down or holds the phone at arm's length — and at
     * [ClickAccent.SUBDIVISION] density there are three sounds per beat that must stay tellable apart
     * while the pattern plays over them.
     */
    private fun clickBuffer(
        accent: ClickAccent,
        sampleRate: Int,
    ): PcmBuffer =
        SynthEngine.renderTone(
            frequencyHz = frequencyFor(accent),
            timbre = TimbreId.PURE,
            durationMs = CLICK_MS,
            sampleRate = sampleRate,
        )

    /** The pattern's own voice — the sound the learner is tapping back. */
    private fun patternBuffer(
        timbre: TimbreId,
        sampleRate: Int,
    ): PcmBuffer = SynthEngine.renderTone(PATTERN_HZ, timbre, PATTERN_MS, sampleRate)

    private fun frequencyFor(accent: ClickAccent): Double =
        when (accent) {
            ClickAccent.DOWNBEAT -> DOWNBEAT_HZ
            ClickAccent.BEAT -> BEAT_HZ
            ClickAccent.SUBDIVISION -> SUBDIVISION_HZ
        }

    /**
     * How loud each accent is, relative to the others.
     *
     * Subdivisions sit well under the beats they divide. At L0 there are twice as many of them, and a
     * subdivision at beat level does not support the pulse, it hides it.
     */
    private fun gainFor(accent: ClickAccent): Float =
        when (accent) {
            ClickAccent.DOWNBEAT -> 1.0f
            ClickAccent.BEAT -> 0.75f
            ClickAccent.SUBDIVISION -> 0.4f
        }

    /** Adds [source] into [target] at [offset], scaled. Clipped at the end rather than growing the mix. */
    private fun mixInto(
        target: FloatArray,
        source: FloatArray,
        offset: Int,
        gain: Float,
    ) {
        if (offset >= target.size) return
        val count = minOf(source.size, target.size - offset)
        for (i in 0 until count) {
            target[offset + i] += source[i] * gain
        }
    }

    private fun msToSamples(
        ms: Double,
        sampleRate: Int,
    ): Int = (ms * sampleRate / MS_PER_SECOND).roundToInt()

    private const val MS_PER_SECOND = 1000.0

    /** Short enough to be a click rather than a note; long enough to have an audible pitch. */
    private const val CLICK_MS = 40L

    private const val PATTERN_MS = 90L

    /** The pattern's voice sits below the metronome so the two never compete for the same register. */
    private const val PATTERN_HZ = 440.0

    private const val DOWNBEAT_HZ = 1568.0
    private const val BEAT_HZ = 1046.5
    private const val SUBDIVISION_HZ = 784.0

    private const val PATTERN_GAIN = 0.9f

    /** Room for the last sound to decay rather than being cut off into a click of its own. */
    private const val TAIL_MS = 250.0
}

/**
 * A rendered rhythm item, and the two offsets a caller needs to use it.
 *
 * [patternStartSample] exists because §4.4 requires taps to be measured against when the *pattern*
 * began, not when playback did — and the count-in means those are different moments. Returning it
 * beside the buffer keeps that arithmetic in one place instead of being recomputed by every caller
 * from the fade level.
 */
public data class RenderedRhythm(
    public val buffer: PcmBuffer,
    public val countInDurationMs: Double,
    public val patternStartSample: Int,
)
