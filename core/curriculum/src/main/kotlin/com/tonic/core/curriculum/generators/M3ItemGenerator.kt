package com.tonic.core.curriculum.generators

import com.tonic.core.model.ids.SkillId
import com.tonic.core.model.ids.SkillIds
import com.tonic.core.model.items.DifficultyAxis
import com.tonic.core.model.items.Item
import com.tonic.core.model.items.RhythmMode
import com.tonic.core.model.music.TimbreId
import com.tonic.core.model.rhythm.Meter
import com.tonic.core.model.rhythm.MetronomeFadeLevel
import com.tonic.core.model.rhythm.MetronomePlanner
import com.tonic.core.model.rhythm.RhythmPattern
import com.tonic.core.model.rhythm.Tempo
import kotlin.random.Random

/**
 * `M3.*` item generation — docs/40-PHASE-4-SPEC.md §5, and Stage 4.2's acceptance in its own words:
 * "Deterministic per `(skill, axes, seed)`."
 *
 * Pure, seeded, and free of any clock, exactly as every generator before it (CLAUDE.md §5). That
 * matters more here than anywhere else in the project, because rhythm is the first module to introduce
 * genuinely non-deterministic *input* — real human tap times — and §4.4's rule for keeping the system
 * testable is that generation stays fully deterministic while the taps are recorded as data. If which
 * pattern you were asked to tap depended on anything but the seed, a recorded session could not be
 * replayed and a scoring bug could not be reproduced offline.
 *
 * Only simple meters are generated. Compound meter and meter changes are `M3.COMPOUND` and
 * `M3.METER_CHANGE`, built at Stage 4.6 with the nodes that teach them.
 *
 * **Production items only, at this stage.** §9 gives the recognition nodes to Stage 4.4, and building
 * them here would mean designing a question this stage has no reason to design: `M3.DOWNBEAT` asks
 * *which beat is "one"*, which is not a choice between patterns at all, and its answer set is beat
 * positions rather than rhythms. Half-answering that now would leave a shape that 4.4 has to undo. A
 * recognition node passed in fails loudly and says where it is built.
 */
object M3ItemGenerator {
    /**
     * @param skill which `M3` node — decides whether this is a recognition or production item, and
     *   which rhythmic figures may appear.
     * @param axisLevels the node's current levels. Missing axes read as 0, which is what a node that
     *   has never been practiced has.
     * @param seed the item seed. The same seed and levels give a byte-identical item, forever.
     */
    fun generate(
        skill: SkillId,
        axisLevels: Map<DifficultyAxis, Int>,
        seed: Long,
    ): Item.RhythmItem {
        require(skill in SkillIds.M3_NODES_IN_ORDER) { "Not an M3 node: $skill" }
        val random = Random(seed)

        fun level(axis: DifficultyAxis) = axisLevels[axis] ?: 0

        val lengthLevel = level(DifficultyAxis.PATTERN_LENGTH)
        val meter = RhythmAxisParameters.meters(lengthLevel).let { it[random.nextInt(it.size)] }
        val bars = RhythmAxisParameters.bars(lengthLevel)

        // Both tempi at this level are offered; which one this item takes is part of what the seed
        // decides. Picking only the fast one would delete the slow half of the axis, which is the half
        // that trains internal timekeeping (docs/40-PHASE-4-SPEC.md §3.5).
        val (slow, fast) = Tempo.bpmPairFor(level(DifficultyAxis.TEMPO_DEVIATION))
        val tempoBpm = if (random.nextBoolean()) slow else fast

        val mode = modeFor(skill)
        require(mode == RhythmMode.PRODUCTION) {
            "Recognition item generation for $skill is not built - docs/40-PHASE-4-SPEC.md stage 4.4"
        }

        val pattern = patternFor(skill, meter, bars, level(DifficultyAxis.RHYTHMIC_DENSITY), random)
        val fade = MetronomeFadeLevel.fromLevel(level(DifficultyAxis.METRONOME_FADE))

        return Item.RhythmItem(
            skill = skill,
            meter = meter,
            tempoBpm = tempoBpm,
            pattern = pattern,
            metronomePlan = MetronomePlanner.plan(fade, meter, bars),
            mode = mode,
            timbre = timbreFor(level(DifficultyAxis.TIMBRE_VARIETY), random),
            seed = seed,
        )
    }

    /**
     * Which half of the module a node belongs to — docs/40-PHASE-4-SPEC.md §5.1's Mode column.
     *
     * Exhaustive over the twelve nodes rather than inferred from the name, even though every recognition
     * node happens to end in `_RECOG` today. A naming convention is not a curriculum: `M3.BEAT_FIND` is
     * production and `M3.DOWNBEAT` is recognition, and neither says so in its name.
     */
    private fun modeFor(skill: SkillId): RhythmMode =
        when (skill) {
            SkillIds.M3_DOWNBEAT,
            SkillIds.M3_BEAT_DIV_RECOG,
            SkillIds.M3_SUBDIV_RECOG,
            SkillIds.M3_SYNCOPATION_RECOG,
            -> RhythmMode.RECOGNITION

            else -> RhythmMode.PRODUCTION
        }

