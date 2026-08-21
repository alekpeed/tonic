package com.tonic.core.engine.simulation

import com.tonic.core.curriculum.graph.SkillGraph
import com.tonic.core.model.ids.SkillIds
import com.tonic.core.model.items.AnswerAlphabet
import com.tonic.core.model.items.CadenceFadeLevel
import com.tonic.core.model.items.DifficultyAxis
import com.tonic.core.model.music.Mode
import com.tonic.core.model.state.MasteryCriterion
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * docs/20-PHASE-2-SPEC.md §7's seven required simulations, in order, one test each.
 *
 * The Phase 1 pattern these follow (docs/10-TESTING.md §5) is worth restating, because it is what
 * makes them worth having: a simulated learner with a *known* property is run through the real
 * generator, the real scheduler and the real mastery evaluator, and the engine is asked whether it
 * reaches the verdict that learner deserves. Nothing here asserts that code executes — it asserts that
 * the pedagogy the code implements discriminates between learners it should discriminate between.
 *
 * Two of the seven are the ones that would actually embarrass the app if they failed. Simulation 4
 * asks whether `M10.MIXED_MODE` tests anything `M2` does not; if a major-only learner sailed through
 * it, the node the spec calls "arguably the most valuable in Phase 2" would be decoration. Simulation
 * 7 is the response-bias check: a learner who presses one button forever must never be certified.
 */
class Phase2LearnerSimulationTest {
    // --- 1. Mode-identification learner: competent, masters M9. ---

    @Test
    fun `simulation 1 - a competent mode-identification learner masters M9`() {
        for (skill in SkillIds.M9_NODES_IN_ORDER) {
            val result =
                SimulationHarness.runModeId(
                    skill,
                    itemCount = 200,
                    responder = BinaryResponder.accurate(COMPETENT),
                    seedBase = 9_100L,
                )
            assertTrue(
                result.everMastered,
                "${skill.raw} was never mastered by a learner answering ${COMPETENT * 100}% correctly; " +
                    "final accuracy ${result.accuracyOverLast(30)}",
            )
        }
    }

    // --- 2. Mode-deaf learner: at chance on M9.MODE_ID_TRIAD, correctly never certified. ---

    @Test
    fun `simulation 2 - a mode-deaf learner is never certified on M9`() {
        val result =
            SimulationHarness.runModeId(
                SkillIds.M9_MODE_ID_TRIAD,
                itemCount = 400,
                responder = BinaryResponder.accurate(CHANCE_BINARY),
                seedBase = 9_200L,
            )
        assertTrue(
            !result.everMastered,
            "a learner at chance on a two-choice task reached mastery at some point in 400 items",
        )
    }

    @Test
    fun `simulation 2b - and neither is one who always answers major`() {
        // The bias form of the same failure. Its accuracy is whatever the generator's major/minor
        // split happens to be, which is why d-prime rather than accuracy is what has to catch it.
        val result =
            SimulationHarness.runModeId(
                SkillIds.M9_MODE_ID_TRIAD,
                itemCount = 400,
                responder = BinaryResponder.alwaysAnswers(AnswerAlphabet.MajorMinor.MAJOR),
                seedBase = 9_250L,
            )
        assertTrue(!result.everMastered, "an always-major responder was certified on a mode-ID node")
    }

    // --- 3. Minor learner: masters M10 including the independence check. ---

    @Test
    fun `simulation 3 - a minor learner masters M10's nodes`() {
        for (skill in listOf(SkillIds.M10_MIN_SET_1, SkillIds.M10_MIN_NATURAL, SkillIds.M10_MIN_MELODIC)) {
            val result =
                SimulationHarness.run(
                    skill,
                    itemCount = 600,
                    responder = SimulatedResponder(correctProbability = { _, _, _ -> COMPETENT }),
                    seedBase = 10_300L,
                )
            assertTrue(
                result.masteryTimeline.any { it },
                "${skill.raw} was never mastered by a competent minor learner",
            )
        }
    }

    @Test
    fun `simulation 3b - a minor learner who is lost without the cadence fails minor's independence check`() {
        // The minor counterpart of Phase 1's most important simulation, and the reason Stage 2.4 made
        // the independence check per-chain: before that, minor could be mastered end to end without
        // ever being asked to hold a key unaided.
        val skill = SkillIds.M10_MIN_MELODIC
        val degreeCount = SkillGraph.activeDegreesFor(skill).size
        val result =
            SimulationHarness.run(
                skill,
                itemCount = 600,
                responder =
                    SimulatedResponder(correctProbability = { axes, _, _ ->
                        val fade = axes[DifficultyAxis.CADENCE_FADE] ?: 0
                        if (fade < CadenceFadeLevel.L6.level) COMPETENT else 1.0 / degreeCount
                    }),
                seedBase = 10_350L,
            )

        val atCheckLevel =
            result.allAttempts.filter { (it.axisLevels[DifficultyAxis.CADENCE_FADE] ?: 0) >= CadenceFadeLevel.L6.level }
        assertTrue(atCheckLevel.isNotEmpty(), "the run never reached the fade level the check probes at")
        val accuracyUnaided = atCheckLevel.count { it.correct }.toDouble() / atCheckLevel.size
        assertTrue(
            accuracyUnaided < 0.5,
            "a cadence-dependent minor learner scored $accuracyUnaided unaided; the simulation is not " +
                "producing the learner it claims to",
        )
        assertTrue(
            SkillGraph.triggersIndependenceCheck(skill),
            "and the minor chain must actually run a check, or that accuracy is never consulted",
        )
    }

