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

import com.atrainingtracker.banalservice.BANALService
import com.atrainingtracker.banalservice.sensor.formater.PaceFormatter
import com.atrainingtracker.banalservice.sensor.formater.SpeedFormatter
import com.atrainingtracker.trainingtracker.MyUnits
import com.atrainingtracker.trainingtracker.settings.TuningPreferencesDefaults
import com.atrainingtracker.trainingtracker.ui.aftermath.zones.HeartRateZoneThresholds
import com.atrainingtracker.trainingtracker.ui.aftermath.zones.PowerZoneThresholds
import java.util.Locale
import kotlin.math.abs

/**
 * Utility functions for extracting and formatting continuous telemetry metric values
 * (REQ-UI-206 / ATT-1740, REQ-UI-260 / ATT-2031).
 */
object TelemetryMetricUtils {

    /**
     * Efficiently searches for the nearest [PathPoint] to the given [targetValue] along the timeline
     * in O(log N) time using binary search (REQ-UI-260 / ATT-2031).
     *
     * Exploits the monotonic ordering of [points] by distance (default) or elapsed seconds ([isTimeDomain] = true).
     * Guarantees 100% numerical parity with linear `minByOrNull` nearest-neighbor resolution,
     * including identical tie-breaking behavior (choosing the earlier point if distances are identical).
     *
     * @param points Chronologically recorded trackpoints (must be sorted monotonically).
     * @param targetValue The target distance in meters, or target time in seconds.
     * @param isTimeDomain When true, matches against [PathPoint.timeSec]; when false, matches against [PathPoint.distance].
     * @return The closest [PathPoint] in [points], or null if [points] is empty.
     */
    fun findNearestPoint(
        points: List<PathPoint>,
        targetValue: Double,
        isTimeDomain: Boolean = false
    ): PathPoint? {
        if (points.isEmpty()) return null
        if (points.size == 1) return points[0]

        if (isTimeDomain) {
            val target = targetValue.toLong()
            if (target <= points.first().timeSec) return points.first()
            if (target >= points.last().timeSec) return points.last()

            val index = points.binarySearch { it.timeSec.compareTo(target) }
            if (index >= 0) return points[index]

            val insertionPoint = -(index + 1)
            val leftPoint = points[insertionPoint - 1]
            val rightPoint = points[insertionPoint]

            val dLeft = abs(leftPoint.timeSec - targetValue)
            val dRight = abs(rightPoint.timeSec - targetValue)
            return if (dRight < dLeft) rightPoint else leftPoint
        } else {
            if (targetValue <= points.first().distance) return points.first()
            if (targetValue >= points.last().distance) return points.last()

            val index = points.binarySearch { it.distance.compareTo(targetValue) }
            if (index >= 0) return points[index]

            val insertionPoint = -(index + 1)
            val leftPoint = points[insertionPoint - 1]
            val rightPoint = points[insertionPoint]

            val dLeft = abs(leftPoint.distance - targetValue)
            val dRight = abs(rightPoint.distance - targetValue)
            return if (dRight < dLeft) rightPoint else leftPoint
        }
    }

    /**
     * Checks whether valid heart rate samples exist in [path].
     */
    fun hasHeartRateData(path: List<PathPoint>?): Boolean {
        if (path.isNullOrEmpty()) return false
        return path.any { it.hr != null && it.hr > 0 }
    }

    /**
     * Checks whether valid speed samples exist in [path].
     */
    fun hasSpeedData(path: List<PathPoint>?): Boolean {
        if (path.isNullOrEmpty()) return false
        return path.any { it.speedMps != null && it.speedMps > 0.0 }
    }

    /**
     * Checks whether valid cycling power samples exist in [path].
     */
    fun hasPowerData(path: List<PathPoint>?): Boolean {
        if (path.isNullOrEmpty()) return false
        return path.any { it.power != null && it.power > 0 }
    }

