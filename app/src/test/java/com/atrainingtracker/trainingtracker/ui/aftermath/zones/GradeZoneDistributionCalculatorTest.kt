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

package com.atrainingtracker.trainingtracker.ui.aftermath.zones

import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.ui.map.ElevationSmoothingMath
import com.google.android.gms.maps.model.LatLng
import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import com.atrainingtracker.trainingtracker.ui.theme.TTColor
import org.junit.Assert.*
import org.junit.Test

class GradeZoneDistributionCalculatorTest {

    @Test
    fun testDetermineGradeZone_canonicalThresholds() {
        // Negative / flat: Zone 1 (0-indexed = 0)
        assertEquals(0, ZoneDistributionCalculator.determineGradeZone(-10.0))
        assertEquals(0, ZoneDistributionCalculator.determineGradeZone(-0.5))
        assertEquals(0, ZoneDistributionCalculator.determineGradeZone(0.0))
        assertEquals(0, ZoneDistributionCalculator.determineGradeZone(1.99))

        // 2% .. 5%: Zone 2 (0-indexed = 1)
        assertEquals(1, ZoneDistributionCalculator.determineGradeZone(2.0))
        assertEquals(1, ZoneDistributionCalculator.determineGradeZone(3.5))
        assertEquals(1, ZoneDistributionCalculator.determineGradeZone(4.99))

        // 5% .. 10%: Zone 3 (0-indexed = 2)
        assertEquals(2, ZoneDistributionCalculator.determineGradeZone(5.0))
        assertEquals(2, ZoneDistributionCalculator.determineGradeZone(7.5))
        assertEquals(2, ZoneDistributionCalculator.determineGradeZone(9.99))

        // 10% .. 15%: Zone 4 (0-indexed = 3)
        assertEquals(3, ZoneDistributionCalculator.determineGradeZone(10.0))
        assertEquals(3, ZoneDistributionCalculator.determineGradeZone(12.5))
        assertEquals(3, ZoneDistributionCalculator.determineGradeZone(14.99))

        // 15% .. 20%: Zone 5 (0-indexed = 4)
        assertEquals(4, ZoneDistributionCalculator.determineGradeZone(15.0))
        assertEquals(4, ZoneDistributionCalculator.determineGradeZone(17.5))
        assertEquals(4, ZoneDistributionCalculator.determineGradeZone(19.99))

        // 20% .. 25%: Zone 6 (0-indexed = 5)
        assertEquals(5, ZoneDistributionCalculator.determineGradeZone(20.0))
        assertEquals(5, ZoneDistributionCalculator.determineGradeZone(22.5))
        assertEquals(5, ZoneDistributionCalculator.determineGradeZone(24.99))

        // > 25%: Zone 7 (0-indexed = 6)
        assertEquals(6, ZoneDistributionCalculator.determineGradeZone(25.0))
        assertEquals(6, ZoneDistributionCalculator.determineGradeZone(30.0))

        // Non-finite values fallback to Zone 1
        assertEquals(0, ZoneDistributionCalculator.determineGradeZone(Double.NaN))
        assertEquals(0, ZoneDistributionCalculator.determineGradeZone(Double.POSITIVE_INFINITY))
    }

