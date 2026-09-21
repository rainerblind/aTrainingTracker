/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.atrainingtracker.trainingtracker.ui.aftermath.periodlist

import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData
import com.google.android.gms.maps.model.LatLng
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime

/**
 * Unit test suite verifying SpatialRegionEngine algorithms under REQ-PER-013 and TST-PER-019.
 * Covers:
 * 1. Single-region fast-path and coordinate invariance (< 60 km)
 * 2. Multi-continent partitioning (Europe and USA)
 * 3. Dominant region ranking (frequency, distance, recency tie-breaking)
 * 4. Antimeridian crossing normalization (+179° / -179°)
 * 5. Fast-path and cluster boundary sensitivity (45 km vs 110 km)
 * 6. Non-GPS, null, and (0.0, 0.0) coordinate resilience
 */
class SpatialRegionEngineTest {

    private fun createWorkout(
        id: Long,
        lat: Double,
        lng: Double,
        distanceMeters: Double = 10_000.0,
        startTimeS: Long = 1_700_000_000L,
        sport: BSportType = BSportType.RUN
    ): WorkoutData {
        val now = LocalDateTime.now()
        return WorkoutData(
            id = id,
            finished = true,
            fileBaseName = "dummy_$id",
            workoutName = "Workout $id",
            sportId = 1L,
            sportName = sport.name,
            bSportType = sport,
            startTimeS = startTimeS,
            formattedDate = now.toLocalDate().toString(),
            formattedTime = now.toLocalTime().toString(),
            localDateTime = now,
            equipmentName = null,
            equipmentId = 0L,
            commute = false,
            trainer = false,
            mapPolyline = "",
            encodedAltitudes = "",
            encodedDistances = "",
            uploadToStrava = 0,
            totalDistance = distanceMeters,
            maxDisplacement = 2000.0,
            activeTimeSec = 1800L,
            totalTimeSec = 1860L,
            avgSpeedMps = 2.77,
            ascentMeters = 100L,
            descentMeters = 100L,
            minAltitude = 200.0,
            maxAltitude = 300.0,
            maxAltitudeLatLng = LatLng(lat, lng),
            maxDisplacementLatLng = LatLng(lat, lng),
            startLatLng = LatLng(lat, lng),
            endLatLng = LatLng(lat, lng),
            description = null,
            goal = null,
            method = null,
            stravaSportName = null,
            stravaActivityData = null,
            clusterId = 0L,
            clusterName = null,
            minLat = lat,
            maxLat = lat,
            minLng = lng,
            maxLng = lng
        )
    }

    @Test
    fun testSingleRegionWithinTerritory_returnsSingleRegionWithExactBounds() {
        // Workouts in Munich and surroundings within ~50 km diagonal
        val w1 = createWorkout(1L, 48.1351, 11.5820) // Munich center
        val w2 = createWorkout(2L, 48.0000, 11.4000) // Starnberg
        val w3 = createWorkout(3L, 48.2000, 11.7000) // Ismaning

        val regions = SpatialRegionEngine.detectRegions(listOf(w1, w2, w3))

        assertEquals(1, regions.size)
        val region = regions[0]
        assertTrue(region.isPrimary)
        assertEquals(3, region.workoutCount)
        assertEquals(30_000.0, region.totalDistanceMeters, 0.001)

        // Bounds should enclose all 3 coordinates
        assertEquals(48.0000, region.bounds.southwest.latitude, 0.001)
        assertEquals(48.2000, region.bounds.northeast.latitude, 0.001)
        assertEquals(11.4000, region.bounds.southwest.longitude, 0.001)
        assertEquals(11.7000, region.bounds.northeast.longitude, 0.001)
    }

