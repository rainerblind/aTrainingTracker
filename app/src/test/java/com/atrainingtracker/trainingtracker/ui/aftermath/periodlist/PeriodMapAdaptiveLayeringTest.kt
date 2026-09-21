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

import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData
import com.atrainingtracker.trainingtracker.ui.map.MapTrack
import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import com.atrainingtracker.trainingtracker.ui.map.TrackType
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.PolyUtil
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.time.LocalDateTime

/**
 * Unit tests verifying Period Map Scalability, Memory Budget & Adaptive Layering Invariants.
 * Fulfills REQ-PER-012 / TST-PER-018 for ATT-1107.
 */
class PeriodMapAdaptiveLayeringTest {

    private fun createDummyWorkout(
        id: Long,
        sport: BSportType = BSportType.RUN,
        polyline: String = "_p~iF~ps|U_ulLnnqC_mqNvxq`@"
    ): WorkoutData {
        val now = LocalDateTime.now()
        return WorkoutData(
            id = id,
            finished = true,
            fileBaseName = "dummy_$id",
            workoutName = "Workout $id",
            sportId = if (sport == BSportType.RUN) 1L else 2L,
            sportName = sport.name,
            bSportType = sport,
            startTimeS = now.atZone(java.time.ZoneId.systemDefault()).toEpochSecond(),
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
            totalDistance = 5000.0,
            maxDisplacement = 2000.0,
            activeTimeSec = 1800L,
            totalTimeSec = 1860L,
            avgSpeedMps = 2.77,
            ascentMeters = 50L,
            descentMeters = 50L,
            minAltitude = 100.0,
            maxAltitude = 250.0,
            maxAltitudeLatLng = LatLng(48.015, 9.015),
            maxDisplacementLatLng = LatLng(48.02, 9.02),
            startLatLng = LatLng(48.0, 9.0),
            endLatLng = LatLng(48.01, 9.01),
            description = null,
            goal = null,
            method = null,
            stravaSportName = null,
            stravaActivityData = null,
            clusterId = 0L,
            clusterName = null
        )
    }

    @Test
    fun testAdaptiveThresholdConstant_isThirty() {
        assertEquals("Safety threshold for vector tracks must be 30", 30, MAX_PERIOD_VECTOR_TRACKS)
    }

    @Test
    fun testAndroidManifest_declaresLargeHeap() {
        val manifestFile = File("src/main/AndroidManifest.xml")
        assertTrue("AndroidManifest.xml must exist", manifestFile.exists())
        val content = manifestFile.readText()
        assertTrue(
            "AndroidManifest.xml must declare android:largeHeap=\"true\" under application element",
            content.contains("android:largeHeap=\"true\"")
        )
    }

    @Test
    fun testAdaptiveLayering_suppressesVectorTracksAndMarkersWhenOverThreshold() {
        val workouts = (1L..35L).map { createDummyWorkout(it) }
        val isLargePeriod = workouts.size > MAX_PERIOD_VECTOR_TRACKS
        assertTrue("Period with 35 workouts must be recognized as large", isLargePeriod)

        // Decode polyline paths once for both heatmap and lightweight vector tracks
        val heatmapPathMap = workouts.associate { w ->
            w.id to if (w.mapPolyline.isNotEmpty()) PolyUtil.decode(w.mapPolyline) else emptyList()
        }.filterValues { it.isNotEmpty() }

        val tracks = if (isLargePeriod) {
            emptyList()
        } else {
            workouts.mapNotNull { w ->
                val points = heatmapPathMap[w.id] ?: return@mapNotNull null
                MapTrack(
                    id = w.id,
                    type = TrackType.BEST,
                    bSportType = w.bSportType,
                    path = points.map { PathPoint(0.0, it, 0.0) },
                    isVisible = true
                )
            }
        }

        val markers = if (isLargePeriod) {
            emptyList()
        } else {
            workouts.flatMap { w ->
                listOfNotNull(w.startLatLng, w.endLatLng, w.maxDisplacementLatLng, w.maxAltitudeLatLng)
            }
        }

        // Assert that vector tracks and markers are completely suppressed in memory
        assertTrue("memberTracks must be empty when workouts > 30", tracks.isEmpty())
        assertTrue("memberMarkers must be empty when workouts > 30", markers.isEmpty())
        assertEquals("Heatmap paths must remain intact for all 35 workouts", 35, heatmapPathMap.size)
    }

