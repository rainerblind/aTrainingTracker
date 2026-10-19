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

package com.atrainingtracker.trainingtracker.ui.clusters

import androidx.compose.runtime.Immutable
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.sensor.formater.PaceFormatter
import com.atrainingtracker.banalservice.sensor.formater.SpeedFormatter
import com.atrainingtracker.banalservice.sensor.formater.TimeFormatter
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData
import kotlin.math.hypot

/**
 * Metric display mode for historical workout cluster progression trends.
 */
enum class ClusterTrendMetricMode {
    DURATION,
    SPEED_OR_PACE
}

/**
 * Data point model representing a single workout effort within a cluster progression series.
 *
 * [normalizedX] is mapped across chronological index in range [0f, 1f].
 * [normalizedY] is mapped in range [0f, 1f] with athletic "faster-is-higher" semantics:
 * 1f represents the best/fastest performance, 0f represents the slowest effort.
 */
@Immutable
data class ClusterTrendDataPoint(
    val workoutId: Long,
    val timestampS: Long,
    val rawDurationSec: Long,
    val rawSpeedMps: Double,
    val formattedDate: String,
    val formattedDuration: String,
    val formattedSpeedOrPace: String,
    val isPr: Boolean,
    val normalizedX: Float,
    val normalizedY: Float
)

/**
 * Full progression series containing sorted points, cluster average baseline, and display metadata.
 */
@Immutable
data class ClusterTrendSeries(
    val points: List<ClusterTrendDataPoint>,
    val metricMode: ClusterTrendMetricMode,
    val avgNormalizedY: Float,
    val formattedAverage: String,
    val prWorkoutId: Long?,
    val isPace: Boolean
) {
    val isEmpty: Boolean get() = points.isEmpty()
}

/**
 * Pure calculation logic for cluster performance trends (REQ-SET-078).
 * Decoupled from Android UI/Canvas dependencies for fast, deterministic unit test verification.
 */
object WorkoutClusterTrendMath {

