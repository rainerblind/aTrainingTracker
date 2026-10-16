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

package com.atrainingtracker.trainingtracker.ui.athleteprofile

import com.atrainingtracker.trainingtracker.database.AthleteMetricEntry
import com.atrainingtracker.trainingtracker.database.AthleteMetricType
import com.atrainingtracker.trainingtracker.settings.SettingsDataStore
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Selectable metrics in the Athlete Profile time-series plot (`REQ-UI-391.4`).
 */
enum class PlotMetricType {
    WEIGHT,
    HR_REST,
    FTP,
    POWER_TO_WEIGHT
}

/**
 * Selectable time ranges in the Athlete Profile time-series plot (`REQ-UI-391.4`).
 *
 * @property days Rolling window duration in days, or `null` for `ALL`.
 */
enum class MetricsTimeRange(val days: Int?) {
    ONE_MONTH(30),
    THREE_MONTHS(90),
    SIX_MONTHS(180),
    ONE_YEAR(365),
    ALL(null)
}

/**
 * Single point in a chronological time-series plot (`timestampMs` ascending).
 */
data class MetricPlotPoint(
    val timestampMs: Long,
    val value: Double
)

/**
 * Pure math and canonical formatting utilities for Athlete Profile time-series plots and derived `W/kg` (`REQ-UI-391.4`).
 */
object AthleteMetricsPlotMath {

    private const val MILLIS_PER_DAY = 86_400_000L

    /**
     * Computes the chronological derived Power-to-Weight (`W/kg`) time series from raw [entries].
     *
     * At every distinct timestamp $t$ where either `WEIGHT_KG` or `FTP_WATTS` was recorded,
     * resolves the effective active FTP $F(t)$ and active Weight $W(t)$ at time $t$ and emits
     * `MetricPlotPoint(t, F(t) / W(t))` in `Double` precision whenever both $F(t) > 0$ and $W(t) > 0$.
     */
    fun computeDerivedPowerToWeightSeries(entries: List<AthleteMetricEntry>): List<MetricPlotPoint> {
        val relevant = entries
            .filter { it.metricType == AthleteMetricType.WEIGHT_KG || it.metricType == AthleteMetricType.FTP_WATTS }
            .sortedWith(compareBy<AthleteMetricEntry> { it.timestampMs }.thenBy { it.id })

        if (relevant.isEmpty()) return emptyList()

        val pointsByTimestamp = LinkedHashMap<Long, MetricPlotPoint>()
        var activeWeightKg: Double? = null
        var activeFtpWatts: Double? = null

        for (entry in relevant) {
            when (entry.metricType) {
                AthleteMetricType.WEIGHT_KG -> if (entry.value > 0.0) activeWeightKg = entry.value
                AthleteMetricType.FTP_WATTS -> if (entry.value > 0.0) activeFtpWatts = entry.value
                else -> Unit
            }
            val w = activeWeightKg
            val f = activeFtpWatts
            if (w != null && w > 0.0 && f != null && f > 0.0) {
                pointsByTimestamp[entry.timestampMs] = MetricPlotPoint(
                    timestampMs = entry.timestampMs,
                    value = f / w
                )
            }
        }
        return pointsByTimestamp.values.toList()
    }

    /**
     * Computes the current derived Power-to-Weight (`W/kg`) from the latest FTP and latest Weight,
     * or returns `null` if either baseline is missing or non-positive.
     */
    fun computeCurrentPowerToWeight(latestFtpWatts: Double?, latestWeightKg: Double?): Double? {
        if (latestFtpWatts == null || latestWeightKg == null) return null
        if (latestFtpWatts <= 0.0 || latestWeightKg <= 0.0) return null
        return latestFtpWatts / latestWeightKg
    }

