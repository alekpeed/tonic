package com.tonic.core.model.rhythm

import kotlin.math.abs

/**
 * What one calibration run produced — docs/40-PHASE-4-SPEC.md §4.3.
 *
 * A result type rather than a nullable [RhythmCalibration], because §4.3's last requirement is that an
 * implausible measurement "means calibration failed — re-prompt rather than storing garbage," and a
 * re-prompt has to say *what* went wrong. A learner who tapped four times and a learner whose device is
 * 400 ms out need different sentences and different next steps.
 */
public sealed interface CalibrationOutcome {
    /** Usable. Store it against the slot the run was measured on, and nowhere else. */
    public data class Measured(
        public val calibration: RhythmCalibration,
    ) : CalibrationOutcome

    /** Not usable. Nothing is stored; the learner is asked to run it again. */
    public data class Failed(
        public val reason: CalibrationFailure,
        /**
         * What was measured anyway, for the explanation and for diagnostics. Null when there was not
         * enough to measure at all. **Never stored** — it is the garbage §4.3 forbids storing, kept
         * only so a failure can say something more useful than "that did not work."
         */
        public val rejected: RhythmCalibration? = null,
    ) : CalibrationOutcome
}

/** Why a calibration run produced nothing storable. */
public enum class CalibrationFailure {
    /**
     * Too few taps landed near enough to a beat to be attributed to one. Covers both "they stopped
     * after three" and "they were not tapping along at all."
     */
    NOT_ENOUGH_TAPS,

    /**
     * The median offset is outside what any combination of device latency and human timing could
     * produce. Almost always means the taps were attributed to the wrong beats — someone tapping on
     * the off-beat, or half-speed — and the resulting constant would be confidently, invisibly wrong.
     */
    OFFSET_IMPLAUSIBLE,
}

/**
 * Turns one calibration run into a stored constant, or into a reason it produced none —
 * docs/40-PHASE-4-SPEC.md §4.3, whose six numbered steps this is.
 *
 * Pure, and takes instants rather than reading a clock, for §4.4's reason: a recorded run replays
 * identically forever, so a constant that came out wrong can be re-derived offline from the taps that
 * produced it rather than reasoned about from a bug report.
 */
public object Calibrator {
    /**
     * @param taps every touch recorded during the run, in any order, on the same monotonic clock as
     *   [beatNanos].
     * @param beatNanos when each metronome beat was *heard*, from [OutputTimebase.nanosForFrame] — not
     *   when it was scheduled. Using the schedule instead would fold the device's output latency into
     *   the constant twice over, once here and once as the thing being measured.
     * @param discardFirstBeats how many beats at the start to ignore. §4.3 step 3: "entrainment is
     *   unstable at the start" — the first taps of a run are a person finding the pulse, not keeping it,
     *   and including them biases the median toward whatever their first guess was.
     * @return [CalibrationOutcome.Measured] with a constant to store, or [CalibrationOutcome.Failed].
     */
    public fun measure(
        taps: List<TapEvent>,
        beatNanos: List<Long>,
        discardFirstBeats: Int = DEFAULT_DISCARD_BEATS,
        minimumTaps: Int = DEFAULT_MINIMUM_TAPS,
    ): CalibrationOutcome {
        val beats = beatNanos.sorted().drop(discardFirstBeats.coerceAtLeast(0))
        if (beats.size < 2) return CalibrationOutcome.Failed(CalibrationFailure.NOT_ENOUGH_TAPS)

        // The metronome is steady by construction, so one interval describes all of them. Taken from the
        // median gap rather than the first, so a single scheduling hiccup does not set the window every
        // later decision is measured against.
        val beatIntervalMs =
            median(beats.zipWithNext { a, b -> (b - a) / NANOS_PER_MS })
                ?: return CalibrationOutcome.Failed(CalibrationFailure.NOT_ENOUGH_TAPS)

        val asynchronies = attributableAsynchronies(taps, beats, beatIntervalMs)
        if (asynchronies.size < minimumTaps) {
            return CalibrationOutcome.Failed(CalibrationFailure.NOT_ENOUGH_TAPS)
        }

        // §4.3: "Median, not mean. One distracted tap must not skew the constant." Not a preference -
        // a single tap two beats late moves a mean by tens of milliseconds and a median by nothing.
        val offsetMs =
            median(asynchronies) ?: return CalibrationOutcome.Failed(CalibrationFailure.NOT_ENOUGH_TAPS)

        // Median absolute deviation, for the same robustness reason as the offset itself. Standard
        // deviation would let one outlier decide how wide every future tolerance window gets.
        val spreadMs = median(asynchronies.map { abs(it - offsetMs) }) ?: 0.0

        val calibration = RhythmCalibration(offsetMs = offsetMs, spreadMs = spreadMs, tapsUsed = asynchronies.size)

        // Note what is *not* checked here: the spread. §4.3 makes it diagnostic rather than a pass mark,
        // and a learner whose taps scatter is someone whose tolerance windows should widen, not someone
        // who is refused a calibration and left unable to practice at all.
        return if (isPlausible(offsetMs, beatIntervalMs)) {
            CalibrationOutcome.Measured(calibration)
        } else {
            CalibrationOutcome.Failed(CalibrationFailure.OFFSET_IMPLAUSIBLE, rejected = calibration)
        }
    }

