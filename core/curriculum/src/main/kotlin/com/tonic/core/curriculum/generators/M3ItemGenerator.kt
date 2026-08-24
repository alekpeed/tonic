package com.tonic.core.curriculum.generators

import com.tonic.core.curriculum.graph.SkillGraph
import com.tonic.core.model.ids.SkillId
import com.tonic.core.model.ids.SkillIds
import com.tonic.core.model.items.DifficultyAxis
import com.tonic.core.model.items.Item
import com.tonic.core.model.items.RhythmMode
import com.tonic.core.model.music.TimbreId
import com.tonic.core.model.rhythm.ChoiceSequence
import com.tonic.core.model.rhythm.Meter
import com.tonic.core.model.rhythm.MeterChange
import com.tonic.core.model.rhythm.MetronomeFadeLevel
import com.tonic.core.model.rhythm.MetronomePlanner
import com.tonic.core.model.rhythm.RhythmFigure
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
        val meterPool =
            if (skill == SkillIds.M3_COMPOUND) {
                RhythmAxisParameters.compoundMeters(lengthLevel)
            } else {
                RhythmAxisParameters.meters(lengthLevel)
            }
        val meter = meterPool[random.nextInt(meterPool.size)]
        val bars = RhythmAxisParameters.bars(lengthLevel)

        // Both tempi at this level are offered; which one this item takes is part of what the seed
        // decides. Picking only the fast one would delete the slow half of the axis, which is the half
        // that trains internal timekeeping (docs/40-PHASE-4-SPEC.md §3.5).
        val (slow, fast) = Tempo.bpmPairFor(level(DifficultyAxis.TEMPO_DEVIATION))
        val tempoBpm = if (random.nextBoolean()) slow else fast

        // §5.1's "meter changes mid-pattern". The change is drawn before the pattern so both the
        // pattern and the metronome plan are built against the same one - the M3.DOWNBEAT bug was
        // exactly this shape, a secret drawn twice and agreed on once.
        val meterChange =
            if (skill == SkillIds.M3_METER_CHANGE) meterChangeFor(meter, bars, random) else null

        val density = level(DifficultyAxis.RHYTHMIC_DENSITY)
        // The check runs at a fixed fade whatever the learner's axis says - §5.1: "30 production items
        // at METRONOME_FADE L6". That is the whole assessment: two beats of count-in and then nothing,
        // two levels above what mastery required, so a learner who leaned on a long count-in to get
        // here still has to keep the pulse alone now.
        val fade =
            if (skill == SkillIds.M3_INDEPENDENCE_CHECK) {
                MetronomeFadeLevel.fromLevel(MetronomeFadeLevel.INDEPENDENCE_CHECK_LEVEL)
            } else {
                MetronomeFadeLevel.fromLevel(level(DifficultyAxis.METRONOME_FADE))
            }

        // M3.DOWNBEAT is the one node whose pattern and whose question share a secret: how far into
        // the bar playback begins. Drawn once, here, and used by both. Drawing it inside the question
        // - which is what this did first - left the pattern unrotated while the recorded answer was
        // derived as though it had been, so the stated answer described audio nobody heard.
        val rotation =
            if (skill == SkillIds.M3_DOWNBEAT) random.nextInt(meter.beatsPerBar) else 0
        val pattern =
            rotate(patternFor(skill, meter, bars, density, random, meterChange), meter, rotation)

        val question =
            when (SkillGraph.rhythmModeFor(skill)) {
                RhythmMode.PRODUCTION -> RhythmQuestion.TapItBack
                RhythmMode.RECOGNITION ->
                    recognitionQuestion(
                        skill,
                        pattern,
                        meter,
                        bars,
                        level(DifficultyAxis.TIMING_TOLERANCE),
                        rotation,
                        random,
                    )
            }

        // Planned across the item's whole *audio*, not across one pattern. A `WhichPattern` item plays
        // the target and then every choice over one continuous metronome (see ChoiceSequence), so a
        // plan covering a single pattern would stop clicking a quarter of the way through and the
        // learner would be comparing the later choices against nothing. One plan per item, spanning
        // exactly what that item sounds, is what keeps the clicks and the patterns in phase.
        val planBars =
            when (question) {
                is RhythmQuestion.WhichPattern ->
                    ChoiceSequence.totalBars(segmentCount = question.choices.size + 1, barsPerSegment = bars)

                is RhythmQuestion.TapItBack, is RhythmQuestion.WhichBeatIsOne -> bars
            }
        val basePlan = MetronomePlanner.plan(fade, meter, planBars, meterChange)

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
        toleranceLevel: Int,
        rotation: Int,
        random: Random,
    ): RhythmQuestion =
        if (skill == SkillIds.M3_DOWNBEAT) {
            whichBeatIsOne(meter, bars, rotation)
        } else {
            whichPattern(skill, pattern, random, toleranceLevel)
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
        rotation: Int,
    ): RhythmQuestion.WhichBeatIsOne {
        val beatsHeard = bars * meter.beatsPerBar
        // [rotation] beats of the bar have already gone by when playback starts, so the first "one"
        // the learner hears arrives that many beats from the end of the bar. Zero is a real and
        // occasional case rather than one to exclude - a node whose answer is never the first option
        // teaches a strategy rather than a skill.
        val downbeatPosition = if (rotation == 0) 1 else meter.beatsPerBar - rotation + 1
        return RhythmQuestion.WhichBeatIsOne(beatsHeard = beatsHeard, downbeatPosition = downbeatPosition)
    }

    /**
     * [pattern] with its first [rotation] beats moved to the end — playback beginning part-way into
     * the bar, which is what makes `M3.DOWNBEAT` a question about hearing rather than about reading
     * the start of a file.
     *
     * A rotation of zero returns the pattern untouched, which is the ordinary case for every other
     * node.
     */
    private fun rotate(
        pattern: RhythmPattern,
        meter: Meter,
        rotation: Int,
    ): RhythmPattern {
        if (rotation == 0) return pattern
        val shift = rotation * Meter.TICKS_PER_BEAT
        val total = pattern.totalTicks
        val moved =
            pattern.onsetTicks
                .map { (it - shift + total) % total }
                .distinct()
                .sorted()
        // Deliberately *not* anchored with a sound at tick 0, unlike every other pattern here. This
        // is a cyclic shift of a figure that repeats once a bar, and forcing an onset at the start
        // adds a sound to the first bar that no other bar has - which destroys the period the learner
        // is being asked to find. It also puts back exactly the cue rotation exists to remove: a
        // pattern that always opens with a sound can be read off the start of the audio, and §3.4
        // asks for the beat to be found in music that does not announce it.
        return RhythmPattern(meter, pattern.bars, moved)
    }

    /**
     * The wrong answers for a `*_RECOG` item, plus the right one, in the order they are played.
     *
     * **Every distractor is the answer with exactly one beat's figure changed.** That is the design,
     * not an optimization. Generating distractors independently — which is what this did first — leaves
     * them differing from the answer in several places at once, so "the figure being tested" is
     * undefined, and with three choices two of them can sound the *same* figure at the first divergent
     * beat while differing later. The attempt log then cannot say which rhythm the learner picked,
     * which is exactly what docs/40-PHASE-4-SPEC.md §8's confusion matrix needs it to say.
     *
     * Changing one beat also makes the item test one thing. A learner who picks wrongly has mistaken a
     * specific figure for another specific figure, and that pair is what gets recorded.
     *
     * How many choices comes off `TIMING_TOLERANCE`, because a recognition item has no timing tolerance
     * of its own — tap timing is irrelevant there (§3.3) — and an axis that changed nothing for half the
     * module would have the scheduler moving a level with no effect. Three is the ceiling: §3.3's own
     * wording is "which of these three patterns did you just hear", and a fourth would test memory for a
     * sequence of sounds rather than discrimination between them.
     */
    private fun whichPattern(
        skill: SkillId,
        answer: RhythmPattern,
        random: Random,
        toleranceLevel: Int,
    ): RhythmQuestion.WhichPattern {
        val alphabet = SkillGraph.activeFiguresFor(skill)
        // Capped by what the node actually teaches. `M3.BEAT_DIV_RECOG`'s alphabet is exactly `ta` and
        // `ta-di` - beat against division, which is the whole node - so a three-way choice does not
        // exist there, and inventing a third figure would change what the node teaches. Only nodes with
        // a wider alphabet, like syncopation's four, ever reach three.
        //
        // A third choice cannot be made by varying a *different* beat either: the choices would then
        // agree at the beat they first diverge on, and two of them would carry the same label.
        val wanted = minOf(if (toleranceLevel >= TWO_TO_THREE_CHOICE_LEVEL) 3 else 2, alphabet.size)
        val beats = answer.bars * answer.meter.beatsPerBar

        // Never beat 0: the downbeat always sounds (see patternFor), so changing it would produce a
        // pattern the rest of the generator would not have made.
        val candidateBeats = (1 until beats).shuffled(random)
        for (beat in candidateBeats) {
            val current = RhythmFigure.signatureAt(answer, beat)
            val alternatives =
                alphabet
                    .filter { it != current }
                    .sorted()
                    .shuffled(random)
                    .take(wanted - 1)
            if (alternatives.size < wanted - 1) continue

            val variants = alternatives.mapNotNull { figure -> withFigureAt(answer, beat, figure) }
            if (variants.size < wanted - 1) continue

            val all = (listOf(answer) + variants).shuffled(random)
            return RhythmQuestion.WhichPattern(choices = all, answerIndex = all.indexOf(answer))
        }
        error("Could not build $wanted distinct choices for $skill - it has too few figures to discriminate")
    }

    /**
     * [pattern] with the beat at [beatIndex] refilled to [figure], or null if that would change nothing.
     *
     * Null rather than an equal pattern, because an item offering two identical choices has no right
     * answer at all — the caller treats it as a beat that cannot be varied and tries another.
     */
    private fun withFigureAt(
        pattern: RhythmPattern,
        beatIndex: Int,
        figure: String,
    ): RhythmPattern? {
        val beatStart = beatIndex * Meter.TICKS_PER_BEAT
        val beatRange = beatStart until (beatStart + Meter.TICKS_PER_BEAT)
        val rebuilt =
            (pattern.onsetTicks.filterNot { it in beatRange } + RhythmFigure.ticksFor(figure).map { beatStart + it })
                .distinct()
                .sorted()
        if (rebuilt == pattern.onsetTicks || rebuilt.isEmpty() || rebuilt.first() != 0) return null
        return RhythmPattern(pattern.meter, pattern.bars, rebuilt)
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
        meterChange: MeterChange? = null,
    ): RhythmPattern {
        // The beat is the same length in both meters - only the grouping changes - so the beat grid is
        // just the total span divided by the beat, whether or not a change happens part-way.
        val totalTicks =
            if (meterChange == null) {
                bars * meter.ticksPerBar
            } else {
                meterChange.atBar * meter.ticksPerBar +
                    (bars - meterChange.atBar) * meterChange.meter.ticksPerBar
            }
        val beatTicks = (0 until totalTicks step Meter.TICKS_PER_BEAT).toList()

        val onsets =
            when (skill) {
                // Plain beats, every one of them. Finding and keeping the pulse is the whole task.
                SkillIds.M3_BEAT_FIND -> beatTicks

                // A figure that repeats once a bar - see barSignature. Plain beats cannot serve here:
                // they carry no information about where the bar starts, so the question had no
                // answer in the sound.
                SkillIds.M3_DOWNBEAT -> barSignature(meter, bars, random)

                // The beat splits in three. §3.1's case for Takadimi is exactly this one - ta-ki-da is
                // three equal parts of one beat, which is what a learner hears, while Kodaly has to
                // reach for a note value that describes the page instead.
                SkillIds.M3_COMPOUND ->
                    subdivide(beatTicks, parts = Meter.COMPOUND, densityLevel, random)

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
                SkillIds.M3_SYNCOPATION_RECOG, SkillIds.M3_SYNCOPATION, SkillIds.M3_INDEPENDENCE_CHECK ->
                    syncopate(beatTicks, meter, random)

                // Beats and divisions across both meters. Nothing new rhythmically - the whole skill
                // is hearing the bar length change under a rhythm that does not otherwise surprise
                // you, so adding syncopation on top would test two things at once.
                SkillIds.M3_METER_CHANGE ->
                    subdivide(beatTicks, parts = meter.division, densityLevel, random)

                else ->
                    error(
                        "Pattern generation for $skill is not built - " +
                            "no pattern shape is defined for it in docs/40-PHASE-4-SPEC.md §5.1",
                    )
            }

        // The downbeat always sounds. Not a musical nicety: without it there is nothing anchoring the
        // pattern to the bar, and a learner tapping a rhythm that starts in silence is being asked to
        // guess where it began rather than to reproduce it.
        val anchored = (onsets + 0).distinct().sorted()
        return RhythmPattern(meter, bars, anchored, meterChange)
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

    /**
     * A one-bar rhythmic figure, repeated for every bar — what `M3.DOWNBEAT` is heard against.
     *
     * **The repetition is the information.** §3.4 asks the learner to find "one" in music that does
     * not announce it, and the honest way to make that findable without announcing it is a figure
     * whose period is the bar: hear it come round, and you have found the downbeat. The first version
     * of this node played plain identical beats with the metronome's accent removed, which left
     * literally nothing in the sound to answer from — the question was unanswerable and the recorded
     * answer corresponded to nothing the learner heard.
     *
     * The figure always sounds on beat one and skips at least one other beat, so the bar has a shape
     * rather than being uniform. Which beats it skips comes from the seed.
     */
    private fun barSignature(
        meter: Meter,
        bars: Int,
        random: Random,
    ): List<Int> {
        val perBar = meter.beatsPerBar
        // Beat 0 always sounds; at least one of the rest does not, or the bar is uniform again.
        val silent =
            if (perBar <= 2) {
                setOf(perBar - 1)
            } else {
                (1 until perBar)
                    .shuffled(random)
                    .take(1 + random.nextInt(perBar - 2))
                    .toSet()
            }
        val inBar = (0 until perBar).filterNot { it in silent }
        // An off-beat sound inside the bar, so the figure is not merely "some beats missing" - it
        // gives the bar an internal shape that survives being heard from the middle.
        val accentAt = (meter.ticksPerBar / 2) + (Meter.TICKS_PER_BEAT / meter.division)
        val onsets =
            (0 until bars).flatMap { bar ->
                val barStart = bar * meter.ticksPerBar
                inBar.map { barStart + it * Meter.TICKS_PER_BEAT } + listOf(barStart + accentAt)
            }
        return onsets.distinct().sorted()
    }

    /**
     * Where the meter changes and to what — `M3.METER_CHANGE`, docs/40-PHASE-4-SPEC.md §5.1.
     *
     * Changes to a *simple* meter of a different length, never between simple and compound. Two
     * things changing at once - how many beats in a bar and how each beat divides - is two skills,
     * and a learner who misses it could not say which one they missed. `M3.COMPOUND` teaches the
     * division on its own; this teaches the grouping on its own.
     *
     * Never in the first bar: the learner needs at least one bar of the original meter to have
     * something to hear the change *against*.
     */
    private fun meterChangeFor(
        meter: Meter,
        bars: Int,
        random: Random,
    ): MeterChange? {
        if (bars < MIN_BARS_FOR_METER_CHANGE) return null
        val alternatives =
            listOf(Meter.FOUR_FOUR, Meter.THREE_FOUR, Meter.TWO_FOUR).filter { it != meter }
        val to = alternatives[random.nextInt(alternatives.size)]
        // At least one bar either side of it.
        val atBar = 1 + random.nextInt(bars - 1)
        return MeterChange(atBar = atBar, meter = to)
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

    /** A change needs a bar before it and a bar after it, or there is nothing to hear it against. */
    private const val MIN_BARS_FOR_METER_CHANGE = 2

    private const val TWELFTHS = 12
    private const val REST_COUNT_RANGE = 2
    private const val TWO_TO_THREE_CHOICE_LEVEL = 2
}
