package com.tonic.core.model.music

/**
 * The four synthesized timbre families, docs/06-AUDIO-ENGINE.md §3. Every
 * skill trains across all four from item one — docs/02-PEDAGOGY.md §5 —
 * because a skill trained on a single timbre becomes "recognizing that
 * timbre," not hearing pitch function.
 */
enum class TimbreId {
    /** Single sine + slight ADSR. Neutral reference; hardest for some listeners. */
    PURE,

    /** Additive: fundamental + 2nd + 3rd partial, decaying. Organ-like, forgiving. */
    SOFT,

    /** Karplus-Strong, short decay. Guitar/harp-like, strong transient. */
    PLUCK,

    /** Additive, odd-harmonic emphasis + slight vibrato after onset. Sustained wind/voice-like. */
    REED,
    ;

    companion object {
        val ALL_PHASE_1 = entries.toSet()
    }
}
