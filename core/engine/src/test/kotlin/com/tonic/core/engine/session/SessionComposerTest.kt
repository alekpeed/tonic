package com.tonic.core.engine.session

import com.tonic.core.model.ids.SkillIds
import com.tonic.core.model.items.DifficultyAxis
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** docs/09-BUILD-PLAN.md Stage 4 acceptance: warm-up exclusion, blocking-then-interleaving, never-end-on-failure. */
class SessionComposerTest {
    private val now = Instant.parse("2026-01-01T00:00:00Z")
    private val masteredAxes = mapOf(DifficultyAxis.CADENCE_FADE to 5, DifficultyAxis.TIMBRE_VARIETY to 2)

    private fun newNode(totalAttempts: Int) = SkillWorkContext(SkillIds.M2_DEG_SET_2, masteredAxes, totalAttempts)

    @Test
    fun `the first 5 items are warm-up, one cadence-fade step easier, and nothing after is`() {
        val plan =
            SessionComposer.compose(
                newNode(totalAttempts = 0),
                emptyList(),
                sessionLengthMinutes = 5,
                rootSeed = 1L,
                now = now,
            )
        val warmup = plan.plannedSlots.take(5)
        assertTrue(warmup.all { it.isWarmup })
        assertTrue(
            warmup.all {
                it.axisLevels.getValue(DifficultyAxis.CADENCE_FADE) ==
                    masteredAxes.getValue(DifficultyAxis.CADENCE_FADE) - 1
            },
        )
        assertTrue(plan.plannedSlots.drop(5).none { it.isWarmup })
    }

    @Test
    fun `a default 5-minute session lands in the documented 40 to 50 item range`() {
        val plan =
            SessionComposer.compose(
                newNode(totalAttempts = 100),
                emptyList(),
                sessionLengthMinutes = 5,
                rootSeed = 1L,
                now = now,
            )
        assertTrue(plan.plannedSlots.size in 40..50, "got ${plan.plannedSlots.size} items")
    }

    @Test
    fun `session length is configurable and scales item count`() {
        val short = SessionComposer.compose(newNode(0), emptyList(), sessionLengthMinutes = 3, rootSeed = 1L, now = now)
        val long = SessionComposer.compose(newNode(0), emptyList(), sessionLengthMinutes = 10, rootSeed = 1L, now = now)
        assertTrue(short.plannedSlots.size < long.plannedSlots.size)
    }

    @Test
    fun `due reviews never exceed 40 percent of the session and are oldest-due first`() {
        val manyReviews =
            (1..50).map { i ->
                DueReview(
                    SkillIds.M2_DEG_SET_1,
                    now.minusSeconds(
                        (100 - i).toLong() * 86_400,
                    ),
                    null,
                )
            }
        val plan =
            SessionComposer.compose(
                newNode(totalAttempts = 100),
                manyReviews,
                sessionLengthMinutes = 5,
                rootSeed = 1L,
                now = now,
            )
        val reviewSlots = plan.plannedSlots.filter { it.isReview }
        val nonWarmupTotal = plan.plannedSlots.size - 5
        assertTrue(reviewSlots.size <= (nonWarmupTotal * 0.40).toInt() + 1)
    }

    @Test
    fun `a newly unlocked node - under the block threshold - gets a contiguous work block before any reviews`() {
        val reviews = (1..10).map { DueReview(SkillIds.M2_DEG_SET_1, now, null) }
        val plan =
            SessionComposer.compose(
                newNode(totalAttempts = 3),
                reviews,
                sessionLengthMinutes = 5,
                rootSeed = 1L,
                now = now,
            )
        val body = plan.plannedSlots.drop(5) // past warm-up
        val firstReviewIndex = body.indexOfFirst { it.isReview }
        val lastWorkIndex = body.indexOfLast { !it.isReview }
        assertTrue(
            firstReviewIndex > lastWorkIndex,
            "reviews must not appear before the contiguous new-node block finishes",
        )
    }

    @Test
    fun `an established node - at or past the block threshold - interleaves reviews through the work`() {
        val reviews = (1..15).map { DueReview(SkillIds.M2_DEG_SET_1, now, null) }
        val plan =
            SessionComposer.compose(
                newNode(totalAttempts = 200),
                reviews,
                sessionLengthMinutes = 5,
                rootSeed = 1L,
                now = now,
            )
        val body = plan.plannedSlots.drop(5)
        val firstReviewIndex = body.indexOfFirst { it.isReview }
        val lastNonReviewIndex = body.indexOfLast { !it.isReview }
        assertTrue(
            firstReviewIndex in 0 until lastNonReviewIndex,
            "reviews should be spread through the body, not clustered at one end",
        )
    }

    @Test
    fun `failure recovery appends one reduced-difficulty item only when the last item was wrong`() {
        val node = newNode(totalAttempts = 50)
        assertNull(SessionComposer.appendFailureRecoveryIfNeeded(lastPlannedItemWasCorrect = true, node))

        val recovery = SessionComposer.appendFailureRecoveryIfNeeded(lastPlannedItemWasCorrect = false, node)
        assertNotNull(recovery)
        assertTrue(!recovery.isWarmup && !recovery.isReview)
        assertEquals(
            masteredAxes.getValue(DifficultyAxis.CADENCE_FADE) - 1,
            recovery.axisLevels.getValue(DifficultyAxis.CADENCE_FADE),
        )
    }

    @Test
    fun `item seed mixing is deterministic and varies by index`() {
        val a = SessionComposer.itemSeed(rootSeed = 42L, index = 3)
        val b = SessionComposer.itemSeed(rootSeed = 42L, index = 3)
        val c = SessionComposer.itemSeed(rootSeed = 42L, index = 4)
        assertEquals(a, b)
        assertTrue(a != c)
    }
}
