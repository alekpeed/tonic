package com.tonic.core.curriculum.generators

import com.tonic.core.curriculum.graph.SkillGraph
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
import com.tonic.core.model.rhythm.RhythmQuestion
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
 * Both halves of the module since Stage 4.4. Which question a node asks comes from
 * [SkillGraph.rhythmModeFor] rather than from the skill id's spelling, and `M3.DOWNBEAT` gets a shape
 * of its own — see [whichBeatIsOne].
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

        val density = level(DifficultyAxis.RHYTHMIC_DENSITY)
        val pattern = patternFor(skill, meter, bars, density, random)
        val fade = MetronomeFadeLevel.fromLevel(level(DifficultyAxis.METRONOME_FADE))
        val basePlan = MetronomePlanner.plan(fade, meter, bars)

        val question =
            when (SkillGraph.rhythmModeFor(skill)) {
                RhythmMode.PRODUCTION -> RhythmQuestion.TapItBack
                RhythmMode.RECOGNITION ->
                    recognitionQuestion(
                        skill,
                        pattern,
                        meter,
                        bars,
                        density,
                        level(DifficultyAxis.TIMING_TOLERANCE),
                        random,
                    )
            }

        return Item.RhythmItem(
            skill = skill,
            meter = meter,
            tempoBpm = tempoBpm,
            pattern = pattern,
            // On a downbeat item the accent is the answer, so it is removed - §5.1 asks the learner to
            // find "one" and §3.4 says the skill is hearing it in music that does not announce it.
            metronomePlan =
                if (question is RhythmQuestion.WhichBeatIsOne) basePlan.withoutDownbeatAccents() else basePlan,
            question = question,
            timbre = timbreFor(level(DifficultyAxis.TIMBRE_VARIETY), random),
            seed = seed,
        )
    }

    /**
     * What a recognition node asks — docs/40-PHASE-4-SPEC.md §3.3, and §5.1 for `M3.DOWNBEAT`.
     *
     * Two shapes, because `M3.DOWNBEAT` genuinely asks something else. The other three ask "which of
     * these did you just hear"; that one asks where the bar turned over, and its answers are positions
     * in time rather than rhythms.
     */
    private fun recognitionQuestion(
        skill: SkillId,
        pattern: RhythmPattern,
        meter: Meter,
        bars: Int,
        density: Int,
        toleranceLevel: Int,
        random: Random,
    ): RhythmQuestion =
        if (skill == SkillIds.M3_DOWNBEAT) {
            whichBeatIsOne(meter, bars, random)
        } else {
            whichPattern(skill, pattern, meter, bars, density, toleranceLevel, random)
        }

    /**
     * `M3.DOWNBEAT` — docs/40-PHASE-4-SPEC.md §5.1 and §3.4.
     *
     * §3.4 makes beat induction a first-class skill: "finding the beat in music that doesn't announce
     * it — hearing where 'one' is". The presentation follows from that phrase. **Playback begins
     * part-way into the bar**, so the first beat heard is usually not the downbeat and the learner has
     * to feel where the bar turns over rather than read it off the start of the audio. An item that
     * always began on the downbeat would have the same answer every time and would train nothing.
     *
     * The rotation is drawn from the seed and is *sometimes* zero. A node whose answer is never the
     * first option teaches a strategy rather than a skill — the same reason `M9` draws its major/minor
     * answer from a coin rather than alternating.
     */
    private fun whichBeatIsOne(
        meter: Meter,
        bars: Int,
        random: Random,
    ): RhythmQuestion.WhichBeatIsOne {
        val beatsHeard = bars * meter.beatsPerBar
        // How far into the bar playback starts. Zero means it starts on the downbeat, which is a real
        // and occasional case rather than one to be excluded.
        val rotation = random.nextInt(meter.beatsPerBar)
        val downbeatPosition = if (rotation == 0) 1 else meter.beatsPerBar - rotation + 1
        return RhythmQuestion.WhichBeatIsOne(beatsHeard = beatsHeard, downbeatPosition = downbeatPosition)
    }

    /**
     * The wrong answers for a `*_RECOG` item, plus the right one, in the order they are played.
     *
     * Every distractor is generated by the same rules as the answer, so the learner discriminates
     * between two real rhythms of the same kind rather than spotting the odd one out. A distractor
     * built differently is answerable without hearing the rhythm at all.
     *
     * How many choices comes off `TIMING_TOLERANCE`, because a recognition item has no timing tolerance
     * of its own — tap timing is irrelevant there (§3.3) — and an axis that changed nothing for half
     * the module would have the scheduler moving a level with no effect. Three is the ceiling: §3.3's
     * own wording is "which of these three patterns did you just hear", and a fourth would test memory
     * for a sequence of sounds rather than discrimination between them.
     */
    private fun whichPattern(
        skill: SkillId,
        answer: RhythmPattern,
        meter: Meter,
        bars: Int,
        density: Int,
        toleranceLevel: Int,
        random: Random,
    ): RhythmQuestion.WhichPattern {
        val wanted = if (toleranceLevel >= TWO_TO_THREE_CHOICE_LEVEL) 3 else 2
        val patterns = mutableListOf(answer)
        var attempts = 0
        while (patterns.size < wanted && attempts < MAX_DISTRACTOR_ATTEMPTS) {
            attempts++
            val candidate = patternFor(skill, meter, bars, density, random)
            if (patterns.none { it.onsetTicks == candidate.onsetTicks }) patterns += candidate
        }
        // A short pattern at a low density has few distinct forms, and two identical choices have no
        // right answer at all. Widening the density is the smallest change that reliably yields a
        // different rhythm and stays within the same node's own figures - the alternative, offering
        // fewer choices than the level asked for, would make the axis mean nothing at the very levels
        // where patterns are scarcest.
        var widened = density
        while (patterns.size < wanted && widened < MAX_DENSITY) {
            widened++
            repeat(MAX_DISTRACTOR_ATTEMPTS) {
                if (patterns.size < wanted) {
                    val candidate = patternFor(skill, meter, bars, widened, random)
                    if (patterns.none { it.onsetTicks == candidate.onsetTicks }) patterns += candidate
                }
            }
        }
        require(patterns.size >= 2) {
            "Could not build a second distinct pattern for $skill - a choice needs something to choose between"
        }

        val shuffled = patterns.shuffled(random)
        return RhythmQuestion.WhichPattern(choices = shuffled, answerIndex = shuffled.indexOf(answer))
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
                    subdivide(beatTicks, parts = 2, densityLevel, random)

                // Beats split in four.
                SkillIds.M3_SUBDIV_RECOG, SkillIds.M3_SUBDIV ->
                    subdivide(beatTicks, parts = 4, densityLevel, random)

                // As SUBDIV, then silence punched into it. A rest is the absence of an onset, so this is
                // a removal rather than a new kind of event - see RhythmPattern.
                SkillIds.M3_RESTS ->
                    withRests(subdivide(beatTicks, parts = 4, densityLevel, random), random)

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
     *
     * **At least one beat always subdivides**, whatever the density level says. This is only called for
     * nodes whose subject *is* subdivision, and `RHYTHMIC_DENSITY` level 0 asks for none of it — so
     * without the floor, `M3.BEAT_DIV` at level 0 produces plain beats and is indistinguishable from
     * `M3.BEAT_FIND`, the node before it. A learner would meet the node that introduces division and
     * hear nothing divided.
     *
     * It also makes recognition answerable at all: a `*_RECOG` item needs two *distinct* patterns to
     * choose between, and at density 0 without this floor every pattern a node can produce is the same
     * one. That is how this was found — `M3RecognitionItemTest` could not build three choices.
     */
    private fun subdivide(
        beatTicks: List<Int>,
        parts: Int,
        densityLevel: Int,
        random: Random,
    ): List<Int> {
        val perTwelve = RhythmAxisParameters.subdividedBeatsPerTwelve(densityLevel)
        val howMany = (beatTicks.size * perTwelve / TWELFTHS).coerceIn(1, beatTicks.size)
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
    private const val MAX_DISTRACTOR_ATTEMPTS = 40
    private const val TWO_TO_THREE_CHOICE_LEVEL = 2
    private const val MAX_DENSITY = 3
}
