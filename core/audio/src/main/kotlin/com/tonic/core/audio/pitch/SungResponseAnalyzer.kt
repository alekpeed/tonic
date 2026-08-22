package com.tonic.core.audio.pitch

import com.tonic.core.model.music.DegreeResolver
import com.tonic.core.model.music.Mode
import com.tonic.core.model.music.PitchClass
import com.tonic.core.model.music.ScaleDegree
import com.tonic.core.model.music.SungAnswer
import com.tonic.core.model.music.Tuning
import com.tonic.core.model.music.UnclearReason
import kotlin.math.exp
import kotlin.math.ln

/**
 * The sung-answer pipeline of docs/30-PHASE-3-SPEC.md §5.2, steps 2 through 4, handing off to
 * [DegreeResolver] for steps 5 and 6.
 *
 * Step 1 — capture — is deliberately absent. This takes a buffer that already exists, so it stays a pure
 * function and can be tested against synthesized input with no microphone. Whether those samples arrived
 * through `AudioRecord` or Oboe is §9's open question 1 and does not change anything computed here.
 *
 * **What this stage is really defending against.** §3 names the risk plainly: the app must not
 * accidentally start testing singing ability instead of ear training. A person can know with certainty
 * that the target is degree 5, audiate it perfectly, and still sing it flat, because vocal pitch
 * production is a separate motor skill affected by range, congestion, confidence, and whether anyone is
 * in earshot. Every constant below exists to keep that person's answer correct.
 */
public object SungResponseAnalyzer {
    /**
     * Frame length for detection. At 48 kHz this is ~43 ms, long enough to hold two periods of the
     * lowest pitch [PitchDetector] considers and short enough that a sung note yields many frames.
     */
    public const val WINDOW_SIZE: Int = 2048

    /** Half-window hop, so a short note still produces enough frames to take a median over. */
    public const val HOP_SIZE: Int = 1024

    /**
     * Minimum [PitchEstimate.clarity] for a frame to count as voiced.
     *
     * **Provisional.** It is calibrated against synthesized tones, which read far above it, and against
     * white noise, which reads far below — a gap wide enough that the exact value does not much matter
     * yet. Real sung input in a real room will sit somewhere between, and this is the first constant to
     * re-measure once Stage 3.0's recorded-signal work has hardware. Erring low is the safer direction:
     * a too-high threshold discards good frames and turns a correct answer into a re-prompt, which is
     * annoying, while a too-low one admits noise, which is wrong.
     */
    public const val CLARITY_THRESHOLD: Double = 0.75

    /**
     * How much of the voiced signal to discard before measuring — §5.2 step 4, "not the onset (attack is
     * unstable; people scoop into notes)."
     *
     * Untrained singers approach a note from below and slide into it. Averaging across that slide would
     * report a pitch systematically flat of what the singer actually arrived at, and §3 mitigation 1's
     * "generous tolerance" would then be spent absorbing an artifact of measurement rather than genuine
     * imprecision. Discarding the approach is cheaper and more honest than widening the tolerance to
     * cover it.
     */
    public const val ONSET_SKIP_MS: Long = 150L

    /**
     * Frames required after the onset is discarded. Five hops at 48 kHz is about 150 ms of sustained
     * tone — brief, but enough for a median to mean something. Fewer than this is [UnclearReason.TOO_SHORT]
     * rather than a guess, because a guess here is recorded as an incorrect attempt.
     */
    public const val MIN_SUSTAINED_FRAMES: Int = 5

    /**
     * Reads a captured response and resolves it to a degree, or declines to.
     *
     * @param buffer mono samples of the learner's response.
     * @param sampleRate frames per second of [buffer].
     * @param tonic the established key center the degree is measured against.
     * @param mode major or minor.
     * @param alphabet the degrees currently answerable.
     * @param a4Hz tuning reference, from settings.
     */
    public fun analyze(
        buffer: FloatArray,
        sampleRate: Int,
        tonic: PitchClass,
        mode: Mode,
        alphabet: Collection<ScaleDegree>,
        a4Hz: Double = Tuning.DEFAULT_A4_HZ,
    ): SungAnswer {
        require(sampleRate > 0) { "sampleRate must be positive, was $sampleRate" }

        val voiced = voicedFrames(buffer, sampleRate)
        if (voiced.isEmpty()) return SungAnswer.Unclear(UnclearReason.NO_VOICED_SIGNAL)

        val onsetEndSample = voiced.first().startSample + (ONSET_SKIP_MS * sampleRate / 1000L)
        val sustained = voiced.filter { it.startSample >= onsetEndSample }
        if (sustained.size < MIN_SUSTAINED_FRAMES) return SungAnswer.Unclear(UnclearReason.TOO_SHORT)

        return DegreeResolver.resolve(
            frequencyHz = medianFrequency(sustained.map { it.frequencyHz }),
            tonic = tonic,
            mode = mode,
            alphabet = alphabet,
            a4Hz = a4Hz,
        )
    }

    private data class VoicedFrame(
        val startSample: Long,
        val frequencyHz: Double,
    )

    private fun voicedFrames(
        buffer: FloatArray,
        sampleRate: Int,
    ): List<VoicedFrame> {
        if (buffer.size < WINDOW_SIZE) return emptyList()
        val frames = mutableListOf<VoicedFrame>()
        var start = 0
        while (start + WINDOW_SIZE <= buffer.size) {
            val window = buffer.copyOfRange(start, start + WINDOW_SIZE)
            val estimate = PitchDetector.detect(window, sampleRate)
            if (estimate != null && estimate.clarity >= CLARITY_THRESHOLD) {
                frames += VoicedFrame(start.toLong(), estimate.frequencyHz)
            }
            start += HOP_SIZE
        }
        return frames
    }

    /**
     * Median rather than mean, per §5.2 step 4's "stable central estimate."
     *
     * A mean is dragged by outliers, and the outliers here are not noise — they are a voice cracking, a
     * moment of creak, a frame straddling the end of the note. One such frame can pull a mean far enough
     * to change which degree is nearest, and it would do so most often for the least confident singers.
     * A median ignores them entirely.
     *
     * The even-count case averages the two central values *in the log domain*, because the midpoint
     * between two pitches is their geometric mean, not their arithmetic one. Pitch is logarithmic in
     * frequency; averaging linearly biases sharp, slightly but systematically.
     */
    private fun medianFrequency(frequencies: List<Double>): Double {
        val sorted = frequencies.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) {
            sorted[mid]
        } else {
            exp((ln(sorted[mid - 1]) + ln(sorted[mid])) / 2.0)
        }
    }
}
