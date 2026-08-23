package com.tonic.core.model.rhythm

/**
 * Tempo, and the one axis in this project that is not monotonic in what it measures —
 * docs/40-PHASE-4-SPEC.md §3.5.
 *
 * Around 100 BPM is easiest, near the natural spontaneous tapping rate. **Both faster and slower are
 * harder**: fast because it outruns motor comfort, slow because a long inter-beat interval demands
 * genuine internal timekeeping rather than reactive entrainment. So `TEMPO_DEVIATION` measures distance
 * from a comfortable center, not beats per minute, and each level offers a *pair* of tempi.
 *
 * A generator that read the axis as "tempo" would make level 3 uniformly fast and quietly delete the
 * harder half of the axis — the slow half, which is the half that actually trains internal pulse.
 */
public object Tempo {
    /** The comfortable center. Level 0 is exactly this, in both directions. */
    public const val CENTER_BPM: Int = 100

    /**
     * The two tempi offered at [level], slower first.
     *
     * ⚠️ The step sizes are reasoned from §3.5's "around 90–120 BPM is easiest", not measured. What
     * would move them is a learner who masters level 3 slow and fails level 1 fast, which would say the
     * pairing is not symmetric in difficulty after all.
     */
    public fun bpmPairFor(level: Int): Pair<Int, Int> {
        require(level in 0..MAX_LEVEL) { "TEMPO_DEVIATION is 0..$MAX_LEVEL, was $level" }
        val spread = SPREADS[level]
        return (CENTER_BPM - spread) to (CENTER_BPM + spread)
    }

    /** Milliseconds per beat at [bpm]. The one place tempo becomes time. */
    public fun msPerBeat(bpm: Int): Double {
        require(bpm > 0) { "Tempo must be positive, was $bpm" }
        return MS_PER_MINUTE / bpm
    }

    /** Milliseconds per tick at [bpm] — see [Meter.TICKS_PER_BEAT]. */
    public fun msPerTick(bpm: Int): Double = msPerBeat(bpm) / Meter.TICKS_PER_BEAT

    /** How far [bpm] sits from the comfortable center, in the axis's own terms. */
    public fun deviationOf(bpm: Int): Int = kotlin.math.abs(bpm - CENTER_BPM)

    /** Highest `TEMPO_DEVIATION` level, matching the axis's own maximum. */
    public const val MAX_LEVEL: Int = 3

    private const val MS_PER_MINUTE = 60_000.0

    /**
     * Distance from [CENTER_BPM] at each level. Level 0 is the center itself; the widest pair is 60 and
     * 140, which brackets the range §3.5 describes without reaching tempi where a beat stops being a
     * beat at all.
     */
    private val SPREADS = intArrayOf(0, 15, 25, 40)
}
