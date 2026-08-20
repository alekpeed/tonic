package com.tonic.feature.diagnostic.engine

import com.tonic.core.audio.synth.PcmBuffer
import com.tonic.core.audio.synth.SynthEngine
import com.tonic.core.model.items.Item
import com.tonic.core.model.music.Tuning

/**
 * Renders M0 items to PCM. Unlike `:feature:practice`'s `renderItemAudio`, there's no
 * `ReferencePlan`-shaped structure to drive playback here (docs/03-CURRICULUM.md §3's four sub-tests
 * are each just one or two short tones/phrases), so this composes [SynthEngine.renderTone] /
 * [SynthEngine.concatBuffers] directly per item type.
 */
object M0AudioRenderer {
    private const val INTER_TONE_GAP_MS = 300L

    fun render(item: Item): PcmBuffer =
        when (item) {
            is Item.PitchDirectionItem -> renderPitchDirection(item)
            is Item.SameDifferentItem -> renderSameDifferent(item)
            is Item.TonalMemoryItem -> renderTonalMemory(item)
            is Item.AmusiaScreenItem -> renderAmusiaScreen(item)
            else -> error("M0AudioRenderer only renders M0 item types, got ${item::class.simpleName}")
        }

    private fun renderPitchDirection(item: Item.PitchDirectionItem): PcmBuffer {
        val first = SynthEngine.renderNote(item.firstMidi, item.timbre, item.timing.targetDurationMs, seed = item.seed)
        val secondHz = Tuning.offsetByCents(Tuning.midiToHz(item.firstMidi), item.secondCentsOffset)
        val second = SynthEngine.renderTone(secondHz, item.timbre, item.timing.targetDurationMs, seed = item.seed + 1)
        return SynthEngine.concatBuffers(listOf(first, gap(), second))
    }

    private fun renderSameDifferent(item: Item.SameDifferentItem): PcmBuffer {
        val first = SynthEngine.renderNote(item.firstMidi, item.timbre, item.timing.targetDurationMs, seed = item.seed)
        val secondHz = Tuning.offsetByCents(Tuning.midiToHz(item.firstMidi), item.centsOffset)
        val second = SynthEngine.renderTone(secondHz, item.timbre, item.timing.targetDurationMs, seed = item.seed + 1)
        return SynthEngine.concatBuffers(listOf(first, gap(), second))
    }

    /** The sequence, a gap, then the replay - with [Item.TonalMemoryItem.alteredIndex]'s note (if any) shifted by [Item.TonalMemoryItem.alterationCents]. */
    private fun renderTonalMemory(item: Item.TonalMemoryItem): PcmBuffer {
        val original = renderSequence(item.sequenceMidi, item.timbre, item.timing.targetDurationMs, item.seed)
        val replay =
            renderSequence(
                item.sequenceMidi,
                item.timbre,
                item.timing.targetDurationMs,
                item.seed + 1000L,
                alteredIndex = item.alteredIndex,
                alterationCents = item.alterationCents,
            )
        return SynthEngine.concatBuffers(listOf(original, gap(), replay))
    }

    private fun renderAmusiaScreen(item: Item.AmusiaScreenItem): PcmBuffer =
        renderSequence(item.phraseMidi, item.timbre, item.timing.targetDurationMs, item.seed)

    private fun renderSequence(
        sequenceMidi: List<Int>,
        timbre: com.tonic.core.model.music.TimbreId,
        noteDurationMs: Long,
        seed: Long,
        alteredIndex: Int? = null,
        alterationCents: Double = 0.0,
    ): PcmBuffer {
        val notes =
            sequenceMidi.mapIndexed { i, midi ->
                if (i == alteredIndex) {
                    val hz = Tuning.offsetByCents(Tuning.midiToHz(midi), alterationCents)
                    SynthEngine.renderTone(hz, timbre, noteDurationMs, seed = seed + i)
                } else {
                    SynthEngine.renderNote(midi, timbre, noteDurationMs, seed = seed + i)
                }
            }
        return SynthEngine.concatBuffers(notes)
    }

    private fun gap(): PcmBuffer = PcmBuffer.silence(PcmBuffer.msToSamples(INTER_TONE_GAP_MS))
}
