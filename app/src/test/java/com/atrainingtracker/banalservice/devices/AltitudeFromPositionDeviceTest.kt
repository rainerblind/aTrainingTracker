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
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0/>.
 */

package com.atrainingtracker.banalservice.devices

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager
import com.atrainingtracker.banalservice.sensor.MySensorManager
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.database.ElevationCacheDatabaseManager
import com.atrainingtracker.trainingtracker.database.KnownLocationsDatabaseManager
import com.atrainingtracker.trainingtracker.database.RoutesDatabaseManager
import com.atrainingtracker.trainingtracker.elevation.ElevationResult
import com.atrainingtracker.trainingtracker.elevation.ElevationService
import com.atrainingtracker.trainingtracker.segments.SegmentsDatabaseManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Unit verification suite for [AltitudeFromPositionDevice] (TST-SEN-025.1, TST-SEN-025.3, TST-SEN-025.4).
 *
 * Verifies:
 * - Device initialization, name, and altitude sensor registration.
 * - Cache hit elevation resolution from ElevationCache.db.
 * - Cache miss asynchronous DEM query via ElevationService and persistence.
 * - Duplicate coordinate suppression.
 * - Clean shutdown.
 * - Zero interaction with KnownLocations.db (Lieblingsorte).
 */
class AltitudeFromPositionDeviceTest {

    private lateinit var mockContext: Context
    private lateinit var mockSensorManager: MySensorManager
    private lateinit var mockDevicesDb: DevicesDatabaseManager
    private lateinit var mockCacheDb: ElevationCacheDatabaseManager
    private lateinit var mockElevationService: ElevationService
    private lateinit var executor: ExecutorService
    private lateinit var device: AltitudeFromPositionDevice

    @Before
    fun setUp() {
        mockContext = mockk(relaxed = true)
        every { mockContext.applicationContext } returns mockContext
        every { mockContext.getString(R.string.devices_altitude_from_position) } returns "Topographic Elevation (DEM)"
        every { mockContext.packageName } returns "com.atrainingtracker"

        mockkConstructor(Intent::class)
        every { anyConstructed<Intent>().setPackage(any()) } answers { self as Intent }
        every { anyConstructed<Intent>().putExtra(any<String>(), any<Double>()) } answers { self as Intent }
        every { anyConstructed<Intent>().putExtra(any<String>(), any<Long>()) } answers { self as Intent }
        every { anyConstructed<Intent>().putExtra(any<String>(), any<String>()) } answers { self as Intent }

        mockkConstructor(IntentFilter::class)
        every { anyConstructed<IntentFilter>().addAction(any()) } returns Unit

        mockkStatic(ContextCompat::class)
        every { ContextCompat.registerReceiver(any(), any(), any(), any()) } returns null

        mockkStatic(DevicesDatabaseManager::class)
        mockDevicesDb = mockk(relaxed = true)
        every { DevicesDatabaseManager.getInstance(any()) } returns mockDevicesDb
        every { mockDevicesDb.getAltitudeFromPositionDeviceId() } returns 101L

        mockkStatic(KnownLocationsDatabaseManager::class)

        mockSensorManager = mockk(relaxed = true)
        mockCacheDb = mockk(relaxed = true)
        mockElevationService = mockk(relaxed = true)
        executor = Executors.newSingleThreadExecutor()

        device = AltitudeFromPositionDevice(
            mockContext,
            mockSensorManager,
            mockCacheDb,
            mockElevationService,
            executor
        )
    }

    @After
    fun tearDown() {
        if (::device.isInitialized) {
            device.shutDown()
        }
        RoutesDatabaseManager.resetForTesting(null)
        SegmentsDatabaseManager.resetForTesting(null)
        unmockkAll()
    }

    /**
     * TST-SEN-025.1: Device metadata, naming, and sensor registration.
     */
    @Test
    fun initialization_registersAltitudeSensorAndMetadata() {
        assertEquals(DeviceType.ALTITUDE_FROM_POSITION, device.deviceType)
        assertEquals("Topographic Elevation (DEM)", device.name)

        val altSensor = device.getAltitudeSensor()
        assertNotNull(altSensor)
        assertEquals(SensorType.ALTITUDE, altSensor.sensorType)
    }

