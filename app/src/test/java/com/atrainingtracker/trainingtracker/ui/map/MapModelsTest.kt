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

package com.atrainingtracker.trainingtracker.ui.map

import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData
import com.atrainingtracker.trainingtracker.ui.utils.NumericalEncodingUtils
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.PolyUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/**
 * Automated unit test suite verifying [WorkoutData.toMapTrack] altitude and distance
 * stream decoding, null-safety, and edge-case fallbacks (REQ-UI-138, TST-UI-091, ATT-818).
 */
class MapModelsTest {

    private fun createWorkout(
        polyline: String = "",
        encodedAltitudes: String = "",
        encodedDistances: String = ""
    ): WorkoutData {
        return WorkoutData(
            id = 42L,
            finished = true,
            fileBaseName = "test_track_42",
            workoutName = "Morning Mountain Ride",
            sportId = 1L,
            sportName = "Cycling",
            bSportType = BSportType.BIKE,
            startTimeS = 1700000000L,
            formattedDate = "2026-09-10",
            formattedTime = "08:00",
            localDateTime = LocalDateTime.now(),
            equipmentName = "Canyon Endurace",
            equipmentId = 5L,
            commute = false,
            trainer = false,
            mapPolyline = polyline,
            encodedAltitudes = encodedAltitudes,
            encodedDistances = encodedDistances,
            uploadToStrava = 0,
            totalDistance = 25000.0,
            maxDisplacement = 8000.0,
            activeTimeSec = 3600L,
            totalTimeSec = 3800L,
            avgSpeedMps = 6.94,
            ascentMeters = 450L,
            descentMeters = 430L,
            minAltitude = 410.0,
            maxAltitude = 680.0,
            startLatLng = LatLng(47.5, 11.0),
            endLatLng = LatLng(47.52, 11.05),
            maxDisplacementLatLng = LatLng(47.58, 11.12),
            description = null,
            goal = null,
            method = null,
            stravaSportName = null,
            clusterId = 10L,
            clusterName = "Lake Loop"
        )
    }

    @Test
    fun toMapTrack_withValidPolylineAndStreams_decodesAltitudesAndDistancesAccurately() {
        val latLngs = listOf(
            LatLng(47.50000, 11.00000),
            LatLng(47.50500, 11.01000),
            LatLng(47.51000, 11.02000)
        )
        val polyline = PolyUtil.encode(latLngs)
        val altitudes = listOf(450.0, 520.5, 610.25)
        val distances = listOf(0.0, 750.0, 1850.5)

        val encodedAltitudes = NumericalEncodingUtils.encodeDoubles(altitudes)
        val encodedDistances = NumericalEncodingUtils.encodeDoubles(distances)

        val workout = createWorkout(
            polyline = polyline,
            encodedAltitudes = encodedAltitudes,
            encodedDistances = encodedDistances
        )

        val track = workout.toMapTrack()

        assertEquals(42L, track.id)
        assertEquals(TrackType.BEST, track.type)
        assertEquals(BSportType.BIKE, track.bSportType)
        assertEquals(3, track.path.size)

        // Point 1
        assertEquals(450.0, track.path[0].altitude, 0.01)
        assertEquals(0.0, track.path[0].distance, 0.01)
        assertEquals(47.50000, track.path[0].latLng.latitude, 0.00001)
        assertEquals(11.00000, track.path[0].latLng.longitude, 0.00001)

        // Point 2
        assertEquals(520.5, track.path[1].altitude, 0.01)
        assertEquals(750.0, track.path[1].distance, 0.01)

        // Point 3
        assertEquals(610.25, track.path[2].altitude, 0.01)
        assertEquals(1850.5, track.path[2].distance, 0.01)
    }

    @Test
    fun toMapTrack_withEmptyStreams_fallsBackToZeroAltitudesAndDistancesWithoutException() {
        val latLngs = listOf(
            LatLng(47.50000, 11.00000),
            LatLng(47.50500, 11.01000)
        )
        val polyline = PolyUtil.encode(latLngs)

        val workout = createWorkout(
            polyline = polyline,
            encodedAltitudes = "",
            encodedDistances = ""
        )

        val track = workout.toMapTrack()

        assertEquals(2, track.path.size)
        assertEquals(0.0, track.path[0].altitude, 0.001)
        assertEquals(0.0, track.path[0].distance, 0.001)
        assertEquals(0.0, track.path[1].altitude, 0.001)
        assertEquals(0.0, track.path[1].distance, 0.001)
    }

    @Test
    fun toMapTrack_withEmptyPolyline_returnsEmptyPath() {
        val workout = createWorkout(
            polyline = "",
            encodedAltitudes = "",
            encodedDistances = ""
        )

        val track = workout.toMapTrack()

        assertTrue(track.path.isEmpty())
        assertEquals(42L, track.id)
    }

    @Test
    fun toMapTrack_withMismatchedStreamLengths_gracefullyPadsMissingWithZero() {
        val latLngs = listOf(
            LatLng(47.50000, 11.00000),
            LatLng(47.50500, 11.01000),
            LatLng(47.51000, 11.02000)
        )
        val polyline = PolyUtil.encode(latLngs)
        // Only 1 altitude and 2 distances
        val encodedAltitudes = NumericalEncodingUtils.encodeDoubles(listOf(500.0))
        val encodedDistances = NumericalEncodingUtils.encodeDoubles(listOf(0.0, 300.0))

        val workout = createWorkout(
            polyline = polyline,
            encodedAltitudes = encodedAltitudes,
            encodedDistances = encodedDistances
        )

        val track = workout.toMapTrack()

        assertEquals(3, track.path.size)
        assertEquals(500.0, track.path[0].altitude, 0.01)
        assertEquals(0.0, track.path[0].distance, 0.01)

        assertEquals(0.0, track.path[1].altitude, 0.01) // Padded with 0.0
        assertEquals(300.0, track.path[1].distance, 0.01)

        assertEquals(0.0, track.path[2].altitude, 0.01) // Padded with 0.0
        assertEquals(0.0, track.path[2].distance, 0.01) // Padded with 0.0
    }

    @Test
    fun toMapTrack_withCorruptedPolylineOrStream_returnsEmptyOrZeroFallbackWithoutCrashing() {
        val workout = createWorkout(
            polyline = "not_a_valid_polyline_at_all!@#$",
            encodedAltitudes = "corrupt_altitude_string",
            encodedDistances = "corrupt_distance_string"
        )

        val track = workout.toMapTrack()

        assertNotNull(track)
        assertEquals(42L, track.id)
        assertNotNull(track.path)
    }
}
