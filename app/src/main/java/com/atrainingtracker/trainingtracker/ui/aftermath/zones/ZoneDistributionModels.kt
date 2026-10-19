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

package com.atrainingtracker.trainingtracker.ui.aftermath.zones

import androidx.compose.ui.graphics.Color

/**
 * Upper heart rate bounds for Zones 1 through 4.
 * Zone 5 corresponds to HR > z4Max.
 */
data class HeartRateZoneThresholds(
    val z1Max: Int,
    val z2Max: Int,
    val z3Max: Int,
    val z4Max: Int
)

/**
 * Upper cycling power bounds in Watts for Zones 1 through 6.
 * Zone 7 corresponds to Power > z6Max (Neuromuscular Sprint Power).
 */
data class PowerZoneThresholds(
    val z1Max: Int,
    val z2Max: Int,
    val z3Max: Int,
    val z4Max: Int,
    val z5Max: Int,
    val z6Max: Int
)

/**
 * Timestamped sensor sample used for distribution calculation.
 */
data class ZoneSample(
    val timeActiveSec: Long,
    val value: Int
)

/**
 * Metric breakdown for an individual training zone.
 */
data class ZoneTimeEntry(
    val zoneIndex: Int, // 1..5
    val zoneLabelResId: Int,
    val durationSec: Long,
    val percentage: Float,
    val color: Color
)

/**
 * Individual frequency bin for fine-grained telemetry histograms.
 *
 * @property isOverflow True if this bin represents a folded rightmost high-wattage sprint overflow bar.
 * @property isReturn True if this bin represents a folded leftmost return bar navigating back to main view.
 */
data class TelemetryHistogramBin(
    val binIndex: Int,
    val rangeMin: Int,
    val rangeMax: Int,
    val durationSec: Long,
    val percentage: Float,
    val zoneIndex: Int, // 1..5
    val color: Color,
    val isOverflow: Boolean = false,
    val isReturn: Boolean = false
)

/**
 * Complete fine-grained telemetry histogram dataset for a workout metric (HR or Power).
 *
 * @property tailCutoff Power threshold (in Watts) where rare sprint spikes were folded into an overflow bar,
 *                      or null if the histogram is rendered linearly across the full data range.
 * @property sprintTail Linked dataset representing the expanded high-power sprint tail view,
 *                      or null if no sprint tail was folded.
 */
data class TelemetryHistogramData(
    val binWidth: Int,
    val dataMin: Int,
    val dataMax: Int,
    val totalActiveTimeSec: Long,
    val bins: List<TelemetryHistogramBin>,
    val tailCutoff: Int? = null,
    val sprintTail: TelemetryHistogramData? = null
)

/**
 * Display modes for zone distribution cards (REQ-UI-372).
 */
enum class ZoneCardDisplayMode {
    STACKED_BAR,
    COLUMNS,
    HISTOGRAM,
    @Deprecated("Legacy alias for COLUMNS", ReplaceWith("COLUMNS"))
    FIVE_ZONES;

    /**
     * Resolves the effective display mode enforcing Tier 3 domain fallback (REQ-UI-372.8):
     * If HISTOGRAM is requested but fine-grained histogram telemetry is unavailable, falls back to COLUMNS.
     */
    fun resolveWithHistogramFallback(hasHistogram: Boolean): ZoneCardDisplayMode =
        if (this == HISTOGRAM && !hasHistogram) COLUMNS else this
}

/**
 * Aggregate 5-zone time distribution data for a workout, optionally containing fine-grained histogram data.
 */
data class ZoneDistributionData(
    val totalActiveTimeSec: Long,
    val entries: List<ZoneTimeEntry>,
    val histogram: TelemetryHistogramData? = null
)
