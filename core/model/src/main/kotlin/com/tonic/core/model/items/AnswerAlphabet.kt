package com.tonic.core.model.items

import com.tonic.core.model.music.ScaleDegree

/**
 * The set of valid answers for one item. [labels] gives the canonical
 * string form stored on [com.tonic.core.model.attempts.Attempt.targetLabel]
 * / `responseLabel` (docs/05-DATA-MODEL.md §1: e.g. `"3"` or `"HIGHER"`).
 */
sealed interface AnswerAlphabet {
    val labels: List<String>

    /** `M0.PITCH_DIR`, `M1.HIGH_LOW`. */
    data object HigherLower : AnswerAlphabet {
        const val HIGHER = "HIGHER"
        const val LOWER = "LOWER"
        override val labels = listOf(HIGHER, LOWER)
    }

    /** `M0.SAME_DIFF`, `M1.SAME_DIFF`, `M0.TONAL_MEMORY`. */
    data object SameDifferent : AnswerAlphabet {
        const val SAME = "SAME"
        const val DIFFERENT = "DIFFERENT"
        override val labels = listOf(SAME, DIFFERENT)
    }

    /** `M1.CONTOUR`: did a 3-note figure rise, fall, or turn. */
    data object Contour : AnswerAlphabet {
        const val UP = "UP"
        const val DOWN = "DOWN"
        const val UP_DOWN = "UP_DOWN"
        const val DOWN_UP = "DOWN_UP"
        override val labels = listOf(UP, DOWN, UP_DOWN, DOWN_UP)
    }

    /** `M1.STEP_LEAP`. */
    data object StepLeap : AnswerAlphabet {
        const val STEP = "STEP"
        const val LEAP = "LEAP"
        override val labels = listOf(STEP, LEAP)
    }

    /** `M0.AMUSIA_SCREEN`: did that sound right, or did something sound off. */
    data object IntactAltered : AnswerAlphabet {
        const val INTACT = "INTACT"
        const val ALTERED = "ALTERED"
        override val labels = listOf(INTACT, ALTERED)
    }

    /**
     * `M9.*`: which mode is sounding — docs/20-PHASE-2-SPEC.md §2.4. A binary answer, so accuracy alone
     * cannot certify it and mastery also requires d-prime (§3, the same reasoning as `M0.SAME_DIFF`):
     * a learner who answers "major" to everything scores 50% while hearing nothing at all.
     */
    data object MajorMinor : AnswerAlphabet {
        const val MAJOR = "MAJOR"
        const val MINOR = "MINOR"
        override val labels = listOf(MAJOR, MINOR)
    }

    /**
     * `M12.*`: the prediction answer — docs/20-PHASE-2-SPEC.md §8.1 decision 3. Three buttons from the
     * first prediction item onward so the control layout never changes shape mid-module, with direction
     * collapsed to a plain "didn't match" for *scoring* at `M12.PREDICT_TRIAD`: a learner who hears that
     * the note was wrong but cannot yet say which way is not penalized for a skill that belongs to
     * `M1.HIGH_LOW`.
     *
     * Direction is carried rather than dropped because a two-way answer makes the confusion matrix
     * useless — a 2×2 grid records *that* a learner was wrong while recording nothing about what they
     * heard instead. Response bias is handled by d-prime either way (docs/07-ADAPTIVE-ENGINE.md §2),
     * computed over [matchedVsNot], so the three-way answer costs nothing there.
     */
    data object MatchDirection : AnswerAlphabet {
        const val MATCHED = "MATCHED"
        const val TOO_LOW = "TOO_LOW"
        const val TOO_HIGH = "TOO_HIGH"
        override val labels = listOf(MATCHED, TOO_LOW, TOO_HIGH)

        /**
         * Collapses a directional answer to the binary a d-prime computation needs, and to the form
         * `M12.PREDICT_TRIAD` scores against. Any non-[MATCHED] label counts as "detected a mismatch."
         */
        fun matchedVsNot(label: String): Boolean = label == MATCHED
    }

    /** `M2.*`, `M10.*`, `M11.*`: the degree ladder, restricted to the node's active degree set. */
    data class ScaleDegrees(
        val degrees: List<ScaleDegree>,
    ) : AnswerAlphabet {
        // canonicalLabel, not degree.toString(): the latter silently dropped the alteration, so ♭3 and
        // ♮3 both produced "3" and would have collided in the attempt log and the confusion matrix the
        // moment Phase 2 put both on screen. Identical output for every unaltered Phase 1 degree.
        override val labels = degrees.map { it.canonicalLabel }
    }
}
