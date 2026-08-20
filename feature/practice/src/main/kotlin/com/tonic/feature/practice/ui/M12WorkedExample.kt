package com.tonic.feature.practice.ui

import com.tonic.core.curriculum.generators.M12ItemGenerator
import com.tonic.core.model.ids.SkillIds
import com.tonic.core.model.items.DifficultyAxis
import com.tonic.core.model.items.Item

/**
 * The worked example for the Module 12 explanation — docs/20-PHASE-2-SPEC.md §5.1, which is unusually
 * emphatic about this one: "a worked example is not optional here; it is the only way this task is
 * comprehensible."
 *
 * A real generated item through the real [M12ItemGenerator], like every other worked example. Fixed
 * seed and floor axes, so the gap is the shortest the app ever uses (1 second) and the deviation, if
 * there is one, is a whole neighboring degree rather than a 30-cent bend. The example exists to make
 * the *shape* of the task concrete — named note, silence, sounded note, judgment — not to test
 * anybody's audiation on their first encounter with the idea.
 */
internal object M12WorkedExample {
    /** Same for every user and every showing, so the narration can speak about it in the present tense. */
    private const val SEED = 20_260_821L

    fun generate(): Item.PredictionItem =
        M12ItemGenerator
            .generate(
                skill = SkillIds.M12_PREDICT_TRIAD,
                axes = DifficultyAxis.PREDICTION_AXES.associateWith { 0 },
                seed = SEED,
            ).item
}