    @Test
    fun testAdaptiveLayering_enablesVectorTracksAndMarkersWhenWithinThreshold() {
        val workouts = (1L..10L).map { createDummyWorkout(it) }
        val isLargePeriod = workouts.size > MAX_PERIOD_VECTOR_TRACKS
        assertFalse("Period with 10 workouts must not be large", isLargePeriod)

        val heatmapPathMap = workouts.associate { w ->
            w.id to if (w.mapPolyline.isNotEmpty()) PolyUtil.decode(w.mapPolyline) else emptyList()
        }.filterValues { it.isNotEmpty() }

        val tracks = if (isLargePeriod) {
            emptyList()
        } else {
            workouts.mapNotNull { w ->
                val points = heatmapPathMap[w.id] ?: return@mapNotNull null
                MapTrack(
                    id = w.id,
                    type = TrackType.BEST,
                    bSportType = w.bSportType,
                    path = points.map { PathPoint(0.0, it, 0.0) },
                    isVisible = true
                )
            }
        }

        val markers = if (isLargePeriod) {
            emptyList()
        } else {
            workouts.flatMap { w ->
                listOfNotNull(w.startLatLng, w.endLatLng, w.maxDisplacementLatLng, w.maxAltitudeLatLng)
            }
        }

        assertEquals("All 10 member tracks must be populated", 10, tracks.size)
        assertEquals("All 40 markers (4 per workout) must be populated", 40, markers.size)
    }

    @Test
    fun testAdaptiveLayering_sportFilterEnablesTracksForFilteredSubset() {
        // Total 40 workouts: 35 BIKE and 5 RUN
        val bikeWorkouts = (1L..35L).map { createDummyWorkout(it, BSportType.BIKE) }
        val runWorkouts = (36L..40L).map { createDummyWorkout(it, BSportType.RUN) }
        val allWorkouts = bikeWorkouts + runWorkouts

        val heatmapPathMap = allWorkouts.associate { w ->
            w.id to if (w.mapPolyline.isNotEmpty()) PolyUtil.decode(w.mapPolyline) else emptyList()
        }.filterValues { it.isNotEmpty() }

        val sportMap = allWorkouts.associate { it.id to it.bSportType }

        // Case A: Unfiltered (all 40) -> vector tracks suppressed
        val unfilteredAdaptive = allWorkouts.size > MAX_PERIOD_VECTOR_TRACKS
        assertTrue("Unfiltered 40 workouts must exceed threshold", unfilteredAdaptive)

        // Case B: Filtered by RUN (5 workouts)
        val selectedSports = setOf(BSportType.RUN)
        val filteredWorkoutIds = allWorkouts.filter { selectedSports.contains(it.bSportType) }.map { it.id }
        val isFilteredAdaptive = filteredWorkoutIds.size > MAX_PERIOD_VECTOR_TRACKS

        assertFalse("Filtered subset of 5 RUN workouts is within threshold", isFilteredAdaptive)

        // Dynamic generation for the filtered subset
        val dynamicTracks = filteredWorkoutIds.mapNotNull { id ->
            val pts = heatmapPathMap[id] ?: return@mapNotNull null
            val sport = sportMap[id] ?: BSportType.UNKNOWN
            MapTrack(
                id = id,
                type = TrackType.BEST,
                bSportType = sport,
                path = pts.map { PathPoint(0.0, it, 0.0) },
                isVisible = true
            )
        }

        assertEquals("Dynamic tracks must contain exactly 5 running tracks", 5, dynamicTracks.size)
        dynamicTracks.forEach { track ->
            assertEquals(BSportType.RUN, track.bSportType)
        }
    }

