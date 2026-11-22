/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see https://www.gnu.org/licenses/gpl-3.0
 */

package com.atrainingtracker.trainingtracker.ui.map

import android.content.Context
import androidx.compose.ui.graphics.Color
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.settings.SettingsDataStore
import com.atrainingtracker.trainingtracker.settings.SettingsDataStoreJavaHelper
import com.atrainingtracker.trainingtracker.settings.TuningPreferencesDefaults
import com.atrainingtracker.trainingtracker.ui.aftermath.zones.HeartRateZoneThresholds
import com.atrainingtracker.trainingtracker.ui.aftermath.zones.PowerZoneThresholds
import com.atrainingtracker.trainingtracker.ui.theme.TTColor
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.round

/**
 * Geometric and analytical representation of a single horizontal training zone band
 * rendered behind continuous telemetry curves. (REQ-UI-230 / ATT-1839)
 */
data class TelemetryZoneBand(
    val zoneIndex: Int, // 1..5
    val minVal: Double,
    val maxVal: Double,
    val color: Color,
    val label: String // "Z1".."Z5"
)

/**
 * Pure calculation engine for partitioning telemetry plot areas into 5 training zones,
 * determining threshold guideline positions, and evaluating label clearance. (REQ-UI-230 / ATT-1839)
 */
object TelemetryZoneMath {

    /**
     * Standard training zone colors matching TTColor palette (Z1..Z7).
     */
    val ZONE_COLORS = listOf(
        TTColor.Zone1,
        TTColor.Zone2,
        TTColor.Zone3,
        TTColor.Zone4,
        TTColor.Zone5,
        TTColor.Zone6,
        TTColor.Zone7
    )

    /**
     * Default subtle alpha for horizontal background zone bands.
     */
    const val ZONE_BAND_ALPHA = 0.10f

    /**
     * Calculates the 5 horizontal zone bands for a given heart rate data range.
     * Each band's [minVal, maxVal] interval is clamped to [dataMin, dataMax].
     */
    fun calculateHeartRateZoneBands(
        thresholds: HeartRateZoneThresholds,
        dataMin: Double,
        dataMax: Double
    ): List<TelemetryZoneBand> {
        if (dataMax <= dataMin) return emptyList()

        val rawBands = listOf(
            Triple(1, dataMin, thresholds.z1Max.toDouble()),
            Triple(2, thresholds.z1Max.toDouble(), thresholds.z2Max.toDouble()),
            Triple(3, thresholds.z2Max.toDouble(), thresholds.z3Max.toDouble()),
            Triple(4, thresholds.z3Max.toDouble(), thresholds.z4Max.toDouble()),
            Triple(5, thresholds.z4Max.toDouble(), dataMax)
        )

        return rawBands.mapNotNull { (zoneIndex, rawMin, rawMax) ->
            val clampedMin = maxOf(dataMin, rawMin)
            val clampedMax = minOf(dataMax, rawMax)
            if (clampedMin < clampedMax) {
                TelemetryZoneBand(
                    zoneIndex = zoneIndex,
                    minVal = clampedMin,
                    maxVal = clampedMax,
                    color = ZONE_COLORS[zoneIndex - 1],
                    label = "Z$zoneIndex"
                )
            } else {
                null
            }
        }
    }

