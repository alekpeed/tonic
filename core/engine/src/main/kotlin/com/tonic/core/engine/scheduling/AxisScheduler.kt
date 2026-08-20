package com.tonic.core.engine.scheduling

import com.tonic.core.engine.staircase.Staircase
import com.tonic.core.model.items.DifficultyAxis
import com.tonic.core.model.state.StaircaseState

/**
 * Decides which of the six M2 difficulty axes moves next — docs/07-ADAPTIVE-ENGINE.md
 * §3. Exactly one axis is active (has a running [Staircase]) at a time;
 * the rest sit at their current level, frozen once converged. This is what
 * makes a performance drop attributable to one cause instead of several —
 * CLAUDE.md's "two things most likely to go wrong."
 */
object AxisScheduler {
    private const val SAFETY_VALVE_WINDOW = 15
    private const val SAFETY_VALVE_THRESHOLD = 0.60
    private const val SAFETY_VALVE_STEP_DOWN = 2

    /** One real response on the currently active axis. Does nothing if every axis is already at max level. */
    fun update(
        state: AxisSchedulerState,
        correct: Boolean,
    ): AxisSchedulerState {
        val activated = ensureActiveAxis(state)
        val axis = activated.activeAxis ?: return activated

        val bounds = axis.levelRange
        val currentStaircase = activated.staircases[axis] ?: freshStaircase(axis, activated.levels)
        val updatedStaircase = Staircase.update(currentStaircase, correct, bounds)

        var levels = activated.levels + (axis to updatedStaircase.level)
        var staircases = activated.staircases + (axis to updatedStaircase)
        var recentCorrectness = (activated.recentCorrectness + correct).takeLast(SAFETY_VALVE_WINDOW)

        // Safety valve: "if the user's accuracy drops below 60% over 15 items: step the activeAxis
        // down 2 levels and clear its reversal history." A user who is drowning must be rescued
        // regardless of what the staircase thinks — docs/07-ADAPTIVE-ENGINE.md §3.
        if (recentCorrectness.size == SAFETY_VALVE_WINDOW) {
            val accuracy = recentCorrectness.count { it }.toDouble() / SAFETY_VALVE_WINDOW
            if (accuracy < SAFETY_VALVE_THRESHOLD) {
                val steppedLevel = (updatedStaircase.level - SAFETY_VALVE_STEP_DOWN).coerceIn(bounds)
                val rescued =
                    updatedStaircase.copy(
                        level = steppedLevel,
                        reversals = emptyList(),
                        consecutiveCorrect = 0,
                    )
                levels = levels + (axis to steppedLevel)
                staircases = staircases + (axis to rescued)
                recentCorrectness = emptyList()
            }
        }

        var result = activated.copy(levels = levels, staircases = staircases, recentCorrectness = recentCorrectness)

        val finalStaircase = staircases.getValue(axis)
        // "Reaching max level also freezes... even without 6 reversals" - but only to *reconfirm* a
        // ceiling the axis was already sitting at, not to canonize the first trial that happens to land
        // there. A sharp difficulty cliff right at the ceiling (e.g. CADENCE_FADE 6->7, where a learner
        // dependent on the cadence crutch craters from ~92% to chance-level) makes that distinction
        // matter for real: with stepSize=1, hopping from one level below max onto max needs only two
        // *lucky* correct answers at whatever the second-to-last level's true accuracy is - a low
        // per-attempt probability that a long practice history (hundreds of reactivations of the
        // highest-priority axis) makes likely to happen eventually anyway, and once it does, the old
        // "freeze on first arrival" rule locked it in forever with zero corroborating evidence, since a
        // maxed axis is never reactivated by a maintenance pass. Requiring the level to have *already*
        // been at max before this trial gives a freshly-arrived level one more genuine trial to reveal a
        // cliff (a miss there reverses back down immediately, same as any other staircase step) before
        // anything is locked in - confirmed directly by running a cadence-dependent learner through a
        // long, realistic session end to end (the headless practice loop, docs/09-BUILD-PLAN.md Stage 6),
        // which reproduced exactly this failure before this fix. A genuinely well-supported ascent (six
        // real reversals) still freezes immediately via [StaircaseState.hasConverged], unaffected.
        // The confirming trial must be a *genuine* confirmation - two consecutive correct completing an
        // upward move the ceiling then clamps - not one lucky answer. [Staircase] returns early on a
        // first correct without moving the level, so `level >= max` was already true after a single
        // correct response, and freezing on that is a coin flip rather than evidence. For a learner at
        // chance on a 7-degree set that is a 1-in-7 shot per trial at being pinned to CADENCE_FADE 7 -
        // no reference at all, the most information-free level on the axis - and since [pickNextAxis]
        // excludes maxed axes from both the fresh pick and the maintenance pass, the pin is permanent
        // and mastery becomes unreachable. A latent bug independent of step size, but §2a's corrected
        // CADENCE_FADE step of 1 makes the learner visit level 6 far more often and so exposed it:
        // measured at 1621 of 1800 simulated items stranded at 7, never mastering. Requiring the pair
        // makes that (1/7)^2 for such a learner while staying trivial for one who genuinely belongs at
        // max. docs/07-ADAPTIVE-ENGINE.md §3, "Arriving at max vs. reconfirming it."
        val wasAlreadyAtMax = currentStaircase.level >= axis.maxLevel
        val completedUpwardMove = correct && finalStaircase.consecutiveCorrect == 0
        val reconfirmedAtMax = wasAlreadyAtMax && completedUpwardMove && finalStaircase.level >= axis.maxLevel
        if (finalStaircase.hasConverged || reconfirmedAtMax) {
            result = freezeAndAdvance(result, axis, finalStaircase)
        }

        return result
    }

