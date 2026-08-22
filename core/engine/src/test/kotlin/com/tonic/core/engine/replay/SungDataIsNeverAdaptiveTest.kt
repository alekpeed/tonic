package com.tonic.core.engine.replay

import com.tonic.core.model.attempts.Attempt
import com.tonic.core.model.attempts.InputMethod
import com.tonic.core.model.ids.SkillIds
import com.tonic.core.model.items.DifficultyAxis
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Phase 3's central safety property, asserted rather than trusted.
 *
 * docs/30-PHASE-3-SPEC.md §2: "Sung and tapped attempts are **not** separate skill states. They
 * contribute to the same `SkillState` for a given node — the input method is a property of the
 * attempt, not of the skill." And §7 on `sungCents`: "**for display and analysis only**. Never read by
 * `Staircase`, `AxisScheduler`, `MasteryEvaluator`, or `ConfusionTracker`. Add a test asserting this,
 * in the same spirit as Phase 1's `replayCount` check."
 *
 * §3 is why it matters. Vocal pitch production is a motor skill with its own learning curve, affected
 * by range, congestion, time of day and whether anyone is in earshot. The moment any of that reaches
 * the staircase, the app has stopped measuring hearing and started measuring singing — and it would
 * do so invisibly, by adapting slightly differently for singers, with the corruption showing up only
 * as an odd-looking progression months later.
 *
 * The check is a whole-state equivalence rather than a spot assertion on one field.
 * [SkillStateReducer.replay] is what drives every adaptive component from the attempt log — staircase
 * steps, axis scheduling, the mastery window, FSRS review state — so replaying one attempt history
 * twice, identical but for input method and cent deviations, and demanding a byte-identical
 * [com.tonic.core.model.state.SkillState] covers all of them at once. A new component that starts
 * reading either field fails this without anyone remembering to extend the test.
 *
 * `ConfusionTracker` is absent deliberately: it takes target and response *labels*, never an
 * [Attempt], so it cannot read these fields even by mistake. That is a stronger guarantee than a test
 * and does not need one — but it does need saying, or the next person adds an `Attempt` overload.
 */
class SungDataIsNeverAdaptiveTest {
    private val skill = SkillIds.M2_DEG_SET_1

    /**
     * Cent deviations chosen to be wildly implausible on purpose. A real sung answer lands within a
     * few tens of cents; these swing between a comfortable near-miss and a full semitone off, exactly
     * the spread that would move a staircase if anything were reading it.
     */
    private fun sungCentsFor(index: Int): Int = listOf(-99, -48, -3, 0, 7, 44, 98)[index % 7]

    private fun history(
        inputMethod: InputMethod,
        withCents: Boolean,
    ): List<Attempt> =
        (0 until ATTEMPT_COUNT).map { i ->
            // A mixture of right and wrong, deliberately not a clean run: a learner who never errs
            // exercises no staircase descent, no confusion, and no failed mastery window, so an
            // all-correct history could pass this while the adaptive paths stayed untouched.
            val correct = i % 3 != 0
            Attempt(
                skillId = skill,
                sessionId = 1L,
                itemSeed = i.toLong(),
                axisLevels = mapOf(DifficultyAxis.CADENCE_FADE to 0),
                targetLabel = "5",
                responseLabel = if (correct) "5" else "3",
                correct = correct,
                latencyMs = 500,
                replayCount = 0,
                keyPitchClass = 0,
                targetMidi = 67,
                timbreId = "PURE",
                cadenceFadeLevel = 0,
                timestamp = Instant.EPOCH.plus(i.toLong(), ChronoUnit.SECONDS),
                inputMethod = inputMethod,
                sungCents = if (withCents) sungCentsFor(i) else null,
            )
        }

    @Test
    fun `a sung history and a tapped history produce the same skill state`() {
        val tapped = SkillStateReducer.replay(skill, history(InputMethod.TAP, withCents = false))
        val sung = SkillStateReducer.replay(skill, history(InputMethod.SUNG, withCents = true))

        assertEquals(
            tapped,
            sung,
            "§2: sung and tapped attempts are not separate skill states. Any difference here means " +
                "some adaptive component is branching on how the answer was given.",
        )
    }

    /**
     * The two fields separated, so a failure names which one leaked. Input method alone, with no cent
     * data at all, must change nothing — this is the case a tap-only learner and a singer who happens
     * to answer identically would produce.
     */
    @Test
    fun `input method alone changes nothing`() {
        assertEquals(
            SkillStateReducer.replay(skill, history(InputMethod.TAP, withCents = false)),
            SkillStateReducer.replay(skill, history(InputMethod.SUNG, withCents = false)),
        )
    }

    /** And cent deviations alone must change nothing — §7's "never read by" clause, on its own. */
    @Test
    fun `cent deviation alone changes nothing`() {
        assertEquals(
            SkillStateReducer.replay(skill, history(InputMethod.SUNG, withCents = false)),
            SkillStateReducer.replay(skill, history(InputMethod.SUNG, withCents = true)),
        )
    }

    /**
     * §3 mitigation 1 and 2, stated as an outcome rather than a tolerance: the consistently flat
     * singer of §8's simulation 2 answers *the same degrees* as an accurate one — being flat is a
     * property of their voice, resolved away before scoring — so their progression must be
     * indistinguishable. This is the phase's most important single property in miniature; the full
     * simulation with a real resolver comes with the sung answer path itself.
     */
    @Test
    fun `a uniformly flat singer progresses identically to an accurate one`() {
        val accurate =
            history(InputMethod.SUNG, withCents = false).map { it.copy(sungCents = 2) }
        val flat =
            history(InputMethod.SUNG, withCents = false).map { it.copy(sungCents = -50) }

        assertEquals(
            SkillStateReducer.replay(skill, accurate),
            SkillStateReducer.replay(skill, flat),
            "a singer 50 cents flat aimed at the same degree and answered it correctly",
        )
    }

    private companion object {
        /** Long enough to move the staircase, fill a mastery window and accumulate FSRS review blocks. */
        const val ATTEMPT_COUNT = 60
    }
}
