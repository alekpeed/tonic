package com.tonic.core.audio.rhythm

import com.tonic.core.model.ids.SkillIds
import com.tonic.core.model.items.Item
import com.tonic.core.model.music.TimbreId
import com.tonic.core.model.rhythm.ChoiceSequence
import com.tonic.core.model.rhythm.Meter
import com.tonic.core.model.rhythm.MetronomeFadeLevel
import com.tonic.core.model.rhythm.MetronomePlanner
import com.tonic.core.model.rhythm.RhythmPattern
import com.tonic.core.model.rhythm.RhythmQuestion
import com.tonic.core.model.rhythm.Tempo
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * docs/40-PHASE-4-SPEC.md §9, Stage 4.2: "All 8 `METRONOME_FADE` levels render correctly."
 *
 * `MetronomePlannerTest` establishes that each level plans the right clicks; these establish that the
 * plan reaches the audio. The two are separate on purpose — a renderer that silently dropped the
 * count-in would leave every planner test green.
 *
 * What no JVM test can settle is whether the result *sounds* like a metronome a person can play along
 * with. That is Stage 4.7's listening gate, and it needs a device.
 */
class RhythmRendererTest {
    private val meter = Meter.FOUR_FOUR
    private val bars = 2

    private fun item(
        level: MetronomeFadeLevel,
        tempoBpm: Int = 100,
        question: RhythmQuestion = RhythmQuestion.TapItBack,
        planBars: Int = bars,
    ): Item.RhythmItem {
        val pattern = RhythmPattern(meter, bars, (0 until bars * meter.beatsPerBar).map { it * Meter.TICKS_PER_BEAT })
        return Item.RhythmItem(
            skill = SkillIds.M3_BEAT_FIND,
            meter = meter,
            tempoBpm = tempoBpm,
            pattern = pattern,
            metronomePlan = MetronomePlanner.plan(level, meter, planBars),
            question = question,
            timbre = TimbreId.PURE,
            seed = 1L,
        )
    }

    /** Plain beats with the second one split, so it is tellable apart from the plain pattern. */
    private fun divided() =
        RhythmPattern(
            meter,
            bars,
            (
                (0 until bars * meter.beatsPerBar).map { it * Meter.TICKS_PER_BEAT } +
                    listOf(Meter.TICKS_PER_BEAT + Meter.TICKS_PER_BEAT / 2)
            ).sorted(),
        )

    /** Peak absolute sample over a window, as a cheap "is anything sounding here". */
    private fun peakBetween(
        samples: FloatArray,
        fromMs: Double,
        toMs: Double,
        sampleRate: Int,
    ): Float {
        val from = (fromMs * sampleRate / 1000.0).toInt().coerceIn(0, samples.size)
        val to = (toMs * sampleRate / 1000.0).toInt().coerceIn(from, samples.size)
        var peak = 0f
        for (i in from until to) peak = maxOf(peak, abs(samples[i]))
        return peak
    }

    @Test
    fun `every fade level renders audio, and never clips`() {
        // docs/06-AUDIO-ENGINE.md §2: "hard clipping must never occur, because clipping generates
        // harmonics that are themselves a pitch cue." Here it would also blur the attack a learner is
        // timing against.
        for (level in MetronomeFadeLevel.entries) {
            val rendered = RhythmRenderer.render(item(level))
            assertTrue(rendered.buffer.samples.isNotEmpty(), "$level rendered nothing")
            assertTrue(
                rendered.buffer.samples.all { abs(it) < 1.0f },
                "$level clips: peak ${rendered.buffer.samples.maxOf { abs(it) }}",
            )
        }
    }

    @Test
    fun `the count-in is heard before the pattern at every level that has one`() {
        for (level in MetronomeFadeLevel.entries) {
            val rendered = RhythmRenderer.render(item(level))
            val expectedCountInBeats =
                MetronomePlanner.plan(level, meter, bars).countIn.count { it.tick % Meter.TICKS_PER_BEAT == 0 }
            val expectedMs = expectedCountInBeats * Tempo.msPerBeat(100)
            assertEquals(expectedMs, rendered.countInDurationMs, 0.001, "$level's count-in is the wrong length")
        }
    }

    @Test
    fun `L7 starts at the pattern, with nothing in front of it`() {
        val rendered = RhythmRenderer.render(item(MetronomeFadeLevel.L7))
        assertEquals(0.0, rendered.countInDurationMs, 0.001)
        assertEquals(0, rendered.patternStartSample)
    }