    /**
     * Calculates the 7 horizontal zone bands for a given cycling power data range.
     * Each band's [minVal, maxVal] interval is clamped to [dataMin, dataMax].
     */
    fun calculatePowerZoneBands(
        thresholds: PowerZoneThresholds,
        dataMin: Double,
        dataMax: Double
    ): List<TelemetryZoneBand> {
        if (dataMax <= dataMin) return emptyList()

        val rawBands = listOf(
            Triple(1, dataMin, thresholds.z1Max.toDouble()),
            Triple(2, thresholds.z1Max.toDouble(), thresholds.z2Max.toDouble()),
            Triple(3, thresholds.z2Max.toDouble(), thresholds.z3Max.toDouble()),
            Triple(4, thresholds.z3Max.toDouble(), thresholds.z4Max.toDouble()),
            Triple(5, thresholds.z4Max.toDouble(), thresholds.z5Max.toDouble()),
            Triple(6, thresholds.z5Max.toDouble(), thresholds.z6Max.toDouble()),
            Triple(7, thresholds.z6Max.toDouble(), dataMax)
        )

        return rawBands.mapNotNull { (zoneIndex, rawMin, rawMax) ->
            val clampedMin = maxOf(dataMin, rawMin)
            val clampedMax = minOf(dataMax, rawMax)
            if (clampedMin < clampedMax) {
                TelemetryZoneBand(
                    zoneIndex = zoneIndex,
                    minVal = clampedMin,
                    maxVal = clampedMax,
                    color = ZONE_COLORS[zoneIndex - 1],
                    label = "Z$zoneIndex"
                )
            } else {
                null
            }
        }
    }

    /**
     * Returns valid threshold boundary guidelines (Z1..Z4 for HR or custom list) that fall strictly
     * within the visible data range (dataMin < threshold < dataMax).
     */
    fun calculateThresholdDashes(
        z1Max: Int,
        z2Max: Int,
        z3Max: Int,
        z4Max: Int,
        dataMin: Double,
        dataMax: Double
    ): List<Double> {
        return listOf(z1Max, z2Max, z3Max, z4Max)
            .map { it.toDouble() }
            .filter { it > dataMin && it < dataMax }
    }

    /**
     * Returns valid threshold boundary guidelines for cycling power (Z1..Z6 upper limits)
     * that fall strictly within the visible data range (dataMin < threshold < dataMax).
     */
    fun calculatePowerThresholdDashes(
        thresholds: PowerZoneThresholds,
        dataMin: Double,
        dataMax: Double
    ): List<Double> {
        return listOf(
            thresholds.z1Max,
            thresholds.z2Max,
            thresholds.z3Max,
            thresholds.z4Max,
            thresholds.z5Max,
            thresholds.z6Max
        )
            .map { it.toDouble() }
            .filter { it > dataMin && it < dataMax }
    }

    /**
     * Determines whether a zone band has sufficient vertical height clearance to render its label.
     */
    fun shouldRenderZoneLabel(bandHeightPx: Float, minHeightPx: Float = 28f): Boolean {
        return bandHeightPx >= minHeightPx
    }

    /**
     * Maps an instantaneous heart rate value to a 1-indexed zone (1..5).
     */
    fun determineHeartRateZone(hr: Double, thresholds: HeartRateZoneThresholds): Int {
        return when {
            hr <= thresholds.z1Max -> 1
            hr <= thresholds.z2Max -> 2
            hr <= thresholds.z3Max -> 3
            hr <= thresholds.z4Max -> 4
            else -> 5
        }
    }

    /**
     * Maps an instantaneous cycling power value (Watts) to a 1-indexed zone (1..7).
     * For all values <= z1Max (including 0 and negative values), returns 1 safely.
     */
    fun determinePowerZone(power: Double, thresholds: PowerZoneThresholds): Int {
        return when {
            power <= thresholds.z1Max -> 1
            power <= thresholds.z2Max -> 2
            power <= thresholds.z3Max -> 3
            power <= thresholds.z4Max -> 4
            power <= thresholds.z5Max -> 5
            power <= thresholds.z6Max -> 6
            else -> 7
        }
    }

