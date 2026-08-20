package com.tonic.core.engine.staircase

import kotlin.math.ln
import kotlin.math.sqrt

/**
 * d-prime with the log-linear extreme-rate correction — docs/07-ADAPTIVE-ENGINE.md
 * §2. Used for `M0.SAME_DIFF` and `M0.AMUSIA_SCREEN`, which have a
 * yes/no response format where raw accuracy is misleading: a responder who
 * answers "different" indiscriminately would otherwise look sensitive.
 */
object DPrime {
    /**
     * `hitRate = (hits + 0.5) / (signalTrials + 1)`,
     * `faRate = (falseAlarms + 0.5) / (noiseTrials + 1)`,
     * `dPrime = z(hitRate) - z(faRate)`. The +0.5/+1 correction keeps both
     * rates strictly inside (0, 1) so [InverseNormalCdf] never sees 0 or 1,
     * which is where it would otherwise blow up to +/-infinity.
     */
    fun compute(
        hits: Int,
        signalTrials: Int,
        falseAlarms: Int,
        noiseTrials: Int,
    ): Double {
        require(signalTrials >= 0 && noiseTrials >= 0) { "trial counts must be non-negative" }
        val hitRate = (hits + 0.5) / (signalTrials + 1)
        val faRate = (falseAlarms + 0.5) / (noiseTrials + 1)
        return InverseNormalCdf.z(hitRate) - InverseNormalCdf.z(faRate)
    }
}

/**
 * The inverse standard-normal CDF, implemented directly per
 * docs/07-ADAPTIVE-ENGINE.md §2: "do not add a statistics dependency for
 * one function." Peter Acklam's rational approximation — a standard,
 * widely published algorithm, accurate to within about 1.15e-9 relative
 * error, which is far tighter than [DPrime] needs for behavioral data.
 */
object InverseNormalCdf {
    private const val A1 = -3.969683028665376e+01
    private const val A2 = 2.209460984245205e+02
    private const val A3 = -2.759285104469687e+02
    private const val A4 = 1.383577518672690e+02
    private const val A5 = -3.066479806614716e+01
    private const val A6 = 2.506628277459239e+00

    private const val B1 = -5.447609879822406e+01
    private const val B2 = 1.615858368580409e+02
    private const val B3 = -1.556989798598866e+02
    private const val B4 = 6.680131188771972e+01
    private const val B5 = -1.328068155288572e+01

    private const val C1 = -7.784894002430293e-03
    private const val C2 = -3.223964580411365e-01
    private const val C3 = -2.400758277161838e+00
    private const val C4 = -2.549732539343734e+00
    private const val C5 = 4.374664141464968e+00
    private const val C6 = 2.938163982698783e+00

    private const val D1 = 7.784695709041462e-03
    private const val D2 = 3.224671290700398e-01
    private const val D3 = 2.445134137142996e+00
    private const val D4 = 3.754408661907416e+00

    private const val P_LOW = 0.02425
    private const val P_HIGH = 1.0 - P_LOW

    fun z(p: Double): Double {
        require(p > 0.0 && p < 1.0) { "p must be strictly between 0 and 1, was $p" }
        return when {
            p < P_LOW -> {
                val q = sqrt(-2.0 * ln(p))
                (((((C1 * q + C2) * q + C3) * q + C4) * q + C5) * q + C6) /
                    ((((D1 * q + D2) * q + D3) * q + D4) * q + 1.0)
            }
            p <= P_HIGH -> {
                val q = p - 0.5
                val r = q * q
                (((((A1 * r + A2) * r + A3) * r + A4) * r + A5) * r + A6) * q /
                    (((((B1 * r + B2) * r + B3) * r + B4) * r + B5) * r + 1.0)
            }
            else -> {
                val q = sqrt(-2.0 * ln(1.0 - p))
                -(((((C1 * q + C2) * q + C3) * q + C4) * q + C5) * q + C6) /
                    ((((D1 * q + D2) * q + D3) * q + D4) * q + 1.0)
            }
        }
    }
}
