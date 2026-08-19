package com.tonic.core.model.items

/**
 * One difficulty axis moving between two consecutive items of the same skill —
 * docs/11-ONBOARDING-CLARITY.md §9.3: "When the adaptive engine changes the reference structure, the
 * active degree set, or any other axis in a way that changes what the exercise sounds or looks like,
 * the app must say so on screen, briefly, before or as it happens."
 *
 * Deliberately carries no copy of its own. What the change *is* belongs to the domain; how it is worded
 * belongs to the UI layer, which owns the string resources — same split docs/04-ARCHITECTURE.md §3
 * applies everywhere else.
 *
 * Covers both causes §9.3 names, without distinguishing them, because the user experiences no
 * difference: a staircase-driven step and the scheduled warmup-to-normal transition at item 6
 * (docs/07-ADAPTIVE-ENGINE.md §2a) are both simply "the exercise changed shape."
 */
data class AxisChange(
    val axis: DifficultyAxis,
    val from: Int,
    val to: Int,
) {
    /**
     * True when difficulty went *up* — less support, wider variety, less time. That is the
     * forward-progress direction and the copy says so; a decrease is support being added back, which
     * must never be framed as a demotion (docs/08-UI-SPEC.md §7 prohibits loss framing).
     */
    val isIncrease: Boolean get() = to > from
}