    /**
     * Standardized factory method to load heart rate training zone thresholds from user preferences
     * for the given [bSportType] (REQ-UI-260 / ATT-2031).
     *
     * Validates that thresholds are strictly ascending and positive (z1 > 0 && z2 > z1 && z3 > z2 && z4 > z3).
     * Returns null if unconfigured or invalid.
     */
    /**
     * Standardized factory method to load heart rate zone thresholds for a sport type
     * (REQ-UI-260 / ATT-2031 / REQ-UI-370), resolving effective historical boundaries
     * at [workoutStartTimeMs] when provided (REQ-SET-082.3).
     *
     * Validates that thresholds are strictly ascending and positive:
     * (z1 > 0 && z2 > z1 && z3 > z2 && z4 > z3). Returns null if unconfigured or invalid.
     */
    fun loadHeartRateThresholds(
        context: Context,
        bSportType: BSportType,
        workoutStartTimeMs: Long = 0L
    ): HeartRateZoneThresholds? {
        return runCatching {
            val zoneType = if (bSportType == BSportType.BIKE) {
                SettingsDataStore.ZoneType.HR_BIKE
            } else {
                SettingsDataStore.ZoneType.HR_RUN
            }
            val bounds = SettingsDataStoreJavaHelper.getEffectiveZoneBoundaries(context, zoneType, workoutStartTimeMs)
            val z1 = bounds.getOrElse(0) { 0 }
            val z2 = bounds.getOrElse(1) { 0 }
            val z3 = bounds.getOrElse(2) { 0 }
            val z4 = bounds.getOrElse(3) { 0 }
            if (z1 > 0 && z2 > z1 && z3 > z2 && z4 > z3) {
                HeartRateZoneThresholds(z1, z2, z3, z4)
            } else null
        }.getOrNull()
    }

    /**
     * Standardized factory method to load cycling power zone thresholds from user preferences
     * (REQ-UI-260 / ATT-2031 / REQ-UI-370), resolving effective historical boundaries
     * at [workoutStartTimeMs] when provided (REQ-SET-082.3).
     *
     * Validates that thresholds are strictly ascending and positive:
     * (z1 > 0 && z2 > z1 && z3 > z2 && z4 > z3 && z5 > z4 && z6 > z5).
     * Returns null if unconfigured or invalid.
     */
    fun loadPowerThresholds(
        context: Context,
        workoutStartTimeMs: Long = 0L
    ): PowerZoneThresholds? {
        return runCatching {
            val zoneType = SettingsDataStore.ZoneType.PWR_BIKE
            val bounds = SettingsDataStoreJavaHelper.getEffectiveZoneBoundaries(context, zoneType, workoutStartTimeMs)
            val z1 = bounds.getOrElse(0) { 0 }
            val z2 = bounds.getOrElse(1) { 0 }
            val z3 = bounds.getOrElse(2) { 0 }
            val z4 = bounds.getOrElse(3) { 0 }
            val z5 = bounds.getOrElse(4) { 0 }
            val z6 = bounds.getOrElse(5) { 0 }
            if (z1 > 0 && z2 > z1 && z3 > z2 && z4 > z3 && z5 > z4 && z6 > z5) {
                PowerZoneThresholds(z1, z2, z3, z4, z5, z6)
            } else null
        }.getOrNull()
    }

    /**
     * Maps an instantaneous DFA alpha 1 value to an inverted 3-zone index (1..3) matching
     * [com.atrainingtracker.trainingtracker.ui.tracking.HrvZoneIndicator.calculateZoneColor]:
     * - Zone 1 (Aerob, `alpha1 > t1`): [TTColor.Zone2]
     * - Zone 2 (Schwelle, `t2 <= alpha1 <= t1`): [TTColor.Zone3] (preserves `alpha1 == t1 -> Zone3`)
     * - Zone 3 (Anaerob, `alpha1 < t2`): [TTColor.Zone4]
     * (REQ-HRV-044.1 / ATT-8710)
     */
    fun determineHrvDfaA1Zone(
        alpha1: Double,
        t1: Float = TuningPreferencesDefaults.DEFAULT_DFA_A1_THRESHOLD_T1,
        t2: Float = TuningPreferencesDefaults.DEFAULT_DFA_A1_THRESHOLD_T2
    ): Int {
        val effT2 = minOf(t1, t2)
        val effT1 = maxOf(t1, t2)
        return when {
            !alpha1.isFinite() -> 2
            alpha1 > effT1 -> 1
            alpha1 >= effT2 -> 2
            else -> 3
        }
    }

