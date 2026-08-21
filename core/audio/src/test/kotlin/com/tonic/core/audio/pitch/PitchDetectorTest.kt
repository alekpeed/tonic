package com.tonic.core.audio.pitch

import com.tonic.core.audio.synth.PcmBuffer
import com.tonic.core.audio.synth.SynthEngine
import com.tonic.core.model.music.TimbreId
import com.tonic.core.model.music.Tuning
import kotlin.math.abs
import kotlin.math.ln
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * docs/30-PHASE-3-SPEC.md Stage 3.0, first acceptance criterion: "Detection accurate to within a
 * semitone on synthetic and recorded test signals."
 *
 * This covers the synthetic half, and it can be proven with no microphone and no device because the app
 * synthesizes its own stimuli: [SynthEngine.renderTone] produces a buffer at an *exactly known*
 * frequency, in any of the four timbres, deterministically from a seed. The detector is then measured
 * against ground truth the app itself defined. The recorded half needs hardware and is deliberately not
 * faked here — see the class-level note at the bottom of this file.
 *
 * Tolerances below are far tighter than the stage requires. A semitone is the *contract*; reporting only
 * that would hide a regression that doubled the error while still passing, and docs/10-TESTING.md §5 asks
 * for measurement to be treated as a report rather than a pass/fail.
 */
class PitchDetectorTest {
    private val sampleRate = PcmBuffer.DEFAULT_SAMPLE_RATE
    private val noteDurationMs = 700L
    private val windowSize = 2048

    /**
     * Register points spanning the detector's declared range, chosen to sit inside a plausible sung
     * range rather than the full synthesis register: F2 through G5 covers an untrained bass to an
     * untrained soprano, which is who Phase 3 is for.
     */
    private val registerMidi = listOf(41, 48, 57, 64, 72, 79)

    /** A window from the sustained middle of the note, skipping attack and release — §5.2 step 4. */
    private fun steadyWindow(buffer: PcmBuffer): FloatArray {
        val start = (buffer.samples.size - windowSize) / 2
        return buffer.samples.copyOfRange(start, start + windowSize)
    }

    private fun centsOff(
        measuredHz: Double,
        targetHz: Double,
    ): Double = 1200.0 * (ln(measuredHz / targetHz) / ln(2.0))

    /**
     * REED applies vibrato of ±8 cents at 5 Hz after a 200 ms onset (docs/06-AUDIO-ENGINE.md §3), so its
     * instantaneous pitch genuinely is up to 8 cents from nominal inside any window taken from the
     * sustained portion. That is the stimulus behaving as specified, not detector error, and the
     * tolerance says so rather than the test pretending otherwise.
     */
    private fun tightToleranceCents(timbre: TimbreId): Double = if (timbre == TimbreId.REED) 16.0 else 8.0

    @Test
    fun `detects every timbre across the sung register, well inside the required semitone`() {
        var worstCents = 0.0
        var worstDescription = ""
        val report = StringBuilder()

        for (timbre in TimbreId.entries) {
            for (midi in registerMidi) {
                val targetHz = Tuning.midiToHz(midi)
                val buffer = SynthEngine.renderTone(targetHz, timbre, noteDurationMs, sampleRate, seed = 7L)
                val estimate = PitchDetector.detect(steadyWindow(buffer), sampleRate)

                assertNotNull(estimate, "no pitch detected for $timbre at MIDI $midi (${targetHz.toInt()} Hz)")

                val error = abs(centsOff(estimate.frequencyHz, targetHz))
                report.append(
                    "  $timbre MIDI $midi: ${"%.2f".format(error)} cents, " +
                        "clarity ${"%.3f".format(estimate.clarity)}\n",
                )

                assertTrue(
                    error < 100.0,
                    "$timbre at MIDI $midi was $error cents off — Stage 3.0 requires within a semitone",
                )
                assertTrue(
                    error < tightToleranceCents(timbre),
                    "$timbre at MIDI $midi was $error cents off, above this timbre's expected ceiling",
                )

                if (error > worstCents) {
                    worstCents = error
                    worstDescription = "$timbre at MIDI $midi"
                }
            }
        }

        println(
            "PitchDetector accuracy across ${TimbreId.entries.size} timbres " +
                "x ${registerMidi.size} register points:",
        )
        print(report)
        println("  worst: ${"%.2f".format(worstCents)} cents ($worstDescription); requirement is 100.0")
    }

