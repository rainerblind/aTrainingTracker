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

import androidx.compose.runtime.Immutable
import com.atrainingtracker.banalservice.BANALService
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.MyUnits
import com.atrainingtracker.trainingtracker.ui.aftermath.zones.HeartRateZoneThresholds
import com.atrainingtracker.trainingtracker.ui.aftermath.zones.PowerZoneThresholds
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Selectable telemetry metrics for the collapsible bottom scrubber panel in full-screen workout map
 * mode (`REQ-MAP-070.2`, `ATT-9030`).
 *
 * Ordered by post-workout route-inspection priority: Elevation first (default when present),
 * followed by effort and kinematic telemetry streams.
 */
enum class FullScreenScrubberMetric {
    ELEVATION,
    HEART_RATE,
    POWER,
    SPEED_PACE,
    CADENCE,
    SLOPE,
    W_PRIME_BAL,
    HRV_DFA_A1,
    HRV_RMSSD
}

/**
 * Formatted live readout payload displayed in the floating scrub pill above the full-screen map
 * scrubber graph (`REQ-MAP-070.3`, `ATT-9030`).
 *
 * @property distanceText Formatted cumulative distance at the scrubbed point (e.g. `"14.20 km"`).
 * @property timeText Formatted elapsed duration when available (e.g. `"38:15"`), or `null` if zero/absent.
 * @property selectedMetricText Formatted primary value for the actively selected metric (e.g. `"285 W"`).
 * @property selectedZoneNumber Optional zone index (`1..5` for HR, `1..7` for Power) when thresholds apply.
 * @property secondaryContextItems Concise available context readouts for non-selected metrics (e.g. Elevation/Grade, HR, Power, Speed/Pace, Cadence).
 */
@Immutable
data class FullScreenScrubReadout(
    val distanceText: String,
    val timeText: String?,
    val selectedMetricText: String,
    val selectedZoneNumber: Int? = null,
    val secondaryContextItems: List<String> = emptyList()
)

/**
 * Returns all [FullScreenScrubberMetric] streams actually present in [path] with valid recorded
 * values (`REQ-MAP-070.2`). Requires at least 2 points to plot a scrubbable profile.
 */
fun resolveAvailableScrubberMetrics(
    path: List<PathPoint>?,
    enableCriticalPowerModel: Boolean = false
): List<FullScreenScrubberMetric> {
    if (path == null || path.size < 2) return emptyList()
    return buildList {
        if (path.any { it.altitude != null && it.altitude.isFinite() }) {
            add(FullScreenScrubberMetric.ELEVATION)
        }
        if (TelemetryMetricUtils.hasHeartRateData(path)) {
            add(FullScreenScrubberMetric.HEART_RATE)
        }
        if (TelemetryMetricUtils.hasPowerData(path)) {
            add(FullScreenScrubberMetric.POWER)
        }
        if (TelemetryMetricUtils.hasSpeedData(path)) {
            add(FullScreenScrubberMetric.SPEED_PACE)
        }
        if (TelemetryMetricUtils.hasCadenceData(path)) {
            add(FullScreenScrubberMetric.CADENCE)
        }
        if (TelemetryMetricUtils.hasSlopeData(path)) {
            add(FullScreenScrubberMetric.SLOPE)
        }
        if (enableCriticalPowerModel && TelemetryMetricUtils.hasWPrimeBalData(path)) {
            add(FullScreenScrubberMetric.W_PRIME_BAL)
        }
        if (TelemetryMetricUtils.hasHrvDfaA1Data(path)) {
            add(FullScreenScrubberMetric.HRV_DFA_A1)
        }
        if (TelemetryMetricUtils.hasHrvRmssdData(path)) {
            add(FullScreenScrubberMetric.HRV_RMSSD)
        }
    }
}

/**
 * Selects the default metric for the full-screen scrubber: [FullScreenScrubberMetric.ELEVATION]
 * when present, otherwise the first available stream, or `null` when [available] is empty.
 */
fun resolveDefaultScrubberMetric(
    available: List<FullScreenScrubberMetric>
): FullScreenScrubberMetric? {
    if (available.isEmpty()) return null
    return if (FullScreenScrubberMetric.ELEVATION in available) {
        FullScreenScrubberMetric.ELEVATION
    } else {
        available.first()
    }
}

/**
 * Maps a non-elevation [FullScreenScrubberMetric] to its corresponding [TelemetryMetricType] for
 * [TelemetryMetricGraph], respecting [BSportType.RUN] pace vs speed presentation.
 */