    /**
     * Resolves the semantic [TTColor] token for a DFA alpha 1 value against thresholds [t1] and [t2].
     * (REQ-HRV-044.1 / ATT-8710)
     */
    fun resolveHrvDfaA1ZoneColor(
        alpha1: Double,
        t1: Float = TuningPreferencesDefaults.DEFAULT_DFA_A1_THRESHOLD_T1,
        t2: Float = TuningPreferencesDefaults.DEFAULT_DFA_A1_THRESHOLD_T2
    ): Color {
        return when (determineHrvDfaA1Zone(alpha1, t1, t2)) {
            1 -> TTColor.Zone2
            2 -> TTColor.Zone3
            else -> TTColor.Zone4
        }
    }

    /**
     * Calculates up to 3 horizontal inverted DFA alpha 1 zone bands clamped to [dataMin, dataMax]:
     * - Anaerob (`[dataMin, t2]`, [TTColor.Zone4], `"Z3"`)
     * - Schwelle (`[t2, t1]`, [TTColor.Zone3], `"Z2"`)
     * - Aerob (`[t1, dataMax]`, [TTColor.Zone2], `"Z1"`)
     * (REQ-HRV-044.1 / ATT-8710)
     */
    fun calculateHrvDfaA1ZoneBands(
        t1: Float,
        t2: Float,
        dataMin: Double,
        dataMax: Double
    ): List<TelemetryZoneBand> {
        if (!dataMin.isFinite() || !dataMax.isFinite() || dataMax <= dataMin || !t1.isFinite() || !t2.isFinite()) {
            return emptyList()
        }
        val effT2 = minOf(t1, t2).toDouble()
        val effT1 = maxOf(t1, t2).toDouble()
        val rawBands = listOf(
            Triple(3, dataMin to effT2, TTColor.Zone4),
            Triple(2, effT2 to effT1, TTColor.Zone3),
            Triple(1, effT1 to dataMax, TTColor.Zone2)
        )
        return rawBands.mapNotNull { (zoneIndex, bounds, color) ->
            val clampedMin = maxOf(dataMin, bounds.first)
            val clampedMax = minOf(dataMax, bounds.second)
            if (clampedMin < clampedMax) {
                TelemetryZoneBand(
                    zoneIndex = zoneIndex,
                    minVal = clampedMin,
                    maxVal = clampedMax,
                    color = color,
                    label = "Z$zoneIndex"
                )
            } else {
                null
            }
        }
    }

    /**
     * Returns valid DFA alpha 1 threshold boundary guidelines ([t2], [t1]) that fall strictly
     * within the visible data range `(dataMin < threshold < dataMax)`.
     * (REQ-HRV-044.4 / ATT-8710)
     */
    fun calculateHrvDfaA1ThresholdDashes(
        t1: Float,
        t2: Float,
        dataMin: Double,
        dataMax: Double
    ): List<Double> {
        if (!dataMin.isFinite() || !dataMax.isFinite() || dataMax <= dataMin) {
            return emptyList()
        }
        val boundaryEps = 1e-5
        return listOf(t2.toDouble(), t1.toDouble())
            .filter { it.isFinite() && it > dataMin + boundaryEps && it < dataMax - boundaryEps }
            .distinct()
    }

    /**
     * Returns true when smaller numeric values represent higher physiological effort and should be
     * plotted toward the top of the chart (`PACE` and `HRV_DFA_A1`) (`REQ-HRV-046.1`).
     */
    fun isInvertedTelemetryYAxis(metricType: TelemetryMetricType?): Boolean {
        return metricType == TelemetryMetricType.PACE || metricType == TelemetryMetricType.HRV_DFA_A1
    }