    @Test
    fun testMultiContinentWorkouts_partitionsIntoEuropeAndUSA() {
        // 5 workouts in Europe (Germany, ~48.5°N, 9.0°E)
        val europeWorkouts = (1..5).map { i ->
            createWorkout(
                id = i.toLong(),
                lat = 48.5 + (i * 0.05),
                lng = 9.0 + (i * 0.05),
                distanceMeters = 10_000.0,
                startTimeS = 1_700_000_000L + (i * 1000)
            )
        }

        // 2 workouts in USA (California, ~37.7°N, -122.4°W)
        val usaWorkouts = (6..7).map { i ->
            createWorkout(
                id = i.toLong(),
                lat = 37.7 + (i * 0.05),
                lng = -122.4 + (i * 0.05),
                distanceMeters = 5_000.0,
                startTimeS = 1_690_000_000L + (i * 1000)
            )
        }

        val allWorkouts = europeWorkouts + usaWorkouts
        val regions = SpatialRegionEngine.detectRegions(allWorkouts)

        // Must partition into exactly 2 distinct regions
        assertEquals(2, regions.size)

        // Europe should be primary due to workout frequency (5 > 2)
        val primary = regions[0]
        val secondary = regions[1]

        assertTrue(primary.isPrimary)
        assertEquals(5, primary.workoutCount)
        assertTrue(primary.bounds.southwest.latitude > 45.0)
        assertTrue(primary.bounds.southwest.longitude > 0.0)

        assertFalse(secondary.isPrimary)
        assertEquals(2, secondary.workoutCount)
        assertTrue(secondary.bounds.southwest.longitude < -100.0)

        // Neither region should encompass Greenland or the North Atlantic ocean
        assertFalse(primary.bounds.contains(LatLng(65.0, -40.0)))
        assertFalse(secondary.bounds.contains(LatLng(65.0, -40.0)))
    }

    @Test
    fun testDominantRegionRanking_breaksTiesByDistanceAndRecency() {
        // Case A: Equal count (2 vs 2), broken by total distance
        val r1w1 = createWorkout(1L, 48.0, 11.0, distanceMeters = 20_000.0, startTimeS = 1_000L)
        val r1w2 = createWorkout(2L, 48.1, 11.1, distanceMeters = 20_000.0, startTimeS = 2_000L) // Total 40km

        val r2w1 = createWorkout(3L, 40.0, -74.0, distanceMeters = 10_000.0, startTimeS = 1_000L)
        val r2w2 = createWorkout(4L, 40.1, -74.1, distanceMeters = 10_000.0, startTimeS = 3_000L) // Total 20km

        val regionsDistanceTie = SpatialRegionEngine.detectRegions(listOf(r1w1, r1w2, r2w1, r2w2))
        assertEquals(2, regionsDistanceTie.size)
        assertEquals("region_0", regionsDistanceTie[0].id)
        assertTrue(regionsDistanceTie[0].isPrimary)
        assertEquals(40_000.0, regionsDistanceTie[0].totalDistanceMeters, 0.001)

        // Case B: Equal count (1 vs 1) and equal distance, broken by recency (startTimeS)
        val rA = createWorkout(10L, 48.0, 11.0, distanceMeters = 10_000.0, startTimeS = 1_000L)
        val rB = createWorkout(11L, 40.0, -74.0, distanceMeters = 10_000.0, startTimeS = 5_000L) // More recent

        val regionsRecencyTie = SpatialRegionEngine.detectRegions(listOf(rA, rB))
        assertEquals(2, regionsRecencyTie.size)
        assertTrue(regionsRecencyTie[0].isPrimary)
        assertEquals(5_000L, regionsRecencyTie[0].mostRecentTimestampS)
        assertEquals(11L, regionsRecencyTie[0].workoutIds.first())
    }

    @Test
    fun testAntimeridianCrossing_buildsWrappedBoundsWithoutWorldSpan() {
        // Points on both sides of the 180° meridian (e.g., Fiji / Tuvalu / Aleutian islands)
        // Point 1: 179.0° E, Point 2: -179.0° W (179.0° W). Geodesic separation is ~2° (approx 220 km)
        val p1 = LatLng(-16.0, 179.0)
        val p2 = LatLng(-16.0, -179.0)

        val bounds = SpatialRegionEngine.buildNormalizedBounds(listOf(p1, p2))

        // In Google Maps Android SDK, a bounds crossing the antimeridian eastward satisfies southwest.longitude > northeast.longitude
        assertTrue(
            "Expected antimeridian crossing bounds (southwest.longitude > northeast.longitude)",
            bounds.southwest.longitude > bounds.northeast.longitude
        )
        assertEquals(179.0, bounds.southwest.longitude, 0.001)
        assertEquals(-179.0, bounds.northeast.longitude, 0.001)
    }

