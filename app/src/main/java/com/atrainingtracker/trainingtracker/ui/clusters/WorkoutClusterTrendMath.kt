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
    val normalizedY: Float,
    val isClampedOutlier: Boolean = false
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
    val isPace: Boolean,
    val yAxisTopLabel: String = "",
    val yAxisBottomLabel: String = "",
    val xAxisStartLabel: String = "",
    val xAxisEndLabel: String = ""
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
                isPace = isPace,
                yAxisTopLabel = "",
                yAxisBottomLabel = "",
                xAxisStartLabel = "",
                xAxisEndLabel = ""
            )
        }

        val prWorkout = validWorkouts.minByOrNull { it.activeTimeSec }
        val prWorkoutId = prWorkout?.id

        val avgDuration = validWorkouts.map { it.activeTimeSec }.average()
        val avgSpeed = validWorkouts.map { it.avgSpeedMps }.average()
        val avgPace = validWorkouts.map { 1.0 / it.avgSpeedMps }.average()

        val (scaleMin, scaleMax, hasFencing) = when (metricMode) {
            ClusterTrendMetricMode.DURATION -> {
                val values = validWorkouts.map { it.activeTimeSec.toDouble() }.sorted()
                computeScalingBounds(values)
            }
            ClusterTrendMetricMode.SPEED_OR_PACE -> {
                if (isPace) {
                    val values = validWorkouts.map { 1.0 / it.avgSpeedMps }.sorted()
                    computeScalingBounds(values)
                } else {
                    val values = validWorkouts.map { it.avgSpeedMps }.sorted()
                    computeScalingBounds(values)
                }
            }
        }

        val n = validWorkouts.size
        val points = validWorkouts.mapIndexed { index, w ->
            val normX = if (n > 1) index.toFloat() / (n - 1).toFloat() else 0.5f

            val (normY, isClampedOutlier) = when (metricMode) {
                ClusterTrendMetricMode.DURATION -> {
                    val d = w.activeTimeSec.toDouble()
                    val isOutlier = hasFencing && (d < scaleMin || d > scaleMax)
                    val y = if (scaleMax == scaleMin) 0.5f
                    else ((scaleMax - d) / (scaleMax - scaleMin)).toFloat().coerceIn(0f, 1f)
                    y to isOutlier
                }
                ClusterTrendMetricMode.SPEED_OR_PACE -> {
                    if (isPace) {
                        val pace = 1.0 / w.avgSpeedMps
                        val isOutlier = hasFencing && (pace < scaleMin || pace > scaleMax)
                        val y = if (scaleMax == scaleMin) 0.5f
                        else ((scaleMax - pace) / (scaleMax - scaleMin)).toFloat().coerceIn(0f, 1f)
                        y to isOutlier
                    } else {
                        val speed = w.avgSpeedMps
                        val isOutlier = hasFencing && (speed < scaleMin || speed > scaleMax)
                        val y = if (scaleMax == scaleMin) 0.5f
                        else ((speed - scaleMin) / (scaleMax - scaleMin)).toFloat().coerceIn(0f, 1f)
                        y to isOutlier
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
                normalizedY = normY,
                isClampedOutlier = isClampedOutlier
            )
        }

        val (avgNormY, formattedAvg) = when (metricMode) {
            ClusterTrendMetricMode.DURATION -> {
                val avgY = if (scaleMax == scaleMin) 0.5f
                else ((scaleMax - avgDuration) / (scaleMax - scaleMin)).toFloat().coerceIn(0f, 1f)
                avgY to timeFormatter.format(avgDuration.toLong())
            }
            ClusterTrendMetricMode.SPEED_OR_PACE -> {
                if (isPace) {
                    val avgY = if (maxOf(scaleMin, scaleMax) == minOf(scaleMin, scaleMax)) 0.5f
                    else ((scaleMax - avgPace) / (scaleMax - scaleMin)).toFloat().coerceIn(0f, 1f)
                    avgY to paceFormatter.format_with_units(avgPace)
                } else {
                    val avgY = if (scaleMax == scaleMin) 0.5f
                    else ((avgSpeed - scaleMin) / (scaleMax - scaleMin)).toFloat().coerceIn(0f, 1f)
                    avgY to speedFormatter.format_with_units(avgSpeed)
                }
            }
        }

        val (topLabel, bottomLabel) = when (metricMode) {
            ClusterTrendMetricMode.DURATION -> {
                timeFormatter.format(scaleMin.toLong()) to timeFormatter.format(scaleMax.toLong())
            }
            ClusterTrendMetricMode.SPEED_OR_PACE -> {
                if (isPace) {
                    paceFormatter.format_with_units(scaleMin) to paceFormatter.format_with_units(scaleMax)
                } else {
                    speedFormatter.format_with_units(scaleMax) to speedFormatter.format_with_units(scaleMin)
                }
            }
        }

        val startLabel = validWorkouts.first().formattedDate
        val endLabel = validWorkouts.last().formattedDate

        return ClusterTrendSeries(
            points = points,
            metricMode = metricMode,
            avgNormalizedY = avgNormY,
            formattedAverage = formattedAvg,
            prWorkoutId = prWorkoutId,
            isPace = isPace,
            yAxisTopLabel = topLabel,
            yAxisBottomLabel = bottomLabel,
            xAxisStartLabel = startLabel,
            xAxisEndLabel = endLabel
        )
    }

    /**
     * Calculates a percentile value across [sortedValues] using linear rank interpolation (REQ-SET-078.6).
     *
     * Rank index: $k = p \times (N - 1)$, where $i = \lfloor k \rfloor$ and $f = k - i$.
     * Percentile value: $P(p) = (1 - f) \times \text{sorted}[i] + f \times \text{sorted}[i + 1]$.
     *
     * @param sortedValues Ascending sorted list of numeric metrics.
     * @param p Percentile fraction in range [0.0, 1.0].
     * @return Interpolated percentile value.
     */
    internal fun percentile(sortedValues: List<Double>, p: Double): Double {
        val n = sortedValues.size
        if (n == 0) return 0.0
        if (n == 1) return sortedValues[0]
        val k = (p * (n - 1)).coerceIn(0.0, (n - 1).toDouble())
        val i = k.toInt()
        val f = k - i
        return if (i >= n - 1) {
            sortedValues[n - 1]
        } else {
            (1.0 - f) * sortedValues[i] + f * sortedValues[i + 1]
        }
    }

    /**
     * Computes scaling bounds [scaleMin, scaleMax] and whether outlier fencing is active (REQ-SET-078.6).
     *
     * When $N \ge 4$:
     * Computes $Q_1$ (25th percentile) and $Q_3$ (75th percentile) via linear rank interpolation.
     * $\text{IQR} = Q_3 - Q_1$.
     * If $\text{IQR} > 0.0$:
     * Lower fence: $L = Q_1 - 1.5 \times \text{IQR}$.
     * Upper fence: $U = Q_3 + 1.5 \times \text{IQR}$.
     * Clamped bounds: $\text{scaleMin} = \max(\text{rawMin}, L)$, $\text{scaleMax} = \min(\text{rawMax}, U)$.
     *
     * When $N < 4$ or $\text{IQR} == 0.0$ or $\text{scaleMax} \le \text{scaleMin}$:
     * Falls back to raw $[\text{rawMin}, \text{rawMax}]$ with fencing disabled.
     */
    internal fun computeScalingBounds(sortedValues: List<Double>): Triple<Double, Double, Boolean> {
        if (sortedValues.isEmpty()) return Triple(0.0, 0.0, false)
        val rawMin = sortedValues.first()
        val rawMax = sortedValues.last()
        val n = sortedValues.size

        if (n >= 4) {
            val q1 = percentile(sortedValues, 0.25)
            val q3 = percentile(sortedValues, 0.75)
            val iqr = q3 - q1
            if (iqr > 0.0) {
                val lowerFence = q1 - 1.5 * iqr
                val upperFence = q3 + 1.5 * iqr
                val sMin = maxOf(rawMin, lowerFence)
                val sMax = minOf(rawMax, upperFence)
                if (sMax > sMin) {
                    return Triple(sMin, sMax, true)
                }
            }
        }
        return Triple(rawMin, rawMax, false)
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
