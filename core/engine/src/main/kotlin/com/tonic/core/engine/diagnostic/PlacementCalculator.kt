package com.tonic.core.engine.diagnostic

import com.tonic.core.model.items.DifficultyAxis
import com.tonic.core.model.state.EntryPoint

/**
 * Turns the M0 diagnostic's four raw measurements into a placement decision — docs/03-CURRICULUM.md
 * §3's output contract and placement rules, and the axis-start table in docs/07-ADAPTIVE-ENGINE.md §5.
 * Pure and pedagogical, so it lives here rather than in `:feature:diagnostic`'s ViewModel
 * (docs/04-ARCHITECTURE.md §3: "if a ViewModel is deciding difficulty, that logic belongs in
 * `:core:engine`").
 */
object PlacementCalculator {
    /** docs/03-CURRICULUM.md §3: "> 200" routes to remediation regardless of every other measurement. */
    private const val REMEDIATION_CENTS_THRESHOLD = 200

    /**
     * The `d-prime` cut below which the amusia screen counts as one of its two required weak signals -
     * docs/03-CURRICULUM.md §3: "flag if d-prime falls below the configured cut and the pitch-direction
     * threshold is also elevated (both conditions, not either)." The spec deliberately doesn't name the
     * cut itself (unlike the 200-cent pitch-direction threshold, given explicitly) - 1.0 is this build's
     * own choice, a commonly used dividing line in signal-detection-theory screening between "typical"
     * and "poor" sensitivity, not a value derived from Tonic's own data (none exists yet). Needs
     * calibration against a real listener population before shipping, same as docs/03-CURRICULUM.md's
     * other build-time tuning constants (see Stage 3's timbre/register/tempo pools).
     */
    private const val AMUSIA_DPRIME_CUT = 1.0

    data class Placement(
        val amusiaIndicatorFlag: Boolean,
        val recommendedEntry: EntryPoint,
        val initialAxisLevels: Map<DifficultyAxis, Int>,
    )

    /**
     * [amusiaDPrime] is `M0.AMUSIA_SCREEN`'s own d-prime (distinct from [discriminationDPrime], which is
     * `M0.SAME_DIFF`'s) - docs/03-CURRICULUM.md §3 scores each subtest independently; only the amusia
     * screen's own sensitivity feeds the flag.
     */
    fun compute(
        pitchDirectionThresholdCents: Int,
        discriminationDPrime: Double,
        tonalMemorySpan: Int,
        amusiaDPrime: Double,
    ): Placement {
        val pitchDirectionElevated = pitchDirectionThresholdCents > REMEDIATION_CENTS_THRESHOLD
        val amusiaIndicatorFlag = amusiaDPrime < AMUSIA_DPRIME_CUT && pitchDirectionElevated

        if (amusiaIndicatorFlag || pitchDirectionElevated) {
            return Placement(amusiaIndicatorFlag, EntryPoint.M1_REMEDIATION, emptyMap())
        }

        val axisLevels = axisStartLevels(pitchDirectionThresholdCents, tonalMemorySpan)
        return Placement(amusiaIndicatorFlag, EntryPoint.M2_STAGE_1, axisLevels)
    }

    /** docs/07-ADAPTIVE-ENGINE.md §5's table exactly. Only reached once [pitchDirectionThresholdCents] is confirmed <= 200 by [compute]. */
    private fun axisStartLevels(
        pitchDirectionThresholdCents: Int,
        tonalMemorySpan: Int,
    ): Map<DifficultyAxis, Int> {
        val (cadenceFade, timbreVariety, keySpread) =
            when {
                pitchDirectionThresholdCents < 20 -> Triple(1, 2, 1)
                pitchDirectionThresholdCents < 50 -> Triple(1, 1, 1)
                pitchDirectionThresholdCents < 100 -> Triple(0, 1, 0)
                else -> Triple(0, 0, 0) // 100..200
            }
        val tempoDensity = if (tonalMemorySpan >= 4) 1 else 0

        return DifficultyAxis.entries.associateWith { axis ->
            when (axis) {
                DifficultyAxis.CADENCE_FADE -> cadenceFade
                DifficultyAxis.TIMBRE_VARIETY -> timbreVariety
                DifficultyAxis.KEY_SPREAD -> keySpread
                DifficultyAxis.TEMPO_DENSITY -> tempoDensity
                DifficultyAxis.REGISTER_SPREAD, DifficultyAxis.OCTAVE_DISPLACE -> 0
            }
        }
    }
}
