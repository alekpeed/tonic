package com.tonic.feature.practice.ui

import com.tonic.core.curriculum.generators.M9ItemGenerator
import com.tonic.core.model.ids.SkillIds
import com.tonic.core.model.items.Item
import com.tonic.core.model.music.Mode

/**
 * The worked pair for the Module 9 explanation — docs/20-PHASE-2-SPEC.md §5.1 and docs/08-UI-SPEC.md
 * §3a. Real generated items through the real [M9ItemGenerator], never hand-built mock audio, for the
 * same reason [WorkedExample] is: a mock is free to drift from the exercise it claims to explain.
 *
 * **Two examples, not one.** Every other explanation in the app demonstrates a single item, but "major"
 * and "minor" are not meaningful alone — they are a contrast, and a learner who has never knowingly
 * heard either cannot calibrate one in isolation. Hearing them back to back, each labeled, is the whole
 * lesson; one example would demonstrate the interaction while leaving the actual distinction untaught.
 */
internal object M9WorkedExample {
    /** Fixed, so every user hears the same pair and the narration can speak about it in the present tense. */
    private const val SEED_BASE = 20_260_820L

    /** A clearly major cadence and a clearly minor one, in that order. */
    fun pair(): Pair<Item.ModeIdentificationItem, Item.ModeIdentificationItem> {
        val items =
            generateSequence(0L) { it + 1 }
                .take(SEARCH_LIMIT)
                .map { M9ItemGenerator.generate(SkillIds.M9_MODE_ID_CADENCE, seed = SEED_BASE + it).item }
                .toList()

        // The generator picks its mode from the seed, so the example asks it for the first of each
        // rather than assuming which a given seed produces.
        val major = items.first { it.mode == Mode.MAJOR }
        val minor = items.first { it.mode == Mode.MINOR }
        return major to minor
    }

    /** Both modes appear within a handful of seeds; the bound only stops an unbounded search. */
    private const val SEARCH_LIMIT = 32
}
