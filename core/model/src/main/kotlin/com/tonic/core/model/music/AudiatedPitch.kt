package com.tonic.core.model.music

import kotlin.math.roundToInt

/**
 * What a learner actually held in their head during an `M12` audiation gap — docs/30-PHASE-3-SPEC.md §5.4.
 *
 * **This never scores anything.** §5.4 settled that on 2026-08-21: "the sung prediction supplements the
 * judgment, it does not replace it." The learner still answers `MATCHED` / `TOO LOW` / `TOO HIGH` with
 * the three-button control, and that button is what the attempt is marked on. This type carries the
 * other half of the record — the evidence of what was actually audiated — so that the two can disagree
 * and the disagreement can be seen.
 *
 * That is the whole reason it exists as a separate reading rather than being folded into the answer.
 * §5.4 again: "a learner who sings the right pitch and then misreports the direction has a specific,
 * diagnosable problem, and collapsing the two would hide it." The button says whether they heard the
 * mismatch; this says whether they had the right note in mind to hear it against. Those are different
 * failures with different fixes, and an app that records only the first cannot tell them apart.
 *
 * @property degree the degree the sung pitch resolved to, which may not be the one that was named.
 * @property statedDegree the degree the item asked for, kept beside [degree] so the two can be compared
 *   without the comparison having to be redone from cents by every reader.
 * @property centsFromStated signed distance from the pitch the learner was *asked* to hold, positive
 *   being sharp, folded into ±600 cents so an answer sung an octave away is the same answer. This is
 *   docs/30-PHASE-3-SPEC.md §7's `sungCents` for a prediction item: §7 defines it as "deviation from
 *   the target degree's true pitch," and on a prediction item the target degree is the stated one —
 *   not the note that eventually sounds, which the learner had not heard yet when they sang.
 */
public data class AudiatedPitch(
    val degree: ScaleDegree,
    val statedDegree: ScaleDegree,
    val centsFromStated: Int,
) {
    /**
     * Whether the learner held the degree they were asked to hold.
     *
     * Degree identity, not closeness, and the distinction is not pedantic. `M12.PREDICT_TRIAD` draws
     * from `{1, 3, 5}`, so the nearest member of the alphabet can be a whole tone away — a learner who
     * sings a `1` 150 cents sharp still resolved to `1`, and a closeness test against
     * [centsFromStated] would call that a different degree. §3 mitigation 1's whole point is that this
     * app tests hearing rather than vocal accuracy, so imprecision must never turn into the wrong
     * degree being recorded. [centsFromStated] carries the imprecision separately, for display, and
     * nothing reads it as a verdict.
     */
    public val heldStatedDegree: Boolean get() = degree == statedDegree

    public companion object {
        private const val CENTS_PER_SEMITONE = 100
        private const val CENTS_PER_OCTAVE = 1_200
        private const val HALF_OCTAVE_CENTS = CENTS_PER_OCTAVE / 2

        /**
         * Reads a resolved sung response as audiation evidence against the degree that was named.
         *
         * [SungAnswer.Resolved.centsFromDegree] is measured from whichever degree the voice landed
         * nearest, which is the right question for `M2` — there the learner is *choosing* a degree and
         * the nearest one is their answer. It is the wrong question here: `M12` already named the
         * degree, so the useful measurement is the distance from *that* one, and a learner who held `5`
         * when asked for `3` should read as several hundred cents out rather than as a tidy few cents
         * from `5`.
         *
         * @param sung the resolved response, from `SungResponseAnalyzer`.
         * @param statedDegree the degree the item named — a prediction item's `statedDegree`.
         * @param mode the item's mode, which is what fixes each degree's semitone position.
         */
        public fun from(
            sung: SungAnswer.Resolved,
            statedDegree: ScaleDegree,
            mode: Mode,
        ): AudiatedPitch {
            val fromStated =
                (sung.degree.semitoneOffset(mode) - statedDegree.semitoneOffset(mode)) * CENTS_PER_SEMITONE +
                    sung.centsFromDegree
            return AudiatedPitch(sung.degree, statedDegree, foldIntoOctave(fromStated))
        }

        /**
         * Folds a signed cent distance into ±600, so the same degree in any octave reads as the same answer.
         *
         * docs/30-PHASE-3-SPEC.md §7's `sung_octave_agnostic` defaults to true, and this is the arithmetic
         * that honors it. Without the fold, a learner with a low voice asked to hold a `1` sitting above
         * their range sings it an octave down and is recorded 1200 cents out — which would look, to
         * anyone reading the data later, exactly like someone who had no idea what note was named.
         */
        private fun foldIntoOctave(cents: Double): Int {
            var folded = cents
            while (folded > HALF_OCTAVE_CENTS) folded -= CENTS_PER_OCTAVE
            while (folded <= -HALF_OCTAVE_CENTS) folded += CENTS_PER_OCTAVE
            return folded.roundToInt()
        }
    }
}