    // --- 4. Major-only learner: masters M2, at chance on M10.MIXED_MODE. ---

    @Test
    fun `simulation 4 - a major-only learner masters M2 and is refused MIXED_MODE`() {
        // The load-bearing one. If a learner fluent in major and helpless in minor could be certified
        // on MIXED_MODE, the node would be testing nothing that M2 does not already test.
        val majorOnly =
            SimulatedResponder(correctProbability = { _, _, mode ->
                if (mode == Mode.MAJOR) COMPETENT else CHANCE_TEN_DEGREES
            })

        val onM2 =
            SimulationHarness.run(
                SkillIds.M2_FULL_DIATONIC,
                itemCount = 600,
                responder = majorOnly,
                seedBase = 10_400L,
            )
        assertTrue(
            onM2.masteryTimeline.any { it },
            "the major-only learner failed even M2, so this simulation proves nothing about MIXED_MODE",
        )

        val onMixed =
            SimulationHarness.run(
                SkillIds.M10_MIXED_MODE,
                itemCount = 800,
                responder = majorOnly,
                seedBase = 10_400L,
            )
        assertTrue(
            onMixed.masteryTimeline.none { it },
            "a learner at chance in minor was certified on MIXED_MODE - the node tests nothing M2 does " +
                "not, and docs/20-PHASE-2-SPEC.md §3's claim for it is false",
        )
    }

    @Test
    fun `simulation 4b - a learner fluent in both modes does master MIXED_MODE`() {
        // The other side of 4, and not a formality: a node nobody can pass is as broken as one anybody
        // can. This is also the end-to-end form of Stage 2.7's reachability measurement.
        val result =
            SimulationHarness.run(
                SkillIds.M10_MIXED_MODE,
                itemCount = 800,
                responder = SimulatedResponder(correctProbability = { _, _, _ -> COMPETENT }),
                seedBase = 10_450L,
            )
        assertTrue(
            result.masteryTimeline.any { it },
            "a learner fluent in both modes never mastered MIXED_MODE in 800 items - the node is " +
                "unreachable, which is a silent failure with no symptom",
        )
    }

    // --- 5. Chromatic learner: masters M11; the per-degree criterion blocks chance on the new degree. ---

    @Test
    fun `simulation 5 - a chromatic learner masters M11`() {
        for (skill in listOf(SkillIds.M11_CHROM_SHARP4, SkillIds.M11_CHROM_FULL)) {
            val result =
                SimulationHarness.run(
                    skill,
                    itemCount = 800,
                    responder = SimulatedResponder(correctProbability = { _, _, _ -> COMPETENT }),
                    seedBase = 11_500L,
                )
            assertTrue(
                result.masteryTimeline.any { it },
                "${skill.raw} was never mastered by a competent chromatic learner",
            )
        }
    }

    @Test
    fun `simulation 5b - a learner at chance on the newly added degree specifically is blocked`() {
        // §7's wording, exactly: "the per-degree criterion verified to block a learner who is at chance
        // on the newly added degree specifically." This learner is fluent on the eight diatonic notes
        // and guessing on ♯4 alone - the case a whole-window accuracy bar cannot see, because eight
        // confident answers carry the average.
        val skill = SkillIds.M11_CHROM_SHARP4
        val focus = SkillGraph.focusDegreeFor(skill)!!
        val degreeCount = SkillGraph.activeDegreesFor(skill).size

        val result =
            SimulationHarness.run(
                skill,
                itemCount = 800,
                responder =
                    SimulatedResponder(correctProbability = { _, target, _ ->
                        if (target == focus) 1.0 / degreeCount else COMPETENT
                    }),
                seedBase = 11_550L,
            )

        assertTrue(
            result.masteryTimeline.none { it },
            "a learner at chance on ${focus.canonicalLabel} was certified on the node that exists to " +
                "teach ${focus.canonicalLabel}",
        )

        // And the criterion doing the blocking is the one the spec added for it, not an incidental
        // side effect of the overall-accuracy bar.
        val verdict = result.finalVerdict(SkillGraph.activeDegreesFor(skill), focusDegree = focus)
        assertTrue(
            MasteryCriterion.Kind.FOCUS_DEGREE in verdict.criteria.filter { !it.met }.map { it.kind },
            "unmet criteria were ${verdict.criteria.filter { !it.met }.map { it.kind }} - the focus " +
                "criterion is not among them",
        )
    }

