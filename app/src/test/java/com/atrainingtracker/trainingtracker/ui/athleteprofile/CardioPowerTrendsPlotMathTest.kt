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

import com.atrainingtracker.trainingtracker.database.CardioPowerDynamicsRecord
import com.atrainingtracker.trainingtracker.database.CardioPowerTrendSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [CardioPowerTrendsPlotMath] (REQ-REV-011 / TST-REV-012.3).
 */
class CardioPowerTrendsPlotMathTest {

    private fun createSample(
        workoutId: Long,
        timestampMs: Long,
        hr200: Double,
        tau: Double,
        k: Double,
        r2: Double = 0.85
    ): CardioPowerTrendSample {
        val record = CardioPowerDynamicsRecord(
            workoutId = workoutId,
            tauStart = tau,
            hr0Start = hr200 - 200.0 * k,
            kStart = k,
            hr200Start = hr200,
            r2Start = r2,
            rmseStart = 3.0
        )
        return CardioPowerTrendSample(workoutId, timestampMs, record)
    }

    @Test
    fun testConvertSamplesToPoints() {
        val baseMs = 1700000000000L
        val s1 = createSample(1L, baseMs + 2000, 145.0, 35.0, 0.20)
        val s2 = createSample(2L, baseMs + 1000, 140.0, 32.0, 0.25)

        val hrPoints = CardioPowerTrendsPlotMath.convertSamplesToPoints(listOf(s1, s2), CardioTrendMetric.HR_200W)
        assertEquals(2, hrPoints.size)
        // Check sorting by timestamp
        assertEquals(2L, hrPoints[0].workoutId)
        assertEquals(140.0, hrPoints[0].value, 0.01)
        assertEquals(1L, hrPoints[1].workoutId)
        assertEquals(145.0, hrPoints[1].value, 0.01)

        val tauPoints = CardioPowerTrendsPlotMath.convertSamplesToPoints(listOf(s1, s2), CardioTrendMetric.TAU)
        assertEquals(32.0, tauPoints[0].value, 0.01)
        assertEquals(35.0, tauPoints[1].value, 0.01)

        val slopePoints = CardioPowerTrendsPlotMath.convertSamplesToPoints(listOf(s1, s2), CardioTrendMetric.SLOPE)
        assertEquals(4.0, slopePoints[0].value, 0.01) // 1 / 0.25 = 4.0 W/bpm
        assertEquals(5.0, slopePoints[1].value, 0.01) // 1 / 0.20 = 5.0 W/bpm
    }

    @Test
    fun testRollingTrendCalculation() {
        val baseMs = 1700000000000L
        val dayMs = CardioPowerTrendsPlotMath.MILLIS_PER_DAY
        val p1 = CardioPlotPoint(baseMs, 150.0, 1L)
        val p2 = CardioPlotPoint(baseMs + 10 * dayMs, 140.0, 2L)
        val p3 = CardioPlotPoint(baseMs + 45 * dayMs, 130.0, 3L)

        val rolling = CardioPowerTrendsPlotMath.computeRollingTrend(listOf(p1, p2, p3), windowDays = 30)
        assertEquals(3, rolling.size)
        assertEquals(150.0, rolling[0].value, 0.01)
        // p2 is 10 days after p1, so window includes p1 and p2 -> avg(150, 140) = 145.0
        assertEquals(145.0, rolling[1].value, 0.01)
        // p3 is 35 days after p2, so window [15d, 45d] only includes p3 -> 130.0
        assertEquals(130.0, rolling[2].value, 0.01)
    }

    @Test
    fun testFilterPointsByTimeRange() {
        val nowMs = 1700000000000L
        val dayMs = CardioPowerTrendsPlotMath.MILLIS_PER_DAY
        val pRecent = CardioPlotPoint(nowMs - 10 * dayMs, 140.0, 1L)
        val pMid = CardioPlotPoint(nowMs - 80 * dayMs, 145.0, 2L)
        val pOld = CardioPlotPoint(nowMs - 200 * dayMs, 150.0, 3L)

        val allPoints = listOf(pOld, pMid, pRecent)

        val filtered3M = CardioPowerTrendsPlotMath.filterPointsByTimeRange(allPoints, MetricsTimeRange.THREE_MONTHS, nowMs)
        assertEquals(listOf(pMid, pRecent), filtered3M)

        val filtered6M = CardioPowerTrendsPlotMath.filterPointsByTimeRange(allPoints, MetricsTimeRange.SIX_MONTHS, nowMs)
        assertEquals(listOf(pMid, pRecent), filtered6M)

        val filteredAll = CardioPowerTrendsPlotMath.filterPointsByTimeRange(allPoints, MetricsTimeRange.ALL, nowMs)
        assertEquals(allPoints, filteredAll)
    }

    @Test
    fun testOutlierResilientDomainClamping() {
        // Test normal points + 1 extreme outlier
        val normalValues = listOf(130.0, 132.0, 135.0, 133.0, 134.0, 136.0, 131.0, 135.0)
        val withOutlier = normalValues + listOf(240.0) // 240 is massive outlier

        val (yMin, yMax) = CardioPowerTrendsPlotMath.computeOutlierResilientDomain(withOutlier, CardioTrendMetric.HR_200W)
        // Upper domain bound should be clamped by IQR fence (around 145) rather than 240
        assertTrue("yMax ($yMax) should be well below 200 due to IQR clamping", yMax < 180.0)
        assertTrue("yMin ($yMin) should be around 125", yMin > 115.0 && yMin < 135.0)
    }

    @Test
    fun testRule48Formatting() {
        val hrFormatted = CardioPowerTrendsPlotMath.formatYAxisValue(132.4, CardioTrendMetric.HR_200W)
        assertEquals("132 bpm", hrFormatted)

        val tauFormatted = CardioPowerTrendsPlotMath.formatYAxisValue(34.8, CardioTrendMetric.TAU)
        assertEquals("35 s", tauFormatted)

        val slopeFormatted = CardioPowerTrendsPlotMath.formatYAxisValue(4.82, CardioTrendMetric.SLOPE)
        assertTrue(slopeFormatted.contains("W/bpm") && (slopeFormatted.contains("4.8") || slopeFormatted.contains("4,8")))
    }
}