    /**
     * TST-SEN-025.2: Cache hit resolves elevation immediately without querying ElevationService.
     */
    @Test
    fun locationCacheHit_resolvesElevationFromCacheWithoutNetwork() {
        val lat = 47.99123
        val lon = 11.45678
        val expectedAlt = 605.5
        every { mockCacheDb.getElevation(lat, lon, 2) } returns expectedAlt

        device.onNewLocation(lat, lon)

        val publishedAlt = device.getAltitudeSensor().value as? Double
        assertNotNull(publishedAlt)
        assertEquals(expectedAlt, publishedAlt!!, 1e-4)
        assertEquals(expectedAlt, device.lastAltitude, 1e-4)

        // ElevationService must not have been queried
        verify(exactly = 0) { mockElevationService.fetchElevation(any(), any()) }
    }

    /**
     * TST-SEN-025.3: Cache miss triggers asynchronous DEM query and caches the result.
     */
    @Test
    fun locationCacheMiss_queriesElevationServiceAndCachesResult() {
        val lat = 48.13715
        val lon = 11.57612
        val demAlt = 520.8

        every { mockCacheDb.getElevation(lat, lon, 2) } returns null
        every { mockElevationService.fetchElevation(lat, lon) } returns ElevationResult.Success(
            elevationMeters = demAlt,
            latitude = 48.13715,
            longitude = 11.57612
        )

        device.onNewLocation(lat, lon)
        Thread.sleep(100)

        val publishedAlt = device.getAltitudeSensor().value as? Double
        assertNotNull(publishedAlt)
        assertEquals(demAlt, publishedAlt!!, 1e-4)

        verify(atLeast = 1) { mockCacheDb.putElevation(lat, lon, demAlt, "DEM_CACHE", any()) }
        verify(atLeast = 1) { mockElevationService.fetchElevation(lat, lon) }
    }

    /**
     * TST-SEN-025.3: Duplicate coordinates within E5 precision do not trigger redundant queries.
     */
    @Test
    fun duplicateCoordinates_suppressesRedundantQueries() {
        val lat = 48.13715
        val lon = 11.57612
        val demAlt = 520.8

        every { mockCacheDb.getElevation(lat, lon, 2) } returns null
        every { mockElevationService.fetchElevation(lat, lon) } returns ElevationResult.Success(
            elevationMeters = demAlt,
            latitude = 48.13715,
            longitude = 11.57612
        )

        device.onNewLocation(lat, lon)
        Thread.sleep(100)

        // Exact same coordinate sent again
        device.onNewLocation(lat, lon)
        Thread.sleep(50)

        // Must only be fetched once
        verify(exactly = 1) { mockElevationService.fetchElevation(lat, lon) }
    }

    /**
     * TST-SEN-025.4: AltitudeFromPositionDevice strictly operates on ElevationCache.db
     * and never writes or queries KnownLocations.db.
     */
    @Test
    fun knownLocationsDecoupling_zeroInteractionWithKnownLocationsDb() {
        val lat = 48.13715
        val lon = 11.57612
        every { mockCacheDb.getElevation(lat, lon, 2) } returns 520.0

        device.onNewLocation(lat, lon)

        verify(exactly = 0) { KnownLocationsDatabaseManager.getInstance(any()) }
    }

    /**
     * TST-SEN-025.3: Background seeding invokes cache indexing for routes and segments.
     */
    @Test
    fun triggerBackgroundSeedingAsync_seedsRoutesAndSegments() {
        val mockRoutesDb = mockk<RoutesDatabaseManager>(relaxed = true)
        val mockSegmentsDb = mockk<SegmentsDatabaseManager>(relaxed = true)

        RoutesDatabaseManager.resetForTesting(mockRoutesDb)
        SegmentsDatabaseManager.resetForTesting(mockSegmentsDb)

        every { mockCacheDb.seedFromRoutes(mockRoutesDb) } returns 5
        every { mockCacheDb.seedFromSegments(mockSegmentsDb) } returns 10

        device.triggerBackgroundSeedingAsync()
        Thread.sleep(100)

        verify(atLeast = 1) { mockCacheDb.seedFromRoutes(mockRoutesDb) }
        verify(atLeast = 1) { mockCacheDb.seedFromSegments(mockSegmentsDb) }
    }
}
