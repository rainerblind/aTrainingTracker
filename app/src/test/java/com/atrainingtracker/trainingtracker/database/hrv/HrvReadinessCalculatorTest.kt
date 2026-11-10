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

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

/**
 * TST-PER-038.4 / TST-PER-038.5: Unit tests verifying [HrvReadinessCalculator] per-posture isolation,
 * 14-day calibration gating, Bessel's sample standard deviation precision, daily deduplication,
 * and Smallest Worthwhile Change (SWC) corridor classification.
 */
class HrvReadinessCalculatorTest {

    private fun createEntry(
        dateIso: String,
        posture: HrvPosture,
        lnRmssd: Double,
        timestampMs: Long = 1731222000000L,
        artifactRatePct: Double? = 1.0,
        isPrimaryForDay: Boolean = true
    ): HrvReadinessEntry {
        return HrvReadinessEntry(
            id = 0L,
            timestampMs = timestampMs,
            dateIso = dateIso,
            timezoneId = "UTC",
            restingHrBpm = 50,
            rmssdMs = Math.exp(lnRmssd),
            lnRmssd = lnRmssd,
            hrvScore100 = 20.0 * lnRmssd,
            artifactRatePct = artifactRatePct,
            durationSec = 180,
            posture = posture,
            isPrimaryForDay = isPrimaryForDay
        )
    }

    /**
     * Acceptance Criterion 1 (ATT-3545):
     * Given 10 SUPINE readings and 10 SEATED readings on distinct days
     * When HrvReadinessCalculator evaluates SUPINE readiness
     * Then SEATED readings are excluded from the SUPINE baseline and the status remains CALIBRATING (10 / 14 days)
     */
    @Test
    fun criterion1_perPostureIsolation_and_14DayCalibrationGate() {
        val targetDate = LocalDate.parse("2026-11-10")
        val entries = mutableListOf<HrvReadinessEntry>()

        // Generate 10 SUPINE readings and 10 SEATED readings on distinct historical days
        for (i in 0 until 10) {
            val dateStr = targetDate.minusDays(i.toLong()).toString()
            entries.add(createEntry(dateStr, HrvPosture.SUPINE, 4.0))
            entries.add(createEntry(dateStr, HrvPosture.SEATED, 3.5))
        }

        val supineResult = HrvReadinessCalculator.calculateReadiness("2026-11-10", HrvPosture.SUPINE, entries)

        assertEquals("SEATED readings must not leak into SUPINE baseline", 10, supineResult.n60d)
        assertEquals(ReadinessStatus.CALIBRATING, supineResult.status)
        assertEquals(10, supineResult.calibrationDaysCount)
        assertEquals("Day 10 of 14 baseline calibration", supineResult.calibrationProgressMessage)
        assertNull(supineResult.mu60d)
        assertNull(supineResult.sigma60d)
        assertNull(supineResult.swcLower)
        assertNull(supineResult.swcUpper)
    }

    /**
     * Acceptance Criterion (ATT-4981 / REQ-PER-031.3 / TST-PER-039.4):
     * Given 14 OVERNIGHT_WEARABLE days and 10 SUPINE waking spot-checks
     * When evaluating SUPINE readiness
     * Then OVERNIGHT_WEARABLE records are strictly excluded and SUPINE remains CALIBRATING (10 / 14 days)
     */
    @Test
    fun criterion_overnightWearableIsolation_from_wakingPostures() {
        val targetDate = LocalDate.parse("2026-11-10")
        val entries = mutableListOf<HrvReadinessEntry>()

        // 14 OVERNIGHT_WEARABLE readings
        for (i in 0 until 14) {
            val dateStr = targetDate.minusDays(i.toLong()).toString()
            entries.add(createEntry(dateStr, HrvPosture.OVERNIGHT_WEARABLE, 4.2))
        }
        // 10 SUPINE readings
        for (i in 0 until 10) {
            val dateStr = targetDate.minusDays(i.toLong()).toString()
            entries.add(createEntry(dateStr, HrvPosture.SUPINE, 4.0))
        }

        val supineResult = HrvReadinessCalculator.calculateReadiness("2026-11-10", HrvPosture.SUPINE, entries)

        assertEquals("OVERNIGHT_WEARABLE readings must not leak into SUPINE baseline", 10, supineResult.n60d)
        assertEquals(ReadinessStatus.CALIBRATING, supineResult.status)
        assertEquals(10, supineResult.calibrationDaysCount)
        assertEquals("Day 10 of 14 baseline calibration", supineResult.calibrationProgressMessage)

        // And OVERNIGHT_WEARABLE has 14 readings -> can compute baseline in isolation
        val overnightResult = HrvReadinessCalculator.calculateReadiness("2026-11-10", HrvPosture.OVERNIGHT_WEARABLE, entries)
        assertEquals(14, overnightResult.n60d)
        assertEquals(ReadinessStatus.OPTIMAL, overnightResult.status)
    }

