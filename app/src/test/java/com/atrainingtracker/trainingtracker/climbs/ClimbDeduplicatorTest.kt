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

package com.atrainingtracker.trainingtracker.climbs

import android.location.Location
import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import com.google.android.gms.maps.model.LatLng
import io.mockk.every
import io.mockk.mockkStatic
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import kotlin.math.*
import kotlin.system.measureTimeMillis

/**
 * Unit tests verifying endpoint proximity, heading alignment, corridor overlap,
 * and presentation deduplication in [ClimbDeduplicator] (REQ-CLM-011.1, REQ-CLM-011.3, TST-CLM-011.1, TST-CLM-011.3).
 */
class ClimbDeduplicatorTest {

    @Before
    fun setUp() {
        mockkStatic(Location::class)
        every { Location.distanceBetween(any(), any(), any(), any(), any()) } answers {
            val startLat = arg<Double>(0)
            val startLng = arg<Double>(1)
            val endLat = arg<Double>(2)
            val endLng = arg<Double>(3)
            val results = arg<FloatArray>(4)
            val dLat = Math.toRadians(endLat - startLat)
            val dLng = Math.toRadians(endLng - startLng)
            val a = sin(dLat / 2) * sin(dLat / 2) +
                    cos(Math.toRadians(startLat)) * cos(Math.toRadians(endLat)) *
                    sin(dLng / 2) * sin(dLng / 2)
            val c = 2 * atan2(sqrt(a), sqrt(1 - a))
            val dist = (6371000 * c).toFloat()
            results[0] = dist
        }
    }

    @Test
    fun computeHeading_returnsExpectedAzimuth() {
        // Due North: lat increases, lng unchanged
        val north = ClimbDeduplicator.computeHeading(48.0, 11.0, 48.01, 11.0)
        assertEquals(0.0, north, 1.0)

        // Due East: lng increases, lat unchanged
        val east = ClimbDeduplicator.computeHeading(48.0, 11.0, 48.0, 11.01)
        assertEquals(90.0, east, 1.0)

        // Due South
        val south = ClimbDeduplicator.computeHeading(48.01, 11.0, 48.0, 11.0)
        assertEquals(180.0, south, 1.0)
    }

    @Test
    fun isDuplicateOrOverlapping_matchesWhenEndpointsWithin100Meters() {
        val climbA = Climb(
            id = 1L,
            name = "Hill Base",
            startLat = 48.0,
            startLng = 11.0,
            endLat = 48.01,
            endLng = 11.01,
            distanceMeters = 1500.0,
            elevationGainMeters = 90.0,
            avgGradePercent = 6.0,
            maxGradePercent = 9.0,
            category = ClimbCategory.CAT_4
        )

        // Candidate with start ~75m away (0.00067 deg lat) and end ~65m away
        val climbB = Climb(
            id = 2L,
            name = "Hill Candidate",
            startLat = 48.00067,
            startLng = 11.0,
            endLat = 48.01058,
            endLng = 11.01,
            distanceMeters = 1490.0,
            elevationGainMeters = 88.0,
            avgGradePercent = 5.9,
            maxGradePercent = 8.8,
            category = ClimbCategory.CAT_4
        )

        assertTrue(
            "Endpoints within 100m should match as duplicate",
            ClimbDeduplicator.isDuplicateOrOverlapping(climbA, climbB, thresholdMeters = 50.0f)
        )
    }

    @Test
    fun isDuplicateOrOverlapping_matchesWhenCorridorOverlapsHeavilyInSameDirection() {
        // Road running from (48.0, 11.0) to (48.02, 11.0) ~2200m North
        val pathA = (0..20).map { i ->
            val lat = 48.0 + (i * 0.001)
            PathPoint(distance = i * 111.0, latLng = LatLng(lat, 11.0), altitude = 200.0 + i * 5.0)
        }
        val climbA = Climb(
            id = 10L,
            name = "North Mountain",
            startLat = 48.0,
            startLng = 11.0,
            endLat = 48.02,
            endLng = 11.0,
            distanceMeters = 2220.0,
            elevationGainMeters = 100.0,
            avgGradePercent = 4.5,
            maxGradePercent = 7.0,
            category = ClimbCategory.CAT_3,
            pathPoints = pathA
        )

        // Subsegment / shifted ascent starting 150m later (start lat 48.0015) along the exact same road
        val pathB = (2..18).map { i ->
            val lat = 48.0 + (i * 0.001)
            PathPoint(distance = (i - 2) * 111.0, latLng = LatLng(lat, 11.0), altitude = 210.0 + (i - 2) * 5.0)
        }
        val climbB = Climb(
            id = 20L,
            name = "North Mountain Subsegment",
            startLat = 48.0015, // > 150m from climbA start
            startLng = 11.0,
            endLat = 48.018,   // > 200m from climbA end
            endLng = 11.0,
            distanceMeters = 1776.0,
            elevationGainMeters = 80.0,
            avgGradePercent = 4.5,
            maxGradePercent = 7.0,
            category = ClimbCategory.CAT_4,
            pathPoints = pathB
        )

        assertTrue(
            "Heavily overlapping ascent along same corridor should match as duplicate",
            ClimbDeduplicator.isDuplicateOrOverlapping(climbA, climbB)
        )
    }

