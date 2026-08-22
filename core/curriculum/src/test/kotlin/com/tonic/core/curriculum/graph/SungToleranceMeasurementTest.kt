package com.tonic.core.curriculum.graph

import com.tonic.core.model.ids.SkillId
import com.tonic.core.model.ids.SkillIds
import com.tonic.core.model.music.DegreeResolver
import com.tonic.core.model.music.Mode
import com.tonic.core.model.music.PitchClass
import com.tonic.core.model.music.ScaleDegree
import com.tonic.core.model.music.SungAnswer
import com.tonic.core.model.music.Tuning
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * How far out of tune a learner can sing before each node's alphabet stops reading them correctly —
 * docs/30-PHASE-3-SPEC.md Stage 3.5, "chromatic tolerance bands verified not to produce systematic
 * misreads," and §9's open question 4.
 *
 * **This is a measurement and its output is the deliverable.** docs/10-TESTING.md §5 asks for
 * measurement runs to be "treated as a report, not just a pass/fail," which is why the numbers print.
 * The assertions are there so a change to an alphabet, the ambiguity margin, or the resolver cannot
 * move these bands without someone being told.
 *
 * **The finding, stated up front, because it is not what the spec expected.** §5.3 warns that
 * "tolerance bands narrow considerably with 12 degrees" and §9 asks whether singing should therefore
 * be limited to diatonic contexts. Measured, the difference is **one cent**: diatonic reads correctly
 * to 45 cents of uniform detuning and chromatic to 44, and the band where a *misread* becomes possible
 * opens at 55 cents for both. The reason is that the major scale already contains semitone steps at
 * `3`–`4` and `7`–`1`, so the binding geometry was set in Phase 1 the moment `4` entered the alphabet
 * at `M2.DEG_SET_4` — long before any chromatic degree existed. Chromatic resolution is not less
 * accurate per degree; what it changes is how many degrees are exposed at once.
 *
 * **Three bands, and the middle one is the designed behavior.** Under about 45 cents every degree
 * resolves correctly. Between there and 55 the resolver returns `Unclear` and the learner is asked
 * again — never scored wrong, which is exactly what §5.2 built the ambiguity margin for. Past 55 cents
 * a confident, perfectly systematic misread appears: every degree resolves to the one below it. That
 * band is the real risk this stage found, and it is identical on diatonic and chromatic alphabets.
 */
class SungToleranceMeasurementTest {
    /**
     * What the resolver did with one degree sung out of tune.
     *
     * Ordered worst-last on purpose: [onsetOf] compares by [Enum.ordinal] to ask "did it reach this
     * severity or beyond", which only reads correctly if the order is the severity order.
     */
    private enum class Reading {
        /** Resolved to the degree that was actually sung. */
        CORRECT,

        /** Resolved to nothing — the learner is asked again, and §5.2 forbids scoring this. */
        UNCLEAR,

        /** Resolved confidently to a different degree. The only outcome here that corrupts data. */
        MISREAD,
    }

    private fun readingFor(
        degree: ScaleDegree,
        offsetCents: Double,
        alphabet: Set<ScaleDegree>,
        mode: Mode,
    ): Reading {
        // One fixed key and octave: the resolver folds into a single octave, so the tonic it measures
        // against is the whole question and the register is not part of it.
        val midi = TONIC_MIDI + degree.semitoneOffset(mode)
        val sungHz = Tuning.offsetByCents(Tuning.midiToHz(midi), offsetCents)
        val answer =
            DegreeResolver.resolve(
                frequencyHz = sungHz,
                tonic = PitchClass(TONIC_MIDI % SEMITONES_PER_OCTAVE),
                mode = mode,
                alphabet = alphabet,
            )
        return when {
            answer is SungAnswer.Unclear -> Reading.UNCLEAR
            answer is SungAnswer.Resolved && answer.degree == degree -> Reading.CORRECT
            else -> Reading.MISREAD
        }
    }