fun FullScreenScrubberMetric.toTelemetryMetricType(bSportType: BSportType): TelemetryMetricType? = when (this) {
    FullScreenScrubberMetric.ELEVATION -> null
    FullScreenScrubberMetric.HEART_RATE -> TelemetryMetricType.HEART_RATE
    FullScreenScrubberMetric.POWER -> TelemetryMetricType.POWER
    FullScreenScrubberMetric.SPEED_PACE -> if (bSportType == BSportType.RUN) {
        TelemetryMetricType.PACE
    } else {
        TelemetryMetricType.SPEED
    }
    FullScreenScrubberMetric.CADENCE -> TelemetryMetricType.CADENCE
    FullScreenScrubberMetric.SLOPE -> TelemetryMetricType.SLOPE
    FullScreenScrubberMetric.W_PRIME_BAL -> TelemetryMetricType.W_PRIME_BAL
    FullScreenScrubberMetric.HRV_DFA_A1 -> TelemetryMetricType.HRV_DFA_A1
    FullScreenScrubberMetric.HRV_RMSSD -> TelemetryMetricType.HRV_RMSSD
}

/**
 * Linearly interpolates altitude at [distanceMeters] along [path] in O(log N) time, falling back
 * to the nearest valid endpoint altitude when one neighbor is missing.
 */
fun interpolateScrubAltitude(
    path: List<PathPoint>,
    distanceMeters: Double
): Double? {
    if (path.isEmpty()) return null
    val floorIdx = TelemetryChartSearch.findFloorIndex(
        points = path,
        targetValue = distanceMeters,
        isTimeDomain = false
    ).coerceAtLeast(0)
    val left = path[floorIdx]
    val right = path.getOrNull(floorIdx + 1)
    return if (
        right != null &&
        right.distance > left.distance &&
        left.altitude != null && left.altitude.isFinite() &&
        right.altitude != null && right.altitude.isFinite()
    ) {
        val factor = ((distanceMeters - left.distance) / (right.distance - left.distance)).coerceIn(0.0, 1.0)
        left.altitude + factor * (right.altitude - left.altitude)
    } else {
        left.altitude?.takeIf { it.isFinite() } ?: right?.altitude?.takeIf { it.isFinite() }
    }
}

/**
 * Pure formatter constructing the live floating scrub readout payload (`Distance · Time · Selected Metric`
 * plus non-duplicating available secondary context) for [point] (`REQ-MAP-070.3`).
 */
fun formatFullScreenScrubReadout(
    point: PathPoint,
    selectedMetric: FullScreenScrubberMetric,
    bSportType: BSportType,
    unit: MyUnits = MyUnits.METRIC,
    interpolatedAltitude: Double? = point.altitude,
    hrZoneThresholds: HeartRateZoneThresholds? = null,
    powerZoneThresholds: PowerZoneThresholds? = null,
    locale: Locale = Locale.getDefault()
): FullScreenScrubReadout {
    val distanceText = formatScrubDistance(point.distance, unit, locale)
    val timeText = point.timeSec.takeIf { it > 0L }?.let { ElevationProfileZoomMath.formatTimeTick(it) }

    val altitudeValue = interpolatedAltitude?.takeIf { it.isFinite() } ?: point.altitude?.takeIf { it.isFinite() }
    val slopeValue = point.slope?.takeIf { it.isFinite() }
    val hrValue = point.hr?.takeIf { it > 0 }
    val powerValue = point.power?.takeIf { it >= 0 }
    val speedValue = point.speedMps?.takeIf { it.isFinite() && it > 0.0 }
    val cadenceValue = point.cadence?.takeIf { it > 0 }

    val elevationWithSlopeText = altitudeValue?.let { alt ->
        val altStr = formatScrubAltitude(alt, unit, locale)
        val slopeSuffix = slopeValue?.let { " (${formatScrubSlope(it, locale)})" }.orEmpty()
        "$altStr$slopeSuffix"
    }
    val hrText = hrValue?.let { "$it bpm" }
    val powerText = powerValue?.let { "$it W" }
    val speedPaceText = speedValue?.let { formatScrubSpeedOrPace(it, bSportType, unit, locale) }
    val cadenceText = cadenceValue?.let { "$it rpm" }
    val slopeOnlyText = slopeValue?.let { formatScrubSlope(it, locale) }

    val selectedZoneNumber: Int? = when (selectedMetric) {
        FullScreenScrubberMetric.HEART_RATE -> hrValue?.let { hr ->
            hrZoneThresholds?.takeIf { TelemetryZoneMath.isValidHeartRateThresholds(it) }?.let { thresholds ->
                TelemetryZoneMath.determineHeartRateZone(hr.toDouble(), thresholds)
            }
        }
        FullScreenScrubberMetric.POWER -> powerValue?.let { watts ->
            powerZoneThresholds?.takeIf { TelemetryZoneMath.isValidPowerThresholds(it) }?.let { thresholds ->
                TelemetryZoneMath.determinePowerZone(watts.toDouble(), thresholds)
            }
        }
        else -> null
    }

    val selectedMetricText = when (selectedMetric) {
        FullScreenScrubberMetric.ELEVATION -> elevationWithSlopeText ?: "--"
        FullScreenScrubberMetric.HEART_RATE -> hrText ?: "--"
        FullScreenScrubberMetric.POWER -> powerText ?: "--"
        FullScreenScrubberMetric.SPEED_PACE -> speedPaceText ?: "--"
        FullScreenScrubberMetric.CADENCE -> cadenceText ?: "--"
        FullScreenScrubberMetric.SLOPE -> slopeOnlyText ?: "--"
        FullScreenScrubberMetric.W_PRIME_BAL -> point.wPrimeBalKj?.takeIf { it.isFinite() && it >= 0.0 }
            ?.let { String.format(locale, "%.1f kJ", it) } ?: "--"
        FullScreenScrubberMetric.HRV_DFA_A1 -> point.hrvDfaA1?.takeIf { it.isFinite() }
            ?.let { String.format(locale, "%.2f", it) } ?: "--"
        FullScreenScrubberMetric.HRV_RMSSD -> point.hrvRmssd?.takeIf { it.isFinite() && it >= 0.0 }
            ?.let { String.format(locale, "%.1f ms", it) } ?: "--"
    }

    val secondaryContext = buildList {
        if (selectedMetric != FullScreenScrubberMetric.ELEVATION && altitudeValue != null) {
            if (selectedMetric == FullScreenScrubberMetric.SLOPE) {
                add(formatScrubAltitude(altitudeValue, unit, locale))
            } else if (elevationWithSlopeText != null) {
                add(elevationWithSlopeText)
            }
        } else if (selectedMetric != FullScreenScrubberMetric.ELEVATION &&
            selectedMetric != FullScreenScrubberMetric.SLOPE &&
            slopeOnlyText != null
        ) {
            add(slopeOnlyText)
        }
        if (selectedMetric != FullScreenScrubberMetric.HEART_RATE && hrText != null) {
            add(hrText)
        }
        if (selectedMetric != FullScreenScrubberMetric.POWER && powerText != null) {
            add(powerText)
        }
        if (selectedMetric != FullScreenScrubberMetric.SPEED_PACE && speedPaceText != null) {
            add(speedPaceText)
        }
        if (selectedMetric != FullScreenScrubberMetric.CADENCE && cadenceText != null && size < 4) {
            add(cadenceText)
        }
    }.take(4)

    return FullScreenScrubReadout(
        distanceText = distanceText,
        timeText = timeText,
        selectedMetricText = selectedMetricText,
        selectedZoneNumber = selectedZoneNumber,
        secondaryContextItems = secondaryContext
    )
}