    @Test
    fun isDuplicateOrOverlapping_rejectsReverseHeadingAscents() {
        val climbNorth = Climb(
            id = 1L,
            name = "Northbound",
            startLat = 48.0,
            startLng = 11.0,
            endLat = 48.01,
            endLng = 11.0,
            distanceMeters = 1110.0,
            elevationGainMeters = 70.0,
            avgGradePercent = 6.3,
            maxGradePercent = 9.0
        )

        val climbSouth = Climb(
            id = 2L,
            name = "Southbound",
            startLat = 48.01,
            startLng = 11.0,
            endLat = 48.0,
            endLng = 11.0,
            distanceMeters = 1110.0,
            elevationGainMeters = 70.0,
            avgGradePercent = 6.3,
            maxGradePercent = 9.0
        )

        assertFalse(
            "Opposite heading ascents must not be deduplicated",
            ClimbDeduplicator.isDuplicateOrOverlapping(climbNorth, climbSouth)
        )
    }

    @Test
    fun isDuplicateOrOverlapping_rejectsPerpendicularOrDistantRoads() {
        val climb1 = Climb(
            id = 1L,
            name = "East Road",
            startLat = 48.0,
            startLng = 11.0,
            endLat = 48.0,
            endLng = 11.02,
            distanceMeters = 1500.0,
            elevationGainMeters = 80.0,
            avgGradePercent = 5.3,
            maxGradePercent = 8.0
        )

        // North Road starting nearby but heading North
        val climb2 = Climb(
            id = 2L,
            name = "North Road",
            startLat = 48.0,
            startLng = 11.0,
            endLat = 48.02,
            endLng = 11.0,
            distanceMeters = 1500.0,
            elevationGainMeters = 80.0,
            avgGradePercent = 5.3,
            maxGradePercent = 8.0
        )

        assertFalse(
            "Perpendicular routes should not be deduplicated",
            ClimbDeduplicator.isDuplicateOrOverlapping(climb1, climb2)
        )
    }

    @Test
    fun deduplicateForDisplay_removesOverlappingClimbs() {
        val climb1 = Climb(
            id = 1L,
            name = "Ascent Primary",
            startLat = 48.0,
            startLng = 11.0,
            endLat = 48.01,
            endLng = 11.01,
            distanceMeters = 1500.0,
            elevationGainMeters = 90.0,
            avgGradePercent = 6.0,
            maxGradePercent = 9.0
        )
        val duplicate1 = Climb(
            id = 2L,
            name = "Ascent Duplicate",
            startLat = 48.0005,
            startLng = 11.0005,
            endLat = 48.0105,
            endLng = 11.0105,
            distanceMeters = 1500.0,
            elevationGainMeters = 90.0,
            avgGradePercent = 6.0,
            maxGradePercent = 9.0
        )
        val distinctClimb = Climb(
            id = 3L,
            name = "Other Hill",
            startLat = 48.1,
            startLng = 11.2,
            endLat = 48.11,
            endLng = 11.21,
            distanceMeters = 1800.0,
            elevationGainMeters = 110.0,
            avgGradePercent = 6.1,
            maxGradePercent = 9.5
        )

        val input = listOf(climb1, duplicate1, distinctClimb)
        val deduplicated = ClimbDeduplicator.deduplicateForDisplay(input)

        assertEquals(2, deduplicated.size)
        assertTrue(deduplicated.contains(climb1))
        assertTrue(deduplicated.contains(distinctClimb))
        assertFalse(deduplicated.contains(duplicate1))
    }