    /**
     * Filters and converts the chronological time series for [metricType] within [timeRange] relative to [nowMs].
     *
     * When [isImperial] is `true` and [metricType] is [PlotMetricType.WEIGHT], converts values from `kg` to `lbs`
     * using [SettingsDataStore.LBS_PER_KG].
     */
    fun filterAndConvertSeries(
        entries: List<AthleteMetricEntry>,
        metricType: PlotMetricType,
        timeRange: MetricsTimeRange,
        nowMs: Long,
        isImperial: Boolean = false
    ): List<MetricPlotPoint> {
        val rawSeries: List<MetricPlotPoint> = when (metricType) {
            PlotMetricType.WEIGHT -> {
                entries
                    .filter { it.metricType == AthleteMetricType.WEIGHT_KG }
                    .sortedWith(compareBy<AthleteMetricEntry> { it.timestampMs }.thenBy { it.id })
                    .map {
                        val displayVal = if (isImperial) {
                            it.value * SettingsDataStore.LBS_PER_KG
                        } else {
                            it.value
                        }
                        MetricPlotPoint(it.timestampMs, displayVal)
                    }
            }
            PlotMetricType.HR_REST -> {
                entries
                    .filter { it.metricType == AthleteMetricType.HR_REST_BPM }
                    .sortedWith(compareBy<AthleteMetricEntry> { it.timestampMs }.thenBy { it.id })
                    .map { MetricPlotPoint(it.timestampMs, it.value) }
            }
            PlotMetricType.FTP -> {
                entries
                    .filter { it.metricType == AthleteMetricType.FTP_WATTS }
                    .sortedWith(compareBy<AthleteMetricEntry> { it.timestampMs }.thenBy { it.id })
                    .map { MetricPlotPoint(it.timestampMs, it.value) }
            }
            PlotMetricType.POWER_TO_WEIGHT -> computeDerivedPowerToWeightSeries(entries)
        }

        val cutoffMs = timeRange.days?.let { nowMs - it.toLong() * MILLIS_PER_DAY }
        return if (cutoffMs != null) {
            rawSeries.filter { it.timestampMs >= cutoffMs }
        } else {
            rawSeries
        }
    }

    /**
     * Formats a canonical weight in `kg` for display (`%.1f kg` or `%.1f lbs`).
     */
    fun formatWeightValue(weightKg: Double, isImperial: Boolean, locale: Locale = Locale.US): String {
        val displayVal = if (isImperial) weightKg * SettingsDataStore.LBS_PER_KG else weightKg
        return String.format(locale, "%.1f", displayVal)
    }

    /**
     * Returns the display unit string for weight (`"lbs"` or `"kg"`).
     */
    fun weightUnitLabel(isImperial: Boolean): String = if (isImperial) "lbs" else "kg"

    /**
     * Formats Resting Heart Rate in integer `bpm`.
     */
    fun formatHrRestValue(bpm: Double): String = bpm.roundToInt().toString()

    /**
     * Formats Functional Threshold Power in integer `W`.
     */
    fun formatFtpValue(watts: Double): String = watts.roundToInt().toString()

    /**
     * Formats derived Power-to-Weight (`W/kg`) to 2 decimal places (`%.2f`) per `REQ-TRK-019` and `REQ-UI-391.4`.
     */
    fun formatPowerToWeightValue(wPerKg: Double, locale: Locale = Locale.US): String =
        String.format(locale, "%.2f", wPerKg)

    /**
     * Formats a timestamp in milliseconds using `DateFormat.getDateInstance(DateFormat.MEDIUM, locale)`.
     */
    fun formatMediumDate(timestampMs: Long, locale: Locale = Locale.getDefault()): String =
        DateFormat.getDateInstance(DateFormat.MEDIUM, locale).format(Date(timestampMs))

    /**
     * Formats a timestamp in milliseconds using `DateFormat.getDateInstance(DateFormat.SHORT, locale)`.
     */
    fun formatShortDate(timestampMs: Long, locale: Locale = Locale.getDefault()): String =
        DateFormat.getDateInstance(DateFormat.SHORT, locale).format(Date(timestampMs))
}
