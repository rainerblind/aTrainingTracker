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

package com.atrainingtracker.trainingtracker.ui.aftermath.periodlist

import com.atrainingtracker.trainingtracker.database.hrv.HrvPosture
import com.atrainingtracker.trainingtracker.database.hrv.HrvReadinessCalculator
import com.atrainingtracker.trainingtracker.database.hrv.HrvReadinessEntry
import com.atrainingtracker.trainingtracker.database.hrv.ReadinessStatus
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sqrt

/**
 * Metric display mode for longitudinal HRV trend evaluation (`REQ-PER-033.2`).
 */
enum class HrvTrendMetricMode {
    LN_RMSSD,
    RMSSD
}

/**
 * Single plotted day in the longitudinal HRV trend series (`REQ-PER-033.2`).
 *
 * @property dateIso Calendar date in `YYYY-MM-DD` format.
 * @property timestampMs Timestamp epoch milliseconds of the measurement.
 * @property rawValue Metric value for this day (`ln(rMSSD)` or `rMSSD ms`).
 * @property mu7d 7-day rolling average of daily values (`null` if < 3 readings in `[D-6, D]`).
 * @property swcLower Lower 60-day Smallest Worthwhile Change boundary (`null` if < 14 readings in `[D-59, D]`).
 * @property swcUpper Upper 60-day Smallest Worthwhile Change boundary (`null` if < 14 readings in `[D-59, D]`).
 * @property status Autonomic readiness classification on this day.
 * @property trainingLoad Daily training load (TSS/ATL) for this day, if available.
 */
data class HrvTrendPoint(
    val dateIso: String,
    val timestampMs: Long,
    val rawValue: Double?,
    val mu7d: Double?,
    val swcLower: Double?,
    val swcUpper: Double?,
    val status: ReadinessStatus,
    val trainingLoad: Double? = null,
    val epochDay: Long = try { LocalDate.parse(dateIso).toEpochDay() } catch (_: Exception) { 0L },
    val notes: String? = null
)

/**
 * Encapsulates the complete processed time series for a posture (`REQ-PER-033.1`, `REQ-PER-033.2`).
 */
data class HrvTrendSeries(
    val posture: HrvPosture,
    val metricMode: HrvTrendMetricMode,
    val points: List<HrvTrendPoint>,
    val availablePostures: List<HrvPosture>,
    val yMin: Double,
    val yMax: Double,
    val maxTrainingLoad: Double
)

/**
 * Pure Kotlin calculation engine for longitudinal HRV trend lines, rolling 7d averages,
 * 60d Smallest Worthwhile Change (SWC) normal corridors, and Rule 48 outlier-resilient Y-clamping
 * (`REQ-PER-033`, `TST-PER-041`).
 */
object HrvReadinessTrendMath {

    const val MIN_7D_DAYS = HrvReadinessCalculator.MIN_7D_DAYS
    const val MIN_60D_DAYS = HrvReadinessCalculator.MIN_60D_DAYS
    const val MAX_VALID_ARTIFACT_RATE_PCT = HrvReadinessCalculator.MAX_VALID_ARTIFACT_RATE_PCT

    /**
     * Resolves all postures with valid readings and identifies the default posture
     * (the posture possessing the highest count of valid readings, defaulting to `SUPINE`).
     */
    fun resolveAvailablePostures(entries: List<HrvReadinessEntry>): Pair<HrvPosture, List<HrvPosture>> {
        val validEntries = entries.filter {
            it.artifactRatePct == null || it.artifactRatePct <= MAX_VALID_ARTIFACT_RATE_PCT
        }
        val countsByPosture = validEntries.groupBy { it.posture }
            .mapValues { it.value.size }

        val available = countsByPosture.keys.sortedBy { it.name }
        val defaultPosture = countsByPosture.maxByOrNull { it.value }?.key ?: HrvPosture.SUPINE
        return Pair(defaultPosture, available)
    }

