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

package com.atrainingtracker.trainingtracker.ui.tracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max

/**
 * Unit and contract tests for [LiveMmpCurveChart], [MmpBenchmark] logarithmic scaling,
 * and PR breakthrough detection conforming to REQ-TRK-058.4, REQ-TRK-058.6, and TST-TRK-058.6b.
 */
class LiveMmpCurveChartTest {

    @Test
    fun logScaleDurationMappingIsMonotonicAndNormalized() {
        val f1 = MmpBenchmark.durationToLogFraction(1)
        val f5 = MmpBenchmark.durationToLogFraction(5)
        val f60 = MmpBenchmark.durationToLogFraction(60)
        val f300 = MmpBenchmark.durationToLogFraction(300)
        val f1200 = MmpBenchmark.durationToLogFraction(1200)
        val f7200 = MmpBenchmark.durationToLogFraction(7200)

        assertEquals(0.0f, f1, 0.001f)
        assertEquals(1.0f, f7200, 0.001f)

        assertTrue("5s > 1s", f5 > f1)
        assertTrue("60s > 5s", f60 > f5)
        assertTrue("300s > 60s", f300 > f60)
        assertTrue("1200s > 300s", f1200 > f300)
        assertTrue("7200s > 1200s", f7200 > f1200)
    }

    @Test
    fun benchmarkDurationsParity() {
        assertEquals(4, MmpBenchmark.ALL.size)
        assertEquals(5, MmpBenchmark.SPRINT_5S.durationSeconds)
        assertEquals(60, MmpBenchmark.ANAEROBIC_1M.durationSeconds)
        assertEquals(300, MmpBenchmark.VO2MAX_5M.durationSeconds)
        assertEquals(1200, MmpBenchmark.FTP_20M.durationSeconds)
    }

    @Test
    fun breakthroughDetection_identifiesExceededBenchmarks() {
        val reference = mapOf(
            5 to 600.0,
            60 to 400.0,
            300 to 300.0,
            1200 to 250.0
        )
        val peakPowers = mapOf(
            5 to 650.0,  // Breakthrough (+50W)
            60 to 390.0,  // No breakthrough
            300 to 325.0, // Breakthrough (+25W)
            1200 to 240.0 // No breakthrough
        )

        val data = LiveMmpCurveData(
            peakPowers = peakPowers,
            referencePoints = reference
        )

        val breakthroughs = data.getBreakthroughs()
        assertEquals(2, breakthroughs.size)

        val b5s = breakthroughs.first { it.benchmark == MmpBenchmark.SPRINT_5S }
        assertEquals(650.0, b5s.sessionWatts, 0.001)
        assertEquals(600.0, b5s.referenceWatts, 0.001)
        assertEquals(50.0, b5s.deltaWatts, 0.001)

        val b5m = breakthroughs.first { it.benchmark == MmpBenchmark.VO2MAX_5M }
        assertEquals(325.0, b5m.sessionWatts, 0.001)
        assertEquals(300.0, b5m.referenceWatts, 0.001)
        assertEquals(25.0, b5m.deltaWatts, 0.001)
    }

    @Test
    fun breakthroughDetection_returnsEmptyWhenSessionBelowReferenceOrMissing() {
        val reference = mapOf(5 to 700.0, 60 to 450.0)
        val peakPowers = mapOf(5 to 680.0, 60 to 420.0)

        val data = LiveMmpCurveData(
            peakPowers = peakPowers,
            referencePoints = reference
        )
        assertTrue(data.getBreakthroughs().isEmpty())

        val emptyData = LiveMmpCurveData()
        assertTrue(emptyData.getBreakthroughs().isEmpty())
    }

    @Test
    fun maxWattsHeadroom_accommodatesReferenceAndPeakValues() {
        val reference = mapOf(5 to 800.0)
        val peakPowers = mapOf(5 to 500.0)
        val data = LiveMmpCurveData(peakPowers = peakPowers, referencePoints = reference)

        val allWatts = data.peakPowers.values + data.rollingMeans.values +
                data.referencePoints.values + listOfNotNull(data.instantaneousPower)
        val maxObserved = allWatts.maxOrNull() ?: 200.0
        val maxWatts = max(400.0, maxObserved * 1.15)

        assertEquals(800.0 * 1.15, maxWatts, 0.001)
    }
}