    @Test
    fun isDuplicateOrOverlapping_rejectsDistantClimbsInstantlyViaBoundingBox() {
        // REQ-CLM-011.1 / TST-CLM-011.5: O(1) Bounding-box pre-filtering
        val climbMunich = Climb(
            id = 1001L,
            name = "Munich Hill",
            startLat = 48.1351,
            startLng = 11.5820,
            endLat = 48.1451,
            endLng = 11.5820,
            distanceMeters = 1110.0,
            elevationGainMeters = 75.0,
            avgGradePercent = 6.7,
            maxGradePercent = 9.0
        )
        // Climb in Hamburg with identical heading (0 deg due North) and identical elevation profile
        val climbHamburg = Climb(
            id = 1002L,
            name = "Hamburg Hill",
            startLat = 53.5511,
            startLng = 9.9937,
            endLat = 53.5611,
            endLng = 9.9937,
            distanceMeters = 1110.0,
            elevationGainMeters = 75.0,
            avgGradePercent = 6.7,
            maxGradePercent = 9.0
        )

        assertFalse("Munich and Hamburg bounding boxes must not overlap", climbMunich.boundingBox.overlaps(climbHamburg.boundingBox))
        assertFalse(
            "Distant climbs with identical heading and elevation must be rejected in O(1) via bounding box",
            ClimbDeduplicator.isDuplicateOrOverlapping(climbMunich, climbHamburg)
        )
    }

    @Test
    fun deduplicateForDisplay_performanceBenchmark300Climbs_completesUnder100ms() {
        // REQ-CLM-011.1, REQ-CLM-011.3 / TST-CLM-011.5: Benchmark asserting sub-100ms deduplication for dense dataset
        val climbs = mutableListOf<Climb>()

        // Generate 300 climbs across 50 distinct geographic regions (6 climbs per region, 3 pairs of duplicates)
        for (i in 0 until 50) {
            val baseLat = 45.0 + i * 0.2
            val baseLng = 10.0 + i * 0.2

            for (j in 0 until 3) {
                // Canonical climb
                val path = (0..20).map { step ->
                    PathPoint(
                        distance = step * 50.0,
                        latLng = LatLng(baseLat + j * 0.01 + step * 0.0005, baseLng + j * 0.01),
                        altitude = 300.0 + step * 3.0
                    )
                }
                val canonical = Climb(
                    id = (i * 10 + j * 2 + 1).toLong(),
                    name = "Region $i Climb $j",
                    startLat = path.first().latLng.latitude,
                    startLng = path.first().latLng.longitude,
                    endLat = path.last().latLng.latitude,
                    endLng = path.last().latLng.longitude,
                    distanceMeters = 1000.0,
                    elevationGainMeters = 60.0,
                    avgGradePercent = 6.0,
                    maxGradePercent = 9.0,
                    pathPoints = path
                )
                climbs.add(canonical)

                // Duplicate climb (overlapping within 50m)
                val dupPath = path.map { pt ->
                    pt.copy(latLng = LatLng(pt.latLng.latitude + 0.0002, pt.latLng.longitude + 0.0002))
                }
                val duplicate = Climb(
                    id = (i * 10 + j * 2 + 2).toLong(),
                    name = "Region $i Climb $j Dup",
                    startLat = dupPath.first().latLng.latitude,
                    startLng = dupPath.first().latLng.longitude,
                    endLat = dupPath.last().latLng.latitude,
                    endLng = dupPath.last().latLng.longitude,
                    distanceMeters = 1000.0,
                    elevationGainMeters = 60.0,
                    avgGradePercent = 6.0,
                    maxGradePercent = 9.0,
                    pathPoints = dupPath
                )
                climbs.add(duplicate)
            }
        }

        assertEquals("Total climbs generated for benchmark", 300, climbs.size)

        // Warm-up run
        val warmResult = ClimbDeduplicator.deduplicateForDisplay(climbs)
        assertEquals("Deduplicated result count should retain 150 canonical climbs", 150, warmResult.size)

        // Timed benchmark run
        val elapsedMs = measureTimeMillis {
            val result = ClimbDeduplicator.deduplicateForDisplay(climbs)
            assertEquals(150, result.size)
        }

        println("Performance benchmark: 300 climbs deduplicated in $elapsedMs ms")
        assertTrue(
            "Deduplicating 300 climbs must execute in under 100ms on JVM (actual: $elapsedMs ms)",
            elapsedMs < 100L
        )
    }
}