    /**
     * Acceptance Criterion 2 (ATT-3545):
     * Given >= 14 daily SUPINE ln(rMSSD) values with mu_60d = 4.00 and sample SD sigma_60d = 0.30
     * (SWC corridor [3.85, 4.15]), and two readings recorded on the target day (07:00 and 19:00)
     * When evaluated
     * Then only the primary/earliest morning reading represents that calendar day,
     * and mu_7d = 3.70 classifies as FATIGUED_LOW while mu_7d = 4.22 classifies as ABOVE_NORMAL.
     */
    @Test
    fun criterion2_dailyDeduplication_and_exactSwcCorridorClassification() {
        val targetDate = LocalDate.parse("2026-11-10")
        val baseEntries = mutableListOf<HrvReadinessEntry>()

        // Construct 20 baseline points such that sample mean is 4.00 and sample SD (N-1) is exactly 0.30
        // Symmetrical offsets around 4.0: 10 points at 4.0 - d, 10 points at 4.0 + d
        // Variance = sum(d^2) / 19 = 20 * d^2 / 19 = 0.30^2 = 0.09
        // d^2 = 0.09 * 19 / 20 = 0.0855 -> d = sqrt(0.0855) approx 0.2924038
        val d = Math.sqrt(0.09 * 19.0 / 20.0)

        // Days D-59 to D-7 (historical baseline)
        for (i in 7..26) {
            val dateStr = targetDate.minusDays(i.toLong()).toString()
            val value = if (i % 2 == 0) 4.0 + d else 4.0 - d
            baseEntries.add(createEntry(dateStr, HrvPosture.SUPINE, value))
        }

        // Add two readings on target date (07:00 and 19:00)
        val morningReading = createEntry(
            dateIso = "2026-11-10",
            posture = HrvPosture.SUPINE,
            lnRmssd = 3.70,
            timestampMs = 1731222000000L, // 07:00
            isPrimaryForDay = true
        )
        val eveningReading = createEntry(
            dateIso = "2026-11-10",
            posture = HrvPosture.SUPINE,
            lnRmssd = 4.80, // evening reading would skew average if not deduplicated
            timestampMs = 1731265200000L, // 19:00
            isPrimaryForDay = false
        )

        // Scenario A: mu_7d = 3.70 (e.g., target day and previous 2 days all 3.70)
        val scenarioA = ArrayList(baseEntries).apply {
            add(morningReading)
            add(eveningReading)
            add(createEntry("2026-11-09", HrvPosture.SUPINE, 3.70))
            add(createEntry("2026-11-08", HrvPosture.SUPINE, 3.70))
        }

        val resultA = HrvReadinessCalculator.calculateReadiness("2026-11-10", HrvPosture.SUPINE, scenarioA)

        // Verify deduplication
        assertEquals(morningReading, resultA.todayEntry)

        // Verify baseline and corridor
        assertNotNull(resultA.mu60d)
        assertNotNull(resultA.sigma60d)
        assertEquals(4.00, resultA.mu60d!!, 0.05)
        assertEquals(0.30, resultA.sigma60d!!, 0.05)
        assertEquals(resultA.mu60d!! - 0.5 * resultA.sigma60d!!, resultA.swcLower!!, 1e-4)
        assertEquals(resultA.mu60d!! + 0.5 * resultA.sigma60d!!, resultA.swcUpper!!, 1e-4)

        // mu_7d is 3.70 -> below corridor [3.85, 4.15] -> FATIGUED_LOW
        assertEquals(3.70, resultA.mu7d!!, 1e-4)
        assertEquals(ReadinessStatus.FATIGUED_LOW, resultA.status)

        // Scenario B: mu_7d = 4.22 (target day and previous 2 days at 4.22)
        val morningHigh = createEntry(
            dateIso = "2026-11-10",
            posture = HrvPosture.SUPINE,
            lnRmssd = 4.22,
            timestampMs = 1731222000000L,
            isPrimaryForDay = true
        )
        val scenarioB = ArrayList(baseEntries).apply {
            add(morningHigh)
            add(eveningReading)
            add(createEntry("2026-11-09", HrvPosture.SUPINE, 4.22))
            add(createEntry("2026-11-08", HrvPosture.SUPINE, 4.22))
        }

        val resultB = HrvReadinessCalculator.calculateReadiness("2026-11-10", HrvPosture.SUPINE, scenarioB)

        // mu_7d is 4.22 -> above corridor [3.85, 4.15] -> ABOVE_NORMAL
        assertEquals(4.22, resultB.mu7d!!, 1e-4)
        assertEquals(ReadinessStatus.ABOVE_NORMAL, resultB.status)

        // Scenario C: mu_7d = 4.00 -> OPTIMAL
        val morningOpt = createEntry(
            dateIso = "2026-11-10",
            posture = HrvPosture.SUPINE,
            lnRmssd = 4.00,
            timestampMs = 1731222000000L,
            isPrimaryForDay = true
        )
        val scenarioC = ArrayList(baseEntries).apply {
            add(morningOpt)
            add(eveningReading)
            add(createEntry("2026-11-09", HrvPosture.SUPINE, 4.00))
            add(createEntry("2026-11-08", HrvPosture.SUPINE, 4.00))
        }

        val resultC = HrvReadinessCalculator.calculateReadiness("2026-11-10", HrvPosture.SUPINE, scenarioC)
        assertEquals(4.00, resultC.mu7d!!, 1e-4)
        assertEquals(ReadinessStatus.OPTIMAL, resultC.status)
    }