    /** The worst reading across every degree of [skillId] at a uniform [offsetCents], flat and sharp alike. */
    private fun worstReading(
        skillId: SkillId,
        offsetCents: Int,
    ): Reading {
        val mode = SkillGraph.modeFor(skillId)
        val alphabet = SkillGraph.activeDegreesFor(skillId)
        val readings =
            listOf(offsetCents, -offsetCents).flatMap { signed ->
                alphabet.map { readingFor(it, signed.toDouble(), alphabet, mode) }
            }
        return when {
            readings.any { it == Reading.MISREAD } -> Reading.MISREAD
            readings.any { it == Reading.UNCLEAR } -> Reading.UNCLEAR
            else -> Reading.CORRECT
        }
    }

    /**
     * The first detuning at which any degree of [skillId] reaches [reading] or worse, or
     * [BEYOND_SWEEP] if it never does within the sweep.
     *
     * The sentinel is not defensive padding — it is the honest answer for the sparse early nodes.
     * `M2.DEG_SET_1` answers from `{1, 3, 5}`, whose nearest pair is a whole tone apart, so no amount
     * of detuning under a semitone can make one of them read as another: it has no unclear band and no
     * misread band at all. Returning a number there would invent a boundary that does not exist.
     */
    private fun onsetOf(
        skillId: SkillId,
        reading: Reading,
    ): Int =
        (0..MAX_SWEEP_CENTS).firstOrNull { worstReading(skillId, it).ordinal >= reading.ordinal }
            ?: BEYOND_SWEEP

    /**
     * The report: where each singable node's three bands fall.
     *
     * Reading it — `correct to` is the last detuning every degree still resolves right; `unclear from`
     * is where the learner starts being asked to sing again; `misread from` is where the app starts
     * confidently recording a degree they did not sing. Only the third column is a correctness
     * problem.
     */
    @Test
    fun `measure and report the sung tolerance of every singable node`() {
        val nodes = SkillIds.M2_NODES_IN_ORDER + SkillIds.M10_NODES_IN_ORDER + SkillIds.M11_NODES_IN_ORDER

        println("[measure] Sung tolerance by node, in cents of uniform detuning")
        println("[measure]   node                                  n  correct to  unclear from  misread from")
        for (id in nodes) {
            val unclear = onsetOf(id, Reading.UNCLEAR)
            val misread = onsetOf(id, Reading.MISREAD)
            val n = SkillGraph.activeDegreesFor(id).size

            fun show(cents: Int) = if (cents == BEYOND_SWEEP) "never" else cents.toString()

            println(
                "[measure]   ${id.raw.padEnd(34)}${n.toString().padStart(3)}" +
                    "${show(unclear - 1).padStart(12)}${show(unclear).padStart(14)}" +
                    show(misread).padStart(14),
            )
        }

        // No node may misread before the ambiguity margin has had its chance. This is §5.2's guarantee
        // expressed structurally: an answer the resolver cannot be confident about must come back as
        // "unclear" and be re-prompted, never as a wrong degree that enters the staircase.
        for (id in nodes) {
            val misread = onsetOf(id, Reading.MISREAD)
            if (misread == BEYOND_SWEEP) continue
            val unclear = onsetOf(id, Reading.UNCLEAR)
            assertTrue(
                unclear < misread,
                "${id.raw} misreads at $misread cents without ever passing through an unclear band " +
                    "(which opens at $unclear) - a detuned singer would be silently marked wrong",
            )
        }
    }

