package com.tonic.feature.practice.engine

import com.tonic.core.audio.synth.PcmBuffer
import com.tonic.core.audio.synth.SynthEngine
import com.tonic.core.curriculum.generators.GenerationHistory
import com.tonic.core.curriculum.generators.M2ItemGenerator
import com.tonic.core.curriculum.generators.M9ItemGenerator
import com.tonic.core.model.ids.SkillId
import com.tonic.core.model.ids.SkillIds
import com.tonic.core.model.items.DifficultyAxis
import com.tonic.core.model.items.Item

/**
 * Everything the practice loop needs to know about *which kind* of item it is holding, in one file.
 *
 * The loop used to be typed on [Item.FunctionalRecognitionItem] end to end — state, rendering,
 * scoring, attempt construction. Phase 2 adds item types with genuinely different shapes (`M9` has no
 * target note; `M12` has no reference plan in the same sense), so the loop is now typed on [Item] and
 * asks here for the handful of per-type facts it actually uses. Keeping those in one place means the
 * loop's control flow — the part with a determinism-race history — stays untouched by every future
 * item type, and adding one is a compile error *here* rather than a silent wrong answer somewhere else.
 *
 * Every `when` below is exhaustive over the sealed hierarchy with explicit branches for the item types
 * the practice loop does not run (`M0.*` and `M1.*` belong to `:feature:diagnostic`, which has its own
 * loop). That is deliberate over an `else`: adding an item type must not silently fall into a default.
 */
internal object PracticeItems {
    /** The label a correct answer records — what `Attempt.targetLabel` stores and answers compare against. */
    fun correctLabel(item: Item): String =
        when (item) {
            is Item.FunctionalRecognitionItem -> item.targetDegree.canonicalLabel
            is Item.ModeIdentificationItem -> item.correctLabel
            is Item.PredictionItem -> item.correctLabel
            else -> unsupported(item)
        }

    /** The full item audio, rendered to one contiguous buffer before playback (docs/06-AUDIO-ENGINE.md §7). */
    fun renderAudio(item: Item): PcmBuffer =
        when (item) {
            is Item.FunctionalRecognitionItem ->
                SynthEngine.renderItem(
                    referencePlan = item.referencePlan,
                    gapAfterReferenceMs = item.timing.gapAfterReferenceMs,
                    targetMidi = item.targetMidi,
                    targetTimbre = item.timbre,
                    targetDurationMs = item.timing.targetDurationMs,
                    seed = item.seed,
                )

            // M9 is reference-only: the whole item *is* the passage being judged, with no target note
            // after it and no gap to leave.
            is Item.ModeIdentificationItem ->
                PcmBuffer(
                    item.elements
                        .mapIndexed { i, element ->
                            SynthEngine.renderElement(element, seed = item.seed + ELEMENT_SEED_STRIDE * i)
                        }.fold(FloatArray(0)) { acc, buffer -> acc + buffer.samples },
                    PcmBuffer.DEFAULT_SAMPLE_RATE,
                )

            is Item.PredictionItem -> error("M12 playback arrives with Stage 2.6, along with its own screen")
            else -> unsupported(item)
        }

    /**
     * Per-type values for the M2-shaped columns on `Attempt` (docs/05-DATA-MODEL.md §1). They are not
     * all meaningful for every skill, and where one is not, the value recorded is documented here
     * rather than invented at the call site.
     */
    fun keyPitchClass(item: Item): Int =
        when (item) {
            is Item.FunctionalRecognitionItem -> item.key.value
            is Item.ModeIdentificationItem -> item.key.value
            is Item.PredictionItem -> item.key.value
            else -> unsupported(item)
        }

    /**
     * The pitch the item is *about*. `M9` has no single target note — the answer is a property of the
     * whole passage — so it records its tonic, which is the reference every other pitch in the item was
     * built from and the only honest single-pitch summary available.
     */
    fun targetMidi(item: Item): Int =
        when (item) {
            is Item.FunctionalRecognitionItem -> item.targetMidi
            is Item.ModeIdentificationItem -> item.tonicMidi
            is Item.PredictionItem -> item.soundedMidi
            else -> unsupported(item)
        }

    /**
     * Denormalized for cheap querying (docs/05-DATA-MODEL.md §1). `M9` and `M12` do not use the
     * cadence-fade axis at all, so they record 0, meaning "not applicable" rather than "level zero" —
     * a distinction that matters only to a query filtering on this column, which should filter by
     * `skillId` first anyway.
     */
    fun cadenceFadeLevel(
        item: Item,
        axisLevels: Map<DifficultyAxis, Int>,
    ): Int =
        when (item) {
            is Item.FunctionalRecognitionItem -> axisLevels[DifficultyAxis.CADENCE_FADE] ?: 0
            is Item.ModeIdentificationItem, is Item.PredictionItem -> 0
            else -> unsupported(item)
        }

    fun timbreId(item: Item): String =
        when (item) {
            is Item.FunctionalRecognitionItem -> item.timbre.name
            is Item.ModeIdentificationItem -> item.timbre.name
            is Item.PredictionItem -> item.timbre.name
            else -> unsupported(item)
        }

    /**
     * Generates the next item for [skill]. The loop does not know which generator serves which module
     * and should not: that mapping lives here, beside the other per-type knowledge.
     */
    fun generate(
        skill: SkillId,
        axisLevels: Map<DifficultyAxis, Int>,
        seed: Long,
        history: GenerationHistory,
    ): Generated =
        if (skill in SkillIds.M9_NODES_IN_ORDER) {
            // M9 has no difficulty axes of its own - its three nodes *are* its progression, each a
            // separate skill rather than a level on a shared axis (docs/20-PHASE-2-SPEC.md §3).
            val result = M9ItemGenerator.generate(skill, seed, history)
            Generated(result.item, result.updatedHistory)
        } else {
            val result = M2ItemGenerator.generate(skill, axisLevels, seed, history)
            Generated(result.item, result.updatedHistory)
        }

    data class Generated(
        val item: Item,
        val updatedHistory: GenerationHistory,
    )

    private fun unsupported(item: Item): Nothing =
        error(
            "${item::class.simpleName} is a diagnostic item type and does not run in the practice loop - " +
                ":feature:diagnostic has its own loop for M0/M1",
        )

    /** Matches [SynthEngine.renderReferencePlan]'s own per-element seed spacing, so renders line up. */
    private const val ELEMENT_SEED_STRIDE = 1_000L
}
