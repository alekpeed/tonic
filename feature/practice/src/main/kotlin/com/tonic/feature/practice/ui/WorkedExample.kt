package com.tonic.feature.practice.ui

import com.tonic.core.curriculum.generators.M2ItemGenerator
import com.tonic.core.model.ids.SkillIds
import com.tonic.core.model.items.DifficultyAxis
import com.tonic.core.model.items.Item

/**
 * The single worked example docs/11-ONBOARDING-CLARITY.md §3 requires before a first-time user is asked
 * to answer anything: "play the reference, play a target note, show which button is correct and why, in
 * real audio."
 *
 * A *real* generated item, not a hand-built mock — it goes through the same [M2ItemGenerator] and the
 * same rendering path as every practice item, so what the user is shown is exactly what they are about
 * to be asked to do. A mock-up would be free to drift from the real thing, which is how explanation
 * screens end up describing an exercise that no longer exists.
 *
 * Fixed seed and floor difficulty on purpose: every user sees the same example, and it is the easiest
 * the app can produce — full four-chord cadence, one timbre, no octave displacement, slowest timing.
 * The example exists to make the *mechanic* concrete, not to be a first test.
 */
internal object WorkedExample {
    /** Same for every user and every showing, so the narration below can name the answer as a constant. */
    private const val SEED = 20_260_819L

    fun generate(): Item.FunctionalRecognitionItem = generate(SkillIds.M2_DEG_SET_1, SEED)

    /**
     * The minor counterpart, for `M10`'s explanation screen. Same discipline: a real generated item at
     * floor difficulty, so the example a learner hears is the exercise they are about to be given.
     * Its own seed, because the point of this one is to demonstrate a *minor* tonic and reusing the
     * major seed would only coincidentally land somewhere useful.
     */
    fun generateMinor(): Item.FunctionalRecognitionItem = generate(SkillIds.M10_MIN_SET_1, MINOR_SEED)

    private fun generate(
        skill: com.tonic.core.model.ids.SkillId,
        seed: Long,
    ): Item.FunctionalRecognitionItem =
        M2ItemGenerator
            .generate(
                skill = skill,
                axes = DifficultyAxis.RECOGNITION_AXES.associateWith { 0 },
                seed = seed,
            ).item

    private const val MINOR_SEED = 20_260_820_555L
}
