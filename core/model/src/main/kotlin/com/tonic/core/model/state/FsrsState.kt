package com.tonic.core.model.state

import java.time.Instant

/**
 * FSRS review state, at skill-node granularity — docs/07-ADAPTIVE-ENGINE.md
 * §6: "a card is a skill node at its mastery configuration... not an
 * individual note or item." Persisted as plain scalar columns on
 * `skill_states` (docs/05-DATA-MODEL.md §1), not a JSON blob, so this
 * doesn't need `@Serializable`.
 */
data class FsrsState(
    val stability: Double,
    val difficulty: Double,
    val lastReview: Instant?,
    val due: Instant?,
    val reps: Int = 0,
    val lapses: Int = 0,
)

/** Grade derived from a review block's accuracy — docs/07-ADAPTIVE-ENGINE.md §6 table. */
enum class FsrsGrade {
    AGAIN,
    HARD,
    GOOD,
    EASY,
    ;

    companion object {
        /** Block accuracy → grade, per the boundaries in docs/07-ADAPTIVE-ENGINE.md §6. */
        fun fromBlockAccuracy(accuracy: Double): FsrsGrade =
            when {
                accuracy < 0.60 -> AGAIN
                accuracy < 0.80 -> HARD
                accuracy <= 0.92 -> GOOD
                else -> EASY
            }
    }
}
