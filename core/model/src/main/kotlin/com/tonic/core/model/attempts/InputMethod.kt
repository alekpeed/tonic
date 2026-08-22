package com.tonic.core.model.attempts

/**
 * How a learner gave an answer — docs/30-PHASE-3-SPEC.md §4 and §7.
 *
 * **A property of the attempt, never of the skill.** §2 states the invariant this exists to serve and
 * to be checked against: "Sung and tapped attempts are **not** separate skill states. They contribute
 * to the same `SkillState` for a given node — the input method is a property of the attempt, not of
 * the skill." Recorded so the confusion matrix and later analysis can tell the two apart without ever
 * splitting a node's progress between them, and so a learner who never grants microphone permission
 * reaches exactly the same mastery by exactly the same route.
 *
 * Anything that adapts — `Staircase`, `AxisScheduler`, `MasteryEvaluator`, `ConfusionTracker` — must
 * treat the two identically. `SungDataIsNeverAdaptiveTest` asserts that rather than trusting it.
 */
enum class InputMethod {
    /** The degree ladder. The only method before Phase 3, and the default for every attempt that does not say otherwise. */
    TAP,

    /** Sung into the microphone and resolved to a degree by `SungResponseAnalyzer` (docs/30-PHASE-3-SPEC.md §5.2). */
    SUNG,
}