    /**
     * Each tap's signed distance from the beat it was aimed at, in milliseconds, for the taps that can
     * be attributed to a beat at all.
     *
     * Nearest-beat matching, not tap-index-to-beat-index. Index matching is what the arithmetic wants
     * and what a person does not do: one missed tap shifts every later pairing by a whole beat and
     * yields a constant that is wrong by exactly one beat interval while looking entirely reasonable.
     * Nearest-beat matching degrades instead — a missed tap costs one sample and nothing else.
     *
     * A tap further than half a beat from any *retained* beat is not attributed. On a steady grid that
     * window rarely fires on its own — every tap is within half a beat of some beat — so its real work
     * is at the edges of the run: it is what makes [measure]'s warm-up discard mean anything, dropping
     * the unstable early taps along with the beats they belong to, and it drops strays after the last
     * beat rather than folding them into the median.
     *
     * Off-beat tapping is therefore *not* caught here. It is attributed to the following beat, at an
     * offset approaching half a beat, and rejected by [isPlausible] — see that function, and the test
     * that pins which guard does the work.
     */
    private fun attributableAsynchronies(
        taps: List<TapEvent>,
        beats: List<Long>,
        beatIntervalMs: Double,
    ): List<Double> {
        val window = beatIntervalMs / 2.0
        return taps.mapNotNull { tap ->
            val nearest = beats.minByOrNull { abs(tap.monotonicNanos - it) } ?: return@mapNotNull null
            val asynchronyMs = (tap.monotonicNanos - nearest) / NANOS_PER_MS
            asynchronyMs.takeIf { abs(it) <= window }
        }
    }

    /**
     * Whether a measured offset is something a real device and a real person could have produced.
     *
     * Two bounds, and the tighter one wins. The absolute pair brackets what the mechanism can be: a
     * learner cannot tap meaningfully *before* the sound reaches them beyond the anticipation humans
     * are known to show when synchronizing, and no output path this app can usefully support puts the
     * sound a quarter of a second behind the call to play it. The relative bound catches the case the
     * absolute one cannot — at a slow tempo, an offset well inside 250 ms may still be most of a beat,
     * which means the taps were attributed to the wrong beats and the constant is a beat's worth of
     * nonsense.
     *
     * ⚠️ **All three numbers are reasoned, not measured.** docs/40-PHASE-4-SPEC.md's Stage 4.1 row
     * asks for a median that is "stable across repeated runs on one device," and until someone runs it
     * on hardware these are bounds on plausibility rather than observations of it. What would move them
     * is a real device producing a legitimate constant this rejects.
     */
    private fun isPlausible(
        offsetMs: Double,
        beatIntervalMs: Double,
    ): Boolean =
        offsetMs >= MIN_PLAUSIBLE_OFFSET_MS &&
            offsetMs <= MAX_PLAUSIBLE_OFFSET_MS &&
            abs(offsetMs) <= beatIntervalMs * MAX_PLAUSIBLE_BEAT_FRACTION

    private fun median(values: List<Double>): Double? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) {
            sorted[middle]
        } else {
            (sorted[middle - 1] + sorted[middle]) / 2.0
        }
    }

    /**
     * Two beats, per §4.3 step 3. Small on purpose: the run is short, and discarding four beats of a
     * twelve-beat run to remove instability that lasts two costs a third of the sample.
     */
    public const val DEFAULT_DISCARD_BEATS: Int = 2

    /**
     * Below this there is no median worth taking — a handful of taps is a handful of guesses, and the
     * robustness the median buys only exists once there is something for an outlier to be outnumbered by.
     */
    public const val DEFAULT_MINIMUM_TAPS: Int = 6

    /**
     * Humans reliably anticipate a beat they are synchronizing with rather than reacting to it, so a
     * negative constant is ordinary and this bound is not near zero. Beyond it, the learner is not
     * tracking the beat they were played.
     */
    public const val MIN_PLAUSIBLE_OFFSET_MS: Double = -120.0

    /** Output latency plus a late finger. Past this, tapping exercises would not be worth scoring anyway. */
    public const val MAX_PLAUSIBLE_OFFSET_MS: Double = 250.0

    /** However fast or slow the metronome, a constant approaching half a beat means the wrong beats were matched. */
    public const val MAX_PLAUSIBLE_BEAT_FRACTION: Double = 0.4

    private const val NANOS_PER_MS = 1_000_000.0
}