    @Test
    fun artifactRateGate_excludesReadingsAbove5Percent() {
        val targetDate = LocalDate.parse("2026-11-10")
        val entries = mutableListOf<HrvReadinessEntry>()

        // 14 days of readings, but 5 have artifact rate = 6.0% (invalid)
        for (i in 0 until 14) {
            val dateStr = targetDate.minusDays(i.toLong()).toString()
            val artifactRate = if (i < 5) 6.0 else 1.0
            entries.add(createEntry(dateStr, HrvPosture.SUPINE, 4.0, artifactRatePct = artifactRate))
        }

        val result = HrvReadinessCalculator.calculateReadiness("2026-11-10", HrvPosture.SUPINE, entries)

        // Only 9 readings pass the <= 5.0% artifact gate -> 9 < 14 -> CALIBRATING
        assertEquals(9, result.n60d)
        assertEquals(ReadinessStatus.CALIBRATING, result.status)
        assertEquals("Day 9 of 14 baseline calibration", result.calibrationProgressMessage)
    }

    @Test
    fun weeklyWindowGate_requiresAtLeast3DaysInLast7Days() {
        val targetDate = LocalDate.parse("2026-11-10")
        val entries = mutableListOf<HrvReadinessEntry>()

        // 20 readings older than 7 days (D-8 to D-27)
        for (i in 8..27) {
            val dateStr = targetDate.minusDays(i.toLong()).toString()
            entries.add(createEntry(dateStr, HrvPosture.SUPINE, 4.0))
        }

        // Only 2 readings in the last 7 days (D-0 and D-1)
        entries.add(createEntry("2026-11-10", HrvPosture.SUPINE, 4.0))
        entries.add(createEntry("2026-11-09", HrvPosture.SUPINE, 4.0))

        val result = HrvReadinessCalculator.calculateReadiness("2026-11-10", HrvPosture.SUPINE, entries)

        // n60d is 22 (>= 14), but n7d is 2 (< 3)
        assertEquals(22, result.n60d)
        assertEquals(2, result.n7d)
        assertEquals(ReadinessStatus.CALIBRATING, result.status)
        assertEquals("Day 2 of 3 weekly readings", result.calibrationProgressMessage)
        assertNull(result.mu7d)
    }
}
