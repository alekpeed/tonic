package com.tonic.core.curriculum.generators

import com.tonic.core.curriculum.graph.SkillGraph
import com.tonic.core.curriculum.sampling.BalancedSampler
import com.tonic.core.curriculum.sampling.KeySampler
import com.tonic.core.model.ids.SkillId
import com.tonic.core.model.items.CadenceFadeLevel
import com.tonic.core.model.items.DifficultyAxis
import com.tonic.core.model.items.Item
import com.tonic.core.model.items.ItemTiming
import com.tonic.core.model.items.ReferencePlan
import com.tonic.core.model.music.Mode
import com.tonic.core.model.music.PitchClass
import com.tonic.core.model.music.ScaleDegree
import com.tonic.core.model.music.TimbreId
import kotlin.random.Random

/**
 * `M12.*` item generation — docs/20-PHASE-2-SPEC.md §2.3. Pure and deterministic given
 * `(skill, axes, seed, history)`, like every other generator (CLAUDE.md §5).
 *
 * The item runs backwards compared to `M2`: establish the key, *name* a degree on screen without
 * sounding it, stay silent while the learner builds it internally, then sound something and ask
 * whether it was what they were holding. That inversion is the Gordon-derived endpoint of
 * docs/02-PEDAGOGY.md §6 — internal pitch generation rather than reaction to a stimulus.
 *
 * Two things here are load-bearing and easy to get subtly wrong.
 *
 * **The key is always fully established.** `M12` does not use the `CADENCE_FADE` axis at all
 * (docs/20-PHASE-2-SPEC.md §4), and every item plays the full four-chord cadence. That is not a
 * simplification: the skill under test is holding a degree against a *known* tonic, and a faded
 * reference would make a wrong answer ambiguous between "could not audiate" and "lost the key" —
 * exactly the confound docs/07-ADAPTIVE-ENGINE.md §3 exists to prevent. Fading the reference here
 * would be measuring two things at once.
 *
 * **Matched and mismatched items are balanced from a seeded coin.** A drifting ratio would reward
 * a learner for guessing the more common answer, and while `BinaryMasteryEvaluator`'s d-prime is
 * invariant to response bias, its accuracy criterion is not.
 */
object M12ItemGenerator {
    fun generate(
        skill: SkillId,
        axes: Map<DifficultyAxis, Int>,
        seed: Long,
        history: GenerationHistory = GenerationHistory(),
    ): M12GenerationResult {
        val random = Random(seed)

        val gapLevel = level(axes, DifficultyAxis.PREDICT_GAP)
        val deviationLevel = level(axes, DifficultyAxis.PREDICT_DEVIATION)

        val mode = SkillGraph.modeFor(skill)
        val pool = SkillGraph.activeDegreesFor(skill).sortedBy { it.semitoneOffset(mode) }
        val statedDegree = BalancedSampler.pick(pool, history.recentDegrees, random)

        val keyValue = KeySampler.pick(ALL_KEYS, history.recentKeys, random)
        val key = PitchClass(keyValue)
        val tonicMidi = TONIC_MIDI_FLOOR + keyValue
        val statedMidi = tonicMidi + statedDegree.semitoneOffset(mode)

        val timbre = TIMBRES[random.nextInt(TIMBRES.size)]
        val referenceTimbre = TimbreId.SOFT

        // Balanced, from the seeded coin - see the class doc.
        val shouldMatch = random.nextBoolean()
        val deviation =
            if (shouldMatch) {
                Deviation(statedMidi, 0.0)
            } else {
                deviate(statedMidi, statedDegree, mode, tonicMidi, pool, deviationLevel, random)
            }

        val referenceElements =
            ReferencePlanBuilder.keyEstablishment(
                keyPitchClass = key,
                mode = mode,
                tonicMidi = tonicMidi,
                timbre = referenceTimbre,
                chordDurationMs = CHORD_DURATION_MS,
                seed = seed,
            )

        val item =
            Item.PredictionItem(
                skill = skill,
                key = key,
                mode = mode,
                statedDegree = statedDegree,
                statedMidi = statedMidi,
                soundedMidi = deviation.midi,
                soundedCentsOffset = deviation.cents,
                // L0 is the full cadence. Recorded on the plan for the same reason every other item
                // records it: so what was heard is reconstructible from the log alone.
                referencePlan = ReferencePlan(CadenceFadeLevel.L0, referenceElements),
                timbre = timbre,
                referenceTimbre = referenceTimbre,
                timing =
                    ItemTiming(
                        referenceDurationMs = referenceElements.sumOf { it.durationMs },
                        // The silent audiation window is `gapBeforeSoundedNoteMs`, not this: the pause
                        // between the cadence ending and the *stated degree appearing* is a short
                        // breath, and conflating the two would start the audiation clock before the
                        // learner has been told what to audiate.
                        gapAfterReferenceMs = SETTLE_AFTER_REFERENCE_MS,
                        targetDurationMs = SOUNDED_NOTE_MS,
                    ),
                gapBeforeSoundedNoteMs = gapMs(gapLevel),
                activeDegrees = pool,
                seed = seed,
            )

        return M12GenerationResult(
            item = item,
            updatedHistory =
                history.copy(
                    recentDegrees = (history.recentDegrees + statedDegree).takeLast(BalancedSampler.WINDOW_SIZE),
                    recentKeys = (history.recentKeys + keyValue).takeLast(BalancedSampler.WINDOW_SIZE),
                ),
        )
    }

