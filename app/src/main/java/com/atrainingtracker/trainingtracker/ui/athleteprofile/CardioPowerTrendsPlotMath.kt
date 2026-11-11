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
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0/>.
 */

package com.atrainingtracker.trainingtracker.ui.athleteprofile

import com.atrainingtracker.trainingtracker.database.CardioPowerTrendSample
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Metric series selector for Cardio-Power fitness trends in Athlete Profile (REQ-REV-011 / ATT-3906).
 */
enum class CardioTrendMetric {
    HR_200W,
    TAU,
    SLOPE
}

/**
 * Single data point in the Cardio-Power longitudinal trend chart.
 */
data class CardioPlotPoint(
    val timestampMs: Long,
    val value: Double,
    val workoutId: Long
)

/**
 * Pure math and formatting utilities for Cardio-Power longitudinal trend charts (REQ-REV-011 / ATT-3906).
 */
object CardioPowerTrendsPlotMath {

    const val MILLIS_PER_DAY = 86_400_000L

    /**
     * Converts raw database trend samples into sorted [CardioPlotPoint] list for the chosen [metric].
     */
    fun convertSamplesToPoints(
        samples: List<CardioPowerTrendSample>,
        metric: CardioTrendMetric
    ): List<CardioPlotPoint> {
        return samples.mapNotNull { sample ->
            val v = when (metric) {
                CardioTrendMetric.HR_200W -> sample.hr200Start
                CardioTrendMetric.TAU -> sample.tauStart
                CardioTrendMetric.SLOPE -> sample.aerobicSlopeStart
            }
            if (v != null && v > 0.0) {
                CardioPlotPoint(
                    timestampMs = sample.timestampMs,
                    value = v,
                    workoutId = sample.workoutId
                )
            } else {
                null
            }
        }.sortedBy { it.timestampMs }
    }

    /**
     * Computes a 30-day moving window rolling average curve over chronological [points].
     */
    fun computeRollingTrend(
        points: List<CardioPlotPoint>,
        windowDays: Int = 30
    ): List<CardioPlotPoint> {
        if (points.isEmpty()) return emptyList()
        val windowMs = windowDays.toLong() * MILLIS_PER_DAY
        return points.map { current ->
            val windowStart = current.timestampMs - windowMs
            val windowPoints = points.filter { it.timestampMs in windowStart..current.timestampMs }
            val avg = if (windowPoints.isNotEmpty()) windowPoints.map { it.value }.average() else current.value
            CardioPlotPoint(
                timestampMs = current.timestampMs,
                value = avg,
                workoutId = current.workoutId
            )
        }
    }

    /**
     * Filters [points] by [timeRange] relative to [nowMs].
     */
    fun filterPointsByTimeRange(
        points: List<CardioPlotPoint>,
        timeRange: MetricsTimeRange,
        nowMs: Long
    ): List<CardioPlotPoint> {
        val cutoffMs = timeRange.days?.let { nowMs - it.toLong() * MILLIS_PER_DAY }
        return if (cutoffMs != null) {
            points.filter { it.timestampMs >= cutoffMs }
        } else {
            points
        }
    }

