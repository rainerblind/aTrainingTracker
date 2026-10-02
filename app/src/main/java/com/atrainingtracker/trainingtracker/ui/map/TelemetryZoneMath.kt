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

import androidx.compose.ui.graphics.Color
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
     * Standard training zone colors matching TTColor palette.
     */
    val ZONE_COLORS = listOf(
        TTColor.Zone1,
        TTColor.Zone2,
        TTColor.Zone3,
        TTColor.Zone4,
        TTColor.Zone5
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
     * Calculates the 5 horizontal zone bands for a given cycling power data range.
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
     * Returns valid threshold boundary guidelines (Z1..Z4 upper limits) that fall strictly
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
     * Maps an instantaneous cycling power value (Watts) to a 1-indexed zone (1..5).
     */
    fun determinePowerZone(power: Double, thresholds: PowerZoneThresholds): Int {
        return when {
            power <= thresholds.z1Max -> 1
            power <= thresholds.z2Max -> 2
            power <= thresholds.z3Max -> 3
            power <= thresholds.z4Max -> 4
            else -> 5
        }
    }
}
