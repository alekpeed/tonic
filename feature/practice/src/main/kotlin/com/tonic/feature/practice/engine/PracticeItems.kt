package com.tonic.feature.practice.engine

import com.tonic.core.audio.rhythm.RhythmRenderer
import com.tonic.core.audio.synth.PcmBuffer
import com.tonic.core.audio.synth.SynthEngine
import com.tonic.core.curriculum.generators.GenerationHistory
import com.tonic.core.curriculum.generators.M12ItemGenerator
import com.tonic.core.curriculum.generators.M2ItemGenerator
import com.tonic.core.curriculum.generators.M3ItemGenerator
import com.tonic.core.curriculum.generators.M9ItemGenerator
import com.tonic.core.curriculum.graph.SkillGraph
import com.tonic.core.model.ids.SkillId
import com.tonic.core.model.ids.SkillIds
import com.tonic.core.model.items.AnswerAlphabet
import com.tonic.core.model.items.DifficultyAxis
import com.tonic.core.model.items.Item
import com.tonic.core.model.music.Tuning

/**
 * Everything the practice loop needs to know about *which kind* of item it is holding, in one file.
 *
 * The loop used to be typed on [Item.FunctionalRecognitionItem] end to end — state, rendering,
 * scoring, attempt construction. Phase 2 adds item types with genuinely different shapes (`M9` has no
 * target note; `M12` has no reference plan in the same sense), so the loop is now typed on [Item] and
 * asks here for the handful of per-type facts it actually uses. Keeping those in one place means the
 * loop's control flow — the part with a determinism-race history — stays untouched by every future
 * item type, and adding one is a compile error *here* rather than a silent wrong answer somewhere else.
 *
 * Every `when` below is exhaustive over the sealed hierarchy with explicit branches for the item types
 * the practice loop does not run (`M0.*` and `M1.*` belong to `:feature:diagnostic`, which has its own
 * loop). That is deliberate over an `else`: adding an item type must not silently fall into a default.
 */
internal object PracticeItems {
    /**
     * Whether [response] scores as correct for [item].
     *
     * Equality for everything except `M12.PREDICT_TRIAD`, where either directional answer scores as
     * "detected a mismatch" — docs/20-PHASE-2-SPEC.md §8.1 decision 3. The three buttons are on screen
     * from the module's first item so the interaction never changes shape mid-module, but a learner
     * who hears *that* it was wrong and cannot yet say which way is not penalized at the introductory
     * node for a skill that belongs to `M1.HIGH_LOW`.
     *
     * The recorded [correctLabel] stays directional even there. Scoring and recording are different
     * questions: the log has to keep the direction or the confusion data loses the one thing §8.1
     * decision 3 added it for.
     */
    fun isCorrect(
        item: Item,
        response: String,
    ): Boolean {
        val exact = response == correctLabel(item)
        if (item !is Item.PredictionItem || SkillGraph.scoresDirection(item.skill)) return exact
        return AnswerAlphabet.MatchDirection.matchedVsNot(response) ==
            AnswerAlphabet.MatchDirection.matchedVsNot(correctLabel(item))
    }

    /** The label a correct answer records — what `Attempt.targetLabel` stores and answers compare against. */
    fun correctLabel(item: Item): String =
        when (item) {
            is Item.FunctionalRecognitionItem -> item.targetDegree.canonicalLabel
            is Item.ModeIdentificationItem -> item.correctLabel
            is Item.PredictionItem -> item.correctLabel
            // On a production item this is the constant "TAPPED" - see RhythmQuestion.TapItBack. A
            // tapped answer has no label to be right or wrong about; what is scored is the taps
            // against the pattern, in submitTaps.
            is Item.RhythmItem -> item.correctLabel
            else -> unsupported(item)
        }

