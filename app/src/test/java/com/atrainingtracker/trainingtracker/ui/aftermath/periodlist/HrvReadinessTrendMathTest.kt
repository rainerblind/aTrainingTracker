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
import com.atrainingtracker.trainingtracker.database.hrv.HrvReadinessEntry
import com.atrainingtracker.trainingtracker.database.hrv.ReadinessStatus
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.sqrt

/**
 * Unit tests for [HrvReadinessTrendMath] verifying posture filtering, daily deduplication,
 * rolling 7-day average, Bessel-corrected 60-day SWC corridor calculation, training load alignment,
 * Rule 48 outlier Y-clamping, and touch hit resolution (`REQ-PER-033`, `TST-PER-041.1`..`TST-PER-041.3`).
 */
class HrvReadinessTrendMathTest {

    private fun createEntry(
        id: Long = 1L,
        dateIso: String,
        timestampMs: Long = 1730419200000L,
        posture: HrvPosture = HrvPosture.SUPINE,
        rmssdMs: Double = 50.0,
        lnRmssd: Double = 3.91,
        artifactRatePct: Double? = 1.0,
        isPrimaryForDay: Boolean = true
    ): HrvReadinessEntry {
        return HrvReadinessEntry(
            id = id,
            timestampMs = timestampMs,
            dateIso = dateIso,
            timezoneId = "UTC",
            restingHrBpm = 55,
            rmssdMs = rmssdMs,
            lnRmssd = lnRmssd,
            hrvScore100 = 20.0 * lnRmssd,
            sdnnMs = 60.0,
            artifactRatePct = artifactRatePct,
            durationSec = 60,
            posture = posture,
            isPrimaryForDay = isPrimaryForDay
        )
    }

    @Test
    fun testResolveAvailablePostures_picksPostureWithMaxValidCount() {
        val entries = listOf(
            createEntry(id = 1, dateIso = "2026-11-01", posture = HrvPosture.SEATED),
            createEntry(id = 2, dateIso = "2026-11-02", posture = HrvPosture.SEATED),
            createEntry(id = 3, dateIso = "2026-11-03", posture = HrvPosture.SEATED),
            createEntry(id = 4, dateIso = "2026-11-01", posture = HrvPosture.SUPINE),
            createEntry(id = 5, dateIso = "2026-11-02", posture = HrvPosture.SUPINE, artifactRatePct = 8.5) // Excluded!
        )

        val (defaultPosture, available) = HrvReadinessTrendMath.resolveAvailablePostures(entries)
        assertEquals(HrvPosture.SEATED, defaultPosture)
        assertEquals(listOf(HrvPosture.SEATED, HrvPosture.SUPINE), available)
    }

    @Test
    fun testCalculateTrendSeries_deduplicatesDailyReadingsAndFiltersArtifacts() {
        val entries = listOf(
            // Duplicate day 2026-11-01: primary=false at 07:00, primary=true at 08:00
            createEntry(id = 1, dateIso = "2026-11-01", timestampMs = 1000L, rmssdMs = 40.0, lnRmssd = 3.68, isPrimaryForDay = false),
            createEntry(id = 2, dateIso = "2026-11-01", timestampMs = 2000L, rmssdMs = 60.0, lnRmssd = 4.09, isPrimaryForDay = true),
            // High artifact rate (> 5.0%) -> must be skipped
            createEntry(id = 3, dateIso = "2026-11-02", artifactRatePct = 6.5, rmssdMs = 30.0, lnRmssd = 3.40),
            // Different posture -> must be skipped when SUPINE selected
            createEntry(id = 4, dateIso = "2026-11-03", posture = HrvPosture.STANDING, rmssdMs = 35.0, lnRmssd = 3.55)
        )

        val series = HrvReadinessTrendMath.calculateTrendSeries(
            entries = entries,
            selectedPosture = HrvPosture.SUPINE,
            metricMode = HrvTrendMetricMode.LN_RMSSD
        )

        assertEquals(1, series.points.size)
        val point = series.points.first()
        assertEquals("2026-11-01", point.dateIso)
        assertEquals(4.09, point.rawValue!!, 0.001)
        assertEquals(ReadinessStatus.CALIBRATING, point.status)
    }

    @Test
    fun testCalculateTrendSeries_7dRollingAverageRequiresAtLeast3Days() {
        val entries = listOf(
            createEntry(id = 1, dateIso = "2026-11-01", lnRmssd = 4.0),
            createEntry(id = 2, dateIso = "2026-11-02", lnRmssd = 4.2),
            createEntry(id = 3, dateIso = "2026-11-03", lnRmssd = 4.4)
        )

        val series = HrvReadinessTrendMath.calculateTrendSeries(
            entries = entries,
            selectedPosture = HrvPosture.SUPINE,
            metricMode = HrvTrendMetricMode.LN_RMSSD
        )

        assertEquals(3, series.points.size)
        // Day 1: only 1 day in 7d window -> null
        assertNull(series.points[0].mu7d)
        // Day 2: only 2 days in 7d window -> null
        assertNull(series.points[1].mu7d)
        // Day 3: 3 days in 7d window -> average (4.0 + 4.2 + 4.4) / 3 = 4.2
        assertNotNull(series.points[2].mu7d)
        assertEquals(4.2, series.points[2].mu7d!!, 0.001)
    }