    @Test
    fun `the metronome goes quiet under the pattern from L4 up`() {
        // The fade, audible rather than merely planned. §7.3's warning is about a crutch silently
        // re-supplied, and a renderer that ignored the plan would re-supply it in exactly this way.
        val sampleRate = rendered(MetronomeFadeLevel.L4).buffer.sampleRate
        for (level in listOf(
            MetronomeFadeLevel.L4,
            MetronomeFadeLevel.L5,
            MetronomeFadeLevel.L6,
            MetronomeFadeLevel.L7,
        )) {
            val r = RhythmRenderer.render(item(level))
            // Halfway between two beats of the pattern: the metronome would sound on the beat, and the
            // pattern's own onsets are on the beat too, so a quiet midpoint means nothing extra is there.
            val beatMs = Tempo.msPerBeat(100)
            val start = r.countInDurationMs + beatMs * 1.45
            val peak = peakBetween(r.buffer.samples, start, start + beatMs * 0.1, sampleRate)
            assertTrue(peak < QUIET, "$level sounds between beats under the pattern: peak $peak")
        }
    }

    @Test
    fun `L0 sounds between the beats and L1 does not`() {
        // The audible difference between "every subdivision" and "every beat", measured where they
        // differ: halfway through a beat.
        val sampleRate = rendered(MetronomeFadeLevel.L0).buffer.sampleRate
        val beatMs = Tempo.msPerBeat(100)

        val zero = RhythmRenderer.render(item(MetronomeFadeLevel.L0))
        val zeroPeak =
            peakBetween(
                zero.buffer.samples,
                zero.countInDurationMs + beatMs * 0.5,
                zero.countInDurationMs + beatMs * 0.62,
                sampleRate,
            )
        assertTrue(zeroPeak > QUIET, "L0 should click on the half-beat, peak was $zeroPeak")

        val one = RhythmRenderer.render(item(MetronomeFadeLevel.L1))
        val onePeak =
            peakBetween(
                one.buffer.samples,
                one.countInDurationMs + beatMs * 0.5,
                one.countInDurationMs + beatMs * 0.62,
                sampleRate,
            )
        assertTrue(onePeak < QUIET, "L1 should be silent on the half-beat, peak was $onePeak")
    }

    @Test
    fun `tempo changes the length, and the onsets move with it`() {
        val slow = RhythmRenderer.render(item(MetronomeFadeLevel.L4, tempoBpm = 60))
        val fast = RhythmRenderer.render(item(MetronomeFadeLevel.L4, tempoBpm = 140))
        assertTrue(
            slow.buffer.durationMs > fast.buffer.durationMs,
            "a slower tempo must produce a longer item: ${slow.buffer.durationMs} vs ${fast.buffer.durationMs}",
        )
        // The count-in is two bars at both tempi, so its length scales with the beat exactly.
        assertEquals(8 * Tempo.msPerBeat(60), slow.countInDurationMs, 0.001)
        assertEquals(8 * Tempo.msPerBeat(140), fast.countInDurationMs, 0.001)
    }

    @Test
    fun `the same item renders byte-identically every time`() {
        // CLAUDE.md §5, applied to audio: docs/06-AUDIO-ENGINE.md §10 already requires "same seed
        // produces a byte-identical buffer" of the pitch renderer, and rhythm inherits the requirement.
        val first = RhythmRenderer.render(item(MetronomeFadeLevel.L3))
        val second = RhythmRenderer.render(item(MetronomeFadeLevel.L3))
        assertTrue(first.buffer.samples.contentEquals(second.buffer.samples), "rendering is not deterministic")
    }

    @Test
    fun `the pattern starts where the renderer says it does`() {
        val r = RhythmRenderer.render(item(MetronomeFadeLevel.L4))
        val sampleRate = r.buffer.sampleRate
        val expected = (r.countInDurationMs * sampleRate / 1000.0).toInt()
        assertTrue(
            abs(r.patternStartSample - expected) <= 1,
            "patternStartSample ${r.patternStartSample} does not match countInDurationMs ${r.countInDurationMs}",
        )
    }

