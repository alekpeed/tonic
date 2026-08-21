package com.tonic.core.audio.pitch

import com.tonic.core.audio.synth.PcmBuffer
import com.tonic.core.model.music.Mode
import com.tonic.core.model.music.PitchClass
import com.tonic.core.model.music.ScaleDegree
import com.tonic.core.model.music.SungAnswer
import com.tonic.core.model.music.Tuning
import com.tonic.core.model.music.UnclearReason
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * docs/30-PHASE-3-SPEC.md Stage 3.1: "Scooping tolerated" and "'Unclear' never scores as wrong."
 *
 * The stimuli are generated here rather than taken from [com.tonic.core.audio.synth.SynthEngine],
 * because what is being tested is behavior the synthesizer deliberately never produces. `SynthEngine`
 * renders in-tune, steady notes — that is its job. A learner does not: they slide into the note from
 * below, and §5.2 step 4 exists for exactly that. Testing against a stimulus that never scoops would
 * leave the anti-scooping logic unexercised while looking thoroughly covered.
 */
class SungResponseAnalyzerTest {
    private val sampleRate = PcmBuffer.DEFAULT_SAMPLE_RATE
    private val tonicC = PitchClass(0)
    private val fullDiatonic = ScaleDegree.ALL_DIATONIC

    /** Degree 3 in C — E4, comfortably mid-range for any voice. */
    private val targetDegree = ScaleDegree(3)
    private val targetHz = Tuning.midiToHz(64)

    /**
     * A sung note: an optional glide up into the pitch, then a sustained portion.
     *
     * The glide interpolates in log-frequency rather than linear, because that is how a slide between
     * two pitches actually sounds — a linear sweep would spend disproportionate time near the top.
     */
    private fun sungNote(
        startHz: Double,
        endHz: Double,
        glideMs: Long,
        steadyMs: Long,
    ): FloatArray {
        val glideSamples = (glideMs * sampleRate / 1000L).toInt()
        val steadySamples = (steadyMs * sampleRate / 1000L).toInt()
        val out = FloatArray(glideSamples + steadySamples)
        var phase = 0.0
        for (i in out.indices) {
            val frequency =
                if (i < glideSamples) {
                    val progress = i.toDouble() / glideSamples
                    exp(ln(startHz) + progress * (ln(endHz) - ln(startHz)))
                } else {
                    endHz
                }
            phase += 2.0 * PI * frequency / sampleRate
            out[i] = (0.5 * sin(phase)).toFloat()
        }
        return out
    }

    private fun analyze(buffer: FloatArray): SungAnswer =
        SungResponseAnalyzer.analyze(
            buffer = buffer,
            sampleRate = sampleRate,
            tonic = tonicC,
            mode = Mode.MAJOR,
            alphabet = fullDiatonic,
        )

    @Test
    fun `a steady sung note resolves to its degree`() {
        val answer = analyze(sungNote(targetHz, targetHz, glideMs = 0, steadyMs = 800))
        assertIs<SungAnswer.Resolved>(answer)
        assertEquals(targetDegree, answer.degree)
        assertTrue(abs(answer.centsFromDegree) < 10.0, "expected a near-exact hit, got ${answer.centsFromDegree}")
    }

    /**
     * The scooping case, and the reason §5.2 step 4 discards the onset. A learner slides up a whole tone
     * into the note over 300 ms before holding it for 500 ms. Averaging across the slide would report a
     * pitch systematically flat of where they actually arrived — and §3's "generous tolerance" would then
     * be spent absorbing an artifact of measurement rather than the learner's genuine imprecision, which
     * is what it exists for.
     */
    @Test
    fun `a scooped onset does not drag the answer flat`() {
        val answer =
            analyze(
                sungNote(
                    startHz = Tuning.offsetByCents(targetHz, -200.0),
                    endHz = targetHz,
                    glideMs = 300,
                    steadyMs = 500,
                ),
            )
        assertIs<SungAnswer.Resolved>(answer, "a scooped note is still an answer")
        assertEquals(targetDegree, answer.degree)
        assertTrue(
            abs(answer.centsFromDegree) < 25.0,
            "the scoop pulled the estimate to ${answer.centsFromDegree} cents; the onset should have been discarded",
        )
    }

    /** §5.2 step 3: no voiced frames is unclear, never a recorded wrong answer. */
    @Test
    fun `silence is unclear, not wrong`() {
        val answer = analyze(FloatArray(sampleRate))
        assertIs<SungAnswer.Unclear>(answer)
        assertEquals(UnclearReason.NO_VOICED_SIGNAL, answer.reason)
    }

    /** Room noise is the realistic version of the same case. */
    @Test
    fun `noise is unclear, not wrong`() {
        val random = Random(1234)
        val noise = FloatArray(sampleRate) { (random.nextDouble() * 2.0 - 1.0).toFloat() * 0.3f }
        val answer = analyze(noise)
        assertIs<SungAnswer.Unclear>(answer, "noise must never resolve to a degree")
    }

    /**
     * A note too brief to survive the onset discard. Declining is correct here rather than pedantic: the
     * only pitch information available is from the part of the signal known to be unreliable.
     */
    @Test
    fun `a note too short to outlast its own onset is unclear`() {
        val answer = analyze(sungNote(targetHz, targetHz, glideMs = 0, steadyMs = 120))
        assertIs<SungAnswer.Unclear>(answer)
        assertEquals(UnclearReason.TOO_SHORT, answer.reason)
    }

    /**
     * §3 mitigation 3 end to end, through the full pipeline rather than the resolver alone: the same
     * degree sung an octave down and an octave up is the same answer.
     */
    @Test
    fun `the same degree is the same answer in any octave`() {
        for (midi in listOf(52, 64, 76)) {
            val hz = Tuning.midiToHz(midi)
            val answer = analyze(sungNote(hz, hz, glideMs = 0, steadyMs = 800))
            assertIs<SungAnswer.Resolved>(answer, "no answer for MIDI $midi")
            assertEquals(targetDegree, answer.degree, "MIDI $midi should still be degree 3")
        }
    }

    /**
     * A singer who is consistently flat still gets their answer — simulation 2 in §8's required set, and
     * the spec's own words: "if it fails, the app is testing singing, not hearing." Thirty cents flat is
     * a real miss, well outside what anyone would call in tune, and it must still resolve.
     */
    @Test
    fun `a consistently flat singer is still understood`() {
        val flatHz = Tuning.offsetByCents(targetHz, -30.0)
        val answer = analyze(sungNote(flatHz, flatHz, glideMs = 0, steadyMs = 800))
        assertIs<SungAnswer.Resolved>(answer, "a flat singer must not be refused")
        assertEquals(targetDegree, answer.degree)
        assertTrue(answer.centsFromDegree < 0.0, "and the readout should show they were flat")
    }
}
