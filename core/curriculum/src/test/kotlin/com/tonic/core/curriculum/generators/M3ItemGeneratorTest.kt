package com.tonic.core.curriculum.generators

import com.tonic.core.model.ids.SkillId
import com.tonic.core.model.ids.SkillIds
import com.tonic.core.model.items.DifficultyAxis
import com.tonic.core.model.items.RhythmMode
import com.tonic.core.model.rhythm.Meter
import com.tonic.core.model.rhythm.MetronomeFadeLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * docs/40-PHASE-4-SPEC.md §9's Stage 4.2 acceptance: "Deterministic per `(skill, axes, seed)`."
 *
 * That requirement carries more weight in this module than anywhere before it. Rhythm is the first
 * module whose *input* is genuinely non-deterministic — real human tap times — and §4.4's whole scheme
 * for keeping the system testable is that generation stays pure while the taps are recorded as data.
 * If which pattern you were asked to tap depended on anything but the seed, a recorded session could
 * not be replayed and a scoring bug could not be reproduced offline.
 */
class M3ItemGeneratorTest {
    private val productionNodes =
        listOf(
            SkillIds.M3_BEAT_FIND,
            SkillIds.M3_BEAT_DIV,
            SkillIds.M3_SUBDIV,
            SkillIds.M3_RESTS,
            SkillIds.M3_SYNCOPATION,
        )

    private fun levels(vararg pairs: Pair<DifficultyAxis, Int>) = pairs.toMap()

    private fun generate(
        skill: SkillId,
        axes: Map<DifficultyAxis, Int> = emptyMap(),
        seed: Long = 42L,
    ) = M3ItemGenerator.generate(skill, axes, seed)

    @Test
    fun `the same skill, axes and seed give an identical item`() {
        for (skill in productionNodes) {
            for (seed in listOf(1L, 7L, 1_000_003L)) {
                val axes = levels(DifficultyAxis.PATTERN_LENGTH to 2, DifficultyAxis.RHYTHMIC_DENSITY to 2)
                assertEquals(
                    generate(skill, axes, seed),
                    generate(skill, axes, seed),
                    "$skill at seed $seed is not reproducible",
                )
            }
        }
    }

    @Test
    fun `different seeds give different items, without degenerating`() {
        // The other half of determinism, and the half that a constant generator would also pass if only
        // the first were asserted. docs/10-TESTING.md §3: "Different seeds -> different output, with no
        // accidental degeneracy."
        val axes = levels(DifficultyAxis.PATTERN_LENGTH to 2, DifficultyAxis.RHYTHMIC_DENSITY to 2)
        val patterns = (1L..60L).map { generate(SkillIds.M3_SUBDIV, axes, it).pattern.onsetTicks }
        assertTrue(patterns.toSet().size > 10, "only ${patterns.toSet().size} distinct patterns in 60 seeds")
    }

    @Test
    fun `axis levels are honored, not merely accepted`() {
        val short = generate(SkillIds.M3_BEAT_FIND, levels(DifficultyAxis.PATTERN_LENGTH to 0))
        val long = generate(SkillIds.M3_BEAT_FIND, levels(DifficultyAxis.PATTERN_LENGTH to 4))
        assertEquals(1, short.pattern.bars)
        assertEquals(4, long.pattern.bars)
    }

    @Test
    fun `an unpracticed node reads every axis as zero`() {
        // What a node that has never been practiced actually has. If a missing axis threw, the first
        // item a learner ever saw would crash; if it defaulted high, their first item would be at a
        // difficulty nothing had placed them at.
        val item = generate(SkillIds.M3_BEAT_FIND, emptyMap())
        assertEquals(1, item.pattern.bars)
        assertEquals(MetronomeFadeLevel.L0, MetronomeFadeLevel.fromLevel(0))
        assertEquals(Meter.FOUR_FOUR, item.meter)
    }

    @Test
    fun `tempo offers both sides of the comfortable centre`() {
        // §3.5: the axis measures distance from ~100 BPM in *both* directions. A generator that always
        // took the fast tempo would silently delete the slow half - the half that trains internal
        // timekeeping rather than reactive entrainment.
        val tempi =
            (1L..40L).map { generate(SkillIds.M3_BEAT_FIND, levels(DifficultyAxis.TEMPO_DEVIATION to 3), it).tempoBpm }
        assertTrue(tempi.any { it < 100 }, "no slow tempo in 40 seeds: ${tempi.toSet()}")
        assertTrue(tempi.any { it > 100 }, "no fast tempo in 40 seeds: ${tempi.toSet()}")
    }

    @Test
    fun `level zero tempo sits at the centre in both directions`() {
        val tempi =
            (1L..20L).map { generate(SkillIds.M3_BEAT_FIND, levels(DifficultyAxis.TEMPO_DEVIATION to 0), it).tempoBpm }
        assertEquals(setOf(100), tempi.toSet())
    }

