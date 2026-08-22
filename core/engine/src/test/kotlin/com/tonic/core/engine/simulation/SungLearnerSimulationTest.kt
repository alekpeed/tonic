package com.tonic.core.engine.simulation

import com.tonic.core.curriculum.generators.GenerationHistory
import com.tonic.core.curriculum.generators.M2ItemGenerator
import com.tonic.core.curriculum.graph.SkillGraph
import com.tonic.core.engine.mastery.MasteryEvaluator
import com.tonic.core.engine.replay.SkillStateReducer
import com.tonic.core.engine.scheduling.AxisScheduler
import com.tonic.core.engine.scheduling.AxisSchedulerState
import com.tonic.core.model.attempts.Attempt
import com.tonic.core.model.attempts.InputMethod
import com.tonic.core.model.ids.SkillId
import com.tonic.core.model.ids.SkillIds
import com.tonic.core.model.items.DifficultyAxis
import com.tonic.core.model.music.DegreeResolver
import com.tonic.core.model.music.Mode
import com.tonic.core.model.music.ScaleDegree
import com.tonic.core.model.music.SungAnswer
import com.tonic.core.model.music.Tuning
import java.time.Instant
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * docs/30-PHASE-3-SPEC.md §8's required simulations 2, 4 and 5, run against the *real* resolver.
 *
 * The distinction matters more here than anywhere else in the phase. A simulation that hands the
 * engine a boolean "correct" is only testing the engine; §3's risk is not in the engine, it is in the
 * step that turns a human voice into a degree. So these simulate a *voice* — a frequency, in hertz,
 * with a deliberate error in cents — and put it through [DegreeResolver] exactly as a real sung answer
 * goes through it. Whether the learner is scored correct is an output of that pipeline, never an input
 * to it.
 *
 * Simulation 2 is the one §8 calls "the single most important test in the phase; if it fails, the app
 * is testing singing, not hearing."
 */
class SungLearnerSimulationTest {
    /**
     * One simulated answer: the learner knows the degree and their voice lands [centsOff] away from it.
     *
     * Deliberately built from the degree the item actually asked for, in the item's own key and mode,
     * so "knows the answer" means exactly that and nothing about the item leaks past the voice model.
     */
    private fun sungFrequency(
        degree: ScaleDegree,
        tonicPitchClass: Int,
        mode: Mode,
        centsOff: Double,
        octave: Int,
    ): Double {
        val semitonesAboveTonic = degree.semitoneOffset(mode)
        // Absolute semitone position relative to A4, then a whole number of octaves away - the octave
        // is free by §3 mitigation 3, and varying it is part of what these check.
        val semitonesFromA4 = (tonicPitchClass + semitonesAboveTonic) - A4_SEMITONE_POSITION + 12 * octave
        return Tuning.DEFAULT_A4_HZ * 2.0.pow((semitonesFromA4 + centsOff / 100.0) / 12.0)
    }

    /**
     * Runs [itemCount] items of [skill] with a simulated singer, resolving every answer through
     * [DegreeResolver]. Mirrors [SimulationHarness.run]'s wiring — same generator, same axis
     * scheduler, same mastery evaluator — and differs only in where the response label comes from.
     *
     * An [SungAnswer.Unclear] result is *not* recorded as an attempt at all, which is §5.2's rule
     * rather than a convenience: "a mumble, a cough, silence, or background noise produces a retry
     * prompt, never a recorded incorrect attempt."
     */
    private fun runSung(
        skill: SkillId,
        itemCount: Int,
        centsOff: (aimed: ScaleDegree, alphabet: List<ScaleDegree>, mode: Mode) -> Double,
        octaveFor: (Int) -> Int = { 0 },
        knowsAnswer: (Int) -> Boolean = { true },
        seedBase: Long = 1L,
    ): SungRun {
        val activeDegrees = SkillGraph.activeDegreesFor(skill).sortedBy { it.degree }
        var axisState = AxisSchedulerState()
        var genHistory = GenerationHistory()
        val attempts = mutableListOf<Attempt>()
        var unclearCount = 0
        val rng = Random(seedBase)

        repeat(itemCount) { i ->
            val axisLevels = axisState.levels
            val generated =
                M2ItemGenerator.generate(skill, axisLevels, seed = seedBase + i * 7919L, history = genHistory)
            genHistory = generated.updatedHistory
            val item = generated.item

            // What the learner *aimed* at. A learner who does not know the answer aims at a different
            // degree entirely - that is a hearing error, and it must be scored as one.
            val aimed =
                if (knowsAnswer(i)) {
                    item.targetDegree
                } else {
                    activeDegrees.filter { it != item.targetDegree }.random(rng)
                }

            val frequency =
                sungFrequency(
                    aimed,
                    item.key.value,
                    item.mode,
                    centsOff(aimed, activeDegrees, item.mode),
                    octaveFor(i),
                )
            val resolved =
                DegreeResolver.resolve(frequency, item.key, item.mode, activeDegrees)

            when (resolved) {
                is SungAnswer.Unclear -> {
                    // Re-prompted, not scored. Nothing reaches the log, the staircase or mastery.
                    unclearCount++
                }

                is SungAnswer.Resolved -> {
                    val responseLabel = resolved.degree.canonicalLabel
                    val correct = responseLabel == item.targetDegree.canonicalLabel
                    attempts +=
                        Attempt(
                            skillId = skill,
                            sessionId = 1L,
                            itemSeed = item.seed,
                            axisLevels = axisLevels,
                            targetLabel = item.targetDegree.canonicalLabel,
                            responseLabel = responseLabel,
                            correct = correct,
                            latencyMs = 600,
                            replayCount = 0,
                            keyPitchClass = item.key.value,
                            targetMidi = item.targetMidi,
                            timbreId = item.timbre.name,
                            cadenceFadeLevel = axisLevels[DifficultyAxis.CADENCE_FADE] ?: 0,
                            timestamp = Instant.EPOCH.plusSeconds(attempts.size.toLong()),
                            inputMethod = InputMethod.SUNG,
                            sungCents = resolved.centsFromDegree.roundToInt(),
                        )
                    axisState = AxisScheduler.update(axisState, correct)
                }
            }
        }
        return SungRun(attempts, unclearCount, axisState)
    }

