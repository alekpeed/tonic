package com.tonic.core.engine.session

import com.tonic.core.model.ids.SkillId
import com.tonic.core.model.items.DifficultyAxis
import com.tonic.core.model.state.PlannedSlot
import com.tonic.core.model.state.SessionPlan
import java.time.Instant

/**
 * Assembles a practice session — docs/07-ADAPTIVE-ENGINE.md §8. Produces
 * the *plan*; the runtime (Stage 6's headless loop, Stage 9's UI) walks it,
 * generating and scoring one item per slot. Two pieces of the spec are
 * inherently runtime decisions this function can't make in advance and are
 * separate entry points instead: whether the block-vs-interleave threshold
 * has actually been crossed depends on live accuracy the composer doesn't
 * have yet ([composeBlockOrInterleave] approximates it from attempt count,
 * documented below), and "never end on failure" ([appendFailureRecoveryIfNeeded])
 * can only be decided after the last planned item is actually answered.
 */
object SessionComposer {
    private const val ITEMS_PER_MINUTE = 9.0
    private const val WARMUP_ITEM_COUNT = 5
    private const val MAX_REVIEW_SHARE = 0.40

    /**
     * A newly-unlocked node is practiced in a contiguous block until it
     * reaches 70% accuracy over 15 items (docs/07-ADAPTIVE-ENGINE.md §8).
     * The composer plans a whole session up front rather than reacting item
     * by item, so it approximates "still new" with the node's total
     * attempt count so far — a node under this many attempts gets its
     * current-node work planned as one contiguous block with reviews
     * pushed to the end, rather than interleaved throughout.
     */
    private const val BLOCK_UNTIL_ATTEMPT_COUNT = 15

    fun compose(
        currentNode: SkillWorkContext,
        dueReviews: List<DueReview>,
        sessionLengthMinutes: Int,
        rootSeed: Long,
        now: Instant,
    ): SessionPlan {
        val totalSlots = (sessionLengthMinutes * ITEMS_PER_MINUTE).toInt().coerceAtLeast(1)
        val warmupCount = WARMUP_ITEM_COUNT.coerceAtMost(totalSlots)
        val remaining = totalSlots - warmupCount

        val reviewCount = (remaining * MAX_REVIEW_SHARE).toInt().coerceAtMost(dueReviews.size)
        val workCount = remaining - reviewCount

        val warmupSlots = List(warmupCount) { warmupSlot(currentNode) }
        val workSlots = List(workCount) { workSlot(currentNode) }
        val reviewSlots = dueReviews.sortedBy { it.dueAt }.take(reviewCount).map { reviewSlot(it, currentNode) }

        val body =
            if (currentNode.totalAttempts < BLOCK_UNTIL_ATTEMPT_COUNT) {
                workSlots + reviewSlots
            } else {
                interleave(workSlots, reviewSlots)
            }

        return SessionPlan(rootSeed = rootSeed, plannedSlots = warmupSlots + body)
    }

    /**
     * Called once the session's last planned item has been answered.
     * "If the final planned item is answered incorrectly, append one item
     * at a reduced difficulty. This is a retention measure, and it is not
     * dishonest — it does not alter scoring." Returns null if no recovery
     * item is needed.
     */
    fun appendFailureRecoveryIfNeeded(
        lastPlannedItemWasCorrect: Boolean,
        currentNode: SkillWorkContext,
    ): PlannedSlot? {
        if (lastPlannedItemWasCorrect) return null
        return PlannedSlot(
            skillId = currentNode.skillId,
            axisLevels = reducedAxisLevels(currentNode.axisLevels),
            isWarmup = false,
            isReview = false,
        )
    }

    /** `rootSeed` combined with item index via a fixed mixing function — docs/04-ARCHITECTURE.md §4. */
    fun itemSeed(
        rootSeed: Long,
        index: Int,
    ): Long = rootSeed xor (index.toLong() * MIXING_PRIME)

    private fun warmupSlot(node: SkillWorkContext): PlannedSlot =
        PlannedSlot(
            skillId = node.skillId,
            axisLevels = reducedAxisLevels(node.axisLevels),
            isWarmup = true,
            isReview = false,
        )

    private fun workSlot(node: SkillWorkContext): PlannedSlot =
        PlannedSlot(skillId = node.skillId, axisLevels = node.axisLevels, isWarmup = false, isReview = false)

    private fun reviewSlot(
        review: DueReview,
        currentNode: SkillWorkContext,
    ): PlannedSlot =
        PlannedSlot(
            skillId = review.skillId,
            // A due review runs at the reviewed node's own mastered axis levels, not the current node's -
            // the caller supplies those via DueReview if they differ; when composing for the same node
            // (self-review after a fade regression) this falls back to the current node's levels.
            axisLevels = review.axisLevels ?: currentNode.axisLevels,
            isWarmup = false,
            isReview = true,
        )

    /** One cadence-fade step easier than the node's current level — the warm-up's reduced difficulty. */
    private fun reducedAxisLevels(axisLevels: Map<DifficultyAxis, Int>): Map<DifficultyAxis, Int> {
        val cadenceFade = axisLevels[DifficultyAxis.CADENCE_FADE] ?: 0
        return axisLevels + (DifficultyAxis.CADENCE_FADE to (cadenceFade - 1).coerceAtLeast(0))
    }

    /** Spreads [reviews] evenly through [work] rather than clustering them at one end. */
    private fun interleave(
        work: List<PlannedSlot>,
        reviews: List<PlannedSlot>,
    ): List<PlannedSlot> {
        if (reviews.isEmpty()) return work
        if (work.isEmpty()) return reviews

        val result = mutableListOf<PlannedSlot>()
        val ratio = reviews.size.toDouble() / work.size
        var reviewDebt = 0.0
        var reviewIndex = 0
        for (slot in work) {
            result += slot
            reviewDebt += ratio
            while (reviewDebt >= 1.0 && reviewIndex < reviews.size) {
                result += reviews[reviewIndex++]
                reviewDebt -= 1.0
            }
        }
        while (reviewIndex < reviews.size) result += reviews[reviewIndex++]
        return result
    }

    private const val MIXING_PRIME = -0x61c8864680b583ebL // 0x9E3779B97F4A7C15 as a signed Long literal
}

/** The node currently being practiced (docs/07-ADAPTIVE-ENGINE.md §8's "current node work" priority tier). */
data class SkillWorkContext(
    val skillId: SkillId,
    val axisLevels: Map<DifficultyAxis, Int>,
    val totalAttempts: Int,
)

/** One mastered node past its FSRS due date — docs/07-ADAPTIVE-ENGINE.md §8's "due reviews" priority tier. */
data class DueReview(
    val skillId: SkillId,
    val dueAt: Instant,
    val axisLevels: Map<DifficultyAxis, Int>? = null,
)
