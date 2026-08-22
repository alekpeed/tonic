package com.tonic.core.engine.simulation

import com.tonic.core.model.ids.SkillIds
import com.tonic.core.model.items.AnswerAlphabet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * docs/30-PHASE-3-SPEC.md §8's required simulation 6 — the guessing predictor, sung.
 *
 * The spec asks for a learner who "sings random pitches on `M12`" and requires that they are never
 * certified. Under §5.4's decision that the sung prediction *supplements* the button rather than
 * replacing it, that requirement is worth restating precisely, because the obvious reading of it makes
 * a test that cannot fail: sung pitches never enter scoring at all, so a random singer who also
 * guesses the button is refused by d-prime, which Phase 2 already proves.
 *
 * What is actually at risk — and what these three runs pin — is the inverse. If sung evidence ever
 * leaked into the adaptive path, the learner it would wrongly certify is not the one singing noise but
 * the one **singing perfectly and guessing the answer**: someone who can produce the named degree on
 * demand and cannot yet hear whether the note that follows matches it. That learner has not mastered
 * `M12`, whose whole question is the comparison, and an app that certified them would have quietly
 * swapped audiation for vocal accuracy — the exact failure §3 is written to prevent.
 *
 * So: noise plus guessing is refused, perfect singing plus guessing is refused, and perfect singing
 * changes a competent learner's timeline by nothing at all.
 */
class SungPredictionSimulationTest {
    /**
     * Simulation 6 as written: random pitches, random answers, never certified.
     *
     * Kept even though d-prime does the work, because it is the spec's own case and because its
     * passing is only uninteresting for as long as the sung path stays out of scoring. If that ever
     * changes, this is one of the tests that notices.
     */
    @Test
    fun `simulation 6 - a guessing predictor who sings random pitches is never certified`() {
        val result =
            SimulationHarness.runPrediction(
                SkillIds.M12_PREDICT_DIATONIC,
                itemCount = 600,
                responder = PredictionResponder { _, _, _, rng -> AnswerAlphabet.MatchDirection.labels.random(rng) },
                seedBase = 30_400L,
                // Wandering by up to a tritone either way: a voice with no idea which degree was named.
                sungCentsFor = { i -> RANDOM_CENTS[i % RANDOM_CENTS.size] },
            )

        assertTrue(
            !result.everMastered,
            "a learner guessing the judgment was certified on M12",
        )
    }

    /**
     * The one that would actually catch a leak: a flawless voice, an ear that is guessing.
     *
     * Every attempt carries `sungCents = 0` — the learner produced exactly the degree they were asked
     * for, on every single item, which is the strongest sung evidence the app can record. The button
     * answers are a coin flip across three labels. `M12` asks whether the sounded note matched, and
     * this learner cannot tell; being able to sing the note is a different skill, and §5.4 refuses to
     * let it stand in. If mastery is ever reachable this way, the node has stopped meaning one thing.
     */
    @Test
    fun `a perfect audiator who cannot judge the match is still never certified`() {
        val result =
            SimulationHarness.runPrediction(
                SkillIds.M12_PREDICT_DIATONIC,
                itemCount = 600,
                responder = PredictionResponder { _, _, _, rng -> AnswerAlphabet.MatchDirection.labels.random(rng) },
                seedBase = 30_450L,
                sungCentsFor = { 0 },
            )

        assertTrue(
            !result.everMastered,
            "singing the named degree perfectly certified a learner who could not hear the mismatch",
        )
    }

    /**
     * And the supplement is inert in the other direction too: it does not help a learner who deserves
     * to pass, and it does not hold one back.
     *
     * The same responder, the same seed, run once silent and once with perfect audiation stamped on
     * every attempt. The mastery timelines must be identical item for item — not merely both ending in
     * mastery, which would tolerate the sung column shifting *when* certification arrived. §2 requires
     * a tap-only learner to reach the same mastery by the same route, and "the same route" is what an
     * equal timeline means.
     */
    @Test
    fun `sung evidence changes a competent learner's timeline by nothing`() {
        fun run(sungCentsFor: (Int) -> Int?) =
            SimulationHarness.runPrediction(
                SkillIds.M12_PREDICT_DIATONIC,
                itemCount = 400,
                responder =
                    PredictionResponder { _, truth, _, rng ->
                        if (rng.nextDouble() < COMPETENT) truth else AnswerAlphabet.MatchDirection.labels.random(rng)
                    },
                seedBase = 30_500L,
                sungCentsFor = sungCentsFor,
            )

        val tapOnly = run { null }
        val singing = run { 0 }

        assertTrue(tapOnly.everMastered, "the control run never mastered, so this test proves nothing")
        assertEquals(
            tapOnly.masteryTimeline,
            singing.masteryTimeline,
            "recording what the learner sang changed when they were certified",
        )
        assertEquals(
            tapOnly.attempts.map { it.correct },
            singing.attempts.map { it.correct },
            "recording what the learner sang changed how an answer was scored",
        )
    }

    private companion object {
        const val COMPETENT = 0.95

        /**
         * A cycle rather than a fresh draw per item, so the run is deterministic (CLAUDE.md §5).
         *
         * Values span most of an octave in both directions and deliberately include 0: a random singer
         * lands on the right note occasionally, and a test whose "noise" never coincides with the
         * answer would be describing a suspiciously tidy failure mode.
         */
        val RANDOM_CENTS = listOf(-580, -313, -97, 0, 44, 208, 466, 592, -205, 119)
    }
}