    /** A singer who always aims correctly and always lands [cents] flat of where they aimed. */
    private fun flatBy(cents: Double): (ScaleDegree, List<ScaleDegree>, Mode) -> Double = { _, _, _ -> -cents }

    /**
     * A voice landing exactly between the aimed degree and its nearest neighbour in the alphabet —
     * the resolver's own refusal case, computed per item rather than assumed.
     *
     * It has to be computed. `M2.DEG_SET_1` answers degrees 1, 3 and 5, which are 400 and 300 cents
     * apart, so the midpoints are 200 and 150 cents out — nowhere near the 50 cents that would be the
     * midpoint if degrees were adjacent semitones. Hardcoding 50 here would produce a perfectly
     * confident resolution and a test that passed while proving nothing.
     */
    private fun halfwayToTheNearestNeighbour(
        aimed: ScaleDegree,
        alphabet: List<ScaleDegree>,
        mode: Mode,
    ): Double {
        val aimedAt = aimed.semitoneOffset(mode)
        val nearest =
            alphabet
                .filter { it != aimed }
                .minByOrNull { candidate ->
                    val raw = candidate.semitoneOffset(mode) - aimedAt
                    // Wrap the short way round the octave: degree 1's nearest neighbour above 7 is 1.
                    minOf(abs(raw.toDouble()), 12.0 - abs(raw.toDouble()))
                } ?: return 0.0
        val raw = (nearest.semitoneOffset(mode) - aimedAt).toDouble()
        val signedSemitones = if (abs(raw) <= 6.0) raw else raw - 12.0 * sign(raw)
        return signedSemitones * 100.0 / 2.0
    }

    private data class SungRun(
        val attempts: List<Attempt>,
        val unclearCount: Int,
        val axisState: AxisSchedulerState,
    )

    private fun SungRun.everMastered(skill: SkillId): Boolean {
        val active = SkillGraph.activeDegreesFor(skill).toSet()
        return attempts.indices.any { i ->
            val window = attempts.take(i + 1).takeLast(MasteryEvaluator.WINDOW_SIZE)
            window.size >= MasteryEvaluator.WINDOW_SIZE &&
                MasteryEvaluator
                    .evaluate(
                        window,
                        active,
                        window.last().axisLevels,
                        focusDegree = SkillGraph.focusDegreeFor(skill),
                    ).isMastered
        }
    }

    /**
     * **Simulation 2 — the consistently flat singer.** §8: "knows every answer, sings uniformly ~50
     * cents flat. **Must still master.** This is the single most important test in the phase; if it
     * fails, the app is testing singing, not hearing."
     *
     * 50 cents flat is the worst case that is still unambiguous: adjacent degrees are 100 cents apart,
     * so this voice sits exactly at the midpoint's edge, closer to the intended degree than to its
     * neighbour by the smallest margin the resolver will still act on. Anyone flatter is genuinely
     * between two notes; anyone less flat is easier. If mastery survives here it survives everywhere
     * a real untrained singer lands.
     */
    @Test
    fun `a singer uniformly 50 cents flat masters normally`() {
        val run = runSung(SKILL, ITEM_COUNT, centsOff = flatBy(CONSISTENTLY_FLAT_CENTS))

        assertEquals(
            0,
            run.attempts.count { !it.correct },
            "every answer aimed at the right degree; being flat is a property of the voice, " +
                "and §3 mitigation 2 resolves it to the degree that was aimed at",
        )
        assertTrue(run.everMastered(SKILL), "§8 simulation 2: a flat singer must still master")
    }