    /**
     * Computes the processed [HrvTrendSeries] for the given [selectedPosture] and [metricMode].
     */
    fun calculateTrendSeries(
        entries: List<HrvReadinessEntry>,
        selectedPosture: HrvPosture,
        metricMode: HrvTrendMetricMode = HrvTrendMetricMode.LN_RMSSD,
        dailyTrainingLoads: Map<String, Double> = emptyMap()
    ): HrvTrendSeries {
        val (defaultPosture, availablePostures) = resolveAvailablePostures(entries)
        val activePosture = if (availablePostures.contains(selectedPosture)) {
            selectedPosture
        } else {
            availablePostures.firstOrNull() ?: defaultPosture
        }

        // 1. Filter by active posture and artifact gate
        val postureEntries = entries.filter {
            it.posture == activePosture &&
                (it.artifactRatePct == null || it.artifactRatePct <= MAX_VALID_ARTIFACT_RATE_PCT)
        }

        // 2. Daily deduplication: group by dateIso and pick primary
        val dailyMap = mutableMapOf<String, HrvReadinessEntry>()
        for ((date, dayEntries) in postureEntries.groupBy { it.dateIso }) {
            val primary = dayEntries.sortedWith(
                compareByDescending<HrvReadinessEntry> { it.isPrimaryForDay }
                    .thenBy { it.timestampMs }
            ).first()
            dailyMap[date] = primary
        }

        val sortedHrvDates = dailyMap.keys.sorted()
        if (sortedHrvDates.isEmpty()) {
            return HrvTrendSeries(
                posture = activePosture,
                metricMode = metricMode,
                points = emptyList(),
                availablePostures = availablePostures,
                yMin = 0.0,
                yMax = 1.0,
                maxTrainingLoad = 0.0
            )
        }

        val minHrvDate = sortedHrvDates.first()
        val maxHrvDate = sortedHrvDates.last()

        val allCandidateDates = (dailyMap.keys + dailyTrainingLoads.keys.filter {
            it in minHrvDate..maxHrvDate
        }).toSortedSet().toList()

        // 3. Compute rolling 7d average and 60d SWC corridor per day
        val points = mutableListOf<HrvTrendPoint>()
        var maxLoad = 0.0

        for (dateStr in allCandidateDates) {
            val targetDate = try {
                LocalDate.parse(dateStr)
            } catch (_: Exception) {
                continue
            }
            val dMinus6 = targetDate.minusDays(6)
            val dMinus59 = targetDate.minusDays(59)

            val window7dValues = mutableListOf<Double>()
            val window60dValues = mutableListOf<Double>()

            for ((dStr, entry) in dailyMap) {
                val d = try {
                    LocalDate.parse(dStr)
                } catch (_: Exception) {
                    continue
                }
                if (!d.isAfter(targetDate)) {
                    val value = if (metricMode == HrvTrendMetricMode.LN_RMSSD) entry.lnRmssd else entry.rmssdMs
                    if (!d.isBefore(dMinus6)) {
                        window7dValues.add(value)
                    }
                    if (!d.isBefore(dMinus59)) {
                        window60dValues.add(value)
                    }
                }
            }

            val n7d = window7dValues.size
            val n60d = window60dValues.size

            val mu7d = if (n7d >= MIN_7D_DAYS) window7dValues.average() else null

            val swcLower: Double?
            val swcUpper: Double?
            val status: ReadinessStatus

            if (n60d >= MIN_60D_DAYS) {
                val mean60 = window60dValues.average()
                val sumSq = window60dValues.sumOf { (it - mean60) * (it - mean60) }
                val sampleSd = sqrt(sumSq / (n60d - 1))
                val swc = 0.5 * sampleSd
                val low = mean60 - swc
                val up = mean60 + swc
                swcLower = low
                swcUpper = up

                status = if (mu7d != null) {
                    when {
                        mu7d < low -> ReadinessStatus.FATIGUED_LOW
                        mu7d > up -> ReadinessStatus.ABOVE_NORMAL
                        else -> ReadinessStatus.OPTIMAL
                    }
                } else {
                    ReadinessStatus.CALIBRATING
                }
            } else {
                swcLower = null
                swcUpper = null
                status = ReadinessStatus.CALIBRATING
            }

            val dayEntry = dailyMap[dateStr]
            val rawVal = dayEntry?.let {
                if (metricMode == HrvTrendMetricMode.LN_RMSSD) it.lnRmssd else it.rmssdMs
            }
            val timestampMs = dayEntry?.timestampMs
                ?: (targetDate.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli())

            val load = dailyTrainingLoads[dateStr]
            if (load != null && load > maxLoad) {
                maxLoad = load
            }

            points.add(
                HrvTrendPoint(
                    dateIso = dateStr,
                    timestampMs = timestampMs,
                    rawValue = rawVal,
                    mu7d = mu7d,
                    swcLower = swcLower,
                    swcUpper = swcUpper,
                    status = status,
                    trainingLoad = load,
                    epochDay = targetDate.toEpochDay(),
                    notes = dayEntry?.notes
                )
            )
        }

        // 4. Calculate outlier-resilient Y-axis domain clamping (Rule 48 / ATT-4287)
        val (yMin, yMax) = computeOutlierResilientYDomain(points, metricMode)

        return HrvTrendSeries(
            posture = activePosture,
            metricMode = metricMode,
            points = points,
            availablePostures = availablePostures,
            yMin = yMin,
            yMax = yMax,
            maxTrainingLoad = maxLoad
        )
    }

