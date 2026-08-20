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

    fun generate(): Item.FunctionalRecognitionItem =
        M2ItemGenerator
            .generate(
                skill = SkillIds.M2_DEG_SET_1,
                axes = DifficultyAxis.entries.associateWith { 0 },
                seed = SEED,
            ).item
}
