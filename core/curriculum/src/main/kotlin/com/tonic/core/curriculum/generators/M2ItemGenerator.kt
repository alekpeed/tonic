package com.tonic.core.curriculum.generators

import com.tonic.core.curriculum.graph.SkillGraph
import com.tonic.core.curriculum.sampling.BalancedSampler
import com.tonic.core.curriculum.sampling.KeySampler
import com.tonic.core.model.ids.SkillId
import com.tonic.core.model.items.DifficultyAxis
import com.tonic.core.model.items.Item
import com.tonic.core.model.items.ItemTiming
import com.tonic.core.model.music.Mode
import com.tonic.core.model.music.PitchClass
import com.tonic.core.model.music.ScaleDegree
import kotlin.random.Random

/**
 * `M2.*` item generation — docs/03-CURRICULUM.md §5.1/§5.4. Pure and
 * deterministic given `(skill, axes, seed, history)`: no clock reads, no
 * unseeded `Random` (CLAUDE.md §5). [GenerationHistory] carries exactly the
 * recent-picks state the balance/repeat constraints need — it's explicit
 * input, not hidden mutable state, so the whole call stays a pure function.
 */
object M2ItemGenerator {
    fun generate(
        skill: SkillId,
        axes: Map<DifficultyAxis, Int>,
        seed: Long,
        history: GenerationHistory = GenerationHistory(),
        degreeWeights: Map<ScaleDegree, Double> = emptyMap(),
    ): M2GenerationResult {
        val random = Random(seed)

        val cadenceFadeLevel =
            com.tonic.core.model.items.CadenceFadeLevel.fromLevel(
                level(axes, DifficultyAxis.CADENCE_FADE),
            )
        val timbreVarietyLevel = level(axes, DifficultyAxis.TIMBRE_VARIETY)
        val registerSpreadLevel = level(axes, DifficultyAxis.REGISTER_SPREAD)
        val octaveDisplaceLevel = level(axes, DifficultyAxis.OCTAVE_DISPLACE)
        val tempoDensityLevel = level(axes, DifficultyAxis.TEMPO_DENSITY)
        val keySpreadLevel = level(axes, DifficultyAxis.KEY_SPREAD)

        val activeDegrees = SkillGraph.activeDegreesFor(skill).sortedBy { it.degree }
        val targetDegree = BalancedSampler.pick(activeDegrees, history.recentDegrees, random, degreeWeights)

        // An open L1 group holds its key and tonic across the whole group; only the first item of a
        // group samples a fresh one. See [ReferenceGroup].
        val openGroup =
            history.referenceGroup?.takeIf {
                cadenceFadeLevel == com.tonic.core.model.items.CadenceFadeLevel.L1 && it.itemsRemaining > 0
            }

        val keyPool = AxisParameters.keyPool(keySpreadLevel)
        val keyValue = openGroup?.keyValue ?: KeySampler.pick(keyPool, history.recentKeys, random)
        val key = PitchClass(keyValue)

        val octaveOffset = pickOctaveOffset(octaveDisplaceLevel, key, targetDegree, history, random)

        val registerRange = AxisParameters.registerRange(registerSpreadLevel)
        val tonicMidi = openGroup?.tonicMidi ?: tonicMidiNear(registerRange, key)
        // Octave displacement deliberately can push the target outside the reference's register band -
        // docs/02-PEDAGOGY.md §5: "the target note may appear an octave above or below where the reference
        // established it." registerRange governs the REFERENCE's register, not a hard clamp on the target.
        val targetMidi =
            (
                tonicMidi +
                    targetDegree.semitoneOffset(
                        Mode.MAJOR,
                    ) + 12 * octaveOffset
            ).coerceIn(SAFE_MIDI_RANGE)

        val timbrePool = AxisParameters.timbrePool(timbreVarietyLevel)
        val targetTimbre = timbrePool[random.nextInt(timbrePool.size)]
        val referenceTimbre =
            if (AxisParameters.independentReferenceTimbre(timbreVarietyLevel)) {
                timbrePool[random.nextInt(timbrePool.size)]
            } else {
                targetTimbre
            }

        val timing = AxisParameters.timing(tempoDensityLevel)
        val builtPlan =
            ReferencePlanBuilder.build(
                cadenceFadeLevel = cadenceFadeLevel,
                keyPitchClass = key,
                mode = Mode.MAJOR,
                tonicMidi = tonicMidi,
                timbre = referenceTimbre,
                chordDurationMs = timing.referenceDurationMs,
                gapAfterReferenceMs = timing.gapAfterReferenceMs,
                targetDurationMs = timing.targetDurationMs,
                seed = seed xor 0x5EEDL,
            )
        // Inside an open L1 group the cadence is *not* re-played - the key it established still stands.
        // This is what finally makes L1 behave as specified; before this, `reusableForItems` was written
        // by the builder and read by nobody, so L1 rendered identically to L0
        // (docs/07-ADAPTIVE-ENGINE.md §2a).
        val referencePlan =
            if (openGroup != null) builtPlan.copy(elements = emptyList()) else builtPlan
        val updatedGroup =
            when {
                cadenceFadeLevel != com.tonic.core.model.items.CadenceFadeLevel.L1 -> null
                openGroup != null ->
                    openGroup
                        .copy(itemsRemaining = openGroup.itemsRemaining - 1)
                        .takeIf { it.itemsRemaining > 0 }
                // A fresh group: this item carried the cadence, the next `reusableForItems - 1` won't.
                else ->
                    ReferenceGroup(keyValue, tonicMidi, builtPlan.reusableForItems - 1)
                        .takeIf { it.itemsRemaining > 0 }
            }

        val item =
            Item.FunctionalRecognitionItem(
                skill = skill,
                key = key,
                mode = Mode.MAJOR,
                targetDegree = targetDegree,
                targetMidi = targetMidi,
                referencePlan = referencePlan,
                timbre = targetTimbre,
                referenceTimbre = referenceTimbre,
                timing = ItemTiming(timing.referenceDurationMs, timing.gapAfterReferenceMs, timing.targetDurationMs),
                activeDegrees = activeDegrees,
                seed = seed,
            )

        return M2GenerationResult(item, history.with(targetDegree, keyValue, octaveOffset, updatedGroup))
    }

