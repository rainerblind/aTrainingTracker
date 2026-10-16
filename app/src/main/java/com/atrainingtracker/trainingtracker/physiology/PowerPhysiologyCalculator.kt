package com.atrainingtracker.trainingtracker.physiology

import com.atrainingtracker.banalservice.BSportType
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
 * Result of evaluating Efficiency Factor ($\text{EF}$) and Aerobic Decoupling ($\text{Pw:Hr }\%$ or $\text{Pa:Hr }\%$)
 * over a paired workout telemetry stream (`REQ-UI-400.1`).
 *
 * @property efficiencyFactor Full-session Efficiency Factor ($\text{NP} / \text{Avg\_HR}$ in $\text{W/bpm}$ for cycling,
 *           or $(\text{Avg\_Speed}_{\text{m/s}} \times 60.0) / \text{Avg\_HR}$ in $\text{m/min/bpm}$ for running).
 * @property decouplingPct Percentage aerobic decoupling $\frac{\text{EF}_1 - \text{EF}_2}{\text{EF}_1} \times 100.0$.
 * @property efFirstHalf First-half Efficiency Factor ($\text{EF}_1$).
 * @property efSecondHalf Second-half Efficiency Factor ($\text{EF}_2$).
 * @property isPowerBased `true` when evaluated from Power + HR (`Pw:Hr`), `false` when evaluated from Speed + HR (`Pa:Hr`).
 * @property isHighDrift `true` when [decouplingPct] $\ge$ [PowerPhysiologyCalculator.AEROBIC_DECOUPLING_DRIFT_THRESHOLD_PCT] ($5.0\%$).
 */
data class AerobicEfficiencyResult(
    val efficiencyFactor: Double?,
    val decouplingPct: Double?,
    val efFirstHalf: Double?,
    val efSecondHalf: Double?,
    val isPowerBased: Boolean,
    val isHighDrift: Boolean
)

