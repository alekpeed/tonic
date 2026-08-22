package com.tonic.core.curriculum.generators

import com.tonic.core.curriculum.graph.SkillGraph
import com.tonic.core.curriculum.sampling.BalancedSampler
import com.tonic.core.model.ids.SkillIds
import com.tonic.core.model.items.CadenceFadeLevel
import com.tonic.core.model.items.DifficultyAxis
import com.tonic.core.model.music.Mode
import com.tonic.core.model.music.ScaleDegree
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `M10.MIXED_MODE` — docs/20-PHASE-2-SPEC.md §3's interleaving node. Degree identification with the
 * mode randomized per item and never announced before the answer.
 */
class MixedModeTest {
    private val skill = SkillIds.M10_MIXED_MODE

    private fun axes(fade: Int = 0) =
        DifficultyAxis.RECOGNITION_AXES.associateWith { 0 } + (DifficultyAxis.CADENCE_FADE to fade)

    private fun items(
        count: Int,
        fade: Int = 0,
        seedBase: Long = 11_000L,
    ) = buildList {
        var history = GenerationHistory()
        repeat(count) { i ->
            val result = M2ItemGenerator.generate(skill, axes(fade), seed = seedBase + i, history = history)
            history = result.updatedHistory
            add(result.item)
        }
    }

    @Test
    fun `the ladder shows all ten degrees, whatever mode the item is in`() {
        // The whole node in one assertion. If the ladder showed only the item's own seven, a ♭3 button
        // on screen would state that this item is minor before a note sounded, and the learner would be
        // reading the buttons instead of hearing the key - which is precisely the skill §3 says this
        // node exists to train.
        val union = ScaleDegree.ALL_DIATONIC + ScaleDegree.ALL_NATURAL_MINOR
        assertEquals(10, union.size)
        assertEquals(union, SkillGraph.activeDegreesFor(skill))

        for (item in items(60)) {
            assertEquals(
                union,
                item.activeDegrees.toSet(),
                "a ${item.mode} item offered ${item.activeDegrees.size} buttons - the set must not vary " +
                    "with the mode, or the buttons announce it",
            )
        }
    }

    @Test
    fun `both modes actually appear, in something close to balance`() {
        val generated = items(200)
        val minor = generated.count { it.mode == Mode.MINOR }
        assertTrue(minor > 0 && minor < generated.size, "only one mode was ever generated")
        assertTrue(
            abs(minor - generated.size / 2) <= generated.size / 5,
            "$minor of ${generated.size} items were minor - too lopsided to call it unannounced",
        )
    }

    @Test
    fun `a major item never targets a minor-only degree, and vice versa`() {
        // The distinction the node forces between "what is on the ladder" and "what can be asked":
        // asking for ♭3 under a major cadence would be asking about a note the key does not contain.
        for (item in items(120)) {
            val allowed = SkillGraph.targetDegreesFor(skill, item.mode)
            assertTrue(
                item.targetDegree in allowed,
                "${item.mode} item targeted ${item.targetDegree.canonicalLabel}, which is not in that mode",
            )
        }
    }

    @Test
    fun `the cadence establishes the item's own mode`() {
        // What the learner has to hear. The reference is the only thing that says which mode this is,
        // so its third has to actually be the mode's third.
        for (item in items(40).filter { it.referencePlan.elements.isNotEmpty() }) {
            val tonicChord =
                item.referencePlan.elements
                    .filterIsInstance<com.tonic.core.model.items.ReferenceElement.ChordEvent>()
                    .first()
            val third = tonicChord.midiNotes[1] - tonicChord.midiNotes[0]
            val expected = if (item.mode == Mode.MINOR) 3 else 4
            assertEquals(
                expected,
                third,
                "a ${item.mode} item's tonic triad has a $third-semitone third - the cadence is stating " +
                    "the wrong mode, and it is the only thing stating one at all",
            )
        }
    }

