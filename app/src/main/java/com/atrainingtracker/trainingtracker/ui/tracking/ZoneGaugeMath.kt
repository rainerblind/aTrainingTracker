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
 * High-performance, pure mathematical calculation engine for the visual training zone gauge bar
 * (REQ-UI-376, REQ-UI-390).
 *
 * Sizes interior closed zones (Z2..Z_{K-1}) proportionally to their configured span (C_i - C_{i-1}),
 * clips the outer open-ended zones (Z1 and Z_K) to a 10% boundary span (w_cap = (10 / 95) * S_inner),
 * and computes strictly linear needle positions across [X_min, X_max].
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
     * Ratio of each outer clipped boundary zone (Z1 and Z_K) relative to the total interior span S_inner.
     * For standard Coggan 7-zone power (Z1max=55% FTP, Z6max=150% FTP -> S_inner = 95% FTP),
     * w_cap = (10.0 / 95.0) * 95% FTP = 10% FTP, yielding X_min = 45% FTP, X_max = 160% FTP, S_total = 115% FTP.
     */
    const val BOUNDARY_CAP_RATIO = 10.0 / 95.0

    /**
     * Computes the normalized visual width weights (summing to 1.0f) for all K zone segments.
     *
     * @param cutoffs Upper cutoff thresholds [C_1, ..., C_{K-1}] (4 for HR, 6 for Power).
     * @param isPower True for 7-zone Cycling Power, false for 5-zone Heart Rate.
     * @return List of K positive Float weights summing to 1.0f.
     */
    fun calculateSegmentWeights(
        cutoffs: List<Int>,
        isPower: Boolean = false
    ): List<Float> {
        val numZones = if (isPower) 7 else 5
        val requiredCutoffs = numZones - 1
        if (cutoffs.size < requiredCutoffs) {
            return List(numZones) { 1.0f / numZones }
        }

        val interiorSpans = DoubleArray(requiredCutoffs - 1) { idx ->
            maxOf(1.0, (cutoffs[idx + 1] - cutoffs[idx]).toDouble())
        }
        val sInner = interiorSpans.sum()
        val wCap = BOUNDARY_CAP_RATIO * sInner
        val sTotal = sInner + 2.0 * wCap

        return buildList(numZones) {
            add((wCap / sTotal).toFloat())
            for (span in interiorSpans) {
                add((span / sTotal).toFloat())
            }
            add((wCap / sTotal).toFloat())
        }
    }

    /**
     * Calculates the strictly linear normalized needle position [0.0..1.0] across [X_min, X_max] (REQ-UI-390.1).
     *
     * @param value The instantaneous reading (bpm or Watts). Returns null if value is null, NaN, or non-finite.
     * @param cutoffs The upper cutoff thresholds for the closed zones:
     *                For HR (5 zones): list of 4 cutoffs [Z1max, Z2max, Z3max, Z4max].
     *                For Power (7 zones): list of 6 cutoffs [Z1max, Z2max, Z3max, Z4max, Z5max, Z6max].
     * @param hrMax Optional athlete HR max (retained for binary/source signature compatibility).
     * @param ftp Optional athlete FTP (retained for binary/source signature compatibility).
     * @param isPower True if calculating for Cycling Power (7 zones), false for Heart Rate (5 zones).
     * @return Float in [0.0..1.0] representing strictly linear horizontal needle fraction, or null if invalid.
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

        var sInner = 0.0
        for (idx in 0 until requiredCutoffs - 1) {
            sInner += maxOf(1.0, (cutoffs[idx + 1] - cutoffs[idx]).toDouble())
        }

        val wCap = BOUNDARY_CAP_RATIO * sInner
        val c1 = cutoffs[0].toDouble()
        val xMin = c1 - wCap
        val xMax = c1 + sInner + wCap
        val sTotal = xMax - xMin

        if (sTotal <= 0.0) {
            return 0.0f
        }

        return ((value - xMin) / sTotal).coerceIn(0.0, 1.0).toFloat()
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
        val segmentWeights = calculateSegmentWeights(cutoffs, isPower)
        val needleFraction = calculateNeedleFraction(value, cutoffs, hrMax, ftp, isPower)
        val activeZoneIndex = calculateActiveZoneIndex(value, cutoffs, isPower)

        return ZoneGaugeState(
            segmentColors = segmentColors,
            needleFraction = needleFraction,
            activeZoneIndex = activeZoneIndex,
            segmentWeights = segmentWeights
        )
    }
}
