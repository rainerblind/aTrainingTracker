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
}
