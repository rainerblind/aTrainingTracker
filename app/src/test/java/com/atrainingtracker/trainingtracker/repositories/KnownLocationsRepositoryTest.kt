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

package com.atrainingtracker.trainingtracker.repositories

import android.content.Context
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.database.KnownLocationsDatabaseManager
import com.atrainingtracker.trainingtracker.elevation.ElevationResult
import com.atrainingtracker.trainingtracker.elevation.ElevationService
import com.atrainingtracker.trainingtracker.elevation.ElevationSource
import com.google.android.gms.maps.model.LatLng
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.Executors

/**
 * Unit test suite for [KnownLocationsRepository].
 *
 * Traceability:
 * - TST-UI-117.2: Concurrency serialization on KnownLocationsDB-Thread with 0 deadlocks.
 * - TST-UI-117.3: updateLocation atomically persists name, altitude, source=MANUAL_USER, is_locked=1.
 */
class KnownLocationsRepositoryTest {

    private lateinit var mockContext: Context
    private lateinit var mockDbManager: KnownLocationsDatabaseManager
    private lateinit var mockElevationService: ElevationService
    private val testDispatcher = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "Test-KnownLocationsDB-Thread").apply { isDaemon = true }
    }.asCoroutineDispatcher()

    private lateinit var repository: KnownLocationsRepository

    @Before
    fun setUp() {
        mockContext = mockk(relaxed = true)
        mockDbManager = mockk(relaxed = true)
        mockElevationService = mockk(relaxed = true)

        every { mockContext.getString(R.string.known_location_unnamed_format, any(), any()) } answers {
            "Startort (${args[1]}, ${args[2]})"
        }

        repository = KnownLocationsRepository(
            context = mockContext,
            databaseManager = mockDbManager,
            elevationService = mockElevationService,
            dbDispatcher = testDispatcher
        )
    }

    @After
    fun tearDown() {
        clearAllMocks()
        KnownLocationsRepository.resetForTesting(null)
    }

    @Test
    fun testLoadLocations_populatesFlow() = runTest {
        val sampleLoc = KnownLocationsDatabaseManager.MyLocation(
            1L, 48.137, 11.576, "Marienplatz", 520.0, 200, 10, true, ElevationSource.MANUAL_USER
        )
        every { mockDbManager.allLocations } returns listOf(sampleLoc)

        val items = repository.loadLocations()

        assertEquals(1, items.size)
        val item = items[0]
        assertEquals(1L, item.id)
        assertEquals("Marienplatz", item.name)
        assertEquals(520.0, item.altitude, 0.001)
        assertTrue(item.isLocked)
        assertEquals(ElevationSource.MANUAL_USER, item.source)
        assertEquals(1, repository.locationsFlow.value.size)
    }

    @Test
    fun testUpdateLocation_manualSource_automaticallyLocksRecord() = runTest {
        val updatedLoc = KnownLocationsDatabaseManager.MyLocation(
            2L, 48.137, 11.576, "Home Base", 535.5, 200, 5, true, ElevationSource.MANUAL_USER
        )
        every { mockDbManager.allLocations } returns listOf(updatedLoc)

        repository.updateLocation(
            id = 2L,
            name = "Home Base",
            altitude = 535.5,
            source = ElevationSource.MANUAL_USER
        )

        verify(exactly = 1) {
            mockDbManager.updateLocation(2L, "Home Base", 535.5, ElevationSource.MANUAL_USER, true)
        }
        assertEquals(1, repository.locationsFlow.value.size)
        assertTrue(repository.locationsFlow.value[0].isLocked)
    }

    @Test
    fun testUpdateLocation_demSource_unlocksRecord() = runTest {
        val updatedLoc = KnownLocationsDatabaseManager.MyLocation(
            3L, 48.137, 11.576, "Park Entrance", 518.0, 200, 2, false, ElevationSource.INTERNET_DEM
        )
        every { mockDbManager.allLocations } returns listOf(updatedLoc)

        repository.updateLocation(
            id = 3L,
            name = "Park Entrance",
            altitude = 518.0,
            source = ElevationSource.INTERNET_DEM
        )

        verify(exactly = 1) {
            mockDbManager.updateLocation(3L, "Park Entrance", 518.0, ElevationSource.INTERNET_DEM, false)
        }
        assertEquals(1, repository.locationsFlow.value.size)
        assertFalse(repository.locationsFlow.value[0].isLocked)
    }

    @Test
    fun testDeleteLocation_invokesDbAndRefreshesFlow() = runTest {
        every { mockDbManager.allLocations } returns emptyList()

        repository.deleteLocation(5L)

        verify(exactly = 1) {
            mockDbManager.deleteId(5L)
        }
        assertTrue(repository.locationsFlow.value.isEmpty())
    }

    @Test
    fun testRefreshDem_success_persistsDemElevationAndSource() = runTest {
        val latLng = LatLng(48.13715, 11.57612)
        val existingLoc = KnownLocationsDatabaseManager.MyLocation(
            10L, latLng.latitude, latLng.longitude, "Odeonsplatz", 500.0, 200, 3, false, ElevationSource.LEGACY_RAW
        )
        every { mockDbManager.getMyLocation(10L) } returns existingLoc
        every { mockElevationService.fetchElevation(latLng.latitude, latLng.longitude) } returns ElevationResult.Success(
            elevationMeters = 523.4,
            latitude = 48.13715,
            longitude = 11.57612
        )

        val updatedLoc = KnownLocationsDatabaseManager.MyLocation(
            10L, latLng.latitude, latLng.longitude, "Odeonsplatz", 523.4, 200, 3, false, ElevationSource.INTERNET_DEM
        )
        every { mockDbManager.allLocations } returns listOf(updatedLoc)

        val result = repository.refreshDem(10L, latLng)

        assertTrue(result is ElevationResult.Success)
        assertEquals(523.4, (result as ElevationResult.Success).elevationMeters, 0.001)
        verify(exactly = 1) {
            mockDbManager.updateLocation(10L, "Odeonsplatz", 523.4, ElevationSource.INTERNET_DEM, false)
        }
    }

    /**
     * TST-UI-117.2: Concurrency serialization on KnownLocationsDB-Thread with 0 deadlocks.
     */
    @Test
    fun testConcurrency_fiftyConcurrentUpdates_executesCleanlyWithoutDeadlock() = runBlocking {
        every { mockDbManager.allLocations } returns emptyList()

        val jobs = (1..50).map { i ->
            async(Dispatchers.Default) {
                repository.updateLocation(
                    id = i.toLong(),
                    name = "Location $i",
                    altitude = 500.0 + i,
                    source = ElevationSource.MANUAL_USER
                )
            }
        }
        jobs.awaitAll()

        verify(exactly = 50) {
            mockDbManager.updateLocation(any(), any(), any(), any(), true)
        }
    }
}
