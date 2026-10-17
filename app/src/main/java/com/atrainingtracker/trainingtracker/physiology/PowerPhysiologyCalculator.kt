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
 * Session-level power-based training metrics (`REQ-MIG-039.1`, `ATT-3860`, `ATT-3858`).
 *
 * @property normalizedPowerWatts Dr. Andrew Coggan's 30-second 4th-power Normalized Power ($\text{NP}$, in $\text{W}$).
 * @property avgPowerWatts Arithmetic mean power over active samples ($\text{Avg\_Power}$, in $\text{W}$).
 * @property maxPowerWatts Maximum power over active samples ($\text{Max\_Power}$, in $\text{W}$).
 * @property variabilityIndex Pacing steadiness ratio $\text{VI} = \text{NP} / \text{Avg\_Power}$.
 * @property intensityFactor Relative intensity $\text{IF} = \text{NP} / \text{FTP}$.
 * @property tss Training Stress Score $\text{TSS} = \frac{T_{\text{active}} \cdot \text{NP} \cdot \text{IF}}{\text{FTP} \cdot 36.0}$.
 * @property workKj Total mechanical work in kilojoules ($\text{Work}_{\text{kJ}} = \sum P_i \cdot \Delta t / 1000.0$).
 * @property avgPowerToWeight Session average power-to-weight ratio ($\text{Avg\_Power} / m_{\text{kg}}$, in $\text{W/kg}$).
 * @property maxPowerToWeight Session peak power-to-weight ratio ($\text{Max\_Power} / m_{\text{kg}}$, in $\text{W/kg}$).
 * @property npPowerToWeight Session normalized power-to-weight ratio ($\text{NP} / m_{\text{kg}}$, in $\text{W/kg}$).
 */
data class PowerTrainingMetricsResult(
    val normalizedPowerWatts: Double?,
    val avgPowerWatts: Double?,
    val maxPowerWatts: Double?,
    val variabilityIndex: Double?,
    val intensityFactor: Double?,
    val tss: Double?,
    val workKj: Double?,
    val avgPowerToWeight: Double?,
    val maxPowerToWeight: Double?,
    val npPowerToWeight: Double?
)

/**
 * Semantic Training Stress Balance ($\text{TSB}$) readiness zone (`REQ-UI-401.1`, `ATT-3859`).
 */
enum class TsbReadinessZone {
    /** $\text{TSB} \ge +5.0$ (including $+5$ to $+25$): Fresh / Peaked (*Race-Ready*). */
    FRESH_RACE_READY,
    /** $-10.0 \le \text{TSB} < +5.0$: Neutral / Grey Zone. */
    NEUTRAL,
    /** $-30.0 \le \text{TSB} < -10.0$: Optimal / Productive Training Overload. */
    OPTIMAL_OVERLOAD,
    /** $\text{TSB} < -30.0$: High Fatigue / Overreaching Risk. */
    HIGH_FATIGUE_RISK
}

/**
 * Semantic Acute:Chronic Workload Ratio ($\text{ACWR}$, Gabbett) zone (`REQ-UI-401.1`, `ATT-3859`).
 */
enum class AcwrZone {
    /** $\text{ACWR} < 0.80$: Under-training / Tapering. */
    UNDER_TRAINING,
    /** $0.80 \le \text{ACWR} \le 1.30$: Optimal training "Sweet Spot". */
    SWEET_SPOT,
    /** $1.30 < \text{ACWR} \le 1.50$: High load / Caution. */
    HIGH_LOAD_CAUTION,
    /** $\text{ACWR} > 1.50$: Danger zone (elevated overreaching / injury risk). */
    DANGER_ZONE
}

/**
 * Lightweight workout training load summary used as input to the Performance Management Chart (`REQ-UI-401.1`, `REQ-UI-401.2`).
 */
data class WorkoutTrainingLoadSample(
    val timestampMs: Long,
    val activeTimeSeconds: Int = 0,
    val powerTss: Double? = null,
    val normalizedPowerWatts: Double? = null,
    val avgPowerWatts: Double? = null,
    val avgHrBpm: Double? = null
)