    /**
     * Silent gap length per `PREDICT_GAP` level — docs/20-PHASE-2-SPEC.md §2.3, verbatim. Longer is
     * harder because the internal representation has to be *held* rather than merely formed.
     */
    fun gapMs(level: Int): Long = GAP_MS_BY_LEVEL[level.coerceIn(GAP_MS_BY_LEVEL.indices)]

    /**
     * How the sounded note departs from the named one, per `PREDICT_DEVIATION` level.
     *
     * The levels are ordered by how *close* the deviation is, and therefore by how precise the
     * learner's internal representation has to be to catch it. An adjacent diatonic degree is a
     * different note in the same key; the wrong octave is the right note in the wrong place; a
     * chromatic neighbor is a semitone away and outside the key; 30 cents is not a different note at
     * all, only a slightly bent one. §2.3 marks that last level as an advanced target rather than a
     * required gate, which is enforced in `PredictionMasteryEvaluator`'s gap-level criterion — mastery
     * asks for `PREDICT_GAP` ≥ 2 and says nothing about deviation.
     */
    private fun deviate(
        statedMidi: Int,
        statedDegree: ScaleDegree,
        mode: Mode,
        tonicMidi: Int,
        pool: List<ScaleDegree>,
        level: Int,
        random: Random,
    ): Deviation =
        when (level.coerceIn(0, MAX_DEVIATION_LEVEL)) {
            0 -> {
                // The neighbor *within the node's own pool*: a learner must have a button-level concept
                // of the note that sounded, or the item is asking them to detect a note they have never
                // been taught, which is not the same skill.
                val index = pool.indexOf(statedDegree)
                val neighborIndex =
                    when {
                        pool.size < 2 -> index
                        index == 0 -> 1
                        index == pool.lastIndex -> index - 1
                        random.nextBoolean() -> index - 1
                        else -> index + 1
                    }
                Deviation(tonicMidi + pool[neighborIndex].semitoneOffset(mode), 0.0)
            }

            1 -> {
                val direction = if (random.nextBoolean()) 1 else -1
                Deviation(statedMidi + direction * OCTAVE, 0.0)
            }

            2 -> {
                val direction = if (random.nextBoolean()) 1 else -1
                Deviation(statedMidi + direction, 0.0)
            }

            else -> {
                val direction = if (random.nextBoolean()) 1.0 else -1.0
                Deviation(statedMidi, direction * DETUNE_CENTS)
            }
        }

    private fun level(
        axes: Map<DifficultyAxis, Int>,
        axis: DifficultyAxis,
    ): Int = axes[axis] ?: 0

    private data class Deviation(
        val midi: Int,
        val cents: Double,
    )

    private const val OCTAVE = 12
    private const val TONIC_MIDI_FLOOR = 60
    private const val CHORD_DURATION_MS = 900L
    private const val SOUNDED_NOTE_MS = 900L
    private const val MAX_DEVIATION_LEVEL = 3
    private const val DETUNE_CENTS = 30.0

    /** A short breath between the cadence ending and the named degree appearing. Not the audiation gap. */
    private const val SETTLE_AFTER_REFERENCE_MS = 400L

    private val GAP_MS_BY_LEVEL = longArrayOf(1_000L, 2_000L, 3_500L, 5_000L)
    private val ALL_KEYS = (0..11).toList()
    private val TIMBRES = listOf(TimbreId.PURE, TimbreId.SOFT)
}

data class M12GenerationResult(
    val item: Item.PredictionItem,
    val updatedHistory: GenerationHistory,
)