    // --- 6. Prediction learner: masters M12 at PREDICT_GAP >= 2. ---

    @Test
    fun `simulation 6 - a prediction learner masters M12, and does so at a real gap`() {
        val result =
            SimulationHarness.runPrediction(
                SkillIds.M12_PREDICT_DIATONIC,
                itemCount = 400,
                responder =
                    PredictionResponder { _, truth, _, rng ->
                        if (rng.nextDouble() <
                            COMPETENT
                        ) {
                            truth
                        } else {
                            other(truth, rng)
                        }
                    },
                seedBase = 12_600L,
            )
        assertTrue(result.everMastered, "a competent audiator never mastered M12 in 400 items")
        assertTrue(
            (result.finalAxisState?.levels?.get(DifficultyAxis.PREDICT_GAP) ?: 0) >= 2,
            "mastery was reached but the gap axis never climbed past " +
                "${result.finalAxisState?.levels?.get(DifficultyAxis.PREDICT_GAP)} - the learner was " +
                "certified on an echo, not on audiation",
        )
    }

    @Test
    fun `simulation 6b - an audiator who collapses once the gap gets long is not certified`() {
        // The prediction counterpart of the cadence-dependent learner: fine at a one-second gap, where
        // the cadence is still ringing, and at chance once there is real silence to hold a note across.
        val result =
            SimulationHarness.runPrediction(
                SkillIds.M12_PREDICT_DIATONIC,
                itemCount = 600,
                responder =
                    PredictionResponder { axes, truth, _, rng ->
                        val gap = axes[DifficultyAxis.PREDICT_GAP] ?: 0
                        val p = if (gap < 2) COMPETENT else CHANCE_THREE_WAY
                        if (rng.nextDouble() < p) truth else other(truth, rng)
                    },
                seedBase = 12_650L,
            )
        assertTrue(
            !result.everMastered,
            "a learner who cannot hold a note across a real silence was certified on the audiation module",
        )
    }

    // --- 7. Biased prediction responder: always MATCHED. Never certified. ---

    @Test
    fun `simulation 7 - an always-MATCHED responder is never certified on M12`() {
        val result =
            SimulationHarness.runPrediction(
                SkillIds.M12_PREDICT_DIATONIC,
                itemCount = 600,
                responder = PredictionResponder { _, _, _, _ -> AnswerAlphabet.MatchDirection.MATCHED },
                seedBase = 12_700L,
            )
        assertTrue(
            !result.everMastered,
            "the always-MATCHED responder reached mastery - the d-prime guard is not working through " +
                "the real generator and scheduler",
        )
        // It is not merely unlucky: roughly half its answers are right by construction, which is
        // exactly why raw accuracy cannot be what refuses it.
        val accuracy = result.accuracyOverLast(200)
        assertTrue(
            accuracy > 0.3,
            "the responder scored $accuracy, far below the ~50% its strategy should earn - the " +
                "simulation is not producing the learner it claims to, so its refusal proves nothing",
        )
    }

    @Test
    fun `simulation 7b - and neither is an always-MATCHED responder at the introductory node`() {
        // Where it would be easiest to slip through: M12.PREDICT_TRIAD forgives a wrong *direction*,
        // so a bias toward MATCHED is the one strategy that gets no help from that forgiveness - but
        // it is worth pinning, since the forgiveness is a scoring rule and scoring rules drift.
        val result =
            SimulationHarness.runPrediction(
                SkillIds.M12_PREDICT_TRIAD,
                itemCount = 600,
                responder = PredictionResponder { _, _, _, _ -> AnswerAlphabet.MatchDirection.MATCHED },
                seedBase = 12_750L,
            )
        assertTrue(!result.everMastered, "an always-MATCHED responder was certified on M12.PREDICT_TRIAD")
    }

    private fun other(
        truth: String,
        rng: kotlin.random.Random,
    ): String =
        AnswerAlphabet.MatchDirection.labels
            .filterNot { it == truth }
            .random(rng)

    private companion object {
        /** Comfortably above every mastery accuracy bar, and short of perfect, like a real learner. */
        const val COMPETENT = 0.95

        const val CHANCE_BINARY = 0.5
        const val CHANCE_THREE_WAY = 1.0 / 3.0

        /** `M10.MIXED_MODE` shows ten buttons, so guessing scores one in ten. */
        const val CHANCE_TEN_DEGREES = 0.1
    }
}
