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

package com.atrainingtracker.trainingtracker.database.hrv

import java.time.LocalDate
import kotlin.math.sqrt

/**
 * Autonomic baseline calibration progress states for morning resting HRV (`REQ-PER-030.4`).
 */
enum class CalibrationProgressState {
    BASELINE_60D,
    WEEKLY_7D,
    COMPLETE
}

/**
 * Result of evaluating morning HRV autonomic readiness on a specific target date (`REQ-PER-030.4`).
 *
 * @property targetDateIso Date evaluated in `YYYY-MM-DD` format.
 * @property posture Athlete posture for which baseline and status are calculated.
 * @property status Autonomic readiness classification.
 * @property mu7d 7-day rolling average of daily ln(rMSSD) (`null` if [n7d] < 3).
 * @property n7d Number of valid daily primary readings in the 7-day window `[D-6, D]`.
 * @property mu60d 60-day baseline mean of daily ln(rMSSD) (`null` if [n60d] < 14).
 * @property sigma60d Unbiased sample standard deviation (Bessel's correction, N-1 denominator) (`null` if [n60d] < 14).
 * @property n60d Number of valid daily primary readings in the 60-day window `[D-59, D]`.
 * @property swcLower Lower Smallest Worthwhile Change boundary (`mu60d - 0.5 * sigma60d`).
 * @property swcUpper Upper Smallest Worthwhile Change boundary (`mu60d + 0.5 * sigma60d`).
 * @property calibrationDaysCount Count of baseline days recorded in the 60-day window.
 * @property calibrationProgressMessage Formatted calibration progress indicator.
 * @property todayEntry Primary measurement recorded on [targetDateIso], if present.
 * @property calibrationState Structured calibration phase indicator for 9-locale UI rendering.
 */
data class HrvReadinessResult(
    val targetDateIso: String,
    val posture: HrvPosture,
    val status: ReadinessStatus,
    val mu7d: Double?,
    val n7d: Int,
    val mu60d: Double?,
    val sigma60d: Double?,
    val n60d: Int,
    val swcLower: Double?,
    val swcUpper: Double?,
    val calibrationDaysCount: Int,
    val calibrationProgressMessage: String,
    val todayEntry: HrvReadinessEntry?,
    val calibrationState: CalibrationProgressState = CalibrationProgressState.COMPLETE
)

/**
 * Pure Kotlin calculation engine for per-posture Smallest Worthwhile Change (SWC) baselines
 * and autonomic training readiness classification (`REQ-PER-030.4`).
 *
 * Statistical & Physiological Invariants:
 * 1. Posture Isolation: Supine, seated, and standing readings are strictly segregated.
 * 2. Artifact Gate: Readings with `artifact_rate_pct > 5.0%` are excluded.
 * 3. Daily Deduplication: Multiple readings on the same date for the same posture resolve to a single
 *    primary reading (`ORDER BY is_primary_for_day DESC, timestamp_ms ASC LIMIT 1`).
 * 4. 7-Day Window: Covers `[D-6, D]` requiring >= 3 valid daily readings.
 * 5. 60-Day Window: Covers `[D-59, D]` requiring >= 14 valid daily readings.
 * 6. Sample Standard Deviation: Computed using Bessel's correction (N - 1 denominator).
 * 7. SWC Corridor: `[mu_60d - 0.5 * sigma_60d, mu_60d + 0.5 * sigma_60d]`.
 */
object HrvReadinessCalculator {

    const val MIN_7D_DAYS = 3
    const val MIN_60D_DAYS = 14
    const val MAX_VALID_ARTIFACT_RATE_PCT = 5.0

