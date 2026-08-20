package com.tonic.core.curriculum.sampling

import kotlin.random.Random

/**
 * Key selection for M2 items. Layers one extra constraint on top of
 * [BalancedSampler]'s 20-item window balance: docs/03-CURRICULUM.md §5.3 —
 * "`KEY_SPREAD` never sits at a state where a single key repeats across
 * consecutive items more than twice — enforce in the generator."
 */
object KeySampler {
    fun pick(
        pool: List<Int>,
        recentKeys: List<Int>,
        random: Random,
    ): Int {
        val lastTwo = recentKeys.takeLast(2)
        val blocked = if (lastTwo.size == 2 && lastTwo[0] == lastTwo[1]) setOf(lastTwo[0]) else emptySet()

        val allowed = pool.filterNot { it in blocked }.ifEmpty { pool }
        return BalancedSampler.pick(allowed, recentKeys, random)
    }
}
