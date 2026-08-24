package com.tonic.feature.settings.debug

import com.tonic.core.curriculum.graph.SkillGraph
import com.tonic.core.data.repository.DebugProgressRepository
import com.tonic.core.data.repository.SkillStateRepository
import com.tonic.core.data.settings.SettingsRepository
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
 * Walks [SkillGraph.practiceChain] the same way [SkillGraph.currentNodeFor] does — or, for a target in
 * [SkillGraph.rhythmChain], walks that chain via [SkillGraph.currentRhythmNodeFor] instead, since the
 * two tracks are independent and neither function knows about the other's nodes. Either way, each node
 * before the target is stamped as mastered ([DebugMasterySeeder.masteredStateFor]) so the next node's
 * gates see real persisted state rather than an assumption about chain order.
 *
 * Two properties, both learned the hard way across three broken builds:
 *
 * **A jump is absolute, not incremental.** It resets progress first, then seeds up to the target. The
 * first version only moved *forward* from wherever the learner already was, so a target at or behind
 * the current node was unsatisfiable: the walk ran to its step ceiling and threw, which inside a
 * `viewModelScope.launch` crashed the app. Every button now works from every state, in any order.
 *
 * **It stamps state instead of replaying a fabricated history.** The original went through the real
 * mastery pipeline — 655 synthetic attempts across 25 nodes — because that felt more honest. On a
 * phone it was seconds of SQLite writes behind a button that looked dead, with a large surface for
 * failures nobody in this loop can attach a debugger to. Principle lost to reliability, correctly:
 * this is a debug affordance, and the invariant that every node is masterable *by real play* is worth
 * proving in a test (`DebugMasterySeederTest` still does) rather than at the cost of every press.
 */
class DebugSkillJumper
    @Inject
    constructor(
        private val debugProgressRepository: DebugProgressRepository,
        private val skillStateRepository: SkillStateRepository,
        private val settingsRepository: SettingsRepository,
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
                // Pitch and rhythm are independent tracks (SkillGraph.rhythmChain's KDoc) with their
                // own "what's next" function; a target belongs to exactly one, and that decides which
                // function walks it below.
                val inRhythmChain = SkillGraph.rhythmChain.any { it.id == target }
                require(inRhythmChain || SkillGraph.practiceChain.any { it.id == target }) {
                    "${target.raw} is not a node in either practice chain"
                }
                val chainSize = if (inRhythmChain) SkillGraph.rhythmChain.size else SkillGraph.practiceChain.size

                debugProgressRepository.resetProgress()
                // The diagnostic is placement, not progress, and resetProgress does not touch it - but a
                // fresh install starts before it and there is no way to skip it by hand. A tester who
                // reinstalls to escape a bad state should not be made to sit through it again.
                settingsRepository.setOnboardingCompleted(true)
                settingsRepository.setDiagnosticCompleted(true)

                val now = clock.now()
                val mastered = mutableSetOf<SkillId>()
                val seeded = mutableListOf<SkillId>()
                while (true) {
                    val next =
                        if (inRhythmChain) {
                            SkillGraph.currentRhythmNodeFor { it in mastered }
                        } else {
                            SkillGraph.currentNodeFor { it in mastered }
                        }
                    if (next == target) break
                    check(seeded.size < chainSize) {
                        "walked the whole chain without reaching ${target.raw} - the graph's gates and " +
                            "its chain order disagree"
                    }
                    skillStateRepository.update(DebugMasterySeeder.masteredStateFor(next, now))
                    mastered += next
                    seeded += next
                }
                seeded
            }
    }
