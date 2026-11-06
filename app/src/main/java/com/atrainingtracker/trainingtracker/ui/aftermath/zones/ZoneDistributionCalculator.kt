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

import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.ui.map.ElevationSmoothingMath
import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import com.atrainingtracker.trainingtracker.ui.theme.TTColor

/**
 * Pure calculation engine for classifying workout telemetry into 5 training zones
 * and computing cumulative time-in-zones and percentages.
 */
object ZoneDistributionCalculator {

    private const val MAX_DELTA_T_SEC = 5L
    private const val DEFAULT_LAST_SAMPLE_DURATION_SEC = 1L

    private val HR_ZONE_LABELS = intArrayOf(
        R.string.zone_1_label,
        R.string.zone_2_label,
        R.string.zone_3_label,
        R.string.zone_4_label,
        R.string.zone_5_label
    )

    private val HR_ZONE_COLORS = arrayOf(
        TTColor.Zone1,
        TTColor.Zone2,
        TTColor.Zone3,
        TTColor.Zone4,
        TTColor.Zone5
    )

    private val POWER_ZONE_LABELS = intArrayOf(
        R.string.zone_1_label,
        R.string.zone_2_label,
        R.string.zone_3_label,
        R.string.zone_4_label,
        R.string.zone_5_label,
        R.string.zone_6_label,
        R.string.zone_7_label
    )

    private val POWER_ZONE_COLORS = arrayOf(
        TTColor.Zone1,
        TTColor.Zone2,
        TTColor.Zone3,
        TTColor.Zone4,
        TTColor.Zone5,
        TTColor.Zone6,
        TTColor.Zone7
    )

    private val GRADE_ZONE_LABELS = intArrayOf(
        R.string.grade_legend_under_2,
        R.string.grade_legend_2_to_5,
        R.string.grade_legend_5_to_10,
        R.string.grade_legend_10_to_15,
        R.string.grade_legend_15_to_20,
        R.string.grade_legend_20_to_25,
        R.string.grade_legend_over_25
    )

    private val GRADE_ZONE_COLORS = arrayOf(
        TTColor.Zone1,
        TTColor.Zone2,
        TTColor.Zone3,
        TTColor.Zone4,
        TTColor.Zone5,
        TTColor.Zone6,
        TTColor.Zone7
    )

    private val GRADE_BADGE_LABELS = arrayOf(
        "<2%",
        "≥2%",
        "≥5%",
        "≥10%",
        "≥15%",
        "≥20%",
        "≥25%"
    )

    /**
     * Calculates 5-zone distribution data from heart rate samples and athlete thresholds.
     *
     * @param samples List of active time and HR samples.
     * @param thresholds Athlete HR thresholds for Zones 1 through 4.
     * @return [ZoneDistributionData] containing exactly 5 entries, or null if telemetry is insufficient.
     */
    fun calculateHeartRateDistribution(
        samples: List<ZoneSample>,
        thresholds: HeartRateZoneThresholds
    ): ZoneDistributionData? {
        val validSamples = samples.filter { it.value > 0 }
        if (validSamples.isEmpty()) return null

        val zoneDurations = LongArray(5)

        val isDegenerate = validSamples.size > 1 && validSamples.first().timeActiveSec == validSamples.last().timeActiveSec

        for (i in validSamples.indices) {
            val sample = validSamples[i]
            val zoneIdx = determineHeartRateZone(sample.value, thresholds)

            val dt = if (isDegenerate) {
                1L
            } else if (i < validSamples.size - 1) {
                val nextSample = validSamples[i + 1]
                val rawDt = nextSample.timeActiveSec - sample.timeActiveSec
                when {
                    rawDt < 0L -> 0L
                    rawDt > MAX_DELTA_T_SEC -> MAX_DELTA_T_SEC
                    else -> rawDt
                }
            } else {
                DEFAULT_LAST_SAMPLE_DURATION_SEC
            }

            zoneDurations[zoneIdx] += dt
        }

        val totalDuration = zoneDurations.sum()
        if (totalDuration <= 0L) return null

        val entries = (0..4).map { idx ->
            val duration = zoneDurations[idx]
            val pct = if (totalDuration > 0L) (duration.toFloat() / totalDuration) * 100f else 0f
            ZoneTimeEntry(
                zoneIndex = idx + 1,
                zoneLabelResId = HR_ZONE_LABELS[idx],
                durationSec = duration,
                percentage = pct,
                color = HR_ZONE_COLORS[idx]
            )
        }

        val histogram = TelemetryHistogramCalculator.calculateHeartRateHistogram(validSamples, thresholds)

        return ZoneDistributionData(
            totalActiveTimeSec = totalDuration,
            entries = entries,
            histogram = histogram
        )
    }