    @Test
    fun testCalculateGradeDistribution_sevenZonesPresentAndStructured() {
        // Construct track segments covering negative, flat, and climbing slopes
        val points = listOf(
            PathPoint(distance = 0.0, latLng = LatLng(0.0, 0.0), altitude = 100.0, timeSec = 0L),
            PathPoint(distance = 100.0, latLng = LatLng(0.0, 0.0), altitude = 99.0, timeSec = 10L),   // downhill (-1.0% -> Z1)
            PathPoint(distance = 200.0, latLng = LatLng(0.0, 0.0), altitude = 102.5, timeSec = 20L), // +3.5% -> Z2
            PathPoint(distance = 300.0, latLng = LatLng(0.0, 0.0), altitude = 110.0, timeSec = 30L), // +7.5% -> Z3
            PathPoint(distance = 400.0, latLng = LatLng(0.0, 0.0), altitude = 122.5, timeSec = 40L), // +12.5% -> Z4
            PathPoint(distance = 500.0, latLng = LatLng(0.0, 0.0), altitude = 140.0, timeSec = 50L), // +17.5% -> Z5
            PathPoint(distance = 600.0, latLng = LatLng(0.0, 0.0), altitude = 162.5, timeSec = 60L), // +22.5% -> Z6
            PathPoint(distance = 700.0, latLng = LatLng(0.0, 0.0), altitude = 192.5, timeSec = 70L)  // +30.0% -> Z7
        )

        // Use small sigma to preserve step grades in test
        val result = ZoneDistributionCalculator.calculateGradeDistribution(points, sigma = 5.0)
        assertNotNull(result)
        assertEquals(7, result!!.entries.size)

        // Verify zone indices 1..7 and badge labels
        val expectedBadgeLabels = listOf("<2%", "≥2%", "≥5%", "≥10%", "≥15%", "≥20%", "≥25%")
        for (i in 0..6) {
            assertEquals(i + 1, result.entries[i].zoneIndex)
            assertEquals(expectedBadgeLabels[i], result.entries[i].badgeLabel)
        }

        // Verify labels match canonical grade legend string resources
        assertEquals(R.string.grade_legend_under_2, result.entries[0].zoneLabelResId)
        assertEquals(R.string.grade_legend_2_to_5, result.entries[1].zoneLabelResId)
        assertEquals(R.string.grade_legend_5_to_10, result.entries[2].zoneLabelResId)
        assertEquals(R.string.grade_legend_10_to_15, result.entries[3].zoneLabelResId)
        assertEquals(R.string.grade_legend_15_to_20, result.entries[4].zoneLabelResId)
        assertEquals(R.string.grade_legend_20_to_25, result.entries[5].zoneLabelResId)
        assertEquals(R.string.grade_legend_over_25, result.entries[6].zoneLabelResId)

        // Verify zone colors match TTColor.Zone1..Zone7
        assertEquals(TTColor.Zone1, result.entries[0].color)
        assertEquals(TTColor.Zone2, result.entries[1].color)
        assertEquals(TTColor.Zone3, result.entries[2].color)
        assertEquals(TTColor.Zone4, result.entries[3].color)
        assertEquals(TTColor.Zone5, result.entries[4].color)
        assertEquals(TTColor.Zone6, result.entries[5].color)
        assertEquals(TTColor.Zone7, result.entries[6].color)

        // Total active time should be non-zero and equal to sum of zone durations
        assertTrue(result.totalActiveTimeSec > 0L)
        assertEquals(result.totalActiveTimeSec, result.entries.sumOf { it.durationSec })

        // Percentages should sum to 100%
        val totalPct = result.entries.sumOf { it.percentage.toDouble() }
        assertEquals(100.0, totalPct, 0.5)

        // Histogram should be present with 1% binWidth
        val hist = result.histogram
        assertNotNull(hist)
        assertEquals(1, hist!!.binWidth)
        assertTrue(hist.bins.isNotEmpty())
        assertEquals(result.totalActiveTimeSec, hist.totalActiveTimeSec)
    }

    @Test
    fun testCalculateGradeDistribution_emptyOrInsufficientData() {
        assertNull(ZoneDistributionCalculator.calculateGradeDistribution(emptyList()))
        assertNull(ZoneDistributionCalculator.calculateGradeDistribution(listOf(
            PathPoint(distance = 0.0, latLng = LatLng(0.0, 0.0), altitude = 100.0, timeSec = 0L)
        )))

        // Missing altitudes
        val noAltPoints = listOf(
            PathPoint(distance = 0.0, latLng = LatLng(0.0, 0.0), altitude = null, timeSec = 0L),
            PathPoint(distance = 10.0, latLng = LatLng(0.0, 0.0), altitude = null, timeSec = 5L)
        )
        assertNull(ZoneDistributionCalculator.calculateGradeDistribution(noAltPoints))
    }

