package com.tonic.feature.settings.debug

import com.tonic.core.curriculum.graph.SkillGraph
import com.tonic.core.data.repository.DebugProgressRepository
import com.tonic.core.data.repository.SessionRepository
import com.tonic.core.data.repository.SkillStateRepository
import com.tonic.core.engine.debug.DebugMasterySeeder
import com.tonic.core.model.ids.SkillId
import com.tonic.core.model.time.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * The orchestration behind `:feature:settings`' debug-only "jump to node" tool
 * (`BuildConfig.DEBUG` only — see [com.tonic.feature.settings.ui.SettingsScreen]).
 *
 * [DebugMasterySeeder] (`:core:engine`) knows how to fabricate one node's mastering attempt log; this
 * walks [SkillGraph.practiceChain] the same way [SkillGraph.currentNodeFor]'s own reachability test
 * does, seeding and persisting one node at a time so the *next* node's gates see a real, freshly-rebuilt
 * [com.tonic.core.model.state.SkillState] rather than an assumption about chain order — exactly the
 * sequence a learner would clear in play, just synthesized.
 *
 * **A jump is absolute, not incremental: it resets all progress first, then seeds up to [jumpTo]'s
 * target.** The first version did not, and only ever moved *forward* from wherever the learner already
 * was. Asking it for a node at or behind the current one was therefore unsatisfiable: the walk could
 * never reach the target, ran to its step ceiling and threw — which, inside a `viewModelScope.launch`,
 * crashed the app. Found the way it should have been found before shipping, by a tester pressing the
 * buttons in the order a person actually presses them rather than the order the walk assumed. Resetting
 * first makes every button work from every state, in any order, and makes the same press always mean
 * the same thing.
 */
class DebugSkillJumper
    @Inject
    constructor(
        private val debugProgressRepository: DebugProgressRepository,
        private val skillStateRepository: SkillStateRepository,
        private val sessionRepository: SessionRepository,
        private val clock: Clock,
    ) {
        /**
         * Resets all progress, then seeds every node before [target] so it becomes what
         * [SkillGraph.currentNodeFor] hands the practice loop. Returns the nodes it seeded, in order —
         * empty when [target] is the chain's first node, which needs no history at all.
         *
         * [target] itself is deliberately left unmastered: the point is to practice it for real.
         */
        suspend fun jumpTo(target: SkillId): List<SkillId> =
            // Seeding is hundreds of inserts plus a full mastery replay per node. On viewModelScope's
            // Dispatchers.Main.immediate that is an ANR, not a slow moment - which is exactly what "I
            // pressed it, it hung for a minute and crashed" was.
            //
            // Hardcoded rather than injected: a constructor default is invisible to Dagger, which binds
            // every parameter or none, so `dispatcher: CoroutineDispatcher = Dispatchers.Default` made
            // the whole app graph fail to compile with a MissingBinding. Tests call this under
            // runBlocking and await the result, so a real dispatcher costs them nothing.
            withContext(Dispatchers.Default) {
                require(SkillGraph.practiceChain.any { it.id == target }) {
                    "${target.raw} is not a node in the practice chain"
                }

                debugProgressRepository.resetProgress()

                val startedAt = clock.now()
                // Created after the reset so it survives it. Deliberately never completed: the streak
                // counts completed sessions by day, and a debug jump is not a day of practice.
                val session =
                    sessionRepository.create(rootSeed = DEBUG_ROOT_SEED, plannedItemCount = 0, startedAt = startedAt)
                val sessionId = requireNotNull(session.id) { "a freshly created session must have an id" }

                val mastered = mutableSetOf<SkillId>()
                val seeded = mutableListOf<SkillId>()
                while (true) {
                    val next = SkillGraph.currentNodeFor { it in mastered }
                    if (next == target) break
                    check(seeded.size < SkillGraph.practiceChain.size) {
                        "walked the whole chain without reaching ${target.raw} - the graph's gates and " +
                            "its chain order disagree"
                    }
                    val attempts =
                        DebugMasterySeeder.attemptsToMaster(
                            next,
                            sessionId,
                            startedAt.plusSeconds(seeded.size * NODE_TIME_BUDGET_SECONDS),
                        )
                    debugProgressRepository.recordAttempts(attempts)
                    skillStateRepository.rebuildFromAttempts(next)
                    mastered += next
                    seeded += next
                }
                seeded
            }

        private companion object {
            /** Arbitrary but fixed — this tool has no real seed to thread through. */
            const val DEBUG_ROOT_SEED = 20_260_821L

            /** Generous headroom so consecutive nodes' synthetic timestamps never overlap. */
            const val NODE_TIME_BUDGET_SECONDS = 3_600L
        }
    }
