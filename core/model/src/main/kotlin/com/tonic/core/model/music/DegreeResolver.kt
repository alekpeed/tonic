package com.tonic.core.model.music

import kotlin.math.abs
import kotlin.math.ln

/**
 * Turns a measured frequency into a scale degree — docs/30-PHASE-3-SPEC.md §5.2, steps 5 and 6.
 *
 * This is the music-theory half of the sung-answer pipeline and it lives in `:core:model` for the same
 * reason [Tuning] does: docs/06-AUDIO-ENGINE.md §6 calls tuning domain logic rather than audio, and this
 * is its inverse. The DSP half — framing, confidence, the sustained median — is in `:core:audio`, which
 * calls this. Keeping the split means the rule that actually decides a learner's answer is testable in a
 * pure JVM module with no audio at all, which is docs/04-ARCHITECTURE.md §1's whole argument.
 */
public object DegreeResolver {
    /**
     * How much closer the nearest degree must be than the runner-up before the answer is committed.
     *
     * Below this the response is [UnclearReason.AMBIGUOUS_BETWEEN_DEGREES] and the learner is asked
     * again. Ten cents of *margin* means the sung pitch must land within five cents of the exact midpoint
     * between two neighbors to be refused — a twentieth of the gap between adjacent semitones.
     *
     * The narrowness is the point, and it was tightened from a first attempt at 20 after working the
     * arithmetic through a real case. Adjacent degrees sit 100 cents apart, so their midpoint is 50 cents
     * from each; a 20-cent margin therefore refuses anyone whose nearest degree is 40 to 50 cents away.
     * A singer 40 cents flat of the tonic is not a coin-flip — the runner-up is half again as far — and
     * flat is exactly how untrained singers miss, which is the case §3 mitigation 1 demands generosity
     * for. Refusing them would be the defect §3 warns about, dressed as caution. At 10 cents only a
     * genuinely undecidable 48-versus-52 is refused, and §5.2's real target — silently rounding a true
     * coin-flip, "the single most likely way this feature produces bad data" — is still caught.
     */
    public const val AMBIGUITY_MARGIN_CENTS: Double = 10.0

    private const val SEMITONES_PER_OCTAVE = 12
    private const val CENTS_PER_SEMITONE = 100.0

    /**
     * Resolves [frequencyHz] to the nearest degree of [alphabet], ignoring octave.
     *
     * **Octave-agnostic by construction, not by tolerance.** §3 mitigation 3 requires that singing
     * degree 5 in whatever register is comfortable counts as degree 5, because forcing a specific octave
     * tests vocal range rather than hearing — and vocal range varies enormously. That is implemented by
     * reducing both the sung pitch and each candidate to a position within one octave before comparing,
     * so a bass an octave below the reference and a soprano an octave above resolve identically. There
     * is no octave window to fall outside of.
     *
     * @param frequencyHz the stable estimate from the sustained portion of the response.
     * @param tonic the established key center — the reference the degree is measured against, per
     *   docs/02-PEDAGOGY.md §1's rule that every pitch question carries a tonal context.
     * @param mode major or minor, since a degree's semitone offset is read per mode.
     * @param alphabet the degrees currently answerable. Only these are candidates: a learner working on
     *   `M2.DEG_SET_1` cannot accidentally be told they sang a 6.
     * @param a4Hz tuning reference, from settings.
     * @return [SungAnswer.Resolved], or [SungAnswer.Unclear] when two candidates are too close to call.
     */
    public fun resolve(
        frequencyHz: Double,
        tonic: PitchClass,
        mode: Mode,
        alphabet: Collection<ScaleDegree>,
        a4Hz: Double = Tuning.DEFAULT_A4_HZ,
    ): SungAnswer {
        require(frequencyHz > 0.0) { "frequencyHz must be positive, was $frequencyHz" }
        require(alphabet.isNotEmpty()) { "alphabet must not be empty" }

        // Continuous semitones above the tonic, wrapped into one octave. Continuous rather than rounded
        // because the distance to each candidate is the whole question here, and rounding first would
        // throw away exactly the information the ambiguity check needs.
        val semitonesAboveA4 = SEMITONES_PER_OCTAVE * (ln(frequencyHz / a4Hz) / ln(2.0))
        val absoluteSemitones = semitonesAboveA4 + A4_SEMITONE_POSITION
        val sungPosition = absoluteSemitones.mod(SEMITONES_PER_OCTAVE.toDouble())
        val tonicPosition = tonic.value.toDouble()

        val distances =
            alphabet.map { degree ->
                val target = (tonicPosition + degree.semitoneOffset(mode)).mod(SEMITONES_PER_OCTAVE.toDouble())
                degree to signedCentsBetweenPositions(from = target, to = sungPosition)
            }

        val sorted = distances.sortedBy { abs(it.second) }
        val (nearest, nearestCents) = sorted.first()

        // A single-degree alphabet has no runner-up, so nothing can be ambiguous against it.
        val runnerUpCents = sorted.getOrNull(1)?.second
        if (runnerUpCents != null && abs(runnerUpCents) - abs(nearestCents) < AMBIGUITY_MARGIN_CENTS) {
            return SungAnswer.Unclear(UnclearReason.AMBIGUOUS_BETWEEN_DEGREES)
        }

        return SungAnswer.Resolved(
            degree = nearest,
            centsFromDegree = nearestCents,
            frequencyHz = frequencyHz,
        )
    }

    /**
     * Signed cent distance between two positions in the octave, taking the short way around.
     *
     * The wrap matters and is easy to miss: a learner asked for degree 1 who sings 40 cents *flat* of it
     * sits near the top of the octave, at position 11.6 against a target of 0.0. Compared linearly that
     * is 1160 cents away and would resolve to whatever degree happens to sit near 11 — most likely a 7,
     * which is precisely the 7↔1 confusion docs/07-ADAPTIVE-ENGINE.md §4 lists as an expected and
     * pedagogically meaningful error. Manufacturing that error out of arithmetic would put a fake entry
     * into the confusion matrix that looks exactly like a real diagnosis.
     */
    private fun signedCentsBetweenPositions(
        from: Double,
        to: Double,
    ): Double {
        val raw = (to - from).mod(SEMITONES_PER_OCTAVE.toDouble())
        val shortest = if (raw > SEMITONES_PER_OCTAVE / 2.0) raw - SEMITONES_PER_OCTAVE else raw
        return shortest * CENTS_PER_SEMITONE
    }

    /** A4 is MIDI 69, whose pitch class is 9 (A). Used to anchor the continuous position calculation. */
    private const val A4_SEMITONE_POSITION = 9.0
}