    /**
     * The failure this detector was chosen to avoid, asserted directly rather than inferred from the
     * accuracy bound. An octave error is uniquely dangerous here: §5.2 step 5 discards octave
     * information on purpose so that singing a degree in any comfortable register scores correct, which
     * means a detected octave slip is erased into a confident *wrong degree* instead of surfacing as a
     * fault. It cannot be caught downstream, so it is pinned here.
     */
    @Test
    fun `never reports a harmonic or subharmonic instead of the fundamental`() {
        for (timbre in TimbreId.entries) {
            for (midi in registerMidi) {
                val targetHz = Tuning.midiToHz(midi)
                val buffer = SynthEngine.renderTone(targetHz, timbre, noteDurationMs, sampleRate, seed = 11L)
                val estimate = PitchDetector.detect(steadyWindow(buffer), sampleRate)
                assertNotNull(estimate, "no pitch detected for $timbre at MIDI $midi")

                val ratio = estimate.frequencyHz / targetHz
                assertTrue(
                    ratio > 0.9 && ratio < 1.1,
                    "$timbre at MIDI $midi detected ${estimate.frequencyHz} Hz against $targetHz Hz " +
                        "(ratio $ratio) — an octave or harmonic error",
                )
            }
        }
    }

    /**
     * §6.4 shows the learner how far off they were, as non-scoring information. That readout is only
     * honest if the detector resolves deviations well below the degree spacing it is otherwise rounding
     * to. 30 cents is the specific figure Phase 2 already uses for a deliberate detune
     * (`PREDICT_DEVIATION` level 3), so it is the deviation most worth being able to measure.
     */
    @Test
    fun `resolves a 30-cent detune as a 30-cent detune`() {
        val baseHz = Tuning.midiToHz(57)
        val detunedHz = Tuning.offsetByCents(baseHz, 30.0)

        for (timbre in TimbreId.entries) {
            val buffer = SynthEngine.renderTone(detunedHz, timbre, noteDurationMs, sampleRate, seed = 3L)
            val estimate = PitchDetector.detect(steadyWindow(buffer), sampleRate)
            assertNotNull(estimate, "no pitch detected for detuned $timbre")

            val measured = centsOff(estimate.frequencyHz, baseHz)
            assertTrue(
                abs(measured - 30.0) < tightToleranceCents(timbre),
                "$timbre: detune measured as $measured cents, expected about 30",
            )
        }
    }

    /**
     * §5.2's central rule: "unclear" is never "wrong." Silence, noise, and a window too short to hold a
     * period must all decline to answer rather than guess, because a guess here is recorded as an
     * incorrect attempt and enters the staircase and the confusion matrix — the structures every
     * adaptive decision is computed from.
     */
    @Test
    fun `declines to answer on silence, noise, and windows too short to carry a period`() {
        val silence = FloatArray(windowSize)
        assertEquals(null, PitchDetector.detect(silence, sampleRate), "silence must not produce a pitch")

        val random = Random(42)
        val noise = FloatArray(windowSize) { (random.nextDouble() * 2.0 - 1.0).toFloat() * 0.3f }
        val noiseEstimate = PitchDetector.detect(noise, sampleRate)
        if (noiseEstimate != null) {
            assertTrue(
                noiseEstimate.clarity < 0.6,
                "white noise reported clarity ${noiseEstimate.clarity} — a confidence threshold could not " +
                    "distinguish it from a sung note",
            )
        }

        // 64 samples at 48 kHz is 1.3 ms: shorter than one period of anything in range.
        val tooShort = SynthEngine
            .renderTone(Tuning.midiToHz(57), TimbreId.PURE, noteDurationMs, sampleRate, seed = 1L)
            .samples
            .copyOfRange(0, 64)
        assertEquals(null, PitchDetector.detect(tooShort, sampleRate), "a sub-period window must not produce a pitch")
    }

    /**
     * A sustained sung vowel should read as strongly periodic, or §5.2 step 2's confidence threshold has
     * nothing to separate. Asserted as a floor across every timbre so the threshold chosen in Stage 3.1
     * has a measured basis rather than a guessed one.
     */
    @Test
    fun `reports high clarity on a sustained tone, giving the confidence threshold something to work with`() {
        for (timbre in TimbreId.entries) {
            val buffer = SynthEngine.renderTone(Tuning.midiToHz(57), timbre, noteDurationMs, sampleRate, seed = 5L)
            val estimate = PitchDetector.detect(steadyWindow(buffer), sampleRate)
            assertNotNull(estimate, "no pitch detected for $timbre")
            assertTrue(
                estimate.clarity > 0.8,
                "$timbre reported clarity ${estimate.clarity} on a sustained tone",
            )
        }
    }

    /** CLAUDE.md §5: a pure function over its inputs, so the same window must always give the same answer. */
    @Test
    fun `is deterministic`() {
        val window = steadyWindow(
            SynthEngine.renderTone(Tuning.midiToHz(64), TimbreId.SOFT, noteDurationMs, sampleRate, seed = 9L),
        )
        val first = PitchDetector.detect(window, sampleRate)
        val second = PitchDetector.detect(window, sampleRate)
        assertEquals(first, second)
    }
}

/*
 * Not covered here, and not fakeable: Stage 3.0's remaining acceptance criteria — accuracy on *recorded*
 * signals, running off the main thread, absence of dropouts, and measured latency and CPU. Every one of
 * those is a property of capture on real hardware, which this environment does not have. Writing a
 * simulated microphone to produce a green check for them would be the exact failure docs/21-HANDOFF.md §6
 * records: a fake cannot disagree with you.
 */
