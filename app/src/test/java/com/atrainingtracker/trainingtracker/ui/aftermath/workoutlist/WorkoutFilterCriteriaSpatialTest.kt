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

package com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist

import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData
import com.google.android.gms.maps.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/**
 * Unit test suite verifying spatial geofence matching, active filter count derivation,
 * and JSON serialization/deserialization for [WorkoutFilterCriteria] (REQ-UI-185, TST-UI-138.1).
 */
class WorkoutFilterCriteriaSpatialTest {

    private fun createWorkoutWithStart(startLatLng: LatLng?): WorkoutData {
        return WorkoutData(
            id = 100L,
            finished = true,
            fileBaseName = "workout_100",
            workoutName = "Morning Run",
            sportId = 1L,
            sportName = "Running",
            bSportType = BSportType.RUN,
            startTimeS = 1718000000L,
            formattedDate = "2024-06-10",
            formattedTime = "07:30",
            localDateTime = LocalDateTime.of(2024, 6, 10, 7, 30),
            equipmentName = null,
            equipmentId = 0L,
            commute = false,
            trainer = false,
            mapPolyline = "poly",
            encodedAltitudes = "",
            encodedDistances = "",
            uploadToStrava = 0,
            totalDistance = 10000.0,
            maxDisplacement = 2000.0,
            activeTimeSec = 3000L,
            totalTimeSec = 3000L,
            avgSpeedMps = 3.33,
            ascentMeters = 50L,
            descentMeters = 50L,
            minAltitude = 500.0,
            maxAltitude = 550.0,
            description = null,
            goal = null,
            method = null,
            stravaSportName = null,
            startLatLng = startLatLng
        )
    }

    @Test
    fun testExactLocationMatch() {
        // Marienplatz, Munich
        val locationLat = 48.13715
        val locationLng = 11.57612

        val criteria = WorkoutFilterCriteria(
            startLocationName = "Marienplatz",
            startLocationLat = locationLat,
            startLocationLng = locationLng,
            startLocationRadiusM = 200.0
        )

        val workout = createWorkoutWithStart(LatLng(locationLat, locationLng))
        assertTrue("Workout starting exactly at location must match", criteria.matches(workout))
    }

    @Test
    fun testWithinRadiusMatch() {
        val locationLat = 48.13715
        val locationLng = 11.57612

        val criteria = WorkoutFilterCriteria(
            startLocationName = "Marienplatz",
            startLocationLat = locationLat,
            startLocationLng = locationLng,
            startLocationRadiusM = 200.0
        )

        // ~111m North (0.001 deg latitude ~ 111m)
        val workoutNearby = createWorkoutWithStart(LatLng(48.13815, 11.57612))
        assertTrue("Workout starting ~111m away within 200m radius must match", criteria.matches(workoutNearby))
    }

    @Test
    fun testOutsideRadiusRejection() {
        val locationLat = 48.13715
        val locationLng = 11.57612

        val criteria = WorkoutFilterCriteria(
            startLocationName = "Marienplatz",
            startLocationLat = locationLat,
            startLocationLng = locationLng,
            startLocationRadiusM = 200.0
        )

        // ~890m North (0.008 deg latitude ~ 890m)
        val workoutFarAway = createWorkoutWithStart(LatLng(48.14515, 11.57612))
        assertFalse("Workout starting ~890m away outside 200m radius must not match", criteria.matches(workoutFarAway))
    }

    @Test
    fun testNullStartCoordinateRejection() {
        val criteria = WorkoutFilterCriteria(
            startLocationName = "Marienplatz",
            startLocationLat = 48.13715,
            startLocationLng = 11.57612,
            startLocationRadiusM = 200.0
        )

        val workoutNoLocation = createWorkoutWithStart(null)
        assertFalse("Workout with null startLatLng must not match spatial filter", criteria.matches(workoutNoLocation))
    }

    @Test
    fun testCustomRadiusOverride() {
        val locationLat = 48.13715
        val locationLng = 11.57612

        // Large 1000m radius
        val criteria = WorkoutFilterCriteria(
            startLocationName = "Marienplatz",
            startLocationLat = locationLat,
            startLocationLng = locationLng,
            startLocationRadiusM = 1000.0
        )

        // ~890m North
        val workoutFarAway = createWorkoutWithStart(LatLng(48.14515, 11.57612))
        assertTrue("Workout ~890m away must match when custom radius is 1000m", criteria.matches(workoutFarAway))
    }

    @Test
    fun testActiveFilterCountIncludesSpatialDimension() {
        val baseCriteria = WorkoutFilterCriteria()
        assertEquals(0, baseCriteria.activeFilterCount)

        val spatialCriteria = WorkoutFilterCriteria(
            startLocationName = "Zuhause",
            startLocationLat = 48.137,
            startLocationLng = 11.576
        )
        assertEquals(1, spatialCriteria.activeFilterCount)
        assertTrue(spatialCriteria.isNotEmpty)
        assertFalse(spatialCriteria.isEmpty)

        val multiCriteria = spatialCriteria.copy(year = 2024, isCommute = true)
        assertEquals(3, multiCriteria.activeFilterCount)
    }

    @Test
    fun testJsonSerializationParity() {
        val original = WorkoutFilterCriteria(
            query = "Tempo",
            year = 2024,
            startLocationName = "Zuhause",
            startLocationLat = 48.13715,
            startLocationLng = 11.57612,
            startLocationRadiusM = 250.0
        )

        val json = original.toJson()
        val restored = WorkoutFilterCriteria.fromJson(json)

        assertEquals("Tempo", restored.query)
        assertEquals(Integer.valueOf(2024), restored.year)
        assertEquals("Zuhause", restored.startLocationName)
        assertEquals(48.13715, restored.startLocationLat!!, 0.00001)
        assertEquals(11.57612, restored.startLocationLng!!, 0.00001)
        assertEquals(250.0, restored.startLocationRadiusM!!, 0.01)
        assertEquals(original.activeFilterCount, restored.activeFilterCount)
    }

    @Test
    fun testJsonDeserializationLegacyWithoutSpatial() {
        val legacyJson = """{"query":"Trail","year":2023}"""
        val restored = WorkoutFilterCriteria.fromJson(legacyJson)

        assertEquals("Trail", restored.query)
        assertEquals(Integer.valueOf(2023), restored.year)
        assertNull(restored.startLocationName)
        assertNull(restored.startLocationLat)
        assertNull(restored.startLocationLng)
        assertNull(restored.startLocationRadiusM)
        assertEquals(2, restored.activeFilterCount)
    }
}
