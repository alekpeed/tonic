package com.tonic.feature.practice.ui

import com.tonic.core.curriculum.generators.GenerationHistory
import com.tonic.core.curriculum.generators.M2ItemGenerator
import com.tonic.core.model.ids.SkillIds
import com.tonic.core.model.items.DifficultyAxis
import com.tonic.core.model.items.Item
import com.tonic.core.model.music.Mode

/**
 * The worked example for the mixed-mode explanation — docs/08-UI-SPEC.md §3a.
 *
 * A real generated `M10.MIXED_MODE` item at floor difficulty, through the real generator, like every
 * other worked example. **Minor on purpose**, and the search below exists only to guarantee it: the
 * new thing this node asks is "decide the mode first, then place the degree," and a major example
 * would demonstrate a flow indistinguishable from the seven-button practice the learner has been doing
 * all along. On a minor example the extra buttons have a visible reason to exist.
 */
internal object MixedModeWorkedExample {
    /** Fixed, so every user hears the same item and the narration can speak about it in the present tense. */
    private const val SEED_BASE = 20_260_822L

    fun generate(): Item.FunctionalRecognitionItem =
        generateSequence(0L) { it + 1 }
            .take(SEARCH_LIMIT)
            .map { offset ->
                M2ItemGenerator
                    .generate(
                        skill = SkillIds.M10_MIXED_MODE,
                        axes = DifficultyAxis.RECOGNITION_AXES.associateWith { 0 },
                        seed = SEED_BASE + offset,
                        history = GenerationHistory(),
                    ).item
            }.first { it.mode == Mode.MINOR }

    /** Both modes appear within a handful of seeds; the bound only stops an unbounded search. */
    private const val SEARCH_LIMIT = 32
}
