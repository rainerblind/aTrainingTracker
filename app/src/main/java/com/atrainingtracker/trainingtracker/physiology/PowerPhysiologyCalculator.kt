package com.atrainingtracker.trainingtracker.physiology

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Result of estimating Critical Power (CP) and W' (Anaerobic Work Capacity)
 * from two maximal power-duration efforts using the linear work-time model:
 * $W_i = P_i \cdot t_i = CP \cdot t_i + W'$.
 *
 * @property cpWatts Estimated Critical Power in Watts ($> 0$).
 * @property wPrimeJoules Estimated W' in Joules ($> 0$).
 * @property wPrimeKj Estimated W' in kilojoules ($W'_{\text{J}} / 1000.0$).
 */
data class CpWPrimeEstimate(
    val cpWatts: Double,
    val wPrimeJoules: Double,
    val wPrimeKj: Double
) {
    /** Rounded integer Critical Power in Watts for UI display and storage. */
    val cpWattsRounded: Int
        get() = cpWatts.roundToInt()
}

/**
 * Summary statistics and 1-Hz series for Skiba's $W'_{\text{bal}}$ (Anaerobic Battery)
 * over a workout power stream (`REQ-UI-399.2`, `REQ-UI-399.3`).
 *
 * @property seriesKj Per-sample remaining $W'_{\text{bal}}$ in kilojoules (`null` when no valid power has been observed yet).
 * @property minWPrimeBalKj Minimum remaining $W'_{\text{bal}}$ reached during the workout (in kJ).
 * @property avgWPrimeBalKj Average remaining $W'_{\text{bal}}$ across active samples (in kJ).
 * @property endWPrimeBalKj Final remaining $W'_{\text{bal}}$ at the end of the workout (in kJ).
 * @property minWPrimeBalPct Minimum remaining $W'_{\text{bal}}$ expressed as a percentage of total $W'$ ($0.0\text{–}100.0\%$).
 * @property isEmptyTankWarning `true` when [minWPrimeBalPct] drops strictly below [PowerPhysiologyCalculator.EMPTY_TANK_WARNING_THRESHOLD_PCT] ($10.0\%$).
 */
data class WPrimeSummaryResult(
    val seriesKj: List<Float?>,
    val minWPrimeBalKj: Double?,
    val avgWPrimeBalKj: Double?,
    val endWPrimeBalKj: Double?,
    val minWPrimeBalPct: Double?,
    val isEmptyTankWarning: Boolean
)

/**
 * Canonical, stateless, pure-Kotlin domain calculator for cycling & running power/cardiovascular
 * physiology metrics (`REQ-UI-399`, Rule 36 — Single Source of Truth & DRY).
 *
 * Threading: Pure mathematical functions with zero mutable state or Android framework dependencies;
 * safe to invoke from any thread or coroutine dispatcher.
 */
object PowerPhysiologyCalculator {

    /** Default Anaerobic Work Capacity ($W'$) in kilojoules when unconfigured by the athlete. */
    const val DEFAULT_W_PRIME_KJ: Float = 20.0f

    /** Default Anaerobic Work Capacity ($W'$) in Joules when unconfigured by the athlete. */
    const val DEFAULT_W_PRIME_JOULES: Double = 20_000.0

    /** Threshold percentage ($10.0\%$) below which an "Empty Tank" anaerobic depletion warning is raised. */
    const val EMPTY_TANK_WARNING_THRESHOLD_PCT: Double = 10.0