    /**
     * Maps a metric [value] within `[dataMin, dataMax]` onto canvas Y coordinates in
     * `[topPaddingPx, topPaddingPx + chartHeightPx]`, preserving `(dataMax - dataMin).coerceAtLeast(1.0)`
     * range safety across all telemetry metrics (`REQ-HRV-046.1`).
     *
     * When [isInverted] is true (`PACE`, `HRV_DFA_A1`), `dataMin` maps to `topPaddingPx` (top) and
     * `dataMax` maps to `topPaddingPx + chartHeightPx` (bottom).
     */
    fun mapTelemetryValueToY(
        value: Double,
        dataMin: Double,
        dataMax: Double,
        topPaddingPx: Float,
        chartHeightPx: Float,
        isInverted: Boolean
    ): Float {
        val valRange = (dataMax - dataMin).coerceAtLeast(1.0)
        val ratio = ((value - dataMin) / valRange).coerceIn(0.0, 1.0)
        return if (isInverted) {
            (topPaddingPx + ratio * chartHeightPx).toFloat()
        } else {
            (topPaddingPx + (1.0 - ratio) * chartHeightPx).toFloat()
        }
    }

    /**
     * Builds monotonically non-decreasing `[0f, 1f]` vertical gradient color stops spanning
     * `[dataMin, dataMax]` on the inverted DFA alpha 1 Y-axis where `0f` maps to `dataMin`
     * (top, Anaerob / harder effort) and `1f` maps to `dataMax` (bottom, Aerob / easier effort).
     * (`REQ-HRV-044.1`, `REQ-HRV-046.2` / `ATT-8953`)
     */
    fun buildHrvDfaA1VerticalGradientStops(
        t1: Float,
        t2: Float,
        dataMin: Double,
        dataMax: Double,
        topAlpha: Float = 1f,
        bottomAlpha: Float = 1f
    ): List<Pair<Float, Color>> {
        if (!dataMin.isFinite() || !dataMax.isFinite() || dataMax <= dataMin || !t1.isFinite() || !t2.isFinite()) {
            return emptyList()
        }
        val effT2 = minOf(t1, t2).toDouble()
        val effT1 = maxOf(t1, t2).toDouble()
        val range = dataMax - dataMin
        val eps = 0.001f
        val boundaryEps = 1e-5

        fun alphaAt(fraction: Float): Float {
            return (topAlpha + (bottomAlpha - topAlpha) * fraction).coerceIn(0f, 1f)
        }

        val topBaseColor = when {
            effT2 > dataMin + boundaryEps -> TTColor.Zone4
            effT1 > dataMin + boundaryEps -> TTColor.Zone3
            else -> TTColor.Zone2
        }
        val bottomBaseColor = when {
            effT2 >= dataMax - boundaryEps -> TTColor.Zone4
            effT1 >= dataMax - boundaryEps -> TTColor.Zone3
            else -> TTColor.Zone2
        }

        val stops = mutableListOf<Pair<Float, Color>>()
        stops.add(0f to topBaseColor.copy(alpha = alphaAt(0f)))
        var lastStop = 0f

        if (effT2 > dataMin + boundaryEps && effT2 < dataMax - boundaryEps) {
            val frac = ((effT2 - dataMin) / range).toFloat().coerceIn(0f, 1f)
            val upperStop = (frac - eps).coerceIn(lastStop, 1f)
            val lowerStop = (frac + eps).coerceIn(upperStop, 1f)
            val belowColor = if (effT2 < effT1 - boundaryEps) TTColor.Zone3 else TTColor.Zone2
            stops.add(upperStop to TTColor.Zone4.copy(alpha = alphaAt(upperStop)))
            stops.add(lowerStop to belowColor.copy(alpha = alphaAt(lowerStop)))
            lastStop = lowerStop
        }

        if (effT1 > dataMin + boundaryEps && effT1 < dataMax - boundaryEps && effT1 > effT2 + boundaryEps) {
            val frac = ((effT1 - dataMin) / range).toFloat().coerceIn(0f, 1f)
            val upperStop = (frac - eps).coerceIn(lastStop, 1f)
            val lowerStop = (frac + eps).coerceIn(upperStop, 1f)
            stops.add(upperStop to TTColor.Zone3.copy(alpha = alphaAt(upperStop)))
            stops.add(lowerStop to TTColor.Zone2.copy(alpha = alphaAt(lowerStop)))
        }

        stops.add(1f to bottomBaseColor.copy(alpha = alphaAt(1f)))
        return stops
    }

