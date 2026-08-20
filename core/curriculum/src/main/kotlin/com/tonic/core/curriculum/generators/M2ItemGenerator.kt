package com.tonic.core.curriculum.generators

import com.tonic.core.curriculum.graph.SkillGraph
import com.tonic.core.curriculum.sampling.BalancedSampler
import com.tonic.core.curriculum.sampling.KeySampler
import com.tonic.core.model.ids.SkillId
import com.tonic.core.model.items.DifficultyAxis
import com.tonic.core.model.items.Item
import com.tonic.core.model.items.ItemTiming
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

        // The node's own mode, not a hardcoded major. M10 mirrors M2 exactly except for this
        // (docs/20-PHASE-2-SPEC.md §3), so one lookup here is the whole of what makes minor work:
        // every reference chord, every target pitch and every octave calculation below already takes
        // mode as a parameter and was simply always being handed MAJOR.
        val mode = SkillGraph.modeFor(skill)
        val activeDegrees = SkillGraph.activeDegreesFor(skill).sortedBy { it.degree }
        val targetDegree = BalancedSampler.pick(activeDegrees, history.recentDegrees, random, degreeWeights)

        // An open reference group holds its key and tonic across the whole group; only the first item
        // of a group samples fresh ones. L1 groups reuse a cadence for a few items; L6/L7 groups are
        // the audiation blocks of docs/02-PEDAGOGY.md §3 - "key established once at block start". A
        // level change always closes the group: the announcement mechanics and the block semantics both
        // start over. See [ReferenceGroup].
        val openGroup =
            history.referenceGroup?.takeIf {
                it.cadenceFadeLevel == cadenceFadeLevel && it.itemsRemaining > 0
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
                        mode,
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
                mode = mode,
                tonicMidi = tonicMidi,
                timbre = referenceTimbre,
                chordDurationMs = timing.referenceDurationMs,
                gapAfterReferenceMs = timing.gapAfterReferenceMs,
                targetDurationMs = timing.targetDurationMs,
                seed = seed xor 0x5EEDL,
            )
        val isAudiationLevel =
            cadenceFadeLevel == com.tonic.core.model.items.CadenceFadeLevel.L6 ||
                cadenceFadeLevel == com.tonic.core.model.items.CadenceFadeLevel.L7
        // Inside an open group the reference is *not* re-played - the key it established still stands.
        // For L1 that makes reusableForItems real (docs/07-ADAPTIVE-ENGINE.md §2a); for L6/L7 the first
        // item of each block plays a full key establishment and the rest play nothing, which is what
        // docs/02-PEDAGOGY.md §3 means by "no reference per item; key established once at block start."
        // Before this, L6/L7 items sampled a FRESH key per item with no reference audio at all - a bare
        // note in a key the user had never heard, unanswerable except by chance, and reported from live
        // use as exactly that.
        // The way back for a learner who lost hold of home mid-group: Replay on a silent item plays
        // this establishment first. Built at the GROUP's key and tonic, i.e. the exact home this item
        // is asking about - not a fresh sample.
        val homeReminder =
            if (openGroup != null) {
                builtPlan.copy(
                    elements =
                        ReferencePlanBuilder.keyEstablishment(
                            keyPitchClass = key,
                            mode = mode,
                            tonicMidi = tonicMidi,
                            timbre = referenceTimbre,
                            chordDurationMs = timing.referenceDurationMs,
                            seed = seed xor 0x5EEDL,
                        ),
                )
            } else {
                null
            }
        val referencePlan =
            when {
                openGroup != null -> builtPlan.copy(elements = emptyList())
                isAudiationLevel ->
                    builtPlan.copy(
                        elements =
                            ReferencePlanBuilder.keyEstablishment(
                                keyPitchClass = key,
                                mode = mode,
                                tonicMidi = tonicMidi,
                                timbre = referenceTimbre,
                                chordDurationMs = timing.referenceDurationMs,
                                seed = seed xor 0x5EEDL,
                            ),
                    )
                else -> builtPlan
            }
        val groupLength =
            when {
                cadenceFadeLevel == com.tonic.core.model.items.CadenceFadeLevel.L1 -> builtPlan.reusableForItems
                isAudiationLevel -> AUDIATION_BLOCK_ITEMS
                else -> 0
            }
        val updatedGroup =
            when {
                groupLength == 0 -> null
                openGroup != null ->
                    openGroup
                        .copy(itemsRemaining = openGroup.itemsRemaining - 1)
                        .takeIf { it.itemsRemaining > 0 }
                // A fresh group: this item carried the establishment, the next groupLength - 1 won't.
                else ->
                    ReferenceGroup(keyValue, tonicMidi, cadenceFadeLevel, groupLength, groupLength - 1)
                        .takeIf { it.itemsRemaining > 0 }
            }
        // L7 is L6 "with the silent gap lengthening across the block" - docs/02-PEDAGOGY.md §3. The
        // learner holds the tonic across a growing silence; position 0 is the establishment item.
        val positionInBlock = openGroup?.let { it.blockLength - it.itemsRemaining } ?: 0
        val gapAfterReferenceMs =
            if (cadenceFadeLevel == com.tonic.core.model.items.CadenceFadeLevel.L7) {
                timing.gapAfterReferenceMs + positionInBlock * L7_GAP_GROWTH_PER_ITEM_MS
            } else {
                timing.gapAfterReferenceMs
            }

        val item =
            Item.FunctionalRecognitionItem(
                skill = skill,
                key = key,
                mode = mode,
                targetDegree = targetDegree,
                targetMidi = targetMidi,
                referencePlan = referencePlan,
                timbre = targetTimbre,
                referenceTimbre = referenceTimbre,
                timing = ItemTiming(timing.referenceDurationMs, gapAfterReferenceMs, timing.targetDurationMs),
                activeDegrees = activeDegrees,
                seed = seed,
                homeReminder = homeReminder,
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

    /**
     * Items per L6/L7 audiation block - how long one key establishment has to last before the key is
     * refreshed (and possibly changed). docs/02-PEDAGOGY.md §3 defines the block behavior but not its
     * length, so this is this build's own tuning, same category as the M0 sub-test counts: long enough
     * that the learner is genuinely retaining the tonic rather than echoing it, short enough that one
     * lapse doesn't poison minutes of practice.
     */
    private const val AUDIATION_BLOCK_ITEMS = 8

    /** L7's gap growth per position in the block - a deliberate, audible stretch, not a subtle one. */
    private const val L7_GAP_GROWTH_PER_ITEM_MS = 250L
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
 * An open CADENCE_FADE reference group. At **L1**: "full cadence, then 2–3 items answered before it repeats"
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
    /** The level this group was opened at. A staircase move to any other level closes the group. */
    val cadenceFadeLevel: com.tonic.core.model.items.CadenceFadeLevel,
    /** Total items in the group, establishment item included — [itemsRemaining]'s starting point + 1. */
    val blockLength: Int,
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