    @Test
    fun testFastPathBoundary_45kmReturnsSingleRegion_110kmReturnsTwoRegions() {
        // Latitudinal distance: 1 degree of latitude is ~111 km.
        // 0.4 degrees latitude is ~44.5 km (< 60 km single-region envelope)
        val wCenter = createWorkout(1L, 48.0, 11.0)
        val w44km = createWorkout(2L, 48.4, 11.0)

        val singleRegion = SpatialRegionEngine.detectRegions(listOf(wCenter, w44km))
        assertEquals(1, singleRegion.size)
        assertTrue(singleRegion[0].isPrimary)

        // 1.0 degree latitude is ~111.2 km (> 75 km REGION_CLUSTER_THRESHOLD_METERS and > 60 km single-region envelope)
        val w111km = createWorkout(3L, 49.0, 11.0)
        val twoRegions = SpatialRegionEngine.detectRegions(listOf(wCenter, w111km))
        assertEquals(2, twoRegions.size)
    }

    @Test
    fun testNonGpsAndSentinelCoordinates_safelyBypassed() {
        val validWorkout = createWorkout(1L, 48.1351, 11.5820)
        
        // Corrupted / Non-GPS workouts: (0.0, 0.0) sentinel or null
        val nullGpsWorkout = createWorkout(2L, 0.0, 0.0).copy(
            startLatLng = null,
            endLatLng = null,
            maxDisplacementLatLng = null,
            maxAltitudeLatLng = null,
            minLat = null,
            maxLat = null,
            minLng = null,
            maxLng = null
        )
        val sentinelWorkout = createWorkout(3L, 0.0, 0.0)

        val regions = SpatialRegionEngine.detectRegions(listOf(validWorkout, nullGpsWorkout, sentinelWorkout))

        // Non-GPS workouts should be bypassed safely without crash or NullPointerException
        assertEquals(1, regions.size)
        assertEquals(1, regions[0].workoutCount)
        assertEquals(1L, regions[0].workoutIds.first())
    }

    @Test
    fun testBuildNormalizedBounds_singlePointHasSafeNonZeroSpan() {
        val singlePoint = LatLng(48.7758, 9.1829)
        val bounds = SpatialRegionEngine.buildNormalizedBounds(listOf(singlePoint))

        // Ensure southwest != northeast so Google Maps newLatLngBounds does not throw IllegalArgumentException
        assertNotEquals(bounds.southwest.latitude, bounds.northeast.latitude, 0.0001)
        assertNotEquals(bounds.southwest.longitude, bounds.northeast.longitude, 0.0001)
        assertTrue(bounds.southwest.latitude < bounds.northeast.latitude)
        assertTrue(bounds.southwest.longitude < bounds.northeast.longitude)
        assertEquals(48.7758 - SpatialRegionEngine.MIN_BOUNDS_DELTA_DEGREES, bounds.southwest.latitude, 0.0001)
        assertEquals(48.7758 + SpatialRegionEngine.MIN_BOUNDS_DELTA_DEGREES, bounds.northeast.latitude, 0.0001)
    }

    @Test
    fun testDetectRegionsFromPaths_multiContinent_isolatesRoutesAndBounds() {
        // Germany path: 3 coordinates around Stuttgart (0.1 deg span)
        val germanyPath = listOf(
            LatLng(48.77, 9.18),
            LatLng(48.80, 9.22),
            LatLng(48.85, 9.26)
        )

        // California path: 3 coordinates around San Francisco (0.1 deg span)
        val californiaPath = listOf(
            LatLng(37.77, -122.41),
            LatLng(37.80, -122.44),
            LatLng(37.85, -122.48)
        )

        val pathRegions = SpatialRegionEngine.detectRegionsFromPaths(listOf(germanyPath, californiaPath))

        assertEquals(2, pathRegions.size)
        val primary = pathRegions[0]
        val secondary = pathRegions[1]

        assertTrue(primary.region.isPrimary)
        assertFalse(secondary.region.isPrimary)
        assertEquals(1, primary.paths.size)
        assertEquals(1, secondary.paths.size)

        // Ensure primary region bounds tightly enclose the entire path coordinates, not just the start point
        val primaryBounds = primary.region.bounds
        val expectedMinLat = primary.paths[0].minOf { it.latitude }
        val expectedMaxLat = primary.paths[0].maxOf { it.latitude }
        assertEquals(expectedMinLat, primaryBounds.southwest.latitude, 0.001)
        assertEquals(expectedMaxLat, primaryBounds.northeast.latitude, 0.001)
    }
}

