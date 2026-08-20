package com.tonic.core.curriculum.sampling

import kotlin.random.Random

/**
 * Enforces the rolling-window frequency balance from docs/03-CURRICULUM.md
 * §5.4: "no degree may appear more than 1.5x the expected rate over any
 * 20-item span." Naive uniform sampling clumps, which distorts the
 * confusion matrix — this is the fix. Generic over the item being balanced
 * (a scale degree, a key, ...) so the same logic serves both
 * [com.tonic.core.curriculum.generators.M2ItemGenerator]'s degree choice
 * and its key choice.
 *
 * Pure and deterministic given [random] — callers own seeding it per
 * CLAUDE.md §5; this class has no internal mutable state of its own beyond
 * consuming the injected [Random].
 */
object BalancedSampler {
    const val WINDOW_SIZE = 20
    const val MAX_FREQUENCY_MULTIPLE = 1.5

    /**
     * Picks one of [candidates], weighted by [weights] (default uniform —
     * remediation weighting per docs/07-ADAPTIVE-ENGINE.md §4 plugs in here
     * once `:core:engine` exists to compute it), but excludes any candidate
     * whose count in [recentWindow] is already at or above the max allowed
     * for a 20-item window — unless *every* candidate would be excluded, in
     * which case the constraint is relaxed rather than generation stalling.
     *
     * [recentWindow] holds the last up to `WINDOW_SIZE - 1` picks, oldest
     * first; this pick becomes the WINDOW_SIZE-th on top of it.
     */
    fun <T> pick(
        candidates: List<T>,
        recentWindow: List<T>,
        random: Random,
        weights: Map<T, Double> = emptyMap(),
    ): T {
        require(candidates.isNotEmpty()) { "candidates must not be empty" }
        val expectedPerCandidate = WINDOW_SIZE.toDouble() / candidates.size
        val maxAllowedCount = expectedPerCandidate * MAX_FREQUENCY_MULTIPLE

        val relevantHistory = recentWindow.takeLast(WINDOW_SIZE - 1)
        val counts = relevantHistory.groupingBy { it }.eachCount()

        val eligible = candidates.filter { (counts[it] ?: 0) + 1 <= maxAllowedCount }
        val pool = eligible.ifEmpty { candidates }

        return weightedPick(pool, weights, random)
    }

    private fun <T> weightedPick(
        pool: List<T>,
        weights: Map<T, Double>,
        random: Random,
    ): T {
        val effectiveWeights = pool.map { (weights[it] ?: 1.0).coerceAtLeast(0.0) }
        val total = effectiveWeights.sum()
        if (total <= 0.0) return pool[random.nextInt(pool.size)]

        var target = random.nextDouble() * total
        for (i in pool.indices) {
            target -= effectiveWeights[i]
            if (target <= 0.0) return pool[i]
        }
        return pool.last()
    }
}