    @Test
    fun `beat-finding patterns are plain beats, every one of them`() {
        val item = generate(SkillIds.M3_BEAT_FIND, levels(DifficultyAxis.PATTERN_LENGTH to 1))
        assertTrue(item.pattern.isOnBeatsOnly, "BEAT_FIND must not subdivide: ${item.pattern.onsetTicks}")
        assertEquals(item.pattern.bars * item.meter.beatsPerBar, item.pattern.onsetCount)
    }

    @Test
    fun `density level zero subdivides nothing, and higher levels subdivide more`() {
        fun offBeatCount(level: Int) =
            (1L..30L).sumOf { seed ->
                generate(
                    SkillIds.M3_SUBDIV,
                    levels(
                        DifficultyAxis.PATTERN_LENGTH to 2,
                        DifficultyAxis.RHYTHMIC_DENSITY to level,
                    ),
                    seed,
                ).pattern.onsetTicks
                    .count { it % Meter.TICKS_PER_BEAT != 0 }
            }
        assertEquals(0, offBeatCount(0), "density 0 must be plain beats")
        assertTrue(offBeatCount(1) < offBeatCount(3), "density must increase with the level")
    }

    @Test
    fun `every pattern starts on the downbeat`() {
        // Without it there is nothing anchoring the pattern to the bar, and a learner tapping a rhythm
        // that begins in silence is guessing where it started rather than reproducing it.
        for (skill in productionNodes) {
            for (seed in 1L..25L) {
                val item = generate(skill, levels(DifficultyAxis.RHYTHMIC_DENSITY to 2), seed)
                assertEquals(0, item.pattern.onsetTicks.first(), "$skill at seed $seed does not start on one")
            }
        }
    }

    @Test
    fun `rests remove sounds without removing the downbeat`() {
        for (seed in 1L..25L) {
            val item =
                generate(
                    SkillIds.M3_RESTS,
                    levels(
                        DifficultyAxis.PATTERN_LENGTH to 2,
                        DifficultyAxis.RHYTHMIC_DENSITY to 2,
                    ),
                    seed,
                )
            assertTrue(0 in item.pattern.onsetTicks, "the downbeat must survive at seed $seed")
        }
    }

    @Test
    fun `syncopation leaves a beat empty and sounds before it`() {
        // What makes it syncopation rather than decoration: the sound arrives early and the beat itself
        // is silent, so the learner must keep the pulse the pattern is contradicting.
        var found = 0
        for (seed in 1L..30L) {
            val item = generate(SkillIds.M3_SYNCOPATION, levels(DifficultyAxis.PATTERN_LENGTH to 1), seed)
            val ticks = item.pattern.onsetTicks
            val beats = (0 until item.pattern.bars * item.meter.beatsPerBar).map { it * Meter.TICKS_PER_BEAT }
            val silentBeat = beats.firstOrNull { it !in ticks }
            if (silentBeat != null) {
                val half = Meter.TICKS_PER_BEAT / item.meter.division
                assertTrue(silentBeat - half in ticks, "beat $silentBeat is silent but nothing sounds before it")
                found++
            }
        }
        assertTrue(found > 0, "no syncopated pattern was produced in 30 seeds")
    }

    @Test
    fun `the metronome plan matches the fade level the axes asked for`() {
        for (level in 0..MetronomeFadeLevel.MAX.level) {
            val item = generate(SkillIds.M3_BEAT_FIND, levels(DifficultyAxis.METRONOME_FADE to level), seed = 5L)
            val fade = MetronomeFadeLevel.fromLevel(level)
            assertEquals(
                fade.soundsUnderPattern,
                item.metronomePlan.underPattern.isNotEmpty(),
                "$fade's plan disagrees with the level asked for",
            )
        }
    }

    @Test
    fun `every production item is tapped, with nothing to choose between`() {
        for (skill in productionNodes) {
            val item = generate(skill)
            assertEquals(RhythmMode.PRODUCTION, item.mode)
            assertTrue(item.answerAlphabet.labels.isEmpty(), "$skill should have no labels to pick from")
            assertTrue(item.choices.isEmpty())
        }
    }

    @Test
    fun `recognition nodes fail loudly rather than half-existing`() {
        // Stage 4.4's, and M3.DOWNBEAT in particular asks which beat is "one" - a question whose answers
        // are beat positions, not rhythms. Half-answering it here would leave a shape 4.4 has to undo.
        for (skill in listOf(SkillIds.M3_DOWNBEAT, SkillIds.M3_BEAT_DIV_RECOG, SkillIds.M3_SUBDIV_RECOG)) {
            assertFailsWith<IllegalArgumentException>("$skill should not generate yet") { generate(skill) }
        }
    }

    @Test
    fun `a node outside M3 is refused`() {
        assertFailsWith<IllegalArgumentException> { generate(SkillIds.M2_DEG_SET_1) }
    }

    @Test
    fun `onset times follow the tempo`() {
        val slow = generate(SkillIds.M3_BEAT_FIND, levels(DifficultyAxis.TEMPO_DEVIATION to 0), seed = 3L)
        // 100 BPM is 600 ms a beat; the second onset of a plain-beat pattern is exactly one beat in.
        assertEquals(600.0, slow.onsetTimesMs[1], 0.001)
    }
}