    private fun level(
        axes: Map<DifficultyAxis, Int>,
        axis: DifficultyAxis,
    ): Int = (axes[axis] ?: 0).coerceIn(axis.levelRange)

    private fun pickOctaveOffset(
        level: Int,
        key: PitchClass,
        degree: ScaleDegree,
        history: GenerationHistory,
        random: Random,
    ): Int {
        val offsets = AxisParameters.octaveOffsets(level)
        val lastTriple = history.recentTriples.lastOrNull()
        // "Never emit the same (key, degree, octave) triple twice in a row" - docs/03-CURRICULUM.md §5.4.
        val forbidden =
            if (lastTriple != null &&
                lastTriple.key == key.value &&
                lastTriple.degree == degree
            ) {
                lastTriple.octave
            } else {
                null
            }
        val allowed = offsets.filterNot { it == forbidden }.ifEmpty { offsets }
        return allowed[random.nextInt(allowed.size)]
    }

    /** The MIDI instance of [key] closest to the middle of [range] — where the reference material sits. */
    private fun tonicMidiNear(
        range: IntRange,
        key: PitchClass,
    ): Int {
        val center = (range.first + range.last) / 2
        var candidate = center - Math.floorMod(center - key.value, 12)
        // floorMod above can land up to 11 semitones below center; nudge up if the neighbor is closer.
        if (center - candidate > 6) candidate += 12
        return candidate
    }

    private val SAFE_MIDI_RANGE = 24..108
}

/**
 * Recent-picks state the balance/repeat constraints in [M2ItemGenerator]
 * need. Bounded to a small trailing window rather than growing forever —
 * callers thread the return value of one [M2ItemGenerator.generate] call
 * into the next.
 */
data class GenerationHistory(
    val recentDegrees: List<ScaleDegree> = emptyList(),
    val recentKeys: List<Int> = emptyList(),
    val recentTriples: List<KeyDegreeOctave> = emptyList(),
    /** Live CADENCE_FADE L1 group, if one is open — see [ReferenceGroup]. Null at every other level. */
    val referenceGroup: ReferenceGroup? = null,
) {
    fun with(
        degree: ScaleDegree,
        key: Int,
        octave: Int,
        referenceGroup: ReferenceGroup? = null,
    ): GenerationHistory =
        GenerationHistory(
            recentDegrees = (recentDegrees + degree).takeLast(BalancedSampler.WINDOW_SIZE),
            recentKeys = (recentKeys + key).takeLast(BalancedSampler.WINDOW_SIZE),
            recentTriples = (recentTriples + KeyDegreeOctave(key, degree, octave)).takeLast(4),
            referenceGroup = referenceGroup,
        )
}

/**
 * An open CADENCE_FADE **L1** group: "full cadence, then 2–3 items answered before it repeats"
 * (docs/02-PEDAGOGY.md §3; docs/06-AUDIO-ENGINE.md §7 states the same as
 * `reusableForItems = 2..3` — "the ViewModel does not re-request it").
 *
 * The first item of a group plays the full four-chord cadence and establishes the key. The remaining
 * [itemsRemaining] items stay in that same key ([keyValue] / [tonicMidi]) and play **no** reference at
 * all — the learner has to hold the tonic across items instead of being handed it back every time.
 * That retention demand is the whole of what separates L1 from L0, which re-establishes the key from
 * scratch, in a freshly sampled key, before every single item.
 *
 * Carried in [GenerationHistory] rather than held as mutable state in the caller so generation stays a
 * pure function of `(skill, axes, seed, history)` — CLAUDE.md §5.
 */
data class ReferenceGroup(
    val keyValue: Int,
    val tonicMidi: Int,
    val itemsRemaining: Int,
)

data class KeyDegreeOctave(
    val key: Int,
    val degree: ScaleDegree,
    val octave: Int,
)

data class M2GenerationResult(
    val item: Item.FunctionalRecognitionItem,
    val updatedHistory: GenerationHistory,
)
