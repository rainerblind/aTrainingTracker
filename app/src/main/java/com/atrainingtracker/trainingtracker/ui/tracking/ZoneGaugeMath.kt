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

package com.atrainingtracker.trainingtracker.ui.tracking

import androidx.compose.ui.graphics.Color
import com.atrainingtracker.trainingtracker.settings.SettingsDataStore
import com.atrainingtracker.trainingtracker.ui.theme.TTColor

/**
 * High-performance, pure mathematical calculation engine for the visual training zone gauge bar (REQ-UI-376).
 *
 * Partitions the horizontal gauge track into K contiguous equal-width segments (K=5 for Heart Rate,
 * K=7 for Coggan Cycling Power). Computes precise intra-zone needle positions, handles dynamic
 * open-ended top zone ceilings (U5 for HR, U7 for Power capped at 2500W), and provides safe null/stale handling.
 */
object ZoneGaugeMath {

    val HR_SEGMENT_COLORS: List<Color> = listOf(
        TTColor.Zone1,
        TTColor.Zone2,
        TTColor.Zone3,
        TTColor.Zone4,
        TTColor.Zone5
    )

    val POWER_SEGMENT_COLORS: List<Color> = listOf(
        TTColor.Zone1,
        TTColor.Zone2,
        TTColor.Zone3,
        TTColor.Zone4,
        TTColor.Zone5,
        TTColor.Zone6,
        TTColor.Zone7
    )

    const val HR_BASELINE_LOWER_BOUND = 30.0
    const val POWER_BASELINE_LOWER_BOUND = 0.0
    const val POWER_PHYSICAL_CEILING = 2500.0

    /**
     * Calculates the normalized needle position [0.0..1.0] across K equal-width zone segments.
     *
     * @param value The instantaneous reading (bpm or Watts). Returns null if value is null, NaN, or non-finite.
     * @param cutoffs The upper cutoff thresholds for the closed zones:
     *                For HR (5 zones): list of 4 cutoffs [Z1max, Z2max, Z3max, Z4max].
     *                For Power (7 zones): list of 6 cutoffs [Z1max, Z2max, Z3max, Z4max, Z5max, Z6max].
     * @param hrMax Optional athlete HR max for dynamic open-ended Z5 ceiling.
     * @param ftp Optional athlete FTP for dynamic open-ended Z7 ceiling.
     * @param isPower True if calculating for Cycling Power (7 zones), false for Heart Rate (5 zones).
     * @return Float in [0.0..1.0] representing horizontal needle fraction, or null if invalid or unconfigured.
     */
    fun calculateNeedleFraction(
        value: Double?,
        cutoffs: List<Int>,
        hrMax: Int? = null,
        ftp: Int? = null,
        isPower: Boolean = false
    ): Float? {
        if (value == null || !value.isFinite() || cutoffs.isEmpty()) {
            return null
        }

        val numZones = if (isPower) 7 else 5
        val requiredCutoffs = numZones - 1
        if (cutoffs.size < requiredCutoffs) {
            return null
        }

        val segmentWidth = 1.0 / numZones
        val baselineLower = if (isPower) POWER_BASELINE_LOWER_BOUND else HR_BASELINE_LOWER_BOUND

        // Below baseline
        if (value <= baselineLower) {
            return 0.0f
        }

        // Zone 1: [baselineLower, cutoffs[0]]
        val z1Max = cutoffs[0].toDouble()
        if (value <= z1Max) {
            val span = z1Max - baselineLower
            val intraFraction = if (span > 0) (value - baselineLower) / span else 0.0
            return (intraFraction.coerceIn(0.0, 1.0) * segmentWidth).toFloat()
        }

        // Intermediate interior zones: i = 1 until requiredCutoffs - 1
        for (i in 1 until requiredCutoffs) {
            val lower = cutoffs[i - 1].toDouble()
            val upper = cutoffs[i].toDouble()
            if (value <= upper) {
                val span = upper - lower
                val intraFraction = if (span > 0) (value - lower) / span else 0.0
                val needlePos = (i + intraFraction.coerceIn(0.0, 1.0)) * segmentWidth
                return needlePos.coerceIn(0.0, 1.0).toFloat()
            }
        }

        // Top open-ended zone (Zone 5 for HR, Zone 7 for Power):
        val topLower = cutoffs[requiredCutoffs - 1].toDouble()
        val topUpper = if (isPower) {
            val dynamicCeiling = maxOf(topLower + 300.0, (ftp?.toDouble() ?: 250.0) * 2.0)
            minOf(POWER_PHYSICAL_CEILING, dynamicCeiling)
        } else {
            val nominalCeiling = hrMax?.toDouble() ?: (topLower + 25.0)
            maxOf(nominalCeiling, topLower + 25.0)
        }

        val topSpan = topUpper - topLower
        val topIntraFraction = if (topSpan > 0) (value - topLower) / topSpan else 0.0
        val topNeedlePos = (requiredCutoffs + topIntraFraction.coerceIn(0.0, 1.0)) * segmentWidth
        return topNeedlePos.coerceIn(0.0, 1.0).toFloat()
    }

    /**
     * Determines the 1-based active zone index (1..K), or null if value is invalid.
     */
    fun calculateActiveZoneIndex(
        value: Double?,
        cutoffs: List<Int>,
        isPower: Boolean = false
    ): Int? {
        if (value == null || !value.isFinite() || cutoffs.isEmpty()) {
            return null
        }
        val numZones = if (isPower) 7 else 5
        val requiredCutoffs = numZones - 1
        if (cutoffs.size < requiredCutoffs) return null

        for (i in 0 until requiredCutoffs) {
            if (value <= cutoffs[i]) {
                return i + 1
            }
        }
        return numZones
    }

    /**
     * Factory function creating the full ZoneGaugeState for the given zone profile and telemetry reading.
     */
    fun calculateZoneGaugeState(
        value: Double?,
        zoneType: SettingsDataStore.ZoneType,
        cutoffs: List<Int>,
        hrMax: Int? = null,
        ftp: Int? = null
    ): ZoneGaugeState {
        val isPower = (zoneType == SettingsDataStore.ZoneType.PWR_BIKE)
        val segmentColors = if (isPower) POWER_SEGMENT_COLORS else HR_SEGMENT_COLORS
        val needleFraction = calculateNeedleFraction(value, cutoffs, hrMax, ftp, isPower)
        val activeZoneIndex = calculateActiveZoneIndex(value, cutoffs, isPower)

        return ZoneGaugeState(
            segmentColors = segmentColors,
            needleFraction = needleFraction,
            activeZoneIndex = activeZoneIndex
        )
    }
}