    /**
     * Builds a chronological effort trend series for a list of cluster member workouts.
     *
     * ### Data Cleansing & Filtering:
     * - Filters out workouts with non-positive duration (`activeTimeSec <= 0`) or non-positive velocity (`avgSpeedMps <= 0.0`).
     * - Sorts surviving workouts in ascending chronological order based on `startTimeS`.
     * - If fewer than 2 valid workouts remain (`N < 2`), returns an empty [ClusterTrendSeries] with `isEmpty == true`.
     *
     * ### Athletic "Faster-is-Higher" Coordinate Inversion:
     * On athletic endurance progression charts, higher position visually represents superior athletic performance.
     * To enforce this invariant:
     * - **Duration Mode**:
     *   $$\text{normY} = \frac{\text{maxDuration} - \text{duration}}{\text{maxDuration} - \text{minDuration}}$$
     *   Faster times (lower seconds) map towards 1.0f (top of canvas), while slower times map towards 0.0f.
     * - **Running Pace Mode** (for `BSportType.RUN`, pace in sec/m):
     *   $$\text{normY} = \frac{\text{maxPace} - \text{pace}}{\text{maxPace} - \text{minPace}}$$
     *   Faster paces (lower seconds per unit distance) map towards 1.0f.
     * - **Cycling / General Speed Mode** (for non-running sports, speed in m/s):
     *   $$\text{normY} = \frac{\text{speed} - \text{minSpeed}}{\text{maxSpeed} - \text{minSpeed}}$$
     *   Higher speeds map towards 1.0f.
     *
     * ### Division-by-Zero & Boundary Safety:
     * When all valid workouts have identical performance metrics (`max == min`), the normalization denominator is zero.
     * In this edge case, `normY` safely evaluates to `0.5f` (centered baseline), completely preventing `NaN`, `Infinity`,
     * or arithmetic exceptions.
     *
     * @param workouts The raw list of workouts associated with the cluster.
     * @param clusterSportType The probable sport type of the cluster (governs Pace vs. Speed display).
     * @param metricMode Whether to plot Duration or Speed/Pace.
     * @param timeFormatter Formatter for duration strings.
     * @param speedFormatter Formatter for velocity strings in speed mode.
     * @param paceFormatter Formatter for velocity strings in pace mode.
     * @return Fully populated [ClusterTrendSeries] ready for presentation.
     */
    fun buildTrendSeries(
        workouts: List<WorkoutData>,
        clusterSportType: BSportType,
        metricMode: ClusterTrendMetricMode,
        timeFormatter: TimeFormatter = TimeFormatter(),
        speedFormatter: SpeedFormatter = SpeedFormatter(),
        paceFormatter: PaceFormatter = PaceFormatter()
    ): ClusterTrendSeries {
        val validWorkouts = workouts
            .filter { it.activeTimeSec > 0 && it.avgSpeedMps > 0.0 }
            .sortedBy { it.startTimeS }

        val isPace = clusterSportType == BSportType.RUN

        if (validWorkouts.size < 2) {
            return ClusterTrendSeries(
                points = emptyList(),
                metricMode = metricMode,
                avgNormalizedY = 0.5f,
                formattedAverage = "",
                prWorkoutId = null,
                isPace = isPace
            )
        }

        val prWorkout = validWorkouts.minByOrNull { it.activeTimeSec }
        val prWorkoutId = prWorkout?.id

        val minDuration = validWorkouts.minOf { it.activeTimeSec }
        val maxDuration = validWorkouts.maxOf { it.activeTimeSec }
        val avgDuration = validWorkouts.map { it.activeTimeSec }.average()

        val minSpeed = validWorkouts.minOf { it.avgSpeedMps }
        val maxSpeed = validWorkouts.maxOf { it.avgSpeedMps }
        val avgSpeed = validWorkouts.map { it.avgSpeedMps }.average()

        val minPace = validWorkouts.minOf { 1.0 / it.avgSpeedMps }
        val maxPace = validWorkouts.maxOf { 1.0 / it.avgSpeedMps }
        val avgPace = validWorkouts.map { 1.0 / it.avgSpeedMps }.average()

        val n = validWorkouts.size
        val points = validWorkouts.mapIndexed { index, w ->
            val normX = if (n > 1) index.toFloat() / (n - 1).toFloat() else 0.5f

            val normY = when (metricMode) {
                ClusterTrendMetricMode.DURATION -> {
                    // Inverted: fastest (min duration) -> 1f, slowest (max duration) -> 0f
                    if (maxDuration == minDuration) 0.5f
                    else ((maxDuration - w.activeTimeSec).toDouble() / (maxDuration - minDuration).toDouble()).toFloat().coerceIn(0f, 1f)
                }
                ClusterTrendMetricMode.SPEED_OR_PACE -> {
                    if (isPace) {
                        // Inverted for pace: fastest pace (min pace sec/m) -> 1f, slowest pace (max pace) -> 0f
                        val pace = 1.0 / w.avgSpeedMps
                        if (maxPace == minPace) 0.5f
                        else ((maxPace - pace) / (maxPace - minPace)).toFloat().coerceIn(0f, 1f)
                    } else {
                        // Standard for speed: highest speed (max speed) -> 1f, lowest speed (min speed) -> 0f
                        if (maxSpeed == minSpeed) 0.5f
                        else ((w.avgSpeedMps - minSpeed) / (maxSpeed - minSpeed)).toFloat().coerceIn(0f, 1f)
                    }
                }
            }

            val speedOrPaceStr = if (isPace) {
                paceFormatter.format_with_units(1.0 / w.avgSpeedMps)
            } else {
                speedFormatter.format_with_units(w.avgSpeedMps)
            }

            ClusterTrendDataPoint(
                workoutId = w.id,
                timestampS = w.startTimeS,
                rawDurationSec = w.activeTimeSec,
                rawSpeedMps = w.avgSpeedMps,
                formattedDate = w.formattedDate,
                formattedDuration = timeFormatter.format(w.activeTimeSec),
                formattedSpeedOrPace = speedOrPaceStr,
                isPr = w.id == prWorkoutId,
                normalizedX = normX,
                normalizedY = normY
            )
        }

        val (avgNormY, formattedAvg) = when (metricMode) {
            ClusterTrendMetricMode.DURATION -> {
                val avgY = if (maxDuration == minDuration) 0.5f
                else ((maxDuration - avgDuration) / (maxDuration - minDuration).toDouble()).toFloat().coerceIn(0f, 1f)
                avgY to timeFormatter.format(avgDuration.toLong())
            }
            ClusterTrendMetricMode.SPEED_OR_PACE -> {
                if (isPace) {
                    val avgY = if (maxPace == minPace) 0.5f
                    else ((maxPace - avgPace) / (maxPace - minPace)).toFloat().coerceIn(0f, 1f)
                    avgY to paceFormatter.format_with_units(avgPace)
                } else {
                    val avgY = if (maxSpeed == minSpeed) 0.5f
                    else ((avgSpeed - minSpeed) / (maxSpeed - minSpeed)).toFloat().coerceIn(0f, 1f)
                    avgY to speedFormatter.format_with_units(avgSpeed)
                }
            }
        }

        return ClusterTrendSeries(
            points = points,
            metricMode = metricMode,
            avgNormalizedY = avgNormY,
            formattedAverage = formattedAvg,
            prWorkoutId = prWorkoutId,
            isPace = isPace
        )
    }

    /**
     * Resolves the nearest data point to the given touch coordinates within maxDistancePx.
     * In Android Canvas coordinate space, Y = 0 is top and Y = heightPx is bottom.
     * Since normalizedY = 1f is best/highest, the rendered Y position is (1f - normalizedY) * heightPx.
     */
    fun findNearestPoint(
        points: List<ClusterTrendDataPoint>,
        touchX: Float,
        touchY: Float,
        widthPx: Float,
        heightPx: Float,
        maxDistancePx: Float
    ): ClusterTrendDataPoint? {
        if (points.isEmpty() || widthPx <= 0f || heightPx <= 0f) return null

        var bestPoint: ClusterTrendDataPoint? = null
        var bestDist = Float.MAX_VALUE

        for (p in points) {
            val px = p.normalizedX * widthPx
            val py = (1f - p.normalizedY) * heightPx
            val dist = hypot((touchX - px).toDouble(), (touchY - py).toDouble()).toFloat()
            if (dist <= maxDistancePx && dist < bestDist) {
                bestDist = dist
                bestPoint = p
            }
        }

        return bestPoint
    }
}
