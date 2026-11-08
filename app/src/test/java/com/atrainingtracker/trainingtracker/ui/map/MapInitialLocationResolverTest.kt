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

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.atrainingtracker.trainingtracker.database.KnownLocationsDatabaseManager
import com.atrainingtracker.trainingtracker.database.KnownLocationsDatabaseManager.MyLocation
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.google.android.gms.maps.model.LatLng
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests verifying defensive coordinate fallback cascade and boundary validation
 * for initial map camera resolution (REQ-MAP-059.1 / TST-MAP-061.1).
 */
class MapInitialLocationResolverTest {

    private lateinit var mockContext: Context
    private lateinit var mockLocationManager: LocationManager
    private lateinit var mockKnownLocationsDb: KnownLocationsDatabaseManager
    private lateinit var mockWorkoutSummariesDb: WorkoutSummariesDatabaseManager

    @Before
    fun setUp() {
        mockContext = mockk(relaxed = true)
        mockLocationManager = mockk(relaxed = true)
        mockKnownLocationsDb = mockk(relaxed = true)
        mockWorkoutSummariesDb = mockk(relaxed = true)

        mockkStatic(ContextCompat::class)
        every { mockContext.getSystemService(Context.LOCATION_SERVICE) } returns mockLocationManager
        every { ContextCompat.checkSelfPermission(any(), any()) } returns PackageManager.PERMISSION_DENIED
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testResolveInitialLocation_priority1_returnsValidLiveFix() {
        val liveLocation = LatLng(52.5200, 13.4050) // Berlin
        val result = MapInitialLocationResolver.resolveInitialLocation(
            context = mockContext,
            liveLocation = liveLocation,
            knownLocationsDb = mockKnownLocationsDb,
            workoutSummariesDb = mockWorkoutSummariesDb
        )
        assertEquals(liveLocation, result)
    }

    @Test
    fun testResolveInitialLocation_rejectsNullIslandLiveFix_andFallsThrough() {
        val nullIsland = LatLng(0.0, 0.0)
        val home = MyLocation(1L, 48.2082, 16.3738, "Vienna", 200.0, 200, 5) // Vienna
        every { mockKnownLocationsDb.homeLocation } returns home

        val result = MapInitialLocationResolver.resolveInitialLocation(
            context = mockContext,
            liveLocation = nullIsland,
            knownLocationsDb = mockKnownLocationsDb,
            workoutSummariesDb = mockWorkoutSummariesDb
        )
        assertEquals(home.latLng, result)
    }

    @Test
    fun testResolveInitialLocation_priority2_returnsSystemLastKnownLocationWhenPermissionGranted() {
        every {
            ContextCompat.checkSelfPermission(mockContext, Manifest.permission.ACCESS_FINE_LOCATION)
        } returns PackageManager.PERMISSION_GRANTED

        val mockLocation = mockk<Location>()
        every { mockLocation.latitude } returns 47.3769
        every { mockLocation.longitude } returns 8.5417 // Zurich
        every { mockLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) } returns true
        every { mockLocationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER) } returns mockLocation