    @Test
    fun `every pitch the reference sounds has a button on the ladder`() {
        // docs/20-PHASE-2-SPEC.md §8.1 decision 4's rule, which is easy to break here: the union
        // contains both ♭3 and ♮3, so both modes' cadences are covered - but that is a fact worth
        // checking rather than assuming, since narrowing the ladder would silently break it.
        for (item in items(40).filter { it.referencePlan.elements.isNotEmpty() }) {
            val tonicMidi = item.targetMidi - item.targetDegree.semitoneOffset(item.mode)
            val offsets =
                item.referencePlan.elements
                    .filterIsInstance<com.tonic.core.model.items.ReferenceElement.ChordEvent>()
                    .flatMap { it.midiNotes }
                    .map { Math.floorMod(it - tonicMidi, 12) }
                    .toSet()
            val available = item.activeDegrees.map { it.semitoneOffset(item.mode) }.toSet()
            assertTrue(
                offsets.all { it in available },
                "the ${item.mode} cadence sounds ${offsets - available} semitones above the tonic, and " +
                    "the learner has no button for them",
            )
        }
    }

    @Test
    fun `an audiation block holds one mode from its establishment to its last item`() {
        // The correctness requirement the reference group exists for. At CADENCE_FADE L6 the first item
        // of a block plays the cadence and the rest play a bare note against it. Re-rolling the mode
        // inside the block would ask about a key the learner was never given - unanswerable rather than
        // merely hard, which is the failure docs/07-ADAPTIVE-ENGINE.md §2a exists to prevent.
        val block = items(24, fade = CadenceFadeLevel.L6.level)
        var establishedMode: Mode? = null
        var blocksSeen = 0
        for (item in block) {
            if (item.referencePlan.elements.isNotEmpty()) {
                establishedMode = item.mode
                blocksSeen++
            } else {
                assertEquals(
                    establishedMode,
                    item.mode,
                    "a silent block item is in ${item.mode} but the block was established in $establishedMode",
                )
            }
        }
        assertTrue(blocksSeen > 1, "the run produced no complete audiation blocks to check")
    }

    @Test
    fun `an L1 group also holds its mode`() {
        val group = items(24, fade = CadenceFadeLevel.L1.level)
        var establishedMode: Mode? = null
        for (item in group) {
            if (item.referencePlan.elements.isNotEmpty()) {
                establishedMode = item.mode
            } else {
                assertEquals(establishedMode, item.mode)
            }
        }
    }

    @Test
    fun `generation is deterministic, mode included`() {
        val a = M2ItemGenerator.generate(skill, axes(), seed = 99L).item
        val b = M2ItemGenerator.generate(skill, axes(), seed = 99L).item
        assertEquals(a, b)
        assertEquals(a.mode, b.mode)
    }

    @Test
    fun `no other node randomizes its mode`() {
        // The guard on the Stage 2.0 golden corpus: the mode branch consumes from the random stream, so
        // it must never be entered for a node whose items are pinned.
        assertTrue(SkillGraph.randomizesMode(SkillIds.M10_MIXED_MODE))
        for (skill in listOf(
            SkillIds.M2_DEG_SET_1,
            SkillIds.M2_FULL_DIATONIC,
            SkillIds.M10_MIN_NATURAL,
            SkillIds.M11_CHROM_FULL,
            SkillIds.M12_PREDICT_DIATONIC,
        )) {
            assertTrue(!SkillGraph.randomizesMode(skill), "${skill.raw} must not randomize its mode")
            assertEquals(SkillGraph.activeDegreesFor(skill), SkillGraph.targetDegreesFor(skill, Mode.MAJOR))
            assertEquals(SkillGraph.activeDegreesFor(skill), SkillGraph.targetDegreesFor(skill, Mode.MINOR))
        }
    }

    @Test
    fun `its three gates are all declared, and none is implied by another`() {
        // docs/20-PHASE-2-SPEC.md §3: M2.FULL_DIATONIC + M10.MIN_NATURAL + M9.MODE_ID_CADENCE. The
        // third is what makes the node answerable at all - without it a learner is being asked to name
        // a degree in a mode they cannot identify.
        val node = SkillGraph.node(skill)
        val gates = setOfNotNull(node.prerequisite) + node.alsoRequires
        assertEquals(
            setOf(SkillIds.M2_FULL_DIATONIC, SkillIds.M10_MIN_NATURAL, SkillIds.M9_MODE_ID_CADENCE),
            gates,
        )
    }