    /**
     * Solves the two-point linear work-time model ($W = CP \cdot t + W'$) given two maximal
     * mean-maximal-power (MMP) efforts $(P_1, t_1)$ and $(P_2, t_2)$ (`REQ-UI-399.1`).
     *
     * Formulas:
     * - $W_1 = P_1 \cdot t_1$, $W_2 = P_2 \cdot t_2$
     * - $CP = \frac{W_2 - W_1}{t_2 - t_1}$
     * - $W'_{\text{J}} = W_1 - CP \cdot t_1$
     *
     * @param p1Watts Average power of the shorter effort in Watts.
     * @param t1Seconds Duration of the shorter effort in seconds.
     * @param p2Watts Average power of the longer effort in Watts.
     * @param t2Seconds Duration of the longer effort in seconds.
     * @return [CpWPrimeEstimate] if the inputs define a physiologically valid model ($t_2 > t_1 > 0$,
     *         $P_1 > P_2 > 0$, $CP \in [50, 1000]\text{ W}$, $W' \in [1.0, 100.0]\text{ kJ}$), or `null` otherwise.
     */
    @JvmStatic
    fun estimateCpAndWPrime(
        p1Watts: Double,
        t1Seconds: Int,
        p2Watts: Double,
        t2Seconds: Int
    ): CpWPrimeEstimate? {
        if (!p1Watts.isFinite() || !p2Watts.isFinite() || t1Seconds <= 0 || t2Seconds <= 0 || t1Seconds == t2Seconds) {
            return null
        }
        // Normalize so point 1 is the shorter effort and point 2 is the longer effort.
        val shortP: Double
        val shortT: Int
        val longP: Double
        val longT: Int
        if (t1Seconds < t2Seconds) {
            shortP = p1Watts
            shortT = t1Seconds
            longP = p2Watts
            longT = t2Seconds
        } else {
            shortP = p2Watts
            shortT = t2Seconds
            longP = p1Watts
            longT = t1Seconds
        }

        if (shortP <= 0.0 || longP <= 0.0 || shortP <= longP) {
            return null
        }

        val w1Joules = shortP * shortT.toDouble()
        val w2Joules = longP * longT.toDouble()
        val deltaT = (longT - shortT).toDouble()
        val cpWatts = (w2Joules - w1Joules) / deltaT
        val wPrimeJoules = w1Joules - cpWatts * shortT.toDouble()
        val wPrimeKj = wPrimeJoules / 1000.0

        if (cpWatts < 50.0 || cpWatts > 1000.0 || wPrimeKj < 1.0 || wPrimeKj > 100.0) {
            return null
        }
        return CpWPrimeEstimate(
            cpWatts = cpWatts,
            wPrimeJoules = wPrimeJoules,
            wPrimeKj = wPrimeKj
        )
    }

    /**
     * Computes Skiba's recovery time constant $\tau_{W'}$ (in seconds) during sub-CP recovery ($P \le CP$):
     * $$\tau_{W'} = 546 \cdot e^{-0.01 \cdot (CP - P)} + 316$$
     */
    @JvmStatic
    fun calculateSkibaTauSeconds(powerWatts: Double, cpWatts: Double): Double {
        val dcp = max(0.0, cpWatts - max(0.0, powerWatts))
        return 546.0 * exp(-0.01 * dcp) + 316.0
    }

    /**
     * Advances Skiba's differential $W'_{\text{bal}}$ state by `deltaTimeSec` seconds (`REQ-UI-399.2`).
     *
     * - When $P(t) > CP$ (Depletion):
     *   $$W'_{\text{bal}}(t) = \max\left(0.0,\; W'_{\text{bal}}(t - \Delta t) - (P(t) - CP) \cdot \Delta t\right)$$
     * - When $P(t) \le CP$ (Exponential Reconstitution):
     *   $$\tau_{W'} = 546 \cdot e^{-0.01 \cdot (CP - P(t))} + 316$$
     *   $$W'_{\text{bal}}(t) = W' - \left(W' - W'_{\text{bal}}(t - \Delta t)\right) \cdot e^{-\Delta t / \tau_{W'}}$$
     *
     * @param prevWPrimeBalJoules Previous remaining $W'_{\text{bal}}$ in Joules.
     * @param powerWatts Instantaneous power output in Watts ($\ge 0$).
     * @param cpWatts Critical Power in Watts ($> 0$).
     * @param wPrimeJoules Total $W'$ capacity in Joules ($> 0$).
     * @param deltaTimeSec Step duration in seconds (default `1.0`).
     * @return Updated $W'_{\text{bal}}$ in Joules, clamped to $[0.0, W'_{\text{Joules}}]$.
     */
    @JvmStatic
    @JvmOverloads
    fun stepWPrimeBalanceJoules(
        prevWPrimeBalJoules: Double,
        powerWatts: Double,
        cpWatts: Double,
        wPrimeJoules: Double,
        deltaTimeSec: Double = 1.0
    ): Double {
        if (wPrimeJoules <= 0.0 || cpWatts <= 0.0 || !prevWPrimeBalJoules.isFinite()) {
            return max(0.0, wPrimeJoules)
        }
        val clampedPrev = prevWPrimeBalJoules.coerceIn(0.0, wPrimeJoules)
        if (!powerWatts.isFinite() || deltaTimeSec <= 0.0) {
            return clampedPrev
        }
        val safePower = max(0.0, powerWatts)
        return if (safePower > cpWatts) {
            val expendedJoules = (safePower - cpWatts) * deltaTimeSec
            max(0.0, clampedPrev - expendedJoules)
        } else {
            val tau = calculateSkibaTauSeconds(safePower, cpWatts)
            val recharged = wPrimeJoules - (wPrimeJoules - clampedPrev) * exp(-deltaTimeSec / tau)
            recharged.coerceIn(0.0, wPrimeJoules)
        }
    }

