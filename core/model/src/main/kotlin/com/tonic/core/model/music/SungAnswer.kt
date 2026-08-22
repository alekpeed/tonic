package com.tonic.core.model.music

/**
 * The outcome of interpreting a sung response — docs/30-PHASE-3-SPEC.md §5.2.
 *
 * The whole point of this type is that it has **three** shapes rather than two. Every other answer path
 * in this app is correct-or-incorrect, because a tap is unambiguous: the learner pressed a button or
 * they did not. A sung answer can also simply fail to be an answer — a cough, a mumble, a room with a
 * fan in it — and §5.2 is explicit that this third case must never collapse into "wrong". A recorded
 * incorrect attempt feeds the staircase, the confusion matrix, and the mastery window, so a false
 * "wrong" does not just annoy the learner, it corrupts the three structures every adaptive decision in
 * the app is computed from. [Unclear] exists so the caller has somewhere to put that case other than
 * scoring it.
 */
public sealed interface SungAnswer {
    /**
     * A degree was identified. Score this exactly as the same tapped degree would be scored — §5.2
     * step 7, and §2's requirement that sung and tapped attempts share one `SkillState`.
     *
     * @property degree the answer, resolved to the nearest member of the active alphabet.
     * @property centsFromDegree signed distance from that degree's true pitch, positive being sharp.
     *   This is docs/30-PHASE-3-SPEC.md §7's `sungCents`: **display and analysis only.** §3 mitigation 4
     *   and §6.4 both require it never to enter scoring — it is shown to the learner as neutral
     *   information ("about a quarter-tone flat") and must never be read by `Staircase`,
     *   `AxisScheduler`, `MasteryEvaluator`, or `ConfusionTracker`.
     * @property frequencyHz the stable estimate this was resolved from.
     */
    public data class Resolved(
        val degree: ScaleDegree,
        val centsFromDegree: Double,
        val frequencyHz: Double,
    ) : SungAnswer

    /**
     * No answer could be read. The caller re-prompts; it does not score.
     *
     * This is deliberately not an error type. Nothing went wrong — the learner sang something the
     * detector could not commit to, which is an ordinary event in a room with a refrigerator in it.
     */
    public data class Unclear(
        val reason: UnclearReason,
    ) : SungAnswer
}

/**
 * Why a sung response could not be read. Recorded so that a learner who is repeatedly unclear for one
 * specific reason can eventually be helped for that reason — a room too noisy to ever produce a voiced
 * frame is a different problem from consistently landing between two degrees, and the fix differs.
 */
public enum class UnclearReason {
    /** No frame reached the confidence threshold — silence, noise, or an unvoiced sound. */
    NO_VOICED_SIGNAL,

    /** Voiced, but too brief to yield a stable sustained estimate once the onset is discarded. */
    TOO_SHORT,

    /**
     * The pitch landed near-equidistant between two degrees of the active alphabet.
     *
     * Resolved by re-prompting rather than by asking the learner to choose between the two candidates.
     * Offering the pair would convert a recall task into a recognition task at precisely the moment the
     * learner was least certain — handing back a shortlist containing the answer is a much easier
     * question than the one the exercise asked, and it is easiest exactly when they knew least. §5.2
     * rules out the third option, silent rounding, as "the single most likely way this feature produces
     * bad data."
     */
    AMBIGUOUS_BETWEEN_DEGREES,
}