    /**
     * Computes outlier-resilient Y-axis domain bounds `[yMin, yMax]` using Tukey's Interquartile Range (IQR).
     *
     * When $N \ge 4$, clamps the domain to $[Q_1 - 1.5 \cdot \text{IQR}, Q_3 + 1.5 \cdot \text{IQR}]$
     * so single anomalous workouts do not compress the seasonal curve (Rule 48 / Design Guidelines §2.6).
     */
    fun computeOutlierResilientDomain(
        values: List<Double>,
        metric: CardioTrendMetric
    ): Pair<Double, Double> {
        if (values.isEmpty()) {
            return when (metric) {
                CardioTrendMetric.HR_200W -> 110.0 to 170.0
                CardioTrendMetric.TAU -> 20.0 to 80.0
                CardioTrendMetric.SLOPE -> 3.0 to 8.0
            }
        }
        if (values.size < 4) {
            val min = values.minOrNull() ?: 0.0
            val max = values.maxOrNull() ?: 1.0
            val span = (max - min).coerceAtLeast(1e-3)
            val pad = when (metric) {
                CardioTrendMetric.HR_200W -> if (span < 5.0) 5.0 else span * 0.1
                CardioTrendMetric.TAU -> if (span < 5.0) 5.0 else span * 0.1
                CardioTrendMetric.SLOPE -> if (span < 0.5) 0.5 else span * 0.1
            }
            val yMin = (min - pad).coerceAtLeast(0.0)
            val yMax = max + pad
            return yMin to yMax
        }

        val sorted = values.sorted()
        val n = sorted.size
        val q1 = sorted[(n * 0.25).toInt().coerceIn(0, n - 1)]
        val q3 = sorted[(n * 0.75).toInt().coerceIn(0, n - 1)]
        val iqr = (q3 - q1).coerceAtLeast(1e-3)
        val lowerFence = (q1 - 1.5 * iqr).coerceAtLeast(0.0)
        val upperFence = q3 + 1.5 * iqr

        val inFences = sorted.filter { it in lowerFence..upperFence }
        val minVal = if (inFences.isNotEmpty()) inFences.first() else lowerFence
        val maxVal = if (inFences.isNotEmpty()) inFences.last() else upperFence

        val span = (maxVal - minVal).coerceAtLeast(1e-3)
        val pad = when (metric) {
            CardioTrendMetric.HR_200W -> if (span < 5.0) 5.0 else span * 0.1
            CardioTrendMetric.TAU -> if (span < 5.0) 5.0 else span * 0.1
            CardioTrendMetric.SLOPE -> if (span < 0.5) 0.5 else span * 0.1
        }
        val yMin = (minVal - pad).coerceAtLeast(0.0)
        val yMax = maxVal + pad
        return yMin to yMax
    }

    /**
     * Formats Y-axis tick values with explicit domain units per Rule 48.
     */
    fun formatYAxisValue(value: Double, metric: CardioTrendMetric): String {
        return when (metric) {
            CardioTrendMetric.HR_200W -> "${value.roundToInt()} bpm"
            CardioTrendMetric.TAU -> "${value.roundToInt()} s"
            CardioTrendMetric.SLOPE -> String.format(Locale.getDefault(), "%.1f W/bpm", value)
        }
    }

    /**
     * Formats X-axis date tick string ("dd.MM." or "MMM yy" depending on span).
     */
    fun formatAxisDate(timestampMs: Long, spanMs: Long): String {
        val pattern = if (spanMs > 180L * MILLIS_PER_DAY) "MMM yy" else "dd.MM."
        val sdf = SimpleDateFormat(pattern, Locale.getDefault())
        return sdf.format(Date(timestampMs))
    }

    /**
     * Resolves the nearest [CardioPlotPoint] from a touch X coordinate within the chart canvas.
     */
    fun findNearestPoint(
        touchX: Float,
        leftPad: Float,
        plotW: Float,
        points: List<CardioPlotPoint>
    ): CardioPlotPoint? {
        if (points.isEmpty() || plotW <= 0f) return null
        if (points.size == 1) return points.first()

        val minTs = points.first().timestampMs
        val maxTs = points.last().timestampMs
        val tsSpan = (maxTs - minTs).coerceAtLeast(1L)
        if (tsSpan <= 1L) return points.first()

        val clampedX = touchX.coerceIn(leftPad, leftPad + plotW)
        val frac = ((clampedX - leftPad) / plotW).coerceIn(0f, 1f)
        val targetTs = minTs + (frac * tsSpan).toLong()

        return points.minByOrNull { kotlin.math.abs(it.timestampMs - targetTs) }
    }
}