    /**
     * Calculates 7-zone Coggan distribution data from power samples and athlete thresholds.
     *
     * @param samples List of active time and power (Watts) samples.
     * @param thresholds Athlete power thresholds for Zones 1 through 6.
     * @return [ZoneDistributionData] containing exactly 7 entries, or null if telemetry is insufficient.
     */
    fun calculatePowerDistribution(
        samples: List<ZoneSample>,
        thresholds: PowerZoneThresholds
    ): ZoneDistributionData? {
        val hasPositivePower = samples.any { it.value > 0 }
        if (!hasPositivePower) return null
        val validSamples = samples.filter { it.value >= 0 }

        val isDegenerate = validSamples.size > 1 && validSamples.first().timeActiveSec == validSamples.last().timeActiveSec
        val zoneDurations = LongArray(7)

        for (i in validSamples.indices) {
            val sample = validSamples[i]
            val zoneIdx = determinePowerZone(sample.value, thresholds)

            val dt = if (isDegenerate) {
                1L
            } else if (i < validSamples.size - 1) {
                val nextSample = validSamples[i + 1]
                val rawDt = nextSample.timeActiveSec - sample.timeActiveSec
                when {
                    rawDt < 0L -> 0L
                    rawDt > MAX_DELTA_T_SEC -> MAX_DELTA_T_SEC
                    else -> rawDt
                }
            } else {
                DEFAULT_LAST_SAMPLE_DURATION_SEC
            }

            zoneDurations[zoneIdx] += dt
        }

        val totalDuration = zoneDurations.sum()
        if (totalDuration <= 0L) return null

        val entries = (0..6).map { idx ->
            val duration = zoneDurations[idx]
            val pct = if (totalDuration > 0L) (duration.toFloat() / totalDuration) * 100f else 0f
            ZoneTimeEntry(
                zoneIndex = idx + 1,
                zoneLabelResId = POWER_ZONE_LABELS[idx],
                durationSec = duration,
                percentage = pct,
                color = POWER_ZONE_COLORS[idx]
            )
        }

        val histogram = TelemetryHistogramCalculator.calculatePowerHistogram(validSamples, thresholds)

        return ZoneDistributionData(
            totalActiveTimeSec = totalDuration,
            entries = entries,
            histogram = histogram
        )
    }

    /**
     * Maps an instantaneous heart rate value to a 0-indexed zone (0..4).
     */
    private fun determineHeartRateZone(hr: Int, thresholds: HeartRateZoneThresholds): Int {
        return when {
            hr <= thresholds.z1Max -> 0
            hr <= thresholds.z2Max -> 1
            hr <= thresholds.z3Max -> 2
            hr <= thresholds.z4Max -> 3
            else -> 4
        }
    }

    /**
     * Maps an instantaneous cycling power value (Watts) to a 0-indexed zone (0..6).
     */
    private fun determinePowerZone(pwr: Int, thresholds: PowerZoneThresholds): Int {
        return when {
            pwr <= thresholds.z1Max -> 0
            pwr <= thresholds.z2Max -> 1
            pwr <= thresholds.z3Max -> 2
            pwr <= thresholds.z4Max -> 3
            pwr <= thresholds.z5Max -> 4
            pwr <= thresholds.z6Max -> 5
            else -> 6
        }
    }