    /**
     * Extracts scalar value for a given [PathPoint] according to [metricType] and [unit].
     * Returns null if sample is invalid or unrecorded.
     */
    fun extractMetricValue(
        point: PathPoint,
        metricType: TelemetryMetricType,
        unit: MyUnits,
        paceCeilingMinKm: Float = TuningPreferencesDefaults.DEFAULT_PACE_CEILING_MIN_KM
    ): Double? {
        return when (metricType) {
            TelemetryMetricType.HEART_RATE -> {
                point.hr?.takeIf { it > 0 }?.toDouble()
            }
            TelemetryMetricType.SPEED -> {
                point.speedMps?.takeIf { it > 0.0 }?.let { mps ->
                    if (unit == MyUnits.METRIC) mps * 3.6 else mps * 2.236936
                }
            }
            TelemetryMetricType.PACE -> {
                point.speedMps?.takeIf { it >= 0.55 }?.let { mps ->
                    val secPerKm = 1000.0 / mps
                    val secPerUnit = if (unit == MyUnits.METRIC) {
                        secPerKm
                    } else {
                        secPerKm * (BANALService.METER_PER_MILE / 1000.0)
                    }
                    val effectiveCeiling = if (unit == MyUnits.METRIC) {
                        paceCeilingMinKm.toDouble()
                    } else {
                        paceCeilingMinKm.toDouble() * (BANALService.METER_PER_MILE / 1000.0)
                    }
                    (secPerUnit / 60.0).coerceIn(effectiveCeiling, 20.0)
                }
            }
            TelemetryMetricType.POWER -> {
                point.power?.takeIf { it >= 0 }?.toDouble()
            }
        }
    }

    /**
     * Formats a pace value in decimal minutes (e.g. 4.5) to athletic "mm:ss" format (e.g. "4:30").
     * (REQ-UI-219 / ATT-1818)
     */
    fun formatPaceMinutes(paceMinutes: Double): String {
        val totalSec = kotlin.math.round(paceMinutes * 60.0).toInt().coerceAtLeast(0)
        val min = totalSec / 60
        val sec = totalSec % 60
        return String.format(Locale.US, "%d:%02d", min, sec)
    }

    /**
     * Formats instantaneous or extreme metric value for display.
     */
    fun formatValue(
        value: Double?,
        metricType: TelemetryMetricType,
        unit: MyUnits,
        speedFormatter: SpeedFormatter,
        paceFormatter: PaceFormatter,
        hrZoneThresholds: HeartRateZoneThresholds? = null,
        powerZoneThresholds: PowerZoneThresholds? = null
    ): String {
        if (value == null) return "--"
        return when (metricType) {
            TelemetryMetricType.HEART_RATE -> {
                val base = "${value.toInt()} bpm"
                if (hrZoneThresholds != null) {
                    val zone = TelemetryZoneMath.determineHeartRateZone(value, hrZoneThresholds)
                    "$base • Z$zone"
                } else {
                    base
                }
            }
            TelemetryMetricType.SPEED -> {
                val mps = if (unit == MyUnits.METRIC) value / 3.6 else value / 2.236936
                speedFormatter.format_with_units(mps)
            }
            TelemetryMetricType.PACE -> {
                val secPerUnit = value * 60.0
                val spm = if (unit == MyUnits.METRIC) {
                    secPerUnit / 1000.0
                } else {
                    secPerUnit / BANALService.METER_PER_MILE
                }
                paceFormatter.format_with_units(spm)
            }
            TelemetryMetricType.POWER -> {
                val base = "${value.toInt()} W"
                if (powerZoneThresholds != null) {
                    val zone = TelemetryZoneMath.determinePowerZone(value, powerZoneThresholds)
                    "$base • Z$zone"
                } else {
                    base
                }
            }
        }
    }

    /**
     * Formats an intermediate milestone distance label without repeating units.
     * (REQ-UI-220 / ATT-1819)
     */
    fun formatMilestoneLabel(dist: Double, visibleSpan: Double, unit: MyUnits): String {
        return if (unit == MyUnits.METRIC) {
            if (visibleSpan < 1500) {
                "${dist.toInt()}m"
            } else if (dist % 1000.0 != 0.0) {
                String.format(Locale.US, "%.1f", dist / 1000.0)
            } else {
                "${(dist / 1000.0).toInt()}"
            }
        } else {
            val miles = dist / BANALService.METER_PER_MILE
            if (miles % 1.0 != 0.0) {
                String.format(Locale.US, "%.1f", miles)
            } else {
                "${miles.toInt()}"
            }
        }
    }

    /**
     * Determines whether an intermediate milestone label satisfies boundary clearance
     * and minimum spacing clearance from the previously rendered label.
     * (REQ-UI-220 / ATT-1819)
     */
    fun shouldRenderMilestoneLabel(
        labelLeft: Float,
        labelRight: Float,
        lastDrawnRightX: Float,
        startBoundaryThreshold: Float,
        endBoundaryThreshold: Float,
        minSpacing: Float = 36f
    ): Boolean {
        val startClearance = labelLeft >= startBoundaryThreshold
        val endClearance = labelRight <= endBoundaryThreshold
        val spacingClearance = labelLeft >= lastDrawnRightX + minSpacing
        return startClearance && endClearance && spacingClearance
    }
}