    @Test
    fun testCalculateTrendSeries_60dSwcCorridorEvaluatesBesselSampleSdAndClassifiesStatus() {
        val entries = mutableListOf<HrvReadinessEntry>()
        // Generate 14 days of baseline readings with known mean and variance
        val baseValues = listOf(
            4.00, 4.10, 3.90, 4.05, 3.95, 4.15, 3.85,
            4.02, 3.98, 4.08, 3.92, 4.12, 3.88, 4.00
        )
        baseValues.forEachIndexed { i, v ->
            val day = String.format("%02d", i + 1)
            entries.add(createEntry(id = i.toLong(), dateIso = "2026-11-$day", lnRmssd = v))
        }

        val series = HrvReadinessTrendMath.calculateTrendSeries(
            entries = entries,
            selectedPosture = HrvPosture.SUPINE,
            metricMode = HrvTrendMetricMode.LN_RMSSD
        )

        val lastPoint = series.points.last()
        assertNotNull(lastPoint.swcLower)
        assertNotNull(lastPoint.swcUpper)

        // Calculate expected Bessel sample SD over 14 points
        val expectedMean = baseValues.average()
        val sumSq = baseValues.sumOf { (it - expectedMean) * (it - expectedMean) }
        val expectedSampleSd = sqrt(sumSq / 13.0)
        val expectedSwc = 0.5 * expectedSampleSd

        assertEquals(expectedMean - expectedSwc, lastPoint.swcLower!!, 0.001)
        assertEquals(expectedMean + expectedSwc, lastPoint.swcUpper!!, 0.001)
        assertEquals(ReadinessStatus.OPTIMAL, lastPoint.status)
    }

    @Test
    fun testCalculateTrendSeries_alignsDailyTrainingLoads() {
        val entries = listOf(
            createEntry(id = 1, dateIso = "2026-11-01", lnRmssd = 4.0),
            createEntry(id = 2, dateIso = "2026-11-02", lnRmssd = 4.1)
        )
        val loads = mapOf(
            "2026-11-01" to 85.0,
            "2026-11-02" to 120.0
        )

        val series = HrvReadinessTrendMath.calculateTrendSeries(
            entries = entries,
            selectedPosture = HrvPosture.SUPINE,
            metricMode = HrvTrendMetricMode.LN_RMSSD,
            dailyTrainingLoads = loads
        )

        assertEquals(85.0, series.points[0].trainingLoad!!, 0.01)
        assertEquals(120.0, series.points[1].trainingLoad!!, 0.01)
        assertEquals(120.0, series.maxTrainingLoad, 0.01)
    }

    @Test
    fun testComputeOutlierResilientYDomain_clampsExtremesWhenNAtLeast4() {
        val points = listOf(
            HrvTrendPoint("2026-11-01", 1L, 4.0, null, null, null, ReadinessStatus.CALIBRATING),
            HrvTrendPoint("2026-11-02", 2L, 4.1, null, null, null, ReadinessStatus.CALIBRATING),
            HrvTrendPoint("2026-11-03", 3L, 4.2, null, null, null, ReadinessStatus.CALIBRATING),
            HrvTrendPoint("2026-11-04", 4L, 4.05, null, null, null, ReadinessStatus.CALIBRATING),
            HrvTrendPoint("2026-11-05", 5L, 12.0, null, null, null, ReadinessStatus.CALIBRATING) // Extreme outlier
        )

        val (yMin, yMax) = HrvReadinessTrendMath.computeOutlierResilientYDomain(points, HrvTrendMetricMode.LN_RMSSD)
        // Ensure yMax did not expand all the way to 12.0 + pad
        assertTrue("yMax ($yMax) must be clamped below raw outlier 12.0", yMax < 10.0)
        assertTrue("yMin ($yMin) must be non-negative and close to ~3.5", yMin >= 0.0 && yMin < 4.0)
    }

    @Test
    fun testFindNearestPoint_projectsTouchCoordinateCorrectly() {
        val points = listOf(
            HrvTrendPoint("2026-11-01", 1L, 4.0, null, null, null, ReadinessStatus.CALIBRATING),
            HrvTrendPoint("2026-11-02", 2L, 4.1, null, null, null, ReadinessStatus.CALIBRATING),
            HrvTrendPoint("2026-11-03", 3L, 4.2, null, null, null, ReadinessStatus.CALIBRATING)
        )

        val leftPad = 40f
        val plotW = 200f

        // Near start -> point 0
        val p0 = HrvReadinessTrendMath.findNearestPoint(50f, leftPad, plotW, points)
        assertEquals("2026-11-01", p0?.dateIso)

        // Near middle (leftPad + 100f = 140f) -> point 1
        val p1 = HrvReadinessTrendMath.findNearestPoint(140f, leftPad, plotW, points)
        assertEquals("2026-11-02", p1?.dateIso)

        // Near end (leftPad + 200f = 240f) -> point 2
        val p2 = HrvReadinessTrendMath.findNearestPoint(235f, leftPad, plotW, points)
        assertEquals("2026-11-03", p2?.dateIso)
    }

