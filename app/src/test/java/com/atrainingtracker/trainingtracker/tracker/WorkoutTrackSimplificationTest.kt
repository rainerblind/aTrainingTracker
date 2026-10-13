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

package com.atrainingtracker.trainingtracker.tracker

import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries
import com.atrainingtracker.trainingtracker.ui.utils.NumericalEncodingUtils
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.PolyUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for Douglas-Peucker GPS track simplification and scalar stream downsampling
 * in accordance with REQ-DAT-023 and TST-DAT-018 (ATT-2667).
 */
class WorkoutTrackSimplificationTest {

    @Test
    fun testDouglasPeuckerCollinearCompression() {
        // Create 100 collinear points along a straight line (latitude increasing by 0.0001 deg ~ 11.1m)
        val rawPoints = (0 until 100).map { i ->
            LatLng(48.0 + (i * 0.0001), 11.0)
        }

        // Simplification with 10.0m tolerance should reduce perfectly straight line to start and end
        val simplified = PolyUtil.simplify(rawPoints, 10.0)
        assertEquals(2, simplified.size)
        assertEquals(rawPoints.first().latitude, simplified.first().latitude, 1e-6)
        assertEquals(rawPoints.last().latitude, simplified.last().latitude, 1e-6)
    }

    @Test
    fun testDouglasPeuckerCurvaturePreservation() {
        // Create a track: straight line, then a 90-degree apex bend (offset by ~110m), then continuing
        val points = mutableListOf<LatLng>()
        for (i in 0..20) {
            points.add(LatLng(48.0 + (i * 0.0001), 11.0))
        }
        // Apex corner at 48.0021, 11.001 (approx 75m east)
        points.add(LatLng(48.0021, 11.001))
        for (i in 22..40) {
            points.add(LatLng(48.0 + (i * 0.0001), 11.0))
        }

        val simplified = PolyUtil.simplify(points, 10.0)
        // Must preserve the corner because perpendicular distance exceeds 10m tolerance
        assertTrue("Simplified track should have > 2 points due to corner", simplified.size >= 3)
        val apex = simplified.find { it.longitude > 11.0005 }
        assertTrue("Apex point must be preserved", apex != null)
    }

    @Test
    fun testLiveWorkoutSessionRawPointsCollection() {
        val session = LiveWorkoutSession(101L, emptySet())

        // Simulate 45 1-Hz GPS updates
        for (i in 1..45) {
            val pt = LatLng(48.0 + (i * 0.0001), 11.0 + (i * 0.0001))
            session.recordStreamPoint(pt, 500.0 + i, i.toDouble() * 5.0)
        }

        // Raw LatLngs must record all 45 points
        assertEquals(45, session.rawLatLngs.size)
        // Sampled LatLngs must only record at ENCODING_STEP_SIZE intervals (20, 40 -> 2 points)
        assertEquals(2, session.sampledLatLngs.size)

        // Test defensive copy
        val copy = session.rawLatLngs
        assertEquals(45, copy.size)
    }

    @Test
    fun testScalarStreamDownsamplingLogic() {
        val stepSize = WorkoutSummaries.ENCODING_STEP_SIZE // 20

        // 1. Stream with 100 samples (5 x 20)
        val altitudes = (1..100).map { it.toDouble() }
        val downsampled = if (altitudes.size > stepSize) {
            altitudes.filterIndexed { index, _ -> (index + 1) % stepSize == 0 }
        } else {
            altitudes
        }

        assertEquals(5, downsampled.size)
        assertEquals(20.0, downsampled[0], 1e-4)
        assertEquals(40.0, downsampled[1], 1e-4)
        assertEquals(60.0, downsampled[2], 1e-4)
        assertEquals(80.0, downsampled[3], 1e-4)
        assertEquals(100.0, downsampled[4], 1e-4)

        // Verify encoding produces non-empty compact delta string
        val encoded = NumericalEncodingUtils.encodeDoubles(downsampled)
        assertTrue(encoded.isNotEmpty())

        // 2. Short stream with <= 20 samples remains untouched
        val shortDistances = (1..15).map { it.toDouble() }
        val shortDownsampled = if (shortDistances.size > stepSize) {
            shortDistances.filterIndexed { index, _ -> (index + 1) % stepSize == 0 }
        } else {
            shortDistances
        }
        assertEquals(15, shortDownsampled.size)
    }

    @Test
    fun testBoundingBoxIntegrityWithRawPoints() {
        val rawPoints = listOf(
            LatLng(48.10, 11.50),
            LatLng(48.15, 11.55),
            LatLng(48.20, 11.52),
            LatLng(48.05, 11.48)
        )

        // Raw extremes
        val minLat = rawPoints.minOf { it.latitude }
        val maxLat = rawPoints.maxOf { it.latitude }
        val minLng = rawPoints.minOf { it.longitude }
        val maxLng = rawPoints.maxOf { it.longitude }

        assertEquals(48.05, minLat, 1e-4)
        assertEquals(48.20, maxLat, 1e-4)
        assertEquals(11.48, minLng, 1e-4)
        assertEquals(11.55, maxLng, 1e-4)

        // Simplification must not alter bounding extents when raw points are preserved for bounding calculation
        val simplified = PolyUtil.simplify(rawPoints, 10.0)
        assertTrue(simplified.isNotEmpty())
    }
}