    /**
     * Freezes at the staircase's *estimated threshold* (mean of the last 4 reversal levels -
     * docs/07-ADAPTIVE-ENGINE.md §2), not the raw level of whichever single trial happened to trigger
     * convergence. A 2-down/1-up staircase oscillates across the true threshold right up until the
     * moment it converges, so the last trial's level is essentially a coin flip between the two sides of
     * that oscillation - freezing on it can leave the axis one step higher (or lower) than where it
     * actually settled. That matters most exactly where it matters most: at a sharp step-function
     * boundary (e.g. CADENCE_FADE 5->6, where accuracy craters once the reference is truly gone), an
     * unlucky "froze on the wrong side" would permanently strand a learner at a level they can't recover
     * from on their own once the axis stops being active - confirmed by
     * [com.tonic.core.engine.simulation.CadenceDependentLearnerSimulationTest]. Ties (a fractional part of
     * exactly 0.5, e.g. a threshold sitting exactly between two adjacent reversal levels - not a rare
     * edge case, since a tight two-level oscillation always alternates its reversal points and averages
     * to exactly X.5) deliberately round DOWN rather than using standard round-half-up: the two possible
     * levels are equally supported by the evidence, so break the tie toward the level the learner has
     * already demonstrably handled rather than the harder, unverified one. Falls back to the raw level
     * when there's no estimate yet (frozen via reaching the level ceiling before accumulating 6 reversals).
     */
    private fun freezeAndAdvance(
        state: AxisSchedulerState,
        converged: DifficultyAxis,
        staircase: StaircaseState,
    ): AxisSchedulerState {
        val settledLevel =
            staircase.estimatedThreshold?.let { threshold ->
                val floor = kotlin.math.floor(threshold).toInt()
                val fraction = threshold - floor
                val rounded = if (fraction > 0.5) floor + 1 else floor
                rounded.coerceIn(converged.levelRange)
            } ?: staircase.level
        val frozen = state.frozen + converged
        val levels = state.levels + (converged to settledLevel)
        val staircases = state.staircases + (converged to staircase.copy(level = settledLevel))
        val next = pickNextAxis(state.scope, frozen, levels)
        return activate(state.copy(frozen = frozen, levels = levels, staircases = staircases), next)
    }

    /**
     * A cold-start staircase for [axis], seeded from its current level and that axis's **own** initial
     * step size — docs/07-ADAPTIVE-ENGINE.md §2a. Reading the step size off the axis rather than one
     * global constant is what keeps [DifficultyAxis.CADENCE_FADE] stepping a single level at a time
     * (every rung genuinely passed through) while the other five keep §2's faster size-2 convergence.
     */
    private fun freshStaircase(
        axis: DifficultyAxis,
        levels: Map<DifficultyAxis, Int>,
    ): StaircaseState =
        StaircaseState(
            level = levels.getOrDefault(axis, 0),
            stepSize = axis.initialStepSize,
        )

    private fun ensureActiveAxis(state: AxisSchedulerState): AxisSchedulerState {
        if (state.activeAxis != null) return state
        return activate(state, pickNextAxis(state.scope, state.frozen, state.levels))
    }