    /**
     * Maps an instantaneous slope / grade percentage to a 0-indexed zone (0..6)
     * matching [com.atrainingtracker.trainingtracker.ui.map.ElevationProfileKt] and [GradeLegend].
     */
    fun determineGradeZone(gradePercent: Double): Int {
        if (!gradePercent.isFinite()) return 0
        return when {
            gradePercent < 2.0 -> 0
            gradePercent < 5.0 -> 1
            gradePercent < 10.0 -> 2
            gradePercent < 15.0 -> 3
            gradePercent < 20.0 -> 4
            gradePercent < 25.0 -> 5
            else -> 6
        }
    }

    const val DEFAULT_GRADE_SIGMA: Double = 50.0 // Spatial kernel radius in meters for grade telemetry (REQ-UI-453)
    private const val MIN_CHORD_DISTANCE_M = 25.0
    private const val MIN_SPATIAL_KNOT_DISTANCE_M = 2.0
    private const val STATIONARY_DISTANCE_THRESHOLD_M = 0.5

    /**
     * Evaluates local terrain slope grade in percent around [targetDist] over a spatial chord
     * window of at least [minChord] meters (default 15.0m) on the smoothed elevation profile (REQ-UI-453).
     */
    private fun calculateChordGrade(
        distances: DoubleArray,
        altitudes: DoubleArray,
        targetDist: Double,
        minChord: Double = MIN_CHORD_DISTANCE_M
    ): Double {
        val n = distances.size
        if (n < 2) return 0.0

        val totalSpan = distances.last() - distances.first()
        if (totalSpan <= 0.0) return 0.0

        var centerIdx = distances.binarySearch(targetDist)
        if (centerIdx < 0) {
            centerIdx = -(centerIdx + 1)
            if (centerIdx >= n) centerIdx = n - 1
            else if (centerIdx > 0 && (targetDist - distances[centerIdx - 1] < distances[centerIdx] - targetDist)) {
                centerIdx--
            }
        }

        var left = centerIdx
        var right = centerIdx
        val halfChord = minChord / 2.0

        while (left > 0 && targetDist - distances[left] < halfChord) {
            left--
        }
        while (right < n - 1 && distances[right] - targetDist < halfChord) {
            right++
        }

        while (distances[right] - distances[left] < minChord && (left > 0 || right < n - 1)) {
            val leftRoom = if (left > 0) targetDist - distances[left - 1] else Double.NEGATIVE_INFINITY
            val rightRoom = if (right < n - 1) distances[right + 1] - targetDist else Double.NEGATIVE_INFINITY
            if (leftRoom >= rightRoom && left > 0) {
                left--
            } else if (right < n - 1) {
                right++
            } else if (left > 0) {
                left--
            } else {
                break
            }
        }

        val dDist = distances[right] - distances[left]
        return if (dDist >= 1.0) {
            ((altitudes[right] - altitudes[left]) / dDist) * 100.0
        } else {
            0.0
        }
    }