    @Test
    fun testCalculateGradeDistribution_degenerateTimestamps() {
        // Multiple points with identical timeSec should not fail with 0 duration
        val points = listOf(
            PathPoint(distance = 0.0, latLng = LatLng(0.0, 0.0), altitude = 100.0, timeSec = 0L),
            PathPoint(distance = 50.0, latLng = LatLng(0.0, 0.0), altitude = 105.0, timeSec = 0L),
            PathPoint(distance = 100.0, latLng = LatLng(0.0, 0.0), altitude = 110.0, timeSec = 0L)
        )
        val result = ZoneDistributionCalculator.calculateGradeDistribution(points)
        assertNotNull(result)
        assertTrue("Degenerate timestamps should fallback to 1s duration per segment", result!!.totalActiveTimeSec > 0L)
    }

    @Test
    fun testCalculateGradeDistribution_deltaTimeClamping() {
        // Two points separated by 30 seconds should clamp delta time to MAX_DELTA_T_SEC (5s)
        val points = listOf(
            PathPoint(distance = 0.0, latLng = LatLng(0.0, 0.0), altitude = 100.0, timeSec = 0L),
            PathPoint(distance = 50.0, latLng = LatLng(0.0, 0.0), altitude = 102.0, timeSec = 30L)
        )
        val result = ZoneDistributionCalculator.calculateGradeDistribution(points)
        assertNotNull(result)
        assertEquals(5L, result!!.totalActiveTimeSec)
    }

    @Test
    fun testCalculateGradeDistribution_stationaryPauseAndJitterDoesNotProduceArtificialGradeSpikes() {
        // Simulate a 1 km ride on gentle terrain (0.8% grade) with a stationary pause at 500m
        // where GPS/barometric altitude jitters by +/- 0.5m.
        val points = mutableListOf<PathPoint>()
        var timeSec = 0L
        var dist = 0.0

        // Phase 1: 0 to 500m, climbing 417m to 421m at 5 m/s (100 samples)
        for (i in 0 until 100) {
            val alt = 417.0 + 4.0 * (dist / 500.0) + kotlin.math.sin(i * 0.1) * 0.1
            points.add(PathPoint(distance = dist, latLng = LatLng(0.0, 0.0), altitude = alt, timeSec = timeSec))
            timeSec += 1L
            dist += 5.0
        }

        // Phase 2: Stationary pause for 60 seconds (distance stays at 500m, altitude fluctuates +/- 0.5m)
        for (i in 0 until 60) {
            val alt = 421.0 + kotlin.math.sin(i * 0.3) * 0.5
            points.add(PathPoint(distance = dist, latLng = LatLng(0.0, 0.0), altitude = alt, timeSec = timeSec))
            timeSec += 1L
        }

        // Phase 3: 500m to 1000m, descending 421m to 417m at 5 m/s (100 samples)
        for (i in 0 until 100) {
            dist += 5.0
            timeSec += 1L
            val alt = 421.0 - 4.0 * ((dist - 500.0) / 500.0) + kotlin.math.sin(i * 0.1) * 0.1
            points.add(PathPoint(distance = dist, latLng = LatLng(0.0, 0.0), altitude = alt, timeSec = timeSec))
        }

        val result = ZoneDistributionCalculator.calculateGradeDistribution(points)
        assertNotNull(result)

        // Verify that the stationary jitter does NOT produce any artificial Zone 7 (> 25%) or Zone 6 (20-25%) spikes
        assertEquals("Zone 7 (>25%) must have 0 duration", 0L, result!!.entries[6].durationSec)
        assertEquals("Zone 6 (20-25%) must have 0 duration", 0L, result.entries[5].durationSec)
        assertEquals("Zone 5 (15-20%) must have 0 duration", 0L, result.entries[4].durationSec)
        assertEquals("Zone 4 (10-15%) must have 0 duration", 0L, result.entries[3].durationSec)

        // Histogram bounds must remain within physically realistic road grades (e.g. -5% to 5%)
        val hist = result.histogram
        assertNotNull(hist)
        assertTrue("Histogram dataMin must be >= -10%", hist!!.dataMin >= -10)
        assertTrue("Histogram dataMax must be <= 10%", hist.dataMax <= 10)
    }
}