    private fun activate(
        state: AxisSchedulerState,
        axis: DifficultyAxis?,
    ): AxisSchedulerState {
        if (axis == null) return state.copy(activeAxis = null, recentCorrectness = emptyList())
        val existing = state.staircases[axis]
        // A maintenance-pass reactivation of a previously-converged axis needs a genuinely fresh
        // staircase, not the stale one it converged with last time: that staircase's `reversals` already
        // has >= 6 entries, so `hasConverged` would read true again before a single new trial runs,
        // instantly refreezing it - and since it's typically the only non-max axis left when a
        // maintenance pass fires, it would then be immediately reselected and instantly refrozen again,
        // forever, never actually re-assessing the learner. Keep the level it settled at as the new
        // run's starting point; discard the exhausted reversal history and streak - but NOT the step
        // size, which stays at the minimum (1) rather than resetting to the "fast initial convergence"
        // starting size of 2. That larger size exists for a cold start from a genuinely unknown level; a
        // maintenance-pass resume is not that; it's re-checking a level the staircase has already
        // localized. Restarting at step size 2 would let two lucky consecutive-correct answers vault
        // straight over a one-level cliff (e.g. CADENCE_FADE 5 -> 7 in a single jump, skipping the actual
        // boundary at 6 entirely) instead of testing it - confirmed by
        // [com.tonic.core.engine.simulation.CadenceDependentLearnerSimulationTest].
        val staircase =
            if (existing != null && existing.hasConverged) {
                StaircaseState(level = existing.level, stepSize = StaircaseState.MIN_STEP_SIZE)
            } else {
                existing ?: freshStaircase(axis, state.levels)
            }
        return state.copy(
            activeAxis = axis,
            // Being active and being frozen are mutually exclusive - this also implements the
            // maintenance-pass "unfreeze" when every axis was frozen (see pickNextAxis).
            frozen = state.frozen - axis,
            staircases = state.staircases + (axis to staircase),
            recentCorrectness = emptyList(),
        )
    }

    /**
     * Highest-priority axis not yet frozen and not at max. If every axis is
     * frozen or maxed, this is the maintenance pass: "after all axes are
     * frozen, run a maintenance pass — the scheduler unfreezes the
     * highest-priority non-max axis and resumes. Progression is a loop, not
     * a single sweep." Returns null only when every axis is genuinely maxed.
     */
    private fun pickNextAxis(
        scope: DifficultyAxis.Scope,
        frozen: Set<DifficultyAxis>,
        levels: Map<DifficultyAxis, Int>,
    ): DifficultyAxis? {
        // The node's own family, not the recognition list. Hardcoding SCHEDULING_PRIORITY here was
        // correct while every scheduled node was a recognition node; for an M12 node it would have
        // picked CADENCE_FADE - an axis that prediction items do not have and whose level would have
        // changed nothing about the item, so the staircase would have been measuring noise.
        val priority = DifficultyAxis.schedulingPriorityFor(scope)
        val fresh =
            priority.firstOrNull {
                it !in frozen &&
                    levels.getOrDefault(it, 0) < it.maxLevel
            }
        if (fresh != null) return fresh
        return priority.firstOrNull { levels.getOrDefault(it, 0) < it.maxLevel }
    }
}

/** Per-skill-node axis scheduling state. Threaded by the caller across successive [AxisScheduler.update] calls. */
data class AxisSchedulerState(
    // Recognition axes, not every axis: a prediction axis in an M2 node's level map would be dead
    // weight in `staircaseStateJson` and a standing invitation for the scheduler to pick one.
    val levels: Map<DifficultyAxis, Int> = DifficultyAxis.RECOGNITION_AXES.associateWith { 0 },
    val staircases: Map<DifficultyAxis, StaircaseState> = emptyMap(),
    val frozen: Set<DifficultyAxis> = emptySet(),
    val activeAxis: DifficultyAxis? = null,
    /** Correctness on the *active* axis only, for the 60%-over-15-items safety valve. Reset whenever the active axis changes. */
    val recentCorrectness: List<Boolean> = emptyList(),
    /**
     * Which family of axes this node is scheduled over — docs/20-PHASE-2-SPEC.md §4. Defaults to
     * recognition so every Phase 1 construction site is unchanged; `M12` nodes are built through
     * [forScope].
     */
    val scope: DifficultyAxis.Scope = DifficultyAxis.Scope.RECOGNITION,
) {
    companion object {
        /** A fresh state over [scope]'s own axes, all at level 0. */
        fun forScope(scope: DifficultyAxis.Scope): AxisSchedulerState =
            AxisSchedulerState(
                levels = DifficultyAxis.axesFor(scope).associateWith { 0 },
                scope = scope,
            )
    }
}
