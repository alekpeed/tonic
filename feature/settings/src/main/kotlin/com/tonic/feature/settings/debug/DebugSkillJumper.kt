package com.tonic.feature.settings.debug

import com.tonic.core.curriculum.graph.SkillGraph
import com.tonic.core.data.repository.AttemptRepository
import com.tonic.core.data.repository.SessionRepository
import com.tonic.core.data.repository.SkillStateRepository
import com.tonic.core.engine.debug.DebugMasterySeeder
import com.tonic.core.model.ids.SkillId
import com.tonic.core.model.state.MasteryState
import com.tonic.core.model.time.Clock
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * The orchestration behind `:feature:settings`' debug-only "jump to node" tool
 * (`BuildConfig.DEBUG` only — see [com.tonic.feature.settings.ui.SettingsScreen]).
 *
 * [DebugMasterySeeder] (`:core:engine`) knows how to fabricate one node's mastering attempt log; this
 * is what walks [SkillGraph.practiceChain] the same way [SkillGraph.currentNodeFor]'s own reachability
 * test does (see `PracticeChainTest`), seeding and persisting one node at a time so the *next* node's
 * gates see a real, freshly-rebuilt [com.tonic.core.model.state.SkillState] rather than an assumption
 * about chain order — exactly the sequence a learner would clear in play, just synthesized. A node the
 * learner has genuinely already mastered is never re-seeded: only what actually still blocks [target]
 * gets synthetic history.
 */
class DebugSkillJumper
    @Inject
    constructor(
        private val attemptRepository: AttemptRepository,
        private val skillStateRepository: SkillStateRepository,
        private val sessionRepository: SessionRepository,
        private val clock: Clock,
    ) {
        /**
         * Seeds whatever still blocks [target] so it becomes the next node [SkillGraph.currentNodeFor]
         * hands the practice loop. Returns the nodes it seeded, in the order it seeded them — empty if
         * [target] was already current.
         */
        suspend fun jumpTo(target: SkillId): List<SkillId> {
            val startedAt = clock.now()
            val session =
                sessionRepository.create(rootSeed = DEBUG_ROOT_SEED, plannedItemCount = 0, startedAt = startedAt)
            val sessionId = requireNotNull(session.id) { "a freshly created session must have an id" }

            val mastered =
                skillStateRepository
                    .observeAll()
                    .first()
                    .filterValues { it.masteryState == MasteryState.MASTERED }
                    .keys
                    .toMutableSet()

            val seeded = mutableListOf<SkillId>()
            val maxSteps = SkillGraph.practiceChain.size
            while (true) {
                val next = SkillGraph.currentNodeFor { it in mastered }
                if (next == target) break
                check(seeded.size < maxSteps) {
                    "could not reach ${target.raw} - it may already be mastered, or is not a real " +
                        "practice-chain node"
                }
                val attempts =
                    DebugMasterySeeder.attemptsToMaster(
                        next,
                        sessionId,
                        startedAt.plusSeconds(seeded.size * NODE_TIME_BUDGET_SECONDS),
                    )
                for (attempt in attempts) attemptRepository.record(attempt)
                skillStateRepository.rebuildFromAttempts(next)
                mastered += next
                seeded += next
            }

            sessionRepository.complete(sessionId, completedItemCount = seeded.size, endedAt = clock.now())
            return seeded
        }

        private companion object {
            /** Arbitrary but fixed — this tool has no real seed to thread through. */
            const val DEBUG_ROOT_SEED = 20_260_821L

            /** Generous headroom so consecutive nodes' synthetic timestamps never overlap. */
            const val NODE_TIME_BUDGET_SECONDS = 3_600L
        }
    }