/**
 * Canonical, stateless, pure-Kotlin domain calculator for cycling & running power/cardiovascular
 * physiology metrics (`REQ-UI-399`, `REQ-UI-400`, Rule 36 — Single Source of Truth & DRY).
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

    /** Threshold percentage ($5.0\%$) at or above which Aerobic Decoupling indicates significant cardiovascular drift (`REQ-UI-400.1`). */
    const val AEROBIC_DECOUPLING_DRIFT_THRESHOLD_PCT: Double = 5.0

    /** Rolling window size in seconds ($30\text{ s}$) for Dr. Andrew Coggan's Normalized Power (NP) algorithm. */
    const val NP_ROLLING_WINDOW_SECONDS: Int = 30

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

    /**
     * Computes Dr. Andrew Coggan's Normalized Power (NP) over a 1-Hz power sample stream (`REQ-UI-400.1`, Rule 40):
     * - Maintains a 30-second rolling average window (using the growing $1..29\text{ s}$ warm-up window per Rule 40).
     * - Raises each rolling average $R_k$ to the 4th power ($R_k^4$).
     * - Returns the 4th root of the mean of $R_k^4$: $\text{NP} = \left(\frac{1}{N} \sum_{k=1}^{N} R_k^4\right)^{1/4}$.
     *
     * @param powerSamplesWatts 1-Hz power samples in Watts (`null` or negative samples are ignored).
     * @return Normalized Power in Watts ($> 0$), or `null` if no positive power samples exist.
     */
    @JvmStatic
    fun calculateNormalizedPower(powerSamplesWatts: List<Double?>): Double? {
        val validSamples = powerSamplesWatts.mapNotNull { p ->
            if (p != null && p.isFinite() && p >= 0.0) p else null
        }
        if (validSamples.isEmpty() || validSamples.all { it <= 0.0 }) {
            return null
        }

        val window = ArrayDeque<Double>(NP_ROLLING_WINDOW_SECONDS)
        var windowSum = 0.0
        var sumFourthPowers = 0.0
        var count = 0

        for (p in validSamples) {
            window.addLast(p)
            windowSum += p
            if (window.size > NP_ROLLING_WINDOW_SECONDS) {
                windowSum -= window.removeFirst()
            }
            val rollingAvg = windowSum / window.size.toDouble()
            sumFourthPowers += rollingAvg.pow(4.0)
            count++
        }

        if (count == 0) return null
        val np = (sumFourthPowers / count.toDouble()).pow(0.25)
        return np.takeIf { it.isFinite() && it > 0.0 }
    }

    /**
     * Computes cycling/power Efficiency Factor ($\text{EF}_{\text{pw}}$, in $\text{W/bpm}$) (`REQ-UI-400.1`):
     * $$\text{EF}_{\text{pw}} = \frac{\text{NP}}{\text{Avg\_HR}}$$
     *
     * @param normalizedPowerWatts Normalized Power (or average power fallback) in Watts ($> 0$).
     * @param avgHeartRateBpm Average Heart Rate in bpm ($> 0$).
     * @return Efficiency Factor in $\text{W/bpm}$, or `null` if inputs are non-positive or non-finite.
     */
    @JvmStatic
    fun calculateEfficiencyFactorPower(normalizedPowerWatts: Double, avgHeartRateBpm: Double): Double? {
        if (!normalizedPowerWatts.isFinite() || !avgHeartRateBpm.isFinite() ||
            normalizedPowerWatts <= 0.0 || avgHeartRateBpm <= 0.0
        ) {
            return null
        }
        val ef = normalizedPowerWatts / avgHeartRateBpm
        return ef.takeIf { it.isFinite() && it > 0.0 }
    }

    /**
     * Computes running/speed Efficiency Factor ($\text{EF}_{\text{pa}}$, in $\text{m/min/bpm}$) (`REQ-UI-400.1`):
     * $$\text{EF}_{\text{pa}} = \frac{\text{Avg\_Speed}_{\text{m/s}} \times 60.0}{\text{Avg\_HR}}$$
     *
     * @param avgSpeedMps Average horizontal speed in $\text{m/s}$ ($> 0$).
     * @param avgHeartRateBpm Average Heart Rate in bpm ($> 0$).
     * @return Efficiency Factor in $\text{m/min/bpm}$, or `null` if inputs are non-positive or non-finite.
     */
    @JvmStatic
    fun calculateEfficiencyFactorSpeed(avgSpeedMps: Double, avgHeartRateBpm: Double): Double? {
        if (!avgSpeedMps.isFinite() || !avgHeartRateBpm.isFinite() ||
            avgSpeedMps <= 0.0 || avgHeartRateBpm <= 0.0
        ) {
            return null
        }
        val speedMetersPerMin = avgSpeedMps * 60.0
        val ef = speedMetersPerMin / avgHeartRateBpm
        return ef.takeIf { it.isFinite() && it > 0.0 }
    }

    /**
     * Computes percentage Aerobic Decoupling ($\text{Pw:Hr }\%$ or $\text{Pa:Hr }\%$) from first-half
     * Efficiency Factor ($\text{EF}_1$) and second-half Efficiency Factor ($\text{EF}_2$) (`REQ-UI-400.1`):
     * $$\text{Decoupling }\% = \frac{\text{EF}_1 - \text{EF}_2}{\text{EF}_1} \times 100.0$$
     *
     * Positive values indicate cardiovascular drift (lower output per heartbeat in the second half);
     * values $< 5.0\%$ indicate a coupled aerobic response, while $\ge 5.0\%$ indicates high drift.
     *
     * @param efFirstHalf First-half Efficiency Factor ($\text{EF}_1 > 0$).
     * @param efSecondHalf Second-half Efficiency Factor ($\text{EF}_2 > 0$).
     * @return Aerobic decoupling percentage, or `null` if either input is non-positive or non-finite.
     */
    @JvmStatic
    fun calculateAerobicDecouplingFromEf(efFirstHalf: Double, efSecondHalf: Double): Double? {
        if (!efFirstHalf.isFinite() || !efSecondHalf.isFinite() ||
            efFirstHalf <= 0.0 || efSecondHalf <= 0.0
        ) {
            return null
        }
        val pct = ((efFirstHalf - efSecondHalf) / efFirstHalf) * 100.0
        return pct.takeIf { it.isFinite() }
    }

    /**
     * Evaluates a workout's paired telemetry streams (`POWER`, `SPEED_mps`, `HR`) to compute
     * full-session Efficiency Factor ($\text{EF}$) and first-half vs. second-half Aerobic Decoupling
     * ($\text{Pw:Hr }\%$ for power, $\text{Pa:Hr }\%$ for speed) (`REQ-UI-400.1`).
     *
     * Modality Selection:
     * - When valid paired `POWER` ($> 0\text{ W}$ average) and `HR` ($> 0\text{ bpm}$) samples exist
     *   (and sport is not `BSportType.RUN` unless speed is absent), evaluates power-based `Pw:Hr` (`isPowerBased = true`).
     * - Otherwise, when valid paired `SPEED_mps` ($> 0\text{ m/s}$) and `HR` ($> 0\text{ bpm}$) samples exist,
     *   evaluates speed/pace-based `Pa:Hr` (`isPowerBased = false`).
     */
    @JvmStatic
    @JvmOverloads
    fun computeAerobicEfficiencyAndDecoupling(
        powerSamplesWatts: List<Double?>,
        speedSamplesMps: List<Double?>,
        hrSamplesBpm: List<Double?>,
        bSportType: BSportType = BSportType.UNKNOWN
    ): AerobicEfficiencyResult {
        val maxLen = maxOf(powerSamplesWatts.size, speedSamplesMps.size, hrSamplesBpm.size)
        if (maxLen == 0 || hrSamplesBpm.isEmpty()) {
            return AerobicEfficiencyResult(
                efficiencyFactor = null,
                decouplingPct = null,
                efFirstHalf = null,
                efSecondHalf = null,
                isPowerBased = bSportType != BSportType.RUN,
                isHighDrift = false
            )
        }

        // Collect paired active Power + HR samples
        val pairedPower = ArrayList<Double>()
        val pairedPowerHr = ArrayList<Double>()
        val powerLimit = minOf(powerSamplesWatts.size, hrSamplesBpm.size)
        for (i in 0 until powerLimit) {
            val p = powerSamplesWatts[i]
            val hr = hrSamplesBpm[i]
            if (p != null && p.isFinite() && p >= 0.0 && hr != null && hr.isFinite() && hr > 0.0) {
                pairedPower.add(p)
                pairedPowerHr.add(hr)
            }
        }

        // Collect paired active Speed + HR samples
        val pairedSpeed = ArrayList<Double>()
        val pairedSpeedHr = ArrayList<Double>()
        val speedLimit = minOf(speedSamplesMps.size, hrSamplesBpm.size)
        for (i in 0 until speedLimit) {
            val s = speedSamplesMps[i]
            val hr = hrSamplesBpm[i]
            if (s != null && s.isFinite() && s > 0.0 && hr != null && hr.isFinite() && hr > 0.0) {
                pairedSpeed.add(s)
                pairedSpeedHr.add(hr)
            }
        }

        val hasValidPowerPairs = pairedPower.isNotEmpty() && pairedPower.any { it > 0.0 }
        val hasValidSpeedPairs = pairedSpeed.isNotEmpty() && pairedSpeed.any { it > 0.0 }

        val usePower = when {
            bSportType == BSportType.RUN && hasValidSpeedPairs && !hasValidPowerPairs -> false
            hasValidPowerPairs -> true
            hasValidSpeedPairs -> false
            else -> return AerobicEfficiencyResult(
                efficiencyFactor = null,
                decouplingPct = null,
                efFirstHalf = null,
                efSecondHalf = null,
                isPowerBased = bSportType != BSportType.RUN,
                isHighDrift = false
            )
        }

        if (usePower) {
            val totalNp = calculateNormalizedPower(pairedPower)
            val totalAvgHr = pairedPowerHr.average()
            val totalEf = if (totalNp != null) calculateEfficiencyFactorPower(totalNp, totalAvgHr) else null

            var ef1: Double? = null
            var ef2: Double? = null
            var decouplingPct: Double? = null
            if (pairedPower.size >= 2) {
                val mid = pairedPower.size / 2
                val np1 = calculateNormalizedPower(pairedPower.subList(0, mid))
                val hr1 = pairedPowerHr.subList(0, mid).average()
                val np2 = calculateNormalizedPower(pairedPower.subList(mid, pairedPower.size))
                val hr2 = pairedPowerHr.subList(mid, pairedPowerHr.size).average()
                if (np1 != null && np2 != null) {
                    ef1 = calculateEfficiencyFactorPower(np1, hr1)
                    ef2 = calculateEfficiencyFactorPower(np2, hr2)
                    if (ef1 != null && ef2 != null) {
                        decouplingPct = calculateAerobicDecouplingFromEf(ef1, ef2)
                    }
                }
            }
            val isDrift = decouplingPct != null && decouplingPct >= AEROBIC_DECOUPLING_DRIFT_THRESHOLD_PCT
            return AerobicEfficiencyResult(
                efficiencyFactor = totalEf,
                decouplingPct = decouplingPct,
                efFirstHalf = ef1,
                efSecondHalf = ef2,
                isPowerBased = true,
                isHighDrift = isDrift
            )
        } else {
            val totalAvgSpeed = pairedSpeed.average()
            val totalAvgHr = pairedSpeedHr.average()
            val totalEf = calculateEfficiencyFactorSpeed(totalAvgSpeed, totalAvgHr)

            var ef1: Double? = null
            var ef2: Double? = null
            var decouplingPct: Double? = null
            if (pairedSpeed.size >= 2) {
                val mid = pairedSpeed.size / 2
                val spd1 = pairedSpeed.subList(0, mid).average()
                val hr1 = pairedSpeedHr.subList(0, mid).average()
                val spd2 = pairedSpeed.subList(mid, pairedSpeed.size).average()
                val hr2 = pairedSpeedHr.subList(mid, pairedSpeedHr.size).average()
                ef1 = calculateEfficiencyFactorSpeed(spd1, hr1)
                ef2 = calculateEfficiencyFactorSpeed(spd2, hr2)
                if (ef1 != null && ef2 != null) {
                    decouplingPct = calculateAerobicDecouplingFromEf(ef1, ef2)
                }
            }
            val isDrift = decouplingPct != null && decouplingPct >= AEROBIC_DECOUPLING_DRIFT_THRESHOLD_PCT
            return AerobicEfficiencyResult(
                efficiencyFactor = totalEf,
                decouplingPct = decouplingPct,
                efFirstHalf = ef1,
                efSecondHalf = ef2,
                isPowerBased = false,
                isHighDrift = isDrift
            )
        }
    }
}
