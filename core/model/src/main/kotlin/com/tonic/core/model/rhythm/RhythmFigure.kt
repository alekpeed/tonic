package com.tonic.core.model.rhythm

/**
 * A rhythmic figure: how one beat is filled — docs/40-PHASE-4-SPEC.md §8, "the 'confusion matrix' for
 * rhythm is over *rhythmic figures*, not labels."
 *
 * **The beat, not the pattern.** A whole pattern is very nearly unique — vary one sixteenth and it is a
 * different pattern — so a matrix over patterns would have a cell per item and never accumulate enough
 * of anything to say a learner confuses one thing for another. A beat's fill is a small alphabet that
 * recurs: `ta`, `ta-di`, `ta-ka-di-mi`, and the handful of syncopated fills between them.
 *
 * It is also the unit the module already teaches. §3.1 chose Takadimi precisely because it names
 * position *within a beat*, so a figure's signature can be its syllables — which means a confusion view
 * can say "you hear `ta-di` when it was `ta-ka-di-mi`" in the learner's own vocabulary rather than in
 * tick offsets.
 */
public object RhythmFigure {
    /**
     * The signature of the beat containing [beatIndex] of [pattern] — its Takadimi syllables, joined.
     *
     * A beat with no onset at all is [SILENT] rather than an empty string: a rest is a real figure and
     * a learner can genuinely mistake it for a sounded one, so it needs a name in the matrix.
     */
    public fun signatureAt(
        pattern: RhythmPattern,
        beatIndex: Int,
    ): String {
        val parts = pattern.finestDivision
        val step = Meter.TICKS_PER_BEAT / parts
        val beatStart = beatIndex * Meter.TICKS_PER_BEAT
        val filled =
            (0 until parts).filter { part -> (beatStart + part * step) in pattern.onsetTicks }
        if (filled.isEmpty()) return SILENT
        return filled.joinToString("-") { Takadimi.syllableAt(parts, it) }
    }

    /**
     * The first beat at which [patterns] do not all agree, or null when they are identical throughout.
     *
     * This is the beat the item is actually testing. A recognition item offers two or three rhythms
     * that are the same until they aren't, and the moment they diverge is the discrimination the
     * learner is being asked to make — so that is the beat whose figure the attempt records. Labelling
     * the attempt with the whole pattern instead would record which of three sounds they picked and
     * lose *what they had to hear* to pick it.
     */
    public fun discriminatingBeat(patterns: List<RhythmPattern>): Int? {
        if (patterns.size < 2) return null
        val beats = patterns.minOf { it.bars * it.meter.beatsPerBar }
        return (0 until beats).firstOrNull { beat ->
            patterns.map { signatureAt(it, beat) }.toSet().size > 1
        }
    }

    /** The figure sounded at the beat the item turns on, for each of [patterns] in order. */
    public fun signaturesAtDiscriminatingBeat(patterns: List<RhythmPattern>): List<String> {
        val beat = discriminatingBeat(patterns) ?: return patterns.map { signatureAt(it, 0) }
        return patterns.map { signatureAt(it, beat) }
    }

    /**
     * The tick offsets within a beat that [signature] describes — the inverse of [signatureAt].
     *
     * Needed to *build* a pattern with a given figure at a given beat, which is how a recognition item
     * makes its distractors: take the answer and change one beat's fill. Without an inverse, distractors
     * have to be generated independently and may differ from the answer in several places at once,
     * which leaves "the figure being tested" undefined.
     *
     * A syllable maps to one tick regardless of how finely the beat is divided, which is what makes this
     * unambiguous: `di` is halfway through the beat whether the beat is in two or in four, and Takadimi
     * names it `di` in both. That is §3.1's whole argument for the system, showing up as a property of
     * the code.
     */
    public fun ticksFor(signature: String): List<Int> {
        if (signature == SILENT) return emptyList()
        return signature.split("-").map { syllable ->
            TICK_BY_SYLLABLE[syllable]
                ?: throw IllegalArgumentException("Not a Takadimi syllable: \"$syllable\" in \"$signature\"")
        }
    }

    /** Every figure that can be written at a beat divided into [parts] or fewer. */
    public fun figuresFor(parts: Int): List<String> {
        val table = Takadimi.forDivision(parts)
        val step = Meter.TICKS_PER_BEAT / parts
        return (1 until (1 shl parts))
            .map { mask ->
                table.indices.filter { (mask shr it) and 1 == 1 }.joinToString("-") { table[it] }
            }.filter { signature -> ticksFor(signature).all { it % step == 0 } }
    }

    /** A beat nothing sounds on. Named rather than empty, because a learner can mistake it for a filled one. */
    public const val SILENT: String = "rest"

    /**
     * Where each syllable sits inside a beat, in ticks.
     *
     * One table rather than one per division, because the mapping genuinely is one: `ta` opens every
     * beat, `di` is halfway through it, and neither changes meaning when the beat divides differently.
     */
    private val TICK_BY_SYLLABLE: Map<String, Int> =
        buildMap {
            for (parts in Takadimi.supportedDivisions) {
                val step = Meter.TICKS_PER_BEAT / parts
                Takadimi.forDivision(parts).forEachIndexed { index, syllable ->
                    val tick = index * step
                    val existing = put(syllable, tick)
                    require(existing == null || existing == tick) {
                        "Takadimi syllable \"$syllable\" means two different positions: $existing and $tick"
                    }
                }
            }
        }
}