    /**
     * The rhythmic figures a node may use.
     *
     * Each node adds exactly one thing to the one before it, which is the same shape `M11` uses for
     * chromatic degrees: a learner meets one new idea at a time and everything else is already familiar.
     */
    private fun patternFor(
        skill: SkillId,
        meter: Meter,
        bars: Int,
        densityLevel: Int,
        random: Random,
    ): RhythmPattern {
        val beats = bars * meter.beatsPerBar
        val beatTicks = (0 until beats).map { it * Meter.TICKS_PER_BEAT }

        val onsets =
            when (skill) {
                // Plain beats, every one of them. Finding and keeping the pulse is the whole task.
                SkillIds.M3_BEAT_FIND, SkillIds.M3_DOWNBEAT -> beatTicks

                // Beats, some of them split in two. The first time anything falls between beats.
                SkillIds.M3_BEAT_DIV_RECOG, SkillIds.M3_BEAT_DIV ->
                    subdivide(beatTicks, meter, parts = 2, densityLevel, random)

                // Beats split in four.
                SkillIds.M3_SUBDIV_RECOG, SkillIds.M3_SUBDIV ->
                    subdivide(beatTicks, meter, parts = 4, densityLevel, random)

                // As SUBDIV, then silence punched into it. A rest is the absence of an onset, so this is
                // a removal rather than a new kind of event - see RhythmPattern.
                SkillIds.M3_RESTS ->
                    withRests(subdivide(beatTicks, meter, parts = 4, densityLevel, random), random)

                // Emphasis off the beat: the beat itself is missing where the ear expects it.
                SkillIds.M3_SYNCOPATION_RECOG, SkillIds.M3_SYNCOPATION ->
                    syncopate(beatTicks, meter, random)

                else ->
                    error(
                        "Pattern generation for $skill is not built - " +
                            "docs/40-PHASE-4-SPEC.md stage 4.6 covers compound meter, meter change " +
                            "and the independence check",
                    )
            }

        // The downbeat always sounds. Not a musical nicety: without it there is nothing anchoring the
        // pattern to the bar, and a learner tapping a rhythm that starts in silence is being asked to
        // guess where it began rather than to reproduce it.
        val anchored = (onsets + 0).distinct().sorted()
        return RhythmPattern(meter, bars, anchored)
    }

    /**
     * Beats, with some proportion of them split into [parts].
     *
     * Which beats subdivide is drawn from the seed, so the figure varies while the *amount* of
     * subdivision stays exactly what `RHYTHMIC_DENSITY` asked for. Varying the count instead would make
     * the axis mean "on average this dense", and a learner could get an easy item at a hard level.
     */
    private fun subdivide(
        beatTicks: List<Int>,
        meter: Meter,
        parts: Int,
        densityLevel: Int,
        random: Random,
    ): List<Int> {
        val perTwelve = RhythmAxisParameters.subdividedBeatsPerTwelve(densityLevel)
        val howMany = beatTicks.size * perTwelve / TWELFTHS
        val chosen = beatTicks.shuffled(random).take(howMany).toSet()
        val step = Meter.TICKS_PER_BEAT / parts
        return beatTicks.flatMap { beat ->
            if (beat in chosen) (0 until parts).map { beat + it * step } else listOf(beat)
        }
    }

    /** Removes a few onsets, leaving silence where the ear expected a sound. Never the downbeat. */
    private fun withRests(
        onsets: List<Int>,
        random: Random,
    ): List<Int> {
        val removable = onsets.filter { it != 0 }
        if (removable.isEmpty()) return onsets
        val toRemove = removable.shuffled(random).take(1 + random.nextInt(REST_COUNT_RANGE)).toSet()
        return onsets.filterNot { it in toRemove }
    }

    /**
     * A pattern whose emphasis lands off the beat: one interior beat is silent and the half-beat before
     * it sounds instead.
     *
     * That is what makes it syncopation rather than merely a subdivision — the sound arrives early and
     * the beat itself is empty, so the learner has to keep the pulse that the pattern is contradicting.
     */
    private fun syncopate(
        beatTicks: List<Int>,
        meter: Meter,
        random: Random,
    ): List<Int> {
        val half = Meter.TICKS_PER_BEAT / meter.division
        val interior = beatTicks.drop(1)
        if (interior.isEmpty()) return beatTicks
        val silenced = interior[random.nextInt(interior.size)]
        return (beatTicks - silenced + (silenced - half)).distinct().sorted()
    }

    private fun timbreFor(
        level: Int,
        random: Random,
    ): TimbreId {
        val pool = AxisParameters.timbrePool(level)
        return pool[random.nextInt(pool.size)]
    }

    private const val TWELFTHS = 12
    private const val REST_COUNT_RANGE = 2
}
