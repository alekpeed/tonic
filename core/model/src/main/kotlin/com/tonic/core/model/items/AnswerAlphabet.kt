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

    /** `M2.*`: the degree ladder, restricted to the node's active degree set. */
    data class ScaleDegrees(
        val degrees: List<ScaleDegree>,
    ) : AnswerAlphabet {
        override val labels = degrees.map { it.degree.toString() }
    }
}