    /**
     * Converts remaining $W'_{\text{bal}}$ (in Joules or kJ) and total $W'$ (in same unit) to percentage $[0.0, 100.0]$.
     */
    @JvmStatic
    fun calculateWPrimeBalancePct(wPrimeBal: Double, wPrimeTotal: Double): Double? {
        if (!wPrimeBal.isFinite() || !wPrimeTotal.isFinite() || wPrimeTotal <= 0.0) return null
        return ((wPrimeBal / wPrimeTotal) * 100.0).coerceIn(0.0, 100.0)
    }

    /**
     * Evaluates a complete workout power series and returns the per-sample $W'_{\text{bal}}$ (kJ)
     * curve and summary extrema (`min`, `avg`, `end`, `minPct`, `isEmptyTankWarning`) (`REQ-UI-399.3`).
     */
    @JvmStatic
    @JvmOverloads
    fun computeWPrimeBalanceSeries(
        powerSamplesWatts: List<Double?>,
        cpWatts: Double,
        wPrimeKj: Double,
        sampleIntervalSec: Double = 1.0
    ): WPrimeSummaryResult {
        val wPrimeJoules = wPrimeKj * 1000.0
        if (powerSamplesWatts.isEmpty() || cpWatts <= 0.0 || wPrimeJoules <= 0.0) {
            return WPrimeSummaryResult(
                seriesKj = List(powerSamplesWatts.size) { null },
                minWPrimeBalKj = null,
                avgWPrimeBalKj = null,
                endWPrimeBalKj = null,
                minWPrimeBalPct = null,
                isEmptyTankWarning = false
            )
        }

        var currentJoules = wPrimeJoules
        var hasValidPower = false
        var minKj = Double.POSITIVE_INFINITY
        var sumKj = 0.0
        var validCount = 0
        var lastKj: Double? = null
        val series = ArrayList<Float?>(powerSamplesWatts.size)

        for (rawPower in powerSamplesWatts) {
            if (rawPower != null && rawPower.isFinite() && rawPower >= 0.0) {
                hasValidPower = true
                currentJoules = stepWPrimeBalanceJoules(
                    prevWPrimeBalJoules = currentJoules,
                    powerWatts = rawPower,
                    cpWatts = cpWatts,
                    wPrimeJoules = wPrimeJoules,
                    deltaTimeSec = sampleIntervalSec
                )
                val currentKj = currentJoules / 1000.0
                if (currentKj < minKj) minKj = currentKj
                sumKj += currentKj
                validCount++
                lastKj = currentKj
                series.add(currentKj.toFloat())
            } else if (hasValidPower) {
                // During missing samples after workout start, recharge at 0W.
                currentJoules = stepWPrimeBalanceJoules(
                    prevWPrimeBalJoules = currentJoules,
                    powerWatts = 0.0,
                    cpWatts = cpWatts,
                    wPrimeJoules = wPrimeJoules,
                    deltaTimeSec = sampleIntervalSec
                )
                val currentKj = currentJoules / 1000.0
                if (currentKj < minKj) minKj = currentKj
                sumKj += currentKj
                validCount++
                lastKj = currentKj
                series.add(currentKj.toFloat())
            } else {
                series.add(null)
            }
        }

        if (!hasValidPower || validCount == 0 || !minKj.isFinite()) {
            return WPrimeSummaryResult(
                seriesKj = series,
                minWPrimeBalKj = null,
                avgWPrimeBalKj = null,
                endWPrimeBalKj = null,
                minWPrimeBalPct = null,
                isEmptyTankWarning = false
            )
        }

        val avgKj = sumKj / validCount.toDouble()
        val minPct = calculateWPrimeBalancePct(minKj, wPrimeKj)
        val isEmptyTank = minPct != null && minPct < EMPTY_TANK_WARNING_THRESHOLD_PCT

        return WPrimeSummaryResult(
            seriesKj = series,
            minWPrimeBalKj = minKj,
            avgWPrimeBalKj = avgKj,
            endWPrimeBalKj = lastKj,
            minWPrimeBalPct = minPct,
            isEmptyTankWarning = isEmptyTank
        )
    }
}