    @Test
    fun `every degree on the ladder is reachable often enough for mastery to be possible`() {
        // The measured claim behind the ten-degree set, and it took the measurement to get right.
        //
        // docs/03-CURRICULUM.md §5.5's coverage criterion asks every active degree for
        // min(5, 30/n) attempts, which at ten degrees is three - and ten degrees at three attempts is
        // thirty items exactly. A qualifying window has to be a *perfect partition* of the mastery
        // window. That is survivable only because MasteryEvaluator runs on every attempt over a
        // *rolling* window: the node needs one covering window to exist, not every window to be one.
        //
        // Under weighting that was merely fair in expectation, one arrived after a median of roughly
        // 440 items and on one seed did not arrive within two thousand - a node a learner could
        // practice for weeks without being certified on, failing silently, since every item generates
        // correctly and mastery simply never comes. SkillGraph.mixedModeWeights' corrective half fixed
        // that; this is what holds it fixed.
        //
        // Measured across independent seeds rather than one, because a single seed's luck is exactly
        // what this is trying not to depend on.
        val required = minOf(5, MASTERY_WINDOW / SkillGraph.activeDegreesFor(skill).size)
        assertEquals(3, required, "if this changed, the arithmetic above is stale")

        val firstCovering =
            (0 until SEED_TRIALS).map { trial ->
                val targets = items(REACHABILITY_LIMIT, seedBase = 500_000L * (trial + 1)).map { it.targetDegree }
                (MASTERY_WINDOW..targets.size).firstOrNull { end ->
                    val counts =
                        targets.subList(end - MASTERY_WINDOW, end).groupingBy { it }.eachCount()
                    SkillGraph.activeDegreesFor(skill).all { (counts[it] ?: 0) >= required }
                }
            }

        assertTrue(
            firstCovering.none { it == null },
            "on ${firstCovering.count { it == null }} of $SEED_TRIALS seeds no covering window arrived " +
                "within $REACHABILITY_LIMIT items - M10.MIXED_MODE would be unmasterable there",
        )
        val worst = firstCovering.filterNotNull().max()
        assertTrue(
            worst <= REACHABLE_WITHIN_ITEMS,
            "the slowest seed took $worst items to produce a covering window (all: $firstCovering); " +
                "reachable, but slow enough that a learner would grind",
        )
    }

    @Test
    fun `driving coverage does not let a degree clump`() {
        // The other half of the balance rule, which the corrective weighting could plausibly have
        // broken: docs/03-CURRICULUM.md §5.4's ceiling of 1.5x the expected rate over any 20-item span
        // exists to stop sampling clumps from distorting the confusion matrix. Pushing hard toward
        // under-covered degrees is exactly the pressure that would violate it.
        val targets = items(300, seedBase = 55_000L).map { it.targetDegree }
        val n = SkillGraph.activeDegreesFor(skill).size
        val ceiling = BalancedSampler.WINDOW_SIZE.toDouble() / n * BalancedSampler.MAX_FREQUENCY_MULTIPLE

        for (end in BalancedSampler.WINDOW_SIZE..targets.size) {
            val counts =
                targets.subList(end - BalancedSampler.WINDOW_SIZE, end).groupingBy { it }.eachCount()
            val worst = counts.maxByOrNull { it.value }!!
            assertTrue(
                worst.value <= ceiling + 1,
                "${worst.key.canonicalLabel} appeared ${worst.value} times in the 20-item window ending " +
                    "at $end, against a ceiling of $ceiling",
            )
        }
    }

    private companion object {
        const val MASTERY_WINDOW = 30

        /** Independent seeds, so the result is not one seed's luck. */
        const val SEED_TRIALS = 8

        /** Long enough to answer the reachability question either way without running forever. */
        const val REACHABILITY_LIMIT = 600

        /**
         * The bar the measurement is held to. Measured range across seeds is 30-71 items — 30 being
         * the earliest a 30-item window can close at all — so this leaves real headroom while still
         * failing loudly if the corrective weighting is ever weakened or removed.
         */
        const val REACHABLE_WITHIN_ITEMS = 150
    }
}