    /** The full item audio, rendered to one contiguous buffer before playback (docs/06-AUDIO-ENGINE.md §7). */
    fun renderAudio(item: Item): PcmBuffer =
        when (item) {
            is Item.FunctionalRecognitionItem ->
                SynthEngine.renderItem(
                    referencePlan = item.referencePlan,
                    gapAfterReferenceMs = item.timing.gapAfterReferenceMs,
                    targetMidi = item.targetMidi,
                    targetTimbre = item.timbre,
                    targetDurationMs = item.timing.targetDurationMs,
                    seed = item.seed,
                )

            // M9 is reference-only: the whole item *is* the passage being judged, with no target note
            // after it and no gap to leave.
            is Item.ModeIdentificationItem ->
                PcmBuffer(
                    item.elements
                        .mapIndexed { i, element ->
                            SynthEngine.renderElement(element, seed = item.seed + ELEMENT_SEED_STRIDE * i)
                        }.fold(FloatArray(0)) { acc, buffer -> acc + buffer.samples },
                    PcmBuffer.DEFAULT_SAMPLE_RATE,
                )

            // Reference -> a breath -> the silent audiation gap -> one note. The gap is *inside* the
            // rendered buffer rather than being a UI-only pause, because the learner has to hear
            // nothing for exactly as long as the item says: a gap timed by the screen and a note
            // played separately would drift apart under any scheduling hiccup, and the gap length is
            // the PREDICT_GAP axis itself, not decoration.
            is Item.PredictionItem -> {
                val reference = SynthEngine.renderReferencePlan(item.referencePlan, seed = item.seed)
                val silenceMs = item.timing.gapAfterReferenceMs + item.gapBeforeSoundedNoteMs
                val silence = FloatArray(PcmBuffer.msToSamples(silenceMs, reference.sampleRate))
                // By frequency, not MIDI: PREDICT_DEVIATION level 3 is a 30-cent bend of the right
                // note, which an integer MIDI number cannot express.
                val hz =
                    Tuning.offsetByCents(Tuning.midiToHz(item.soundedMidi), item.soundedCentsOffset)
                val sounded =
                    SynthEngine.renderTone(
                        frequencyHz = hz,
                        timbre = item.timbre,
                        durationMs = item.timing.targetDurationMs,
                        sampleRate = reference.sampleRate,
                        seed = item.seed + SOUNDED_NOTE_SEED_OFFSET,
                    )
                PcmBuffer(reference.samples + silence + sounded.samples, reference.sampleRate)
            }

            // Count-in, metronome and pattern mixed onto one timeline rather than laid end to end:
            // here the metronome and the pattern sound *together* and their alignment is the exercise.
            // The count-in offset RhythmRenderer also returns is dropped here, because the loop plays
            // the buffer whole; the tap surface reads it from the item when it needs to know when the
            // pattern proper began (Stage 4.5's screen).
            is Item.RhythmItem -> RhythmRenderer.render(item).buffer
            else -> unsupported(item)
        }

    /**
     * Per-type values for the M2-shaped columns on `Attempt` (docs/05-DATA-MODEL.md §1). They are not
     * all meaningful for every skill, and where one is not, the value recorded is documented here
     * rather than invented at the call site.
     */
    fun keyPitchClass(item: Item): Int =
        when (item) {
            is Item.FunctionalRecognitionItem -> item.key.value
            is Item.ModeIdentificationItem -> item.key.value
            is Item.PredictionItem -> item.key.value
            // Rhythm has no key at all. `M3` shares no node with the pitch track and a learner can
            // start it having never touched `M2` (docs/40-PHASE-4-SPEC.md §2), so 0 here means "not
            // applicable" rather than "the key of C" - the same reading `cadenceFadeLevel` records for
            // a module without that axis.
            is Item.RhythmItem -> 0
            else -> unsupported(item)
        }