    @Test
    fun testCalculateTrendSeries_preservesNonHrvWorkoutDaysWithNullRawValueAndLoad() {
        val entries = listOf(
            createEntry(id = 1, dateIso = "2026-11-01", lnRmssd = 4.0),
            createEntry(id = 2, dateIso = "2026-11-03", lnRmssd = 4.2)
        )
        val loads = mapOf(
            "2026-11-02" to 95.0 // Non-HRV workout day in between
        )

        val series = HrvReadinessTrendMath.calculateTrendSeries(
            entries = entries,
            selectedPosture = HrvPosture.SUPINE,
            metricMode = HrvTrendMetricMode.LN_RMSSD,
            dailyTrainingLoads = loads
        )

        assertEquals(3, series.points.size)
        // Day 1: HRV reading
        assertEquals("2026-11-01", series.points[0].dateIso)
        assertEquals(4.0, series.points[0].rawValue!!, 0.001)
        assertNull(series.points[0].trainingLoad)

        // Day 2: Workout only, rawValue is null, load is 95.0
        assertEquals("2026-11-02", series.points[1].dateIso)
        assertNull(series.points[1].rawValue)
        assertEquals(95.0, series.points[1].trainingLoad!!, 0.01)

        // Day 3: HRV reading
        assertEquals("2026-11-03", series.points[2].dateIso)
        assertEquals(4.2, series.points[2].rawValue!!, 0.001)
        assertNull(series.points[2].trainingLoad)

        assertEquals(95.0, series.maxTrainingLoad, 0.01)
    }

    @Test
    fun testFindNearestPoint_calendarEpochDayProjectionWithMultiDayGaps() {
        // Multi-day gap: Nov 01 (day 0) and Nov 05 (day 4) -> 4 day span
        val points = listOf(
            HrvTrendPoint("2026-11-01", 1L, 4.0, null, null, null, ReadinessStatus.CALIBRATING),
            HrvTrendPoint("2026-11-05", 2L, 4.2, null, null, null, ReadinessStatus.CALIBRATING)
        )

        val leftPad = 40f
        val plotW = 200f

        // Touch at 20% along width: targetEpoch = 0 + 0.2 * 4 = 0.8 -> closer to day 0 (dist 0.8) than day 4 (dist 3.2)
        val pStart = HrvReadinessTrendMath.findNearestPoint(leftPad + 40f, leftPad, plotW, points)
        assertEquals("2026-11-01", pStart?.dateIso)

        // Touch at 80% along width: targetEpoch = 0 + 0.8 * 4 = 3.2 -> closer to day 4 (dist 0.8) than day 0 (dist 3.2)
        val pEnd = HrvReadinessTrendMath.findNearestPoint(leftPad + 160f, leftPad, plotW, points)
        assertEquals("2026-11-05", pEnd?.dateIso)
    }

    @Test
    fun testNotesPreservedInTrendPointsAndProvenanceBadgeParsing() {
        val entryWithNotes = createEntry(
            id = 10L,
            dateIso = "2026-11-01",
            posture = HrvPosture.OVERNIGHT_WEARABLE
        ).copy(notes = "HealthConnect:com.ouraring.oura:12345")

        val series = HrvReadinessTrendMath.calculateTrendSeries(
            entries = listOf(entryWithNotes),
            selectedPosture = HrvPosture.OVERNIGHT_WEARABLE,
            metricMode = HrvTrendMetricMode.LN_RMSSD,
            dailyTrainingLoads = emptyMap()
        )

        assertEquals(1, series.points.size)
        assertEquals("HealthConnect:com.ouraring.oura:12345", series.points[0].notes)

        // Test parseProvenanceBadge (REQ-PER-033.1)
        assertEquals("Health Connect (Oura)", parseProvenanceBadge("HealthConnect:com.ouraring.oura:12345"))
        assertEquals("Health Connect (Fitbit)", parseProvenanceBadge("HealthConnect:com.fitbit.FitbitMobile"))
        assertEquals("Health Connect (Whoop)", parseProvenanceBadge("HealthConnect:com.whoop.client"))
        assertEquals("Health Connect (Garmin)", parseProvenanceBadge("HealthConnect:com.garmin.android.apps.connectmobile"))
        assertEquals("Health Connect (Samsung Health)", parseProvenanceBadge("HealthConnect:com.sec.android.app.shealth"))
        assertEquals("Health Connect (Google Fit)", parseProvenanceBadge("HealthConnect:com.google.android.apps.fitness"))
        assertEquals("Health Connect", parseProvenanceBadge("HealthConnect"))
        assertEquals("Health Connect", parseProvenanceBadge(null))
    }
}