    /**
     * Minimum Y-axis upper bound (ms) for `HRV_RMSSD` graphs so empty, all-zero, or ultra-low
     * streams remain well-formed (`REQ-HRV-047.1`).
     */
    const val MIN_HRV_RMSSD_Y_CEILING = 5.0

    /**
     * Computes dynamic, outlier-resilient `(dataMin, dataMax)` Y-axis bounds for `HRV_RMSSD`
     * telemetry graphs (`REQ-HRV-047.1` / `ATT-9171`).
     *
     * Filters finite values `>= 0.0` (returning `0.0 to 5.0` when empty), applies a nearest-rank
     * `P95` spike-ratio guard when `N >= 5 && p95 > 0.0 && max > p95 * 2.0` so transient warmup or
     * artifact spikes do not flatten steady-state exercise traces, preserves `>= max * 1.15` (zero
     * clipping) on genuine non-spike recordings (`40..180+ ms`), guards against extreme `Double`
     * overflow, and rounds upward to clean nice steps (`1/2/5/10/20`).
     */
    fun calculateHrvRmssdYAxisBounds(values: List<Double>): Pair<Double, Double> {
        val finiteValues = values.filter { it.isFinite() && it >= 0.0 }
        if (finiteValues.isEmpty()) return 0.0 to MIN_HRV_RMSSD_Y_CEILING

        val sorted = finiteValues.sorted()
        val max = sorted.last()
        val base = if (sorted.size >= 5) {
            val p95Index = floor(0.95 * (sorted.size - 1)).toInt().coerceIn(0, sorted.lastIndex)
            val p95 = sorted[p95Index]
            if (p95 > 0.0 && max > p95 * 2.0) p95 else max
        } else {
            max
        }

        val scaled = base * 1.15
        val rawUpper = (if (scaled.isFinite()) scaled else base).coerceAtLeast(MIN_HRV_RMSSD_Y_CEILING)
        val step = when {
            rawUpper <= 10.0 -> 1.0
            rawUpper <= 20.0 -> 2.0
            rawUpper <= 50.0 -> 5.0
            rawUpper <= 100.0 -> 10.0
            else -> 20.0
        }
        val rounded = ceil(rawUpper / step) * step
        val niceCeiling = if (rounded.isFinite()) rounded else rawUpper
        return 0.0 to niceCeiling
    }

    /**
     * Validates that heart rate zone thresholds are strictly ascending and positive (`REQ-UI-2798.1`).
     */
    fun isValidHeartRateThresholds(thresholds: HeartRateZoneThresholds): Boolean =
        thresholds.z1Max > 0 &&
            thresholds.z2Max > thresholds.z1Max &&
            thresholds.z3Max > thresholds.z2Max &&
            thresholds.z4Max > thresholds.z3Max

    /**
     * Validates that cycling power zone thresholds are strictly ascending and positive (`REQ-UI-2798.1`).
     */
    fun isValidPowerThresholds(thresholds: PowerZoneThresholds): Boolean =
        thresholds.z1Max > 0 &&
            thresholds.z2Max > thresholds.z1Max &&
            thresholds.z3Max > thresholds.z2Max &&
            thresholds.z4Max > thresholds.z3Max &&
            thresholds.z5Max > thresholds.z4Max &&
            thresholds.z6Max > thresholds.z5Max