/**
 * Single calendar-day state in the Performance Management Chart (`REQ-UI-401.1`).
 */
data class PmcDailyPoint(
    val dayEpochIndex: Long,
    val dayStartTimestampMs: Long,
    val dailyTss: Double,
    val ctl: Double,
    val atl: Double,
    val tsb: Double,
    val acwr: Double?,
    val rampRate7d: Double,
    val tsbZone: TsbReadinessZone,
    val acwrZone: AcwrZone?,
    val isAggressiveRampUp: Boolean
)

/**
 * Longitudinal Performance Management Chart (PMC) summary and daily time series (`REQ-UI-401.1`, `ATT-3859`).
 */
data class PmcSummaryResult(
    val dailyPoints: List<PmcDailyPoint> = emptyList(),
    val currentCtl: Double = 0.0,
    val currentAtl: Double = 0.0,
    val currentTsb: Double = 0.0,
    val currentAcwr: Double? = null,
    val currentRampRate7d: Double = 0.0,
    val currentTsbZone: TsbReadinessZone = TsbReadinessZone.NEUTRAL,
    val currentAcwrZone: AcwrZone? = null,
    val isAggressiveRampUp: Boolean = false
)

/**
 * Canonical, stateless, pure-Kotlin domain calculator for cycling & running power/cardiovascular
 * physiology metrics (`REQ-UI-399`, `REQ-UI-400`, `REQ-MIG-039`, `REQ-UI-401`, Rule 36 — Single Source of Truth & DRY).
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

    /** Chronic Training Load (Fitness) EWMA time constant $\tau_{\text{CTL}} = 42.0$ days (`REQ-UI-401.1`). */
    const val CTL_TAU_DAYS: Double = 42.0

    /** Acute Training Load (Fatigue) EWMA time constant $\tau_{\text{ATL}} = 7.0$ days (`REQ-UI-401.1`). */
    const val ATL_TAU_DAYS: Double = 7.0

    /** Weekly CTL ramp rate threshold ($+8.0\text{ CTL/week}$) above which aggressive build is flagged (`REQ-UI-401.1`). */
    const val AGGRESSIVE_RAMP_RATE_THRESHOLD: Double = 8.0

    /** Default Resting Heart Rate ($60\text{ bpm}$) used for $\text{hrTSS}$ fallback when unconfigured. */
    const val DEFAULT_HR_REST_BPM: Double = 60.0

    /** Default Maximum Heart Rate ($190\text{ bpm}$) used for $\text{hrTSS}$ fallback when unconfigured. */
    const val DEFAULT_HR_MAX_BPM: Double = 190.0

    /** Standard Heart Rate Reserve fraction at Lactate Threshold ($HR_{r,\text{thr}} = 0.85$). */
    const val LTHR_HR_RESERVE_FRACTION: Double = 0.85

    private const val MILLIS_PER_DAY: Long = 86_400_000L

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

    /**
     * Computes Intensity Factor ($\text{IF} = \text{NP} / \text{FTP}$) (`REQ-MIG-039.1`, `REQ-TRK-020`).
     *
     * @param normalizedPowerWatts Normalized Power in Watts ($> 0$).
     * @param ftpWatts Athlete Functional Threshold Power in Watts ($> 0$).
     * @return Intensity Factor, or `null` if either input is non-positive or non-finite.
     */
    @JvmStatic
    fun calculateIntensityFactor(normalizedPowerWatts: Double, ftpWatts: Double): Double? {
        if (!normalizedPowerWatts.isFinite() || !ftpWatts.isFinite() ||
            normalizedPowerWatts <= 0.0 || ftpWatts <= 0.0
        ) {
            return null
        }
        val ifVal = normalizedPowerWatts / ftpWatts
        return ifVal.takeIf { it.isFinite() && it > 0.0 }
    }

    /**
     * Computes Training Stress Score ($\text{TSS}$) (`REQ-MIG-039.1`, `REQ-TRK-021`):
     * $$\text{TSS} = \frac{T_{\text{active}} \cdot \text{NP} \cdot \text{IF}}{\text{FTP} \cdot 36.0}$$
     *
     * @param activeTimeSeconds Active riding duration in seconds ($> 0$).
     * @param normalizedPowerWatts Normalized Power in Watts ($> 0$).
     * @param intensityFactor Intensity Factor ($\text{NP} / \text{FTP} > 0$).
     * @param ftpWatts Athlete Functional Threshold Power in Watts ($> 0$).
     * @return Training Stress Score ($\ge 0$), or `null` if any input is non-positive or non-finite.
     */
    @JvmStatic
    fun calculateTss(
        activeTimeSeconds: Double,
        normalizedPowerWatts: Double,
        intensityFactor: Double,
        ftpWatts: Double
    ): Double? {
        if (!activeTimeSeconds.isFinite() || !normalizedPowerWatts.isFinite() ||
            !intensityFactor.isFinite() || !ftpWatts.isFinite() ||
            activeTimeSeconds <= 0.0 || normalizedPowerWatts <= 0.0 ||
            intensityFactor <= 0.0 || ftpWatts <= 0.0
        ) {
            return null
        }
        val tss = (activeTimeSeconds * normalizedPowerWatts * intensityFactor) / (ftpWatts * 36.0)
        return tss.takeIf { it.isFinite() && it >= 0.0 }
    }

    /**
     * Computes Mechanical Work in kilojoules ($\text{Work}_{\text{kJ}} = \frac{\sum P_i \cdot \Delta t}{1000.0}$) (`REQ-MIG-039.1`, `REQ-TRK-021`).
     *
     * @param powerSamplesWatts 1-Hz power samples in Watts.
     * @param sampleIntervalSec Duration represented by each sample in seconds (default `1.0`).
     * @return Total mechanical work in kJ ($> 0$), or `null` if no positive power samples exist.
     */
    @JvmStatic
    @JvmOverloads
    fun calculateWorkKj(
        powerSamplesWatts: List<Double?>,
        sampleIntervalSec: Double = 1.0
    ): Double? {
        if (powerSamplesWatts.isEmpty() || !sampleIntervalSec.isFinite() || sampleIntervalSec <= 0.0) {
            return null
        }
        var totalJoules = 0.0
        var hasPositive = false
        for (p in powerSamplesWatts) {
            if (p != null && p.isFinite() && p >= 0.0) {
                if (p > 0.0) hasPositive = true
                totalJoules += p * sampleIntervalSec
            }
        }
        if (!hasPositive || totalJoules <= 0.0) return null
        val workKj = totalJoules / 1000.0
        return workKj.takeIf { it.isFinite() && it > 0.0 }
    }

    /**
     * Computes Power-to-Weight ratio ($\text{W/kg} = P / m_{\text{kg}}$) (`REQ-MIG-039.1`, `REQ-TRK-019`).
     *
     * @param powerWatts Power in Watts ($\ge 0$).
     * @param weightKg Athlete body weight in kilograms ($> 0$).
     * @return Power-to-Weight in $\text{W/kg}$, or `null` if inputs are invalid.
     */
    @JvmStatic
    fun calculatePowerToWeight(powerWatts: Double, weightKg: Double): Double? {
        if (!powerWatts.isFinite() || !weightKg.isFinite() || powerWatts < 0.0 || weightKg <= 0.0) {
            return null
        }
        val wPerKg = powerWatts / weightKg
        return wPerKg.takeIf { it.isFinite() }
    }

    /**
     * Computes Variability Index ($\text{VI} = \text{NP} / \text{Avg\_Power}$) (`REQ-MIG-039.1`, `ATT-3858`).
     *
     * @param normalizedPowerWatts Normalized Power in Watts ($> 0$).
     * @param avgPowerWatts Arithmetic mean power in Watts ($> 0$).
     * @return Variability Index ($> 0$), or `null` if either input is non-positive or non-finite.
     */
    @JvmStatic
    fun calculateVariabilityIndex(normalizedPowerWatts: Double, avgPowerWatts: Double): Double? {
        if (!normalizedPowerWatts.isFinite() || !avgPowerWatts.isFinite() ||
            normalizedPowerWatts <= 0.0 || avgPowerWatts <= 0.0
        ) {
            return null
        }
        val vi = normalizedPowerWatts / avgPowerWatts
        return vi.takeIf { it.isFinite() && it > 0.0 }
    }

    /**
     * Evaluates a workout's recorded power sample stream (with optional FIT session-level fallback seeds)
     * and computes session-level power training metrics (`REQ-MIG-039.1`, `ATT-3860`, `ATT-3858`):
     * - `normalizedPowerWatts` ($\text{NP}$)
     * - `avgPowerWatts` ($\text{Avg\_Power}$)
     * - `maxPowerWatts` ($\text{Max\_Power}$)
     * - `variabilityIndex` ($\text{VI} = \text{NP} / \text{Avg\_Power}$)
     * - `intensityFactor` ($\text{IF} = \text{NP} / \text{FTP}$)
     * - `tss` ($\text{TSS} = (T_{\text{active}} \cdot \text{NP} \cdot \text{IF}) / (\text{FTP} \cdot 36.0)$)
     * - `workKj` ($\text{Work}_{\text{kJ}}$)
     * - `avgPowerToWeight`, `maxPowerToWeight`, `npPowerToWeight` ($\text{W/kg}$)
     */
    @JvmStatic
    @JvmOverloads
    fun computePowerTrainingMetrics(
        powerSamplesWatts: List<Double?>,
        activeTimeSeconds: Int = 0,
        ftpWatts: Double? = null,
        athleteWeightKg: Double? = null,
        sampleIntervalSec: Double = 1.0,
        fallbackNpWatts: Double? = null,
        fallbackIf: Double? = null,
        fallbackTss: Double? = null,
        fallbackWorkKj: Double? = null
    ): PowerTrainingMetricsResult {
        val validSamples = powerSamplesWatts.mapNotNull { p ->
            if (p != null && p.isFinite() && p >= 0.0) p else null
        }
        val hasPositiveSample = validSamples.any { it > 0.0 }

        val avgPower = if (hasPositiveSample) validSamples.average() else null
        val maxPower = if (hasPositiveSample) validSamples.maxOrNull() else null

        val npWatts = calculateNormalizedPower(powerSamplesWatts)
            ?: fallbackNpWatts?.takeIf { it.isFinite() && it > 0.0 }

        val vi = if (npWatts != null && avgPower != null && avgPower > 0.0) {
            calculateVariabilityIndex(npWatts, avgPower)
        } else {
            null
        }

        val effectiveFtp = ftpWatts?.takeIf { it.isFinite() && it > 0.0 }
        val ifVal = if (npWatts != null && effectiveFtp != null) {
            calculateIntensityFactor(npWatts, effectiveFtp)
        } else {
            fallbackIf?.takeIf { it.isFinite() && it > 0.0 }
        }

        val effectiveDurationSec = when {
            activeTimeSeconds > 0 -> activeTimeSeconds.toDouble()
            hasPositiveSample && sampleIntervalSec > 0.0 -> validSamples.size * sampleIntervalSec
            else -> 0.0
        }

        val tssVal = if (effectiveDurationSec > 0.0 && npWatts != null && ifVal != null && effectiveFtp != null) {
            calculateTss(effectiveDurationSec, npWatts, ifVal, effectiveFtp)
        } else {
            fallbackTss?.takeIf { it.isFinite() && it > 0.0 }
        }

        val workKjVal = if (hasPositiveSample) {
            if (activeTimeSeconds > 0 && avgPower != null && validSamples.size != activeTimeSeconds) {
                // Scale by authoritative activeTime when sample rate differs from 1Hz
                val sampleBasedKj = calculateWorkKj(validSamples, sampleIntervalSec)
                sampleBasedKj
            } else {
                calculateWorkKj(validSamples, sampleIntervalSec)
            }
        } else {
            fallbackWorkKj?.takeIf { it.isFinite() && it > 0.0 }
        }

        val effectiveWeightKg = athleteWeightKg?.takeIf { it.isFinite() && it > 0.0 }
        val avgWPerKg = if (avgPower != null && avgPower > 0.0 && effectiveWeightKg != null) {
            calculatePowerToWeight(avgPower, effectiveWeightKg)
        } else {
            null
        }
        val maxWPerKg = if (maxPower != null && maxPower > 0.0 && effectiveWeightKg != null) {
            calculatePowerToWeight(maxPower, effectiveWeightKg)
        } else {
            null
        }
        val npWPerKg = if (npWatts != null && npWatts > 0.0 && effectiveWeightKg != null) {
            calculatePowerToWeight(npWatts, effectiveWeightKg)
        } else {
            null
        }

        return PowerTrainingMetricsResult(
            normalizedPowerWatts = npWatts,
            avgPowerWatts = avgPower,
            maxPowerWatts = maxPower,
            variabilityIndex = vi,
            intensityFactor = ifVal,
            tss = tssVal,
            workKj = workKjVal,
            avgPowerToWeight = avgWPerKg,
            maxPowerToWeight = maxWPerKg,
            npPowerToWeight = npWPerKg
        )
    }

    /**
     * Classifies a Training Stress Balance ($\text{TSB}$) value into its semantic readiness zone (`REQ-UI-401.1`).
     */
    @JvmStatic
    fun classifyTsbZone(tsb: Double): TsbReadinessZone = when {
        tsb >= 5.0 -> TsbReadinessZone.FRESH_RACE_READY
        tsb >= -10.0 -> TsbReadinessZone.NEUTRAL
        tsb >= -30.0 -> TsbReadinessZone.OPTIMAL_OVERLOAD
        else -> TsbReadinessZone.HIGH_FATIGUE_RISK
    }

    /**
     * Classifies an Acute:Chronic Workload Ratio ($\text{ACWR}$) value into its semantic workload zone (`REQ-UI-401.1`).
     */
    @JvmStatic
    fun classifyAcwrZone(acwr: Double?): AcwrZone? {
        if (acwr == null || !acwr.isFinite() || acwr < 0.0) return null
        return when {
            acwr < 0.80 -> AcwrZone.UNDER_TRAINING
            acwr <= 1.30 -> AcwrZone.SWEET_SPOT
            acwr <= 1.50 -> AcwrZone.HIGH_LOAD_CAUTION
            else -> AcwrZone.DANGER_ZONE
        }
    }

    /**
     * Estimates Heart-Rate Training Stress Score ($\text{hrTSS}$) via Banister's $\text{TRIMP}$
     * normalized to 1 hour at Lactate Threshold ($HR_{r,\text{thr}} = 0.85$) (`REQ-UI-401.1`).
     *
     * Formula:
     * - $HR_r = \text{clamp}\left(\frac{HR_{\text{avg}} - HR_{\text{rest}}}{HR_{\text{max}} - HR_{\text{rest}}}, 0.0, 1.0\right)$
     * - $\text{TRIMP}(t_{\text{min}}, HR_r) = t_{\text{min}} \cdot HR_r \cdot 0.64 \cdot e^{1.92 \cdot HR_r}$
     * - $\text{hrTSS} = 100.0 \cdot \frac{(T_{\text{active}} / 60.0) \cdot HR_r \cdot e^{1.92 \cdot HR_r}}{60.0 \cdot 0.85 \cdot e^{1.92 \cdot 0.85}}$
     */
    @JvmStatic
    @JvmOverloads
    fun calculateHrTss(
        activeTimeSeconds: Double,
        avgHrBpm: Double,
        hrRestBpm: Double = DEFAULT_HR_REST_BPM,
        hrMaxBpm: Double = DEFAULT_HR_MAX_BPM
    ): Double? {
        if (!activeTimeSeconds.isFinite() || activeTimeSeconds <= 0.0 ||
            !avgHrBpm.isFinite() || avgHrBpm <= 0.0
        ) {
            return null
        }
        val safeHrRest = if (hrRestBpm.isFinite() && hrRestBpm > 20.0 && hrRestBpm < 130.0) hrRestBpm else DEFAULT_HR_REST_BPM
        val safeHrMax = if (hrMaxBpm.isFinite() && hrMaxBpm > safeHrRest + 20.0) hrMaxBpm else max(DEFAULT_HR_MAX_BPM, safeHrRest + 60.0)
        val hrSpan = safeHrMax - safeHrRest
        if (hrSpan <= 0.0) return null

        val hrr = ((avgHrBpm - safeHrRest) / hrSpan).coerceIn(0.0, 1.0)
        if (hrr <= 0.0) return 0.0

        val durationMinutes = activeTimeSeconds / 60.0
        val trimpNumerator = durationMinutes * hrr * exp(1.92 * hrr)
        val trimpThreshold1h = 60.0 * LTHR_HR_RESERVE_FRACTION * exp(1.92 * LTHR_HR_RESERVE_FRACTION)
        return 100.0 * (trimpNumerator / trimpThreshold1h)
    }

    /**
     * Resolves a workout's effective training stress score ($\text{TSS}$ or $\text{hrTSS}$) using the 3-tier hierarchy (`REQ-UI-401.1`):
     * 1. Stored `powerTss` ($> 0.0$).
     * 2. Power-derived $\text{TSS}$ from `normalizedPowerWatts` (or `avgPowerWatts`), `activeTimeSeconds`, and `ftpWatts`.
     * 3. Multi-sport Heart-Rate fallback ($\text{hrTSS}$) from `avgHrBpm`, `activeTimeSeconds`, `hrRestBpm`, and `hrMaxBpm`.
     */
    @JvmStatic
    @JvmOverloads
    fun resolveWorkoutTrainingLoad(
        sample: WorkoutTrainingLoadSample,
        ftpWatts: Double? = null,
        hrRestBpm: Double? = null,
        hrMaxBpm: Double? = null
    ): Double {
        val directTss = sample.powerTss
        if (directTss != null && directTss.isFinite() && directTss > 0.0) {
            return directTss
        }

        val activeSec = sample.activeTimeSeconds.toDouble()
        val effectiveFtp = ftpWatts?.takeIf { it.isFinite() && it > 0.0 }
        val effectivePower = (sample.normalizedPowerWatts?.takeIf { it.isFinite() && it > 0.0 })
            ?: (sample.avgPowerWatts?.takeIf { it.isFinite() && it > 0.0 })
        if (activeSec > 0.0 && effectivePower != null && effectiveFtp != null) {
            val ifVal = calculateIntensityFactor(effectivePower, effectiveFtp)
            val derivedTss = if (ifVal != null) calculateTss(activeSec, effectivePower, ifVal, effectiveFtp) else null
            if (derivedTss != null && derivedTss > 0.0) {
                return derivedTss
            }
        }

        val avgHr = sample.avgHrBpm
        if (activeSec > 0.0 && avgHr != null && avgHr.isFinite() && avgHr > 0.0) {
            val hrTss = calculateHrTss(
                activeTimeSeconds = activeSec,
                avgHrBpm = avgHr,
                hrRestBpm = hrRestBpm ?: DEFAULT_HR_REST_BPM,
                hrMaxBpm = hrMaxBpm ?: DEFAULT_HR_MAX_BPM
            )
            if (hrTss != null && hrTss > 0.0) {
                return hrTss
            }
        }

        return 0.0
    }

    /**
     * Computes the longitudinal Performance Management Chart (PMC) series from workout samples (`REQ-UI-401.1`, `ATT-3859`).
     *
     * Aggregates daily training stress $TSS_t$ across calendar days ($t = \lfloor \text{timestampMs} / 86{,}400{,}000 \rfloor$,
     * with $TSS_t = 0.0$ on rest days) through [nowMs] and evaluates:
     * - $\text{CTL}_t = \text{CTL}_{t-1} + \frac{TSS_t - \text{CTL}_{t-1}}{42.0}$
     * - $\text{ATL}_t = \text{ATL}_{t-1} + \frac{TSS_t - \text{ATL}_{t-1}}{7.0}$
     * - $\text{TSB}_t = \text{CTL}_{t-1} - \text{ATL}_{t-1}$
     * - $\text{ACWR}_t = \text{ATL}_t / \text{CTL}_t$ (when $\text{CTL}_t > 0$)
     * - $\text{RampRate}_t = \text{CTL}_t - \text{CTL}_{t-7}$
     */
    @JvmStatic
    @JvmOverloads
    fun computePerformanceManagementChart(
        workoutSamples: List<WorkoutTrainingLoadSample>,
        nowMs: Long = System.currentTimeMillis(),
        ftpWatts: Double? = null,
        hrRestBpm: Double? = null,
        hrMaxBpm: Double? = null
    ): PmcSummaryResult {
        if (workoutSamples.isEmpty()) {
            return PmcSummaryResult()
        }

        val dailyLoads = HashMap<Long, Double>()
        var minDayIndex = Long.MAX_VALUE
        var maxSampleDayIndex = Long.MIN_VALUE

        for (sample in workoutSamples) {
            if (sample.timestampMs <= 0L) continue
            val load = resolveWorkoutTrainingLoad(
                sample = sample,
                ftpWatts = ftpWatts,
                hrRestBpm = hrRestBpm,
                hrMaxBpm = hrMaxBpm
            )
            if (load <= 0.0) continue
            val dayIdx = sample.timestampMs / MILLIS_PER_DAY
            dailyLoads[dayIdx] = (dailyLoads[dayIdx] ?: 0.0) + load
            if (dayIdx < minDayIndex) minDayIndex = dayIdx
            if (dayIdx > maxSampleDayIndex) maxSampleDayIndex = dayIdx
        }

        if (dailyLoads.isEmpty() || minDayIndex == Long.MAX_VALUE) {
            return PmcSummaryResult()
        }

        val nowDayIndex = max(maxSampleDayIndex, nowMs / MILLIS_PER_DAY)
        val totalDays = (nowDayIndex - minDayIndex + 1L).coerceAtMost(3650L).toInt()
        val startDayIndex = nowDayIndex - totalDays + 1L

        val points = ArrayList<PmcDailyPoint>(totalDays)
        var prevCtl = 0.0
        var prevAtl = 0.0

        for (i in 0 until totalDays) {
            val dayIdx = startDayIndex + i.toLong()
            val tss = dailyLoads[dayIdx] ?: 0.0
            val tsb = prevCtl - prevAtl
            val ctl = prevCtl + (tss - prevCtl) / CTL_TAU_DAYS
            val atl = prevAtl + (tss - prevAtl) / ATL_TAU_DAYS
            val acwr = if (ctl > 0.01) atl / ctl else null
            val ctl7DaysAgo = if (i >= 7) points[i - 7].ctl else 0.0
            val rampRate7d = ctl - ctl7DaysAgo
            val tsbZone = classifyTsbZone(tsb)
            val acwrZone = classifyAcwrZone(acwr)
            val aggressiveRamp = rampRate7d > AGGRESSIVE_RAMP_RATE_THRESHOLD

            points.add(
                PmcDailyPoint(
                    dayEpochIndex = dayIdx,
                    dayStartTimestampMs = dayIdx * MILLIS_PER_DAY,
                    dailyTss = tss,
                    ctl = ctl,
                    atl = atl,
                    tsb = tsb,
                    acwr = acwr,
                    rampRate7d = rampRate7d,
                    tsbZone = tsbZone,
                    acwrZone = acwrZone,
                    isAggressiveRampUp = aggressiveRamp
                )
            )

            prevCtl = ctl
            prevAtl = atl
        }

        val latest = points.last()
        return PmcSummaryResult(
            dailyPoints = points,
            currentCtl = latest.ctl,
            currentAtl = latest.atl,
            currentTsb = latest.tsb,
            currentAcwr = latest.acwr,
            currentRampRate7d = latest.rampRate7d,
            currentTsbZone = latest.tsbZone,
            currentAcwrZone = latest.acwrZone,
            isAggressiveRampUp = latest.isAggressiveRampUp
        )
    }
}

