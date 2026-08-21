package com.tonic.core.audio.pitch

/**
 * One frame's pitch reading: what was heard, and how much the detector trusts it.
 *
 * [clarity] exists because docs/30-PHASE-3-SPEC.md §5.2 draws a distinction the rest of this app has
 * never needed — the difference between a *wrong* answer and an *unclear* one. Step 2 of that pipeline
 * discards frames below a confidence threshold and step 3 rejects the whole attempt as unclear when too
 * few survive, and §5.2 is explicit that "unclear" must never score as wrong: a mumble, a cough, or
 * silence produces a retry prompt, never a recorded incorrect attempt. A detector that returned only a
 * frequency would force the caller to treat a confident A3 and a coin-flip reading of room noise
 * identically, and a false "wrong" corrupts the staircase and the confusion matrix — the two structures
 * every adaptive decision in the app is computed from.
 *
 * @property frequencyHz the detected fundamental, in Hz. Sub-bin accurate — see [PitchDetector].
 * @property clarity the NSDF peak height, in `0.0..1.0`. 1.0 is a perfectly periodic signal; sung vowels
 *   in a quiet room typically read well above 0.9, and unvoiced noise falls away sharply. It is a
 *   periodicity measure, not a loudness measure: a quiet but steady hum scores high, and a loud
 *   consonant scores low. That is the right sense for this use, since the thing being asked of the
 *   learner is a sustained pitch.
 */
public data class PitchEstimate(
    val frequencyHz: Double,
    val clarity: Double,
)
