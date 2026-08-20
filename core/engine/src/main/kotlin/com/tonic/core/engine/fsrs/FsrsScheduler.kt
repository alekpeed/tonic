package com.tonic.core.engine.fsrs

import com.tonic.core.model.state.FsrsGrade
import com.tonic.core.model.state.FsrsState
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * FSRS-6 review scheduling, docs/07-ADAPTIVE-ENGINE.md §6.
 *
 * "First check whether a maintained Kotlin/JVM FSRS implementation exists.
 * Report what you find." Checked Maven Central directly (`fsrs`, `py-fsrs`,
 * "free spaced repetition", "open-spaced-repetition" all return zero
 * results) — nothing is published there, and this project's dependencies
 * resolve from Maven Central/Google's repository only, so a GitHub-hosted
 * package isn't a drop-in option without adding a new repository source
 * (CLAUDE.md §7: don't add a dependency without asking). Per the doc's
 * fallback instruction, this ports the algorithm instead — from the
 * authoritative source, not memory: `open-spaced-repetition/py-fsrs`
 * v6.3.2 (fetched from PyPI, since that's the maintained reference
 * implementation the FSRS project itself publishes).
 *
 * Two simplifications versus that reference, both because this app's
 * granularity is coarser than Anki's:
 * - No same-day/short-term stability formula (`_short_term_stability`) or
 *   sub-day learning/relearning steps. Reviews here are whole probe blocks
 *   (docs/07-ADAPTIVE-ENGINE.md §6 — "a review is a probe block of 10
 *   items"), always at least a day apart in practice; the day-granularity
 *   long-term formulas are what apply.
 * - No interval fuzzing (small random jitter to avoid review pile-ups).
 *   A nice-to-have the docs don't mention; omitted for simplicity.
 */
object FsrsScheduler {
    /** Verbatim from py-fsrs 6.3.2's `DEFAULT_PARAMETERS`. */
    val DEFAULT_PARAMETERS =
        doubleArrayOf(
            0.212,
            1.2931,
            2.3065,
            8.2956,
            6.4133,
            0.8334,
            3.0194,
            0.001,
            1.8722,
            0.1666,
            0.796,
            1.4835,
            0.0614,
            0.2629,
            1.6483,
            0.6014,
            1.8729,
            0.5425,
            0.0912,
            0.0658,
            0.1542,
        )

    private const val DESIRED_RETENTION = 0.9
    private const val STABILITY_MIN = 0.001
    private const val MIN_DIFFICULTY = 1.0
    private const val MAX_DIFFICULTY = 10.0

    private val decay = -DEFAULT_PARAMETERS[20]
    private val factor = 0.9.pow(1.0 / decay) - 1.0

    /** First-ever review of a node — no prior [FsrsState] to build on. */
    fun initial(
        grade: FsrsGrade,
        now: Instant,
    ): FsrsState {
        val stability = DEFAULT_PARAMETERS[grade.ratingValue - 1].coerceAtLeast(STABILITY_MIN)
        val difficulty = initialDifficulty(grade).coerceIn(MIN_DIFFICULTY, MAX_DIFFICULTY)
        return FsrsState(
            stability = stability,
            difficulty = difficulty,
            lastReview = now,
            due = dueAfter(now, stability),
            reps = 1,
            lapses = 0,
        )
    }

    /** A subsequent review. */
    fun review(
        previous: FsrsState,
        grade: FsrsGrade,
        now: Instant,
    ): FsrsState {
        val elapsedDays =
            ChronoUnit.DAYS
                .between(previous.lastReview ?: now, now)
                .toInt()
                .coerceAtLeast(0)
        val retrievability = retrievability(elapsedDays, previous.stability)

        val nextStability =
            if (grade == FsrsGrade.AGAIN) {
                forgetStability(previous.difficulty, previous.stability, retrievability)
            } else {
                recallStability(previous.difficulty, previous.stability, retrievability, grade)
            }.coerceAtLeast(STABILITY_MIN)

        val nextDifficulty = nextDifficulty(previous.difficulty, grade)

        return FsrsState(
            stability = nextStability,
            difficulty = nextDifficulty,
            lastReview = now,
            due = dueAfter(now, nextStability),
            reps = previous.reps + 1,
            lapses = previous.lapses + if (grade == FsrsGrade.AGAIN) 1 else 0,
        )
    }

    /** Predicted probability of recall [elapsedDays] after the last review, given [stability]. */
    fun retrievability(
        elapsedDays: Int,
        stability: Double,
    ): Double = (1.0 + factor * elapsedDays / stability).pow(decay)

    /** Days until the card should next be reviewed, at [DESIRED_RETENTION]. */
    fun nextIntervalDays(stability: Double): Int =
        ((stability / factor) * (DESIRED_RETENTION.pow(1.0 / decay) - 1.0)).roundToInt().coerceAtLeast(1)

    private fun dueAfter(
        now: Instant,
        stability: Double,
    ): Instant = now.plus(nextIntervalDays(stability).toLong(), ChronoUnit.DAYS)

    private fun initialDifficulty(grade: FsrsGrade): Double =
        DEFAULT_PARAMETERS[4] - exp(DEFAULT_PARAMETERS[5] * (grade.ratingValue - 1)) + 1.0

    private fun nextDifficulty(
        difficulty: Double,
        grade: FsrsGrade,
    ): Double {
        val deltaDifficulty = -(DEFAULT_PARAMETERS[6] * (grade.ratingValue - 3))
        val linearDamping = (10.0 - difficulty) * deltaDifficulty / 9.0
        val dampened = difficulty + linearDamping
        // Mean-reverts toward the (unclamped) difficulty an "Easy" first rating would imply.
        val target = DEFAULT_PARAMETERS[4] - exp(DEFAULT_PARAMETERS[5] * (FsrsGrade.EASY.ratingValue - 1)) + 1.0
        val reverted = DEFAULT_PARAMETERS[7] * target + (1.0 - DEFAULT_PARAMETERS[7]) * dampened
        return reverted.coerceIn(MIN_DIFFICULTY, MAX_DIFFICULTY)
    }

    private fun forgetStability(
        difficulty: Double,
        stability: Double,
        retrievability: Double,
    ): Double =
        DEFAULT_PARAMETERS[11] *
            difficulty.pow(-DEFAULT_PARAMETERS[12]) *
            (((stability + 1.0).pow(DEFAULT_PARAMETERS[13])) - 1.0) *
            exp((1.0 - retrievability) * DEFAULT_PARAMETERS[14])

    private fun recallStability(
        difficulty: Double,
        stability: Double,
        retrievability: Double,
        grade: FsrsGrade,
    ): Double {
        val hardPenalty = if (grade == FsrsGrade.HARD) DEFAULT_PARAMETERS[15] else 1.0
        val easyBonus = if (grade == FsrsGrade.EASY) DEFAULT_PARAMETERS[16] else 1.0
        return stability * (
            1.0 +
                exp(DEFAULT_PARAMETERS[8]) *
                (11.0 - difficulty) *
                stability.pow(-DEFAULT_PARAMETERS[9]) *
                (exp((1.0 - retrievability) * DEFAULT_PARAMETERS[10]) - 1.0) *
                hardPenalty *
                easyBonus
        )
    }
}

/** `AGAIN`=1 .. `EASY`=4, matching FSRS's rating convention (py-fsrs `Rating` enum) and this codebase's [FsrsGrade] order. */
val FsrsGrade.ratingValue: Int get() = ordinal + 1