    /**
     * Calculates 7-zone slope / grade distribution data and fine-grained 1% telemetry histogram
     * from track path points using Loess altitude smoothing (REQ-UI-453).
     *
     * Spatial knots are filtered with a minimum spatial separation (>= 2.0m) prior to Gaussian Loess
     * smoothing to prevent stationary clustering distortion, and grade is evaluated over a minimum
     * chord window of >= 15.0m (returning 0.0% when stationary).
     *
     * @param pathPoints Monotonically distance-ordered path points containing elevation and distance.
     * @param sigma Gaussian kernel spatial standard deviation in meters (default: 21.0m).
     * @return [ZoneDistributionData] containing exactly 7 entries and a 1% histogram, or null if telemetry is insufficient.
     */
    fun calculateGradeDistribution(
        pathPoints: List<PathPoint>,
        sigma: Double = DEFAULT_GRADE_SIGMA
    ): ZoneDistributionData? {
        if (pathPoints.size < 2) return null
        val validAlts = pathPoints.mapNotNull { it.altitude }.filter { !it.isNaN() }
        if (validAlts.isEmpty()) return null

        // Filter / collapse stationary or sub-meter distance knots (>= 2.0m) to eliminate
        // multi-sample stationary distortion in local linear regression.
        val spatialKnots = mutableListOf<PathPoint>()
        for (p in pathPoints) {
            val alt = p.altitude
            if (alt != null && !alt.isNaN()) {
                if (spatialKnots.isEmpty() || (p.distance - spatialKnots.last().distance) >= MIN_SPATIAL_KNOT_DISTANCE_M) {
                    spatialKnots.add(p)
                }
            }
        }
        val lastValidPoint = pathPoints.lastOrNull { it.altitude != null && !it.altitude.isNaN() }
        if (lastValidPoint != null && spatialKnots.isNotEmpty() && spatialKnots.last() !== lastValidPoint) {
            if (lastValidPoint.distance > spatialKnots.last().distance) {
                spatialKnots.add(lastValidPoint)
            }
        }

        val smoothedSpatialAlts = if (spatialKnots.size >= 2) {
            ElevationSmoothingMath.smoothAltitudes(spatialKnots, sigma = sigma)
        } else {
            DoubleArray(spatialKnots.size) { spatialKnots.firstOrNull()?.altitude ?: 0.0 }
        }
        val knotDistances = DoubleArray(spatialKnots.size) { spatialKnots[it].distance }

        val isDegenerate = pathPoints.size > 1 && pathPoints.first().timeSec == pathPoints.last().timeSec
        val zoneDurations = LongArray(7)
        val gradeSegments = mutableListOf<Pair<Double, Long>>()

        for (i in 0 until pathPoints.size - 1) {
            val p1 = pathPoints[i]
            val p2 = pathPoints[i + 1]

            val dt = if (isDegenerate) {
                1L
            } else {
                val rawDt = p2.timeSec - p1.timeSec
                when {
                    rawDt < 0L -> 0L
                    rawDt > MAX_DELTA_T_SEC -> MAX_DELTA_T_SEC
                    else -> rawDt
                }
            }
            if (dt <= 0L) continue

            val segmentDist = p2.distance - p1.distance
            val grade = if (segmentDist < STATIONARY_DISTANCE_THRESHOLD_M || spatialKnots.size < 2) {
                0.0
            } else {
                val midDist = (p1.distance + p2.distance) / 2.0
                calculateChordGrade(knotDistances, smoothedSpatialAlts, midDist, MIN_CHORD_DISTANCE_M)
            }

            val zoneIdx = determineGradeZone(grade)
            zoneDurations[zoneIdx] += dt
            gradeSegments.add(grade to dt)
        }

        val totalDuration = zoneDurations.sum()
        if (totalDuration <= 0L) return null

        val entries = (0..6).map { idx ->
            val duration = zoneDurations[idx]
            val pct = if (totalDuration > 0L) (duration.toFloat() / totalDuration) * 100f else 0f
            ZoneTimeEntry(
                zoneIndex = idx + 1,
                zoneLabelResId = GRADE_ZONE_LABELS[idx],
                durationSec = duration,
                percentage = pct,
                color = GRADE_ZONE_COLORS[idx],
                customBadgeLabel = GRADE_BADGE_LABELS[idx]
            )
        }

        val histogram = TelemetryHistogramCalculator.calculateGradeHistogram(gradeSegments)

        return ZoneDistributionData(
            totalActiveTimeSec = totalDuration,
            entries = entries,
            histogram = histogram
        )
    }
}