        val result = MapInitialLocationResolver.resolveInitialLocation(
            context = mockContext,
            liveLocation = null,
            knownLocationsDb = mockKnownLocationsDb,
            workoutSummariesDb = mockWorkoutSummariesDb
        )
        assertEquals(LatLng(47.3769, 8.5417), result)
    }

    @Test
    fun testResolveInitialLocation_priority3_returnsHomeLocationWhenNoSystemLocation() {
        val home = MyLocation(1L, 48.7758, 9.1829, "Stuttgart", 250.0, 200, 10) // Stuttgart
        every { mockKnownLocationsDb.homeLocation } returns home

        val result = MapInitialLocationResolver.resolveInitialLocation(
            context = mockContext,
            liveLocation = null,
            knownLocationsDb = mockKnownLocationsDb,
            workoutSummariesDb = mockWorkoutSummariesDb
        )
        assertEquals(home.latLng, result)
    }

    @Test
    fun testResolveInitialLocation_priority4_returnsMostVisitedKnownLocation() {
        every { mockKnownLocationsDb.homeLocation } returns null
        val loc1 = MyLocation(1L, 50.1109, 8.6821, "Frankfurt", 100.0, 200, 5)
        val loc2 = MyLocation(2L, 53.5511, 9.9937, "Hamburg", 50.0, 200, 42) // Most visited
        val loc3 = MyLocation(3L, 50.9375, 6.9603, "Cologne", 80.0, 200, 12)
        every { mockKnownLocationsDb.allLocations } returns listOf(loc1, loc2, loc3)

        val result = MapInitialLocationResolver.resolveInitialLocation(
            context = mockContext,
            liveLocation = null,
            knownLocationsDb = mockKnownLocationsDb,
            workoutSummariesDb = mockWorkoutSummariesDb
        )
        assertEquals(loc2.latLng, result)
    }

    @Test
    fun testResolveInitialLocation_priority5_returnsRecentWorkoutStart() {
        every { mockKnownLocationsDb.homeLocation } returns null
        every { mockKnownLocationsDb.allLocations } returns emptyList()

        val recentStart = LatLng(45.4642, 9.1900) // Milan
        every { mockWorkoutSummariesDb.allWorkoutStartLocations } returns listOf(
            LatLng(41.9028, 12.4964), // Rome (older)
            recentStart
        )

        val result = MapInitialLocationResolver.resolveInitialLocation(
            context = mockContext,
            liveLocation = null,
            knownLocationsDb = mockKnownLocationsDb,
            workoutSummariesDb = mockWorkoutSummariesDb
        )
        assertEquals(recentStart, result)
    }

    @Test
    fun testResolveInitialLocation_priority6_defaultsToMunichFallback() {
        every { mockKnownLocationsDb.homeLocation } returns null
        every { mockKnownLocationsDb.allLocations } returns emptyList()
        every { mockWorkoutSummariesDb.allWorkoutStartLocations } returns emptyList()

        val result = MapInitialLocationResolver.resolveInitialLocation(
            context = mockContext,
            liveLocation = null,
            knownLocationsDb = mockKnownLocationsDb,
            workoutSummariesDb = mockWorkoutSummariesDb
        )
        assertEquals(MapInitialLocationResolver.DEFAULT_FALLBACK_LOCATION, result)
        assertEquals(48.137154, result.latitude, 0.000001)
        assertEquals(11.576124, result.longitude, 0.000001)
    }

    @Test
    fun testResolveInitialLocation_resilientToDatabaseExceptions() {
        every { mockKnownLocationsDb.homeLocation } throws RuntimeException("DB locked")
        every { mockKnownLocationsDb.allLocations } throws IllegalStateException("Corrupt table")
        every { mockWorkoutSummariesDb.allWorkoutStartLocations } throws RuntimeException("Query failed")

        val result = MapInitialLocationResolver.resolveInitialLocation(
            context = mockContext,
            liveLocation = null,
            knownLocationsDb = mockKnownLocationsDb,
            workoutSummariesDb = mockWorkoutSummariesDb
        )
        assertEquals(MapInitialLocationResolver.DEFAULT_FALLBACK_LOCATION, result)
    }

    @Test
    fun testIsValidCoordinate_boundaryValidation() {
        assertFalse("null must be invalid", MapInitialLocationResolver.isValidCoordinate(null))
        assertFalse("Null Island (0,0) must be invalid", MapInitialLocationResolver.isValidCoordinate(LatLng(0.0, 0.0)))
        assertTrue("Munich must be valid", MapInitialLocationResolver.isValidCoordinate(LatLng(48.137154, 11.576124)))
        assertTrue("Min boundary (-90, -180) must be valid", MapInitialLocationResolver.isValidCoordinate(LatLng(-90.0, -180.0)))
        assertTrue("Max boundary (90, 180) must be valid", MapInitialLocationResolver.isValidCoordinate(LatLng(90.0, 180.0)))

        assertFalse("Raw Latitude > 90 must be invalid", MapInitialLocationResolver.isValidCoordinate(90.001, 10.0))
        assertFalse("Raw Latitude < -90 must be invalid", MapInitialLocationResolver.isValidCoordinate(-90.001, 10.0))
        assertFalse("Raw Longitude > 180 must be invalid", MapInitialLocationResolver.isValidCoordinate(10.0, 180.001))
        assertFalse("Raw Longitude < -180 must be invalid", MapInitialLocationResolver.isValidCoordinate(10.0, -180.001))
        assertFalse("NaN latitude must be invalid", MapInitialLocationResolver.isValidCoordinate(Double.NaN, 10.0))
        assertFalse("NaN longitude must be invalid", MapInitialLocationResolver.isValidCoordinate(10.0, Double.NaN))
    }
}