    /**
     * Evaluates outlier-resilient Y-axis bounds (Rule 48 — `ATT-4287`).
     * Clamps extreme anomalous readings using IQR when N >= 4, ensuring the 60-day SWC corridor
     * and rolling trend line utilize the vertical canvas height optimally.
     */
    fun computeOutlierResilientYDomain(
        points: List<HrvTrendPoint>,
        metricMode: HrvTrendMetricMode
    ): Pair<Double, Double> {
        val rawValues = points.mapNotNull { it.rawValue }.sorted()
        if (rawValues.isEmpty()) {
            return if (metricMode == HrvTrendMetricMode.LN_RMSSD) Pair(2.0, 5.0) else Pair(10.0, 100.0)
        }

        val allValues = mutableListOf<Double>()
        allValues.addAll(rawValues)
        points.forEach { p ->
            p.mu7d?.let { allValues.add(it) }
            p.swcLower?.let { allValues.add(it) }
            p.swcUpper?.let { allValues.add(it) }
        }

        val minVal: Double
        val maxVal: Double

        if (rawValues.size >= 4) {
            val q1 = rawValues[rawValues.size / 4]
            val q3 = rawValues[(rawValues.size * 3) / 4]
            val iqr = (q3 - q1).coerceAtLeast(if (metricMode == HrvTrendMetricMode.LN_RMSSD) 0.1 else 5.0)
            val lowerFence = q1 - 1.5 * iqr
            val upperFence = q3 + 1.5 * iqr

            val boundedValues = allValues.filter { it in lowerFence..upperFence }
            minVal = (boundedValues.minOrNull() ?: rawValues.first()).coerceAtLeast(0.0)
            maxVal = (boundedValues.maxOrNull() ?: rawValues.last()).coerceAtLeast(minVal + 0.1)
        } else {
            minVal = (allValues.minOrNull() ?: 0.0).coerceAtLeast(0.0)
            maxVal = (allValues.maxOrNull() ?: (minVal + 1.0)).coerceAtLeast(minVal + 0.1)
        }

        val span = (maxVal - minVal).coerceAtLeast(if (metricMode == HrvTrendMetricMode.LN_RMSSD) 0.5 else 15.0)
        val pad = span * 0.12
        return Pair(max(0.0, minVal - pad), maxVal + pad)
    }

    /**
     * Resolves the nearest [HrvTrendPoint] from a touch X coordinate within the chart canvas using calendar epoch days.
     */
    fun findNearestPoint(
        touchX: Float,
        leftPad: Float,
        plotW: Float,
        points: List<HrvTrendPoint>
    ): HrvTrendPoint? {
        if (points.isEmpty() || plotW <= 0f) return null
        if (points.size == 1) return points.first()

        val clampedX = touchX.coerceIn(leftPad, leftPad + plotW)
        val frac = ((clampedX - leftPad) / plotW).coerceIn(0f, 1f)

        val minEpoch = points.first().epochDay
        val maxEpoch = points.last().epochDay
        if (maxEpoch == minEpoch) return points.first()

        val targetEpoch = minEpoch + frac * (maxEpoch - minEpoch).toFloat()
        return points.minByOrNull { kotlin.math.abs(it.epochDay - targetEpoch) }
    }
}