    /**
     * §9's open question 4, answered: limiting singing to diatonic contexts would buy nothing.
     *
     * The premise behind the question is that twelve degrees resolve less accurately than seven. They
     * do not, in any way a learner could feel — the correct band differs by a single cent and the
     * misread band opens at the same place, because the diatonic scale's own `3`–`4` and `7`–`1` steps
     * are already semitones. Whatever is decided about chromatic singing, it cannot be decided on
     * accuracy grounds.
     */
    @Test
    fun `chromatic resolution is no less accurate than diatonic`() {
        val diatonic = SkillIds.M2_FULL_DIATONIC
        val chromatic = SkillIds.M11_CHROM_FULL

        val diatonicCorrect = onsetOf(diatonic, Reading.UNCLEAR) - 1
        val chromaticCorrect = onsetOf(chromatic, Reading.UNCLEAR) - 1
        assertTrue(
            diatonicCorrect - chromaticCorrect <= NEGLIGIBLE_CENTS,
            "diatonic tolerates $diatonicCorrect cents and chromatic only $chromaticCorrect; the gap has " +
                "grown past anything a voice could hit deliberately, and §9 question 4 needs revisiting",
        )
        assertEquals(
            onsetOf(diatonic, Reading.MISREAD),
            onsetOf(chromatic, Reading.MISREAD),
            "the misread band no longer opens at the same detuning for both, which was the whole basis " +
                "for keeping chromatic singing enabled",
        )
        println(
            "[measure] Correct to $diatonicCorrect cents diatonic vs $chromaticCorrect chromatic; " +
                "misread opens at ${onsetOf(diatonic, Reading.MISREAD)} cents for both.",
        )
    }

    /**
     * What actually happens to §8 simulation 2's singer — the uniformly ~50-cent-flat one the spec
     * calls the most important test in the phase.
     *
     * They are **never misread**, on any alphabet, which is the requirement that matters: nothing they
     * sing is recorded as a wrong degree, so nothing corrupts the staircase or the confusion matrix,
     * and they reach mastery by tapping exactly as a Phase 2 learner does. What they lose is the
     * feature — on the chromatic node every degree comes back unclear, so singing never once works for
     * them. That is a usability finding rather than a correctness one, and it is recorded here because
     * it is invisible from any pass/fail: the app behaves correctly and is useless to that person.
     */
    @Test
    fun `the uniformly flat singer of simulation 2 is never misread, only unheard`() {
        for (id in listOf(SkillIds.M2_FULL_DIATONIC, SkillIds.M10_MIXED_MODE, SkillIds.M11_CHROM_FULL)) {
            val mode = SkillGraph.modeFor(id)
            val alphabet = SkillGraph.activeDegreesFor(id)
            val readings =
                alphabet.map { readingFor(it, -UNIFORM_FLAT_SINGER_CENTS.toDouble(), alphabet, mode) }

            assertTrue(
                readings.none { it == Reading.MISREAD },
                "${id.raw} misread a ${UNIFORM_FLAT_SINGER_CENTS}-cent-flat singer, which §8 simulation 2 forbids",
            )
            println(
                "[measure] ${id.raw} at -$UNIFORM_FLAT_SINGER_CENTS cents: " +
                    "${readings.count { it == Reading.CORRECT }} of ${alphabet.size} degrees still readable.",
            )
        }
    }

    private companion object {
        const val MAX_SWEEP_CENTS = 99

        /**
         * Returned by [onsetOf] when a band never opens inside the sweep. One past the sweep, so it
         * compares correctly against any real onset without being mistaken for one.
         */
        const val BEYOND_SWEEP = MAX_SWEEP_CENTS + 1
        const val TONIC_MIDI = 60
        const val SEMITONES_PER_OCTAVE = 12

        /** §8 simulation 2's singer: "knows every answer, sings uniformly ~50 cents flat." */
        const val UNIFORM_FLAT_SINGER_CENTS = 50

        /**
         * How much diatonic may out-tolerate chromatic before the difference is worth acting on.
         *
         * Measured at one cent. Five leaves room for the ambiguity margin to be retuned without this
         * failing spuriously, while still catching any change that made chromatic genuinely worse.
         */
        const val NEGLIGIBLE_CENTS = 5
    }
}
