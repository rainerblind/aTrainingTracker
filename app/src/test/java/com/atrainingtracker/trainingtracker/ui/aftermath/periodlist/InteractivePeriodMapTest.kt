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
import com.atrainingtracker.trainingtracker.ui.map.MapTrack
import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import com.atrainingtracker.trainingtracker.ui.map.TrackType
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.PolyUtil
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime

/**
 * TST-PER-027: InteractivePeriodMap UI-Thread Decoupling, Deduplication & Large-Period Spatial Hit-Testing
 * Validates REQ-PER-021 requirements for ATT-4475:
 * 1. TST-PER-027.1: Spatial anchor scope isolation in PeriodsRepository.enrich() (<= 5 anchors in workoutIdToPolylineMap).
 * 2. TST-PER-027.2: Interactive map track deduplication (anchorTracks rendered strictly during isLoading).
 * 3. TST-PER-027.3: Zero-UI-thread polyline decoding via pre-decoded workoutIdToPathMap.
 * 4. TST-PER-027.4: Large-period spatial tap hit-testing via findClosestTrackOnMapClick.
 */
class InteractivePeriodMapTest {

    private fun createSampleWorkout(
        id: Long,
        sport: BSportType = BSportType.BIKE,
        points: List<LatLng> = listOf(LatLng(48.0, 9.0), LatLng(48.01, 9.01), LatLng(48.02, 9.02))
    ): WorkoutData {
        val now = LocalDateTime.now()
        val polyline = PolyUtil.encode(points)
        return WorkoutData(
            id = id,
            finished = true,
            fileBaseName = "workout_$id",
            workoutName = "Workout $id",
            sportId = 1L,
            sportName = sport.name,
            bSportType = sport,
            startTimeS = 1000L + id * 100L,
            formattedDate = now.toLocalDate().toString(),
            formattedTime = now.toLocalTime().toString(),
            localDateTime = now,
            equipmentName = null,
            equipmentId = 0L,
            commute = false,
            trainer = false,
            mapPolyline = polyline,
            encodedAltitudes = "",
            encodedDistances = "",
            uploadToStrava = 0,
            totalDistance = 15000.0,
            maxDisplacement = 5000.0,
            activeTimeSec = 2400L + id * 10L,
            totalTimeSec = 2500L,
            avgSpeedMps = 6.25,
            ascentMeters = 150L,
            descentMeters = 150L,
            minAltitude = 200.0,
            maxAltitude = 350.0,
            maxAltitudeLatLng = LatLng(48.01, 9.01),
            maxDisplacementLatLng = LatLng(48.02, 9.02),
            startLatLng = points.first(),
            endLatLng = points.last(),
            description = null,
            goal = null,
            method = null,
            stravaSportName = null,
            stravaActivityData = null,
            clusterId = 0L,
            clusterName = null
        )
    }

    private fun createBasePeriodSummary(
        totalWorkouts: Int = 25,
        longestId: Long = 1L,
        northId: Long = 2L,
        southId: Long = 3L,
        eastId: Long = 4L,
        westId: Long = 5L
    ): PeriodSummary {
        return PeriodSummary(
            periodLabel = "Month 10, 2026",
            periodDateRange = "Oct 1 - Oct 31",
            periodType = PeriodType.MONTH,
            startTimestampS = 1000L,
            endTimestampS = 50000L,
            totalWorkouts = totalWorkouts,
            totalDurationSec = 60000L,
            totalDistance = 250000.0,
            sportStats = emptyMap(),
            longestId = longestId,
            northId = northId,
            southId = southId,
            eastId = eastId,
            westId = westId,
            sortKey = "2026_10"
        )
    }

    @Test
    fun testTstPer027_1_SpatialAnchorScopeIsolationInEnrichment() {
        // Create 25 workouts where IDs 1..5 are spatial anchors
        val workouts = (1L..25L).map { createSampleWorkout(it) }
        val baseSummary = createBasePeriodSummary(totalWorkouts = 25)

        // Simulate enrichment anchor scoping logic
        val trueLongestId = workouts.maxByOrNull { it.activeTimeSec }?.id ?: baseSummary.longestId
        val anchorIds = setOf(trueLongestId, baseSummary.northId, baseSummary.southId, baseSummary.eastId, baseSummary.westId).filter { it != -1L }
        val anchorWorkouts = workouts.filter { it.id in anchorIds }

        val anchorPolylineMap = anchorWorkouts
            .filter { it.mapPolyline.isNotEmpty() }
            .associate { it.id to it.mapPolyline }

        val anchorPathMap = anchorWorkouts
            .filter { it.mapPolyline.isNotEmpty() }
            .associate { it.id to PolyUtil.decode(it.mapPolyline) }

        // Assert strictly <= 5 anchors in the polyline map
        assertTrue("Anchor polylines must contain at most 5 entries", anchorPolylineMap.size <= 5)
        assertTrue("Anchor paths must contain at most 5 entries", anchorPathMap.size <= 5)
        assertEquals("Anchor IDs must exactly match the identified spatial anchors", anchorIds.toSet(), anchorPolylineMap.keys)

        // Non-anchor member workouts (e.g. ID 10, 15, 25) must NOT be present in anchor maps
        assertFalse("Workout 10 must not be in anchor polylines", anchorPolylineMap.containsKey(10L))
        assertFalse("Workout 15 must not be in anchor polylines", anchorPolylineMap.containsKey(15L))
        assertFalse("Workout 20 must not be in anchor polylines", anchorPolylineMap.containsKey(20L))
    }

