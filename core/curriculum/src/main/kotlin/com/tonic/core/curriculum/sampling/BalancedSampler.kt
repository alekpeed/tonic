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
        /**
         * How many distinct values [recentWindow] is drawn from, when that is larger than
         * [candidates] — `M10.MIXED_MODE` is the only caller that differs, and it differs for a real
         * reason: the history spans all ten degrees of both modes while any one item can only target
         * the seven of its own mode.
         *
         * The ceiling below is "1.5× the expected rate", and *expected* is a property of the pool the
         * history came from, not of the shortlist this particular call is choosing between. Deriving
         * it from `candidates.size` there computed 1.5 × 20/7 ≈ 4.3 where the honest figure is
         * 1.5 × 20/10 = 3.0 — a ceiling loose enough that the balance rule barely bound at all, and
         * mode-specific degrees ended up with 1 or 2 attempts per 30-item window against a mastery
         * requirement of 3. Defaults to `candidates.size`, which is what every other caller means.
         */
        universeSize: Int = candidates.size,
    ): T {
        require(candidates.isNotEmpty()) { "candidates must not be empty" }
        val expectedPerCandidate = WINDOW_SIZE.toDouble() / maxOf(universeSize, candidates.size)
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