    /**
     * Builds monotonically non-decreasing `[0f, 1f]` hard-stepped vertical gradient color stops
     * spanning `[dataMin, dataMax]` for non-inverted zoned metrics (`HEART_RATE`, `POWER`) where
     * `0f` maps to `dataMax` (top) and `1f` maps to `dataMin` (bottom) (`REQ-UI-2798.1` / `ATT-9164`).
     *
     * Uses theme-adaptive WCAG 2.1 AA stroke colors from [TTColor.zoneStrokeColor] so every zone
     * (`Z1`..`Z7`) remains high-contrast in Light, Dark, and AMOLED themes.
     */
    fun buildZoneBandsVerticalGradientStops(
        zoneBands: List<TelemetryZoneBand>,
        dataMin: Double,
        dataMax: Double,
        isDarkSurface: Boolean,
        topAlpha: Float = 1f,
        bottomAlpha: Float = 1f
    ): List<Pair<Float, Color>> {
        if (zoneBands.isEmpty() || !dataMin.isFinite() || !dataMax.isFinite() || dataMax <= dataMin) {
            return emptyList()
        }
        val orderedTopToBottom = zoneBands
            .mapNotNull { band ->
                val clampedMin = maxOf(dataMin, band.minVal)
                val clampedMax = minOf(dataMax, band.maxVal)
                if (clampedMin < clampedMax) band.copy(minVal = clampedMin, maxVal = clampedMax) else null
            }
            .sortedByDescending { it.maxVal }
        if (orderedTopToBottom.isEmpty()) return emptyList()

        val range = dataMax - dataMin
        val eps = 0.001f

        fun alphaAt(fraction: Float): Float =
            (topAlpha + (bottomAlpha - topAlpha) * fraction).coerceIn(0f, 1f)

        val stops = mutableListOf<Pair<Float, Color>>()
        var prevColor = TTColor.zoneStrokeColor(orderedTopToBottom.first().zoneIndex, isDarkSurface)
        stops.add(0f to prevColor.copy(alpha = alphaAt(0f)))
        var lastStop = 0f

        for (i in 1 until orderedTopToBottom.size) {
            val band = orderedTopToBottom[i]
            val currColor = TTColor.zoneStrokeColor(band.zoneIndex, isDarkSurface)
            val frac = ((dataMax - band.maxVal) / range).toFloat().coerceIn(0f, 1f)
            val upperStop = (frac - eps).coerceIn(lastStop, 1f)
            val lowerStop = (frac + eps).coerceIn(upperStop, 1f)
            stops.add(upperStop to prevColor.copy(alpha = alphaAt(upperStop)))
            stops.add(lowerStop to currColor.copy(alpha = alphaAt(lowerStop)))
            lastStop = lowerStop
            prevColor = currColor
        }

        stops.add(1f to prevColor.copy(alpha = alphaAt(1f)))
        return stops
    }

    /**
     * Unified dispatcher producing vertical gradient color stops for zone-colored metrics
     * (`HRV_DFA_A1`, zoned `HEART_RATE`, zoned `POWER`), or an empty list when unzoned (`REQ-UI-2798.1`).
     */
    fun buildMetricZoneGradientStops(
        metricType: TelemetryMetricType,
        zoneBands: List<TelemetryZoneBand>,
        hrThresholds: HeartRateZoneThresholds?,
        powerThresholds: PowerZoneThresholds?,
        dfaA1ThresholdT1: Float,
        dfaA1ThresholdT2: Float,
        dataMin: Double,
        dataMax: Double,
        isDarkSurface: Boolean,
        topAlpha: Float,
        bottomAlpha: Float
    ): List<Pair<Float, Color>> = when (metricType) {
        TelemetryMetricType.HRV_DFA_A1 -> buildHrvDfaA1VerticalGradientStops(
            t1 = dfaA1ThresholdT1,
            t2 = dfaA1ThresholdT2,
            dataMin = dataMin,
            dataMax = dataMax,
            topAlpha = topAlpha,
            bottomAlpha = bottomAlpha
        )
        TelemetryMetricType.HEART_RATE -> if (hrThresholds != null && isValidHeartRateThresholds(hrThresholds) && zoneBands.isNotEmpty()) {
            buildZoneBandsVerticalGradientStops(zoneBands, dataMin, dataMax, isDarkSurface, topAlpha, bottomAlpha)
        } else emptyList()
        TelemetryMetricType.POWER -> if (powerThresholds != null && isValidPowerThresholds(powerThresholds) && zoneBands.isNotEmpty()) {
            buildZoneBandsVerticalGradientStops(zoneBands, dataMin, dataMax, isDarkSurface, topAlpha, bottomAlpha)
        } else emptyList()
        else -> emptyList()
    }