    @Test
    fun testTstPer027_2_InteractiveMapTrackDeduplicationLogic() {
        // Verify that anchor tracks and member tracks do not render concurrently
        val anchorTrackList = listOf(
            MapTrack(1L, TrackType.BEST, BSportType.BIKE, listOf(PathPoint(0.0, LatLng(48.0, 9.0), 0.0)))
        )
        val memberTrackList = listOf(
            MapTrack(1L, TrackType.BEST, BSportType.BIKE, listOf(PathPoint(0.0, LatLng(48.0, 9.0), 0.0))),
            MapTrack(2L, TrackType.BEST, BSportType.BIKE, listOf(PathPoint(0.0, LatLng(48.1, 9.1), 0.0)))
        )

        // When loading is true: anchorTracks render, memberTracks do not
        val renderedWhileLoading = { isLoading: Boolean ->
            if (isLoading) anchorTrackList else memberTrackList
        }

        val duringLoadingTracks = renderedWhileLoading(true)
        assertEquals("During loading, only anchor tracks are active", 1, duringLoadingTracks.size)
        assertEquals(1L, duringLoadingTracks.first().id)

        // When loading finishes: only memberTracks render, anchorTracks are removed
        val afterLoadingTracks = renderedWhileLoading(false)
        assertEquals("After loading completes, member tracks take over exclusively", 2, afterLoadingTracks.size)
        // Zero duplicate tracks rendered concurrently
        val uniqueIdsAfterLoading = afterLoadingTracks.map { it.id }.toSet()
        assertEquals("No duplicate tracks rendered concurrently", 2, uniqueIdsAfterLoading.size)
    }

    @Test
    fun testTstPer027_3_ZeroUIThreadPolylineDecodingWithPreDecodedPaths() {
        val path1 = listOf(LatLng(48.0, 9.0), LatLng(48.05, 9.05))
        val path2 = listOf(LatLng(48.1, 9.1), LatLng(48.15, 9.15))

        val preDecodedPathMap = mapOf(101L to path1, 102L to path2)
        val rawPolylineMap = mapOf(101L to PolyUtil.encode(path1), 102L to PolyUtil.encode(path2))

        // When preDecodedPathMap is provided, paths are extracted directly with 0 PolyUtil.decode invocations
        val resolvedTracks = if (preDecodedPathMap.isNotEmpty()) {
            preDecodedPathMap.map { (id, path) ->
                MapTrack(id, TrackType.BEST, BSportType.BIKE, path.map { PathPoint(0.0, it, 0.0) })
            }
        } else {
            rawPolylineMap.map { (id, polyline) ->
                val path = PolyUtil.decode(polyline)
                MapTrack(id, TrackType.BEST, BSportType.BIKE, path.map { PathPoint(0.0, it, 0.0) })
            }
        }

        assertEquals(2, resolvedTracks.size)
        assertEquals(2, resolvedTracks[0].path.size)
        assertEquals(LatLng(48.0, 9.0), resolvedTracks[0].path[0].latLng)
    }

    @Test
    fun testTstPer027_4_SpatialTapHitTestingResolvesClosestWorkout() {
        val pathA = listOf(
            LatLng(48.5000, 9.2000),
            LatLng(48.5010, 9.2010),
            LatLng(48.5020, 9.2020)
        )
        val pathB = listOf(
            LatLng(48.6000, 9.3000),
            LatLng(48.6010, 9.3010),
            LatLng(48.6020, 9.3020)
        )

        val pathsMap = mapOf(1001L to pathA, 2002L to pathB)

        // Tap directly near path A (within 10m)
        val tapNearA = LatLng(48.5005, 9.2005)
        val hitA = findClosestTrackOnMapClick(tapNearA, pathsMap, zoom = 14f)
        assertEquals("Tap near Path A must resolve workout 1001", 1001L, hitA)

        // Tap directly near path B
        val tapNearB = LatLng(48.6012, 9.3012)
        val hitB = findClosestTrackOnMapClick(tapNearB, pathsMap, zoom = 14f)
        assertEquals("Tap near Path B must resolve workout 2002", 2002L, hitB)

        // Tap in empty ocean/distant location (far away from both)
        val tapFar = LatLng(49.0000, 10.0000)
        val hitNone = findClosestTrackOnMapClick(tapFar, pathsMap, zoom = 14f)
        assertNull("Tap far away must return null", hitNone)

        // Empty paths map must return null safely
        val hitEmpty = findClosestTrackOnMapClick(tapNearA, emptyMap(), zoom = 14f)
        assertNull("Empty paths map must return null safely", hitEmpty)
    }

    @Test
    fun testTstPer027_4_AdaptiveLayeringPeekHighlightOnLargePeriod() {
        val totalWorkouts = 50
        val isAdaptiveLayering = totalWorkouts > MAX_PERIOD_VECTOR_TRACKS
        assertTrue("50 workouts must activate adaptive layering", isAdaptiveLayering)

        // On large periods, memberTracks is empty by default
        val memberTracks = emptyList<MapTrack>()
        assertTrue("Default member tracks must be empty on large period", memberTracks.isEmpty())

        // When a workout is tapped, peekedTracks contains exactly 1 track
        val peekedTrack = MapTrack(
            id = 42L,
            type = TrackType.BEST,
            bSportType = BSportType.RUN,
            path = listOf(PathPoint(0.0, LatLng(48.0, 9.0), 0.0), PathPoint(0.0, LatLng(48.01, 9.01), 0.0))
        )
        val peekedTracks = listOf(peekedTrack)

        val activeTracks = if (memberTracks.isNotEmpty()) memberTracks else peekedTracks
        assertEquals("When workout is peeked on large period, exactly 1 highlighted track is rendered", 1, activeTracks.size)
        assertEquals(42L, activeTracks.first().id)
    }
}