    /**
     * The pitch the item is *about*. `M9` has no single target note — the answer is a property of the
     * whole passage — so it records its tonic, which is the reference every other pitch in the item was
     * built from and the only honest single-pitch summary available.
     */
    fun targetMidi(item: Item): Int =
        when (item) {
            is Item.FunctionalRecognitionItem -> item.targetMidi
            is Item.ModeIdentificationItem -> item.tonicMidi
            is Item.PredictionItem -> item.soundedMidi
            // No pitch is being asked about, and none is inventable: a rhythm item's sounds are a
            // percussive timbre with no note to name.
            is Item.RhythmItem -> 0
            else -> unsupported(item)
        }

    /**
     * Denormalized for cheap querying (docs/05-DATA-MODEL.md §1). `M9` and `M12` do not use the
     * cadence-fade axis at all, so they record 0, meaning "not applicable" rather than "level zero" —
     * a distinction that matters only to a query filtering on this column, which should filter by
     * `skillId` first anyway.
     */
    fun cadenceFadeLevel(
        item: Item,
        axisLevels: Map<DifficultyAxis, Int>,
    ): Int =
        when (item) {
            is Item.FunctionalRecognitionItem -> axisLevels[DifficultyAxis.CADENCE_FADE] ?: 0
            // Rhythm's counterpart is METRONOME_FADE, which is denormalized nowhere: this column is
            // named for the pitch track's axis and repurposing it would make one integer mean two
            // different fades depending on the row's module.
            is Item.ModeIdentificationItem, is Item.PredictionItem, is Item.RhythmItem -> 0
            else -> unsupported(item)
        }

    fun timbreId(item: Item): String =
        when (item) {
            is Item.FunctionalRecognitionItem -> item.timbre.name
            is Item.ModeIdentificationItem -> item.timbre.name
            is Item.PredictionItem -> item.timbre.name
            is Item.RhythmItem -> item.timbre.name
            else -> unsupported(item)
        }

    /**
     * Generates the next item for [skill]. The loop does not know which generator serves which module
     * and should not: that mapping lives here, beside the other per-type knowledge.
     */
    fun generate(
        skill: SkillId,
        axisLevels: Map<DifficultyAxis, Int>,
        seed: Long,
        history: GenerationHistory,
    ): Generated =
        if (skill in SkillIds.M3_NODES_IN_ORDER) {
            // No GenerationHistory. History exists to stop the pitch track repeating a degree or a key
            // (docs/03-CURRICULUM.md §4); a rhythm item's variety comes from which beats subdivide,
            // which the seed already varies, and threading an unused history through would suggest a
            // constraint that is not being enforced.
            Generated(M3ItemGenerator.generate(skill, axisLevels, seed), history)
        } else if (skill in SkillIds.M12_NODES_IN_ORDER) {
            val result = M12ItemGenerator.generate(skill, axisLevels, seed, history)
            Generated(result.item, result.updatedHistory)
        } else if (skill in SkillIds.M9_NODES_IN_ORDER) {
            // M9 has no difficulty axes of its own - its three nodes *are* its progression, each a
            // separate skill rather than a level on a shared axis (docs/20-PHASE-2-SPEC.md §3).
            val result = M9ItemGenerator.generate(skill, seed, history)
            Generated(result.item, result.updatedHistory)
        } else {
            val result = M2ItemGenerator.generate(skill, axisLevels, seed, history)
            Generated(result.item, result.updatedHistory)
        }

    data class Generated(
        val item: Item,
        val updatedHistory: GenerationHistory,
    )

    private fun unsupported(item: Item): Nothing =
        error(
            "${item::class.simpleName} is a diagnostic item type and does not run in the practice loop - " +
                ":feature:diagnostic has its own loop for M0/M1",
        )

    /** Matches [SynthEngine.renderReferencePlan]'s own per-element seed spacing, so renders line up. */
    private const val ELEMENT_SEED_STRIDE = 1_000L

    /** Mirrors [SynthEngine.renderItem]'s own target-note seed offset, so a prediction note and a recognition target of the same seed render identically. */
    private const val SOUNDED_NOTE_SEED_OFFSET = 999_999L
}
