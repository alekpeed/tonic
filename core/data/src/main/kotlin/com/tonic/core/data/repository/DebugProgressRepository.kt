package com.tonic.core.data.repository

import com.tonic.core.model.attempts.Attempt

/**
 * Bulk progress operations that exist **only** for the debug "jump to node" tool
 * (`:feature:settings`, `BuildConfig.DEBUG`-gated). Deliberately its own interface rather than extra
 * methods on [AttemptRepository]/[SkillStateRepository]: `resetProgress` erases a learner's entire
 * history, and that is not an operation the practice loop should be able to reach by autocomplete.
 *
 * Nothing in the production code path calls either method.
 */
interface DebugProgressRepository {
    /**
     * Erases every attempt, every derived skill state, and every confusion matrix — the whole of what
     * makes up "where the learner is." Sessions are left alone: attempts reference them, and the
     * streak reads them, so deleting them would strand data rather than reset it.
     *
     * This is what makes a debug jump *absolute* instead of incremental. The first version of the jump
     * tool only ever moved forward from wherever the learner already was, so asking for a node at or
     * behind the current one was unsatisfiable — it looped to an error and crashed the app. Resetting
     * first means any node can be asked for from any state, in any order, and the same request always
     * produces the same result.
     */
    suspend fun resetProgress()

    /** Records many attempts in one transaction — see [com.tonic.core.data.dao.AttemptDao.insertAll]. */
    suspend fun recordAttempts(attempts: List<Attempt>)
}