    /**
     * Resolves a single-point or scrub-marker zone stroke color for [metricType] when zone coloring
     * is active, or `null` to fall back to the metric's standard accent color (`REQ-UI-2798.1`).
     */
    fun resolveMetricZoneStrokeColor(
        metricType: TelemetryMetricType,
        value: Double,
        hrThresholds: HeartRateZoneThresholds?,
        powerThresholds: PowerZoneThresholds?,
        dfaA1ThresholdT1: Float,
        dfaA1ThresholdT2: Float,
        isDarkSurface: Boolean
    ): Color? = when (metricType) {
        TelemetryMetricType.HRV_DFA_A1 -> resolveHrvDfaA1ZoneColor(
            alpha1 = value,
            t1 = dfaA1ThresholdT1,
            t2 = dfaA1ThresholdT2
        )
        TelemetryMetricType.HEART_RATE -> hrThresholds?.takeIf(::isValidHeartRateThresholds)?.let {
            TTColor.zoneStrokeColor(determineHeartRateZone(value, it), isDarkSurface)
        }
        TelemetryMetricType.POWER -> powerThresholds?.takeIf(::isValidPowerThresholds)?.let {
            TTColor.zoneStrokeColor(determinePowerZone(value, it), isDarkSurface)
        }
        else -> null
    }

    /**
     * Computes clean intermediate round-number Y-axis tick values strictly inside `(dataMin, dataMax)`
     * for unzoned `HEART_RATE` (10/20/25/50/100 bpm) and `POWER` (25/50/100/200/250/500 W) graphs,
     * dynamically choosing the finest step that stays within [maxTickCount] (`REQ-UI-2798.3` / `ATT-9164`).
     */
    fun calculateNiceIntermediateTicks(
        metricType: TelemetryMetricType,
        dataMin: Double,
        dataMax: Double,
        maxTickCount: Int = 4
    ): List<Double> {
        if (!dataMin.isFinite() || !dataMax.isFinite() || dataMax <= dataMin || maxTickCount <= 0) {
            return emptyList()
        }
        val candidateSteps = when (metricType) {
            TelemetryMetricType.HEART_RATE -> listOf(10.0, 20.0, 25.0, 50.0, 100.0)
            TelemetryMetricType.POWER -> listOf(25.0, 50.0, 100.0, 200.0, 250.0, 500.0)
            else -> return emptyList()
        }
        val boundaryEps = 1e-6

        fun ticksForStep(step: Double): List<Double> {
            val result = mutableListOf<Double>()
            var v = ceil((dataMin + boundaryEps) / step) * step
            while (v < dataMax - boundaryEps) {
                result.add(v)
                v += step
            }
            return result
        }

        for (step in candidateSteps) {
            val ticks = ticksForStep(step)
            if (ticks.size <= maxTickCount) {
                return ticks
            }
        }
        return ticksForStep(candidateSteps.last())
    }

    /**
     * Formats an `HRV_RMSSD` Y-axis boundary label cleanly without silent integer truncation or
     * `-0.0` / non-finite artifacts (`REQ-HRV-047.2` / `ATT-9171`).
     */
    fun formatHrvRmssdAxisLabel(value: Double, locale: Locale = Locale.getDefault()): String {
        if (!value.isFinite()) return "0"
        val clean = if (abs(value) < 1e-9) 0.0 else value
        val nearestInt = round(clean)
        return when {
            abs(clean - nearestInt) < 1e-6 && abs(nearestInt) < 9e18 -> "${nearestInt.toLong()}"
            abs(clean - nearestInt) < 1e-6 -> String.format(locale, "%.0f", clean)
            else -> String.format(locale, "%.1f", clean)
        }
    }
}
