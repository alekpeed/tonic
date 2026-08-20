package com.tonic.core.curriculum.golden

import com.tonic.core.curriculum.generators.GenerationHistory
import com.tonic.core.curriculum.generators.M2ItemGenerator
import com.tonic.core.curriculum.graph.SkillGraph
import com.tonic.core.model.items.DifficultyAxis

/**
 * The frozen record of what `M2` generation produced before Phase 2 began — docs/20-PHASE-2-SPEC.md §7,
 * Stage 2.0: "same seeds must produce the same items before and after," treated as literal.
 *
 * Stage 2.0 threads [com.tonic.core.model.music.Mode] through the generator, widens the answer alphabet
 * past seven degrees, and makes difficulty axes skill-specific. Every one of those touches the path that
 * turns `(skill, axes, seed, history)` into an item, and CLAUDE.md §5 makes that function's output a
 * contract rather than an implementation detail. A passing test suite does not prove the contract held:
 * Phase 1's own tests all passed while generation was silently non-deterministic across runs. Only a
 * corpus captured *before* the refactor can prove it, and only if it is captured before the first line
 * changes — after that, a true "before" is unrecoverable.
 *
 * The corpus renders items with `toString()` deliberately. A hash would detect a change without saying
 * what changed; a data class's `toString()` enumerates every property, so a regression shows up in the
 * diff as the field that moved.
 *
 * **Runs, not isolated items.** Each entry generates a consecutive run threading [GenerationHistory],
 * because the behavior most at risk in Stage 2.0 is exactly the history-dependent kind: L1 reference
 * groups, L6/L7 audiation blocks, the balance constraint, and key spread all read history, and a corpus
 * of independent single items would be blind to every one of them.
 */
object M2GoldenCorpus {
    /** Long enough to open, hold, and close a reference group (L1 reuse, L6/L7 blocks are 8 items). */
    private const val RUN_LENGTH = 12

    /**
     * Axis configurations sampled per skill. `CADENCE_FADE` is swept across all eight levels because it
     * is the axis that restructures the reference itself; the others are sampled at their extremes plus
     * one middle, which is enough to catch a mapping that shifts without multiplying the corpus by every
     * combination.
     */
    private val AXIS_CONFIGS: List<Pair<String, Map<DifficultyAxis, Int>>> =
        buildList {
            for (fade in 0..DifficultyAxis.CADENCE_FADE.maxLevel) {
                add("fade$fade" to baseAxes(DifficultyAxis.CADENCE_FADE to fade))
            }
            add(
                "varied-low" to
                    baseAxes(
                        DifficultyAxis.CADENCE_FADE to 2,
                        DifficultyAxis.TIMBRE_VARIETY to 1,
                        DifficultyAxis.REGISTER_SPREAD to 1,
                        DifficultyAxis.OCTAVE_DISPLACE to 1,
                        DifficultyAxis.TEMPO_DENSITY to 1,
                        DifficultyAxis.KEY_SPREAD to 1,
                    ),
            )
            add(
                "varied-max" to
                    baseAxes(
                        DifficultyAxis.CADENCE_FADE to 5,
                        DifficultyAxis.TIMBRE_VARIETY to DifficultyAxis.TIMBRE_VARIETY.maxLevel,
                        DifficultyAxis.REGISTER_SPREAD to DifficultyAxis.REGISTER_SPREAD.maxLevel,
                        DifficultyAxis.OCTAVE_DISPLACE to DifficultyAxis.OCTAVE_DISPLACE.maxLevel,
                        DifficultyAxis.TEMPO_DENSITY to DifficultyAxis.TEMPO_DENSITY.maxLevel,
                        DifficultyAxis.KEY_SPREAD to DifficultyAxis.KEY_SPREAD.maxLevel,
                    ),
            )
        }

    private fun baseAxes(vararg overrides: Pair<DifficultyAxis, Int>): Map<DifficultyAxis, Int> =
        DifficultyAxis.entries.associateWith { 0 } + overrides

    /**
     * The corpus as a stable, line-oriented string. One header line per run, then one line per item.
     * Seeds are a pure function of the run's index so the matrix can grow at the end without renumbering
     * — an appended run must never perturb an existing one's seeds, or the golden file would appear to
     * regress when nothing regressed.
     */
    fun render(): String =
        buildString {
            var runIndex = 0
            for (node in SkillGraph.m2Nodes) {
                for ((configName, axes) in AXIS_CONFIGS) {
                    val seedBase = SEED_ORIGIN + runIndex * SEED_STRIDE
                    appendLine("## run=$runIndex skill=${node.id.raw} config=$configName seedBase=$seedBase")
                    var history = GenerationHistory()
                    for (i in 0 until RUN_LENGTH) {
                        val result = M2ItemGenerator.generate(node.id, axes, seedBase + i, history)
                        history = result.updatedHistory
                        appendLine("[$i] ${result.item}")
                    }
                    runIndex++
                }
            }
        }

    /** Arbitrary but fixed. Any change to these two constants invalidates the entire golden file. */
    private const val SEED_ORIGIN = 20_260_820_000L
    private const val SEED_STRIDE = 1_000L
}