    /** And the cent deviation is *recorded*, because §3 mitigation 4 shows it back as information. */
    @Test
    fun `the flat singer's deviation is recorded without being scored`() {
        val run = runSung(SKILL, ITEM_COUNT, centsOff = flatBy(CONSISTENTLY_FLAT_CENTS))

        val deviations = run.attempts.mapNotNull { it.sungCents }
        assertEquals(run.attempts.size, deviations.size, "every sung attempt carries its deviation")
        assertTrue(
            deviations.all { it <= -40 },
            "the readout must report what was actually sung - got ${deviations.distinct()}",
        )
    }

    /**
     * §3 mitigation 3, end to end: the same singer moving between octaves, as anyone comfortable in
     * one register and not another does mid-session. Octave is free, so this must be indistinguishable
     * from singing everything in one.
     */
    @Test
    fun `octave wandering changes nothing`() {
        val inOneOctave = runSung(SKILL, ITEM_COUNT, centsOff = flatBy(20.0))
        val wandering = runSung(SKILL, ITEM_COUNT, centsOff = flatBy(20.0), octaveFor = { i -> (i % 3) - 1 })

        assertEquals(
            inOneOctave.attempts.map { it.responseLabel },
            wandering.attempts.map { it.responseLabel },
            "§3 mitigation 3: singing in whatever register is comfortable is the same answer",
        )
    }

    /**
     * **Simulation 5 — the mixed-input user.** §8: "alternates tapping and singing. Both feed one
     * `SkillState` coherently; no double-counting, no split progression."
     *
     * Asserted through the reducer, which is what actually derives a node's state from its log: a
     * history where every other attempt is sung must replay to precisely the state the same history
     * replays to when all of it is tapped. Anything else is a split progression by definition.
     */
    @Test
    fun `alternating tapped and sung answers feed one skill state`() {
        val sungRun = runSung(SKILL, ITEM_COUNT, centsOff = flatBy(CONSISTENTLY_FLAT_CENTS))
        val mixed =
            sungRun.attempts.mapIndexed { i, attempt ->
                if (i % 2 == 0) {
                    attempt.copy(inputMethod = InputMethod.TAP, sungCents = null)
                } else {
                    attempt
                }
            }
        val allTapped =
            sungRun.attempts.map { it.copy(inputMethod = InputMethod.TAP, sungCents = null) }

        assertEquals(
            SkillStateReducer.replay(SKILL, allTapped),
            SkillStateReducer.replay(SKILL, mixed),
            "§2: one SkillState, whatever mix of inputs produced it",
        )
    }

    /**
     * **Simulation 4 — the tap-only user.** §8: "never grants mic permission. Must reach full mastery,
     * including every independence check, identically to a Phase 2 user."
     *
     * The strongest form of that is not "the tap-only user masters" but "the tap-only user's
     * progression is byte-identical to what it was before Phase 3 existed" — and since
     * [InputMethod.TAP] is the default on every field added, that is exactly what constructing an
     * attempt without mentioning either field produces. A regression that made sung the default, or
     * made the absent fields matter, fails here.
     */
    @Test
    fun `a tap-only history is unchanged by Phase 3 existing`() {
        val result =
            SimulationHarness.run(
                SKILL,
                ITEM_COUNT,
                SimulatedResponder(correctProbability = { _, _, _ -> 1.0 }),
                seedBase = 5L,
            )

        assertTrue(
            result.allAttempts.all { it.inputMethod == InputMethod.TAP && it.sungCents == null },
            "an attempt that never mentions input method is a tapped one with no pitch data",
        )
        assertTrue(
            result.masteryTimeline.any { it },
            "§8 simulation 4: a tap-only learner reaches mastery, exactly as before",
        )
    }

    /**
     * **Simulation 3's rule, at the resolver.** §5.2: an unreadable answer "produces a retry prompt,
     * never a recorded incorrect attempt," because a false wrong corrupts the staircase and the
     * confusion matrix. A voice landing on the exact midpoint between two candidates is the resolver's
     * own unclear case, and it must leave no trace in the log.
     */
    @Test
    fun `an ambiguous pitch is re-prompted rather than recorded wrong`() {
        val run = runSung(SKILL, ITEM_COUNT, centsOff = ::halfwayToTheNearestNeighbour)

        assertTrue(run.unclearCount > 0, "the midpoint must actually be refused, or this proves nothing")
        assertTrue(
            run.attempts.isEmpty(),
            "§5.2: unclear is never wrong - a refused answer must not reach the attempt log at all. " +
                "Asserted as empty rather than as 'none incorrect', which an empty list satisfies " +
                "vacuously and would keep satisfying if refusal started recording correct answers.",
        )
    }

    private companion object {
        val SKILL = SkillIds.M2_DEG_SET_1

        /**
         * Sized against what mastery actually needs, not a guess: the window is 30 with at least 5
         * attempts per degree and a cadence-fade minimum, and every existing masters-normally
         * simulation runs 200+ items. 80 was enough to move the staircase and not enough to certify.
         */
        const val ITEM_COUNT = 240

        /** §8 simulation 2's "~50 cents flat", taken at the hardest end of that. */
        const val CONSISTENTLY_FLAT_CENTS = 50.0

        const val A4_SEMITONE_POSITION = 9
    }
}