    @JvmStatic
    fun calculateReadiness(
        targetDateIso: String,
        posture: HrvPosture,
        entries: List<HrvReadinessEntry>
    ): HrvReadinessResult {
        val targetDate = LocalDate.parse(targetDateIso)
        val dMinus6 = targetDate.minusDays(6)
        val dMinus59 = targetDate.minusDays(59)

        // 1. Filter by posture, valid artifact rate (<= 5.0% or null), and presence of HRV metrics
        val validPostureEntries = entries.filter { entry ->
            entry.posture == posture &&
                (entry.artifactRatePct == null || entry.artifactRatePct <= MAX_VALID_ARTIFACT_RATE_PCT) &&
                entry.lnRmssd != null
        }

        // 2. Daily deduplication: group by dateIso and select primary
        val dailyPrimaryMap = mutableMapOf<String, HrvReadinessEntry>()
        val groupedByDate = validPostureEntries.groupBy { it.dateIso }
        for ((date, dayEntries) in groupedByDate) {
            val primary = dayEntries.sortedWith(
                compareByDescending<HrvReadinessEntry> { it.isPrimaryForDay }
                    .thenBy { it.timestampMs }
            ).first()
            dailyPrimaryMap[date] = primary
        }

        val todayEntry = dailyPrimaryMap[targetDateIso]

        // 3. Filter daily readings into 7-day and 60-day windows
        val entries7d = mutableListOf<HrvReadinessEntry>()
        val entries60d = mutableListOf<HrvReadinessEntry>()

        for ((dateStr, entry) in dailyPrimaryMap) {
            val date = try {
                LocalDate.parse(dateStr)
            } catch (_: Exception) {
                continue
            }
            if (!date.isAfter(targetDate)) {
                if (!date.isBefore(dMinus6)) {
                    entries7d.add(entry)
                }
                if (!date.isBefore(dMinus59)) {
                    entries60d.add(entry)
                }
            }
        }

        val n7d = entries7d.size
        val n60d = entries60d.size

        val mu7d = if (n7d >= MIN_7D_DAYS) {
            entries7d.mapNotNull { it.lnRmssd }.average()
        } else {
            null
        }

        val mu60d: Double?
        val sigma60d: Double?
        val swcLower: Double?
        val swcUpper: Double?

        if (n60d >= MIN_60D_DAYS) {
            val mean60 = entries60d.mapNotNull { it.lnRmssd }.average()
            val sumSquaredDiff = entries60d.mapNotNull { it.lnRmssd }.map {
                val diff = it - mean60
                diff * diff
            }.sum()

            // Bessel's correction: N - 1 denominator
            val sampleSd = sqrt(sumSquaredDiff / (n60d - 1))
            val swc = 0.5 * sampleSd

            mu60d = mean60
            sigma60d = sampleSd
            swcLower = mean60 - swc
            swcUpper = mean60 + swc
        } else {
            mu60d = null
            sigma60d = null
            swcLower = null
            swcUpper = null
        }

        val status: ReadinessStatus
        val progressMsg: String
        val calibrationState: CalibrationProgressState

        if (n60d < MIN_60D_DAYS) {
            status = ReadinessStatus.CALIBRATING
            calibrationState = CalibrationProgressState.BASELINE_60D
            progressMsg = "Day $n60d of $MIN_60D_DAYS baseline calibration"
        } else if (n7d < MIN_7D_DAYS || mu7d == null || swcLower == null || swcUpper == null) {
            status = ReadinessStatus.CALIBRATING
            calibrationState = CalibrationProgressState.WEEKLY_7D
            progressMsg = "Day $n7d of $MIN_7D_DAYS weekly readings"
        } else {
            calibrationState = CalibrationProgressState.COMPLETE
            progressMsg = "Calibration complete"
            status = when {
                mu7d < swcLower -> ReadinessStatus.FATIGUED_LOW
                mu7d > swcUpper -> ReadinessStatus.ABOVE_NORMAL
                else -> ReadinessStatus.OPTIMAL
            }
        }

        return HrvReadinessResult(
            targetDateIso = targetDateIso,
            posture = posture,
            status = status,
            mu7d = mu7d,
            n7d = n7d,
            mu60d = mu60d,
            sigma60d = sigma60d,
            n60d = n60d,
            swcLower = swcLower,
            swcUpper = swcUpper,
            calibrationDaysCount = n60d,
            calibrationProgressMessage = progressMsg,
            todayEntry = todayEntry,
            calibrationState = calibrationState
        )
    }
}