    @Test
    fun `a bare metronome renders for calibration`() {
        // §4.3 step 1. Shares this renderer so a calibration constant is measured against exactly the
        // clicks practice will use.
        val pattern = RhythmPattern(meter, bars, listOf(0))
        val plan = MetronomePlanner.plan(MetronomeFadeLevel.L1, meter, bars)
        val buffer = RhythmRenderer.renderMetronome(plan, tempoBpm = 100, pattern = pattern)
        assertTrue(buffer.samples.any { abs(it) > QUIET }, "the calibration metronome is silent")
        assertTrue(buffer.samples.all { abs(it) < 1.0f }, "the calibration metronome clips")
    }

    private fun rendered(level: MetronomeFadeLevel) = RhythmRenderer.render(item(level))

    private companion object {
        /** Below this, nothing meaningful is sounding. Well above the tail of a decayed click. */
        const val QUIET = 0.02f

        /** Long enough to contain a click or an onset, short enough not to reach the next one. */
        const val SEGMENT_PROBE_MS = 60.0
    }

    @Test
    fun `a which-pattern item sounds the target and then every choice`() {
        // §3.3's "which of these did you just hear" needs the learner to have heard one first, and the
        // choices to arrive one at a time in the order the buttons name. Before this, render() laid
        // only item.pattern, so a recognition item played its answer once and offered three buttons.
        val full = divided()
        val choices = listOf(full, full.copy(onsetTicks = full.onsetTicks.dropLast(1)))
        val question = RhythmQuestion.WhichPattern(choices = choices, answerIndex = 0)
        val segmentCount = choices.size + 1
        val subject =
            item(
                MetronomeFadeLevel.L4,
                question = question,
                planBars = ChoiceSequence.totalBars(segmentCount, bars),
            )
        val rendered = RhythmRenderer.render(subject)
        val sampleRate = rendered.buffer.sampleRate
        val starts = RhythmRenderer.segmentStartsMs(subject)

        assertEquals(segmentCount, starts.size, "the target plus every choice")
        // L4 has a count-in and nothing under the pattern, so anything sounding at a segment start is
        // the pattern itself rather than a click.
        for (start in starts) {
            val peak = peakBetween(rendered.buffer.samples, start, start + SEGMENT_PROBE_MS, sampleRate)
            assertTrue(peak > QUIET, "nothing sounds at segment start ${start}ms")
        }

        // And the separator really is silent, or the segments run together into one longer pattern
        // with no seam for the learner to find.
        val segmentMs = bars * meter.beatsPerBar * Tempo.msPerBeat(subject.tempoBpm)
        val gapPeak =
            peakBetween(
                rendered.buffer.samples,
                starts[0] + segmentMs + SEGMENT_PROBE_MS,
                starts[1] - SEGMENT_PROBE_MS,
                sampleRate,
            )
        assertTrue(gapPeak < QUIET, "the bar between segments must be silent at L4")
    }

    @Test
    fun `the backing is the same timeline with the pattern taken out`() {
        // §3.2's fade table describes what the learner has *while producing*, so the backing has to be
        // the identical timeline they were just shown - same count-in, same clicks, same offsets -
        // minus the sounds they are reproducing.
        val subject = item(MetronomeFadeLevel.L1)
        val heard = RhythmRenderer.render(subject)
        val backing = RhythmRenderer.renderBacking(subject)

        assertEquals(heard.buffer.samples.size, backing.buffer.samples.size)
        assertEquals(heard.countInDurationMs, backing.countInDurationMs)
        assertEquals(heard.patternStartSample, backing.patternStartSample)
    }

    @Test
    fun `at L4 the backing falls silent once the count-in ends`() {
        // The level where the learner first keeps time unaided. If renderBacking left anything sounding
        // here, METRONOME_FADE would stop meaning anything from L4 up - which is the half of the axis
        // that trains internal pulse.
        val subject = item(MetronomeFadeLevel.L4)
        val backing = RhythmRenderer.renderBacking(subject)
        val sampleRate = backing.buffer.sampleRate
        val patternStartMs = backing.countInDurationMs

        assertTrue(
            peakBetween(backing.buffer.samples, 0.0, patternStartMs, sampleRate) > QUIET,
            "the count-in must still be there - the learner needs the tempo",
        )
        val underPatternPeak =
            peakBetween(
                backing.buffer.samples,
                patternStartMs + SEGMENT_PROBE_MS,
                patternStartMs + bars * meter.beatsPerBar * Tempo.msPerBeat(subject.tempoBpm),
                sampleRate,
            )
        assertTrue(underPatternPeak < QUIET, "nothing may sound under the pattern at L4")
    }
}