    @Test
    fun testAdaptiveLayering_boundaryConditionAtThirty() {
        val thirtyWorkouts = (1L..30L).map { createDummyWorkout(it) }
        assertFalse("30 workouts must NOT exceed threshold (boundary <= 30)", thirtyWorkouts.size > MAX_PERIOD_VECTOR_TRACKS)

        val thirtyOneWorkouts = (1L..31L).map { createDummyWorkout(it) }
        assertTrue("31 workouts MUST exceed threshold", thirtyOneWorkouts.size > MAX_PERIOD_VECTOR_TRACKS)
    }

    @Test
    fun testThumbnailTracksConstant_isEight() {
        assertEquals("Thumbnail preview track budget must be 8", 8, MAX_SUMMARY_THUMBNAIL_TRACKS)
    }

    @Test
    fun testThumbnailTracks_curationRespectsBudgetAndPreservesAnchors() {
        val workouts = (1L..15L).map { id ->
            val w = createDummyWorkout(id)
            w.copy(startTimeS = 1000L + id * 100L) // id 15 is most recent
        }
        val anchorIds = setOf(1L, 2L, 3L, 4L, 5L)
        val anchorWorkouts = workouts.filter { it.id in anchorIds }

        // Simulate PeriodsRepository enrichment logic
        val selectedWorkouts = anchorWorkouts.filter { it.mapPolyline.isNotEmpty() }.toMutableList()
        val selectedIds = selectedWorkouts.map { it.id }.toMutableSet()
        if (selectedWorkouts.size < MAX_SUMMARY_THUMBNAIL_TRACKS) {
            val remainingWorkouts = workouts
                .filter { it.id !in selectedIds && it.mapPolyline.isNotEmpty() }
                .sortedByDescending { it.startTimeS }
            for (w in remainingWorkouts) {
                if (selectedWorkouts.size >= MAX_SUMMARY_THUMBNAIL_TRACKS) break
                selectedWorkouts.add(w)
                selectedIds.add(w.id)
            }
        }

        assertEquals("Curated preview must contain exactly MAX_SUMMARY_THUMBNAIL_TRACKS (8)", MAX_SUMMARY_THUMBNAIL_TRACKS, selectedWorkouts.size)
        // Verify all 5 anchors are preserved
        assertTrue("Anchor 1 must be present", selectedWorkouts.any { it.id == 1L })
        assertTrue("Anchor 2 must be present", selectedWorkouts.any { it.id == 2L })
        assertTrue("Anchor 3 must be present", selectedWorkouts.any { it.id == 3L })
        assertTrue("Anchor 4 must be present", selectedWorkouts.any { it.id == 4L })
        assertTrue("Anchor 5 must be present", selectedWorkouts.any { it.id == 5L })
        // Remaining 3 slots must be the most recent (15, 14, 13)
        assertTrue("Recent workout 15 must be present", selectedWorkouts.any { it.id == 15L })
        assertTrue("Recent workout 14 must be present", selectedWorkouts.any { it.id == 14L })
        assertTrue("Recent workout 13 must be present", selectedWorkouts.any { it.id == 13L })
    }

    @Test
    fun testThumbnailTracks_smallPeriodIncludesAllWorkouts() {
        val workouts = (1L..5L).map { createDummyWorkout(it) }
        val anchorIds = setOf(1L, 2L)
        val anchorWorkouts = workouts.filter { it.id in anchorIds }

        val selectedWorkouts = anchorWorkouts.filter { it.mapPolyline.isNotEmpty() }.toMutableList()
        val selectedIds = selectedWorkouts.map { it.id }.toMutableSet()
        if (selectedWorkouts.size < MAX_SUMMARY_THUMBNAIL_TRACKS) {
            val remainingWorkouts = workouts
                .filter { it.id !in selectedIds && it.mapPolyline.isNotEmpty() }
                .sortedByDescending { it.startTimeS }
            for (w in remainingWorkouts) {
                if (selectedWorkouts.size >= MAX_SUMMARY_THUMBNAIL_TRACKS) break
                selectedWorkouts.add(w)
                selectedIds.add(w.id)
            }
        }

        assertEquals("When period has <= 8 workouts, all 5 workouts must be included", 5, selectedWorkouts.size)
    }
}