internal fun formatScrubDistance(
    distanceMeters: Double,
    unit: MyUnits,
    locale: Locale = Locale.getDefault()
): String {
    if (!distanceMeters.isFinite()) return "--"
    return when (unit) {
        MyUnits.METRIC -> {
            if (distanceMeters < 1000.0) {
                String.format(locale, "%.0f m", distanceMeters)
            } else {
                String.format(locale, "%.2f km", distanceMeters / 1000.0)
            }
        }
        MyUnits.IMPERIAL -> {
            val miles = distanceMeters / BANALService.METER_PER_MILE
            String.format(locale, "%.2f mi", miles)
        }
    }
}

internal fun formatScrubAltitude(
    altitudeMeters: Double,
    unit: MyUnits,
    locale: Locale = Locale.getDefault()
): String {
    if (!altitudeMeters.isFinite()) return "--"
    return when (unit) {
        MyUnits.METRIC -> String.format(locale, "%.0f m", altitudeMeters)
        MyUnits.IMPERIAL -> String.format(locale, "%.0f ft", altitudeMeters / BANALService.METER_PER_FOOT)
    }
}

internal fun formatScrubSlope(
    slopePercent: Double,
    locale: Locale = Locale.getDefault()
): String {
    if (!slopePercent.isFinite()) return "--"
    val sign = if (slopePercent > 0.0) "+" else ""
    return "$sign${String.format(locale, "%.1f", slopePercent)}%"
}

internal fun formatScrubSpeedOrPace(
    speedMps: Double,
    bSportType: BSportType,
    unit: MyUnits,
    locale: Locale = Locale.getDefault()
): String {
    if (!speedMps.isFinite() || speedMps <= 0.0) return "--"
    return if (bSportType == BSportType.RUN) {
        if (speedMps < 0.55) {
            "--"
        } else {
            val unitMeters = if (unit == MyUnits.IMPERIAL) BANALService.METER_PER_MILE else 1000.0
            val totalSeconds = (unitMeters / speedMps).roundToInt()
            val mins = totalSeconds / 60
            val secs = totalSeconds % 60
            val suffix = if (unit == MyUnits.IMPERIAL) "/mi" else "/km"
            String.format(locale, "%d:%02d %s", mins, secs, suffix)
        }
    } else {
        when (unit) {
            MyUnits.METRIC -> String.format(locale, "%.1f km/h", speedMps * 3.6)
            MyUnits.IMPERIAL -> String.format(locale, "%.1f mph", speedMps * 3600.0 / BANALService.METER_PER_MILE)
        }
    }
}
