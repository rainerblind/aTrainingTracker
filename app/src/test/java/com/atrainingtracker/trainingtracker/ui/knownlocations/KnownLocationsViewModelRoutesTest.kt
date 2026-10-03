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
 */

package com.atrainingtracker.trainingtracker.ui.knownlocations

import android.app.Application
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.database.WorkoutCluster
import com.atrainingtracker.trainingtracker.database.WorkoutClusterRepository
import com.atrainingtracker.trainingtracker.elevation.ElevationSource
import com.atrainingtracker.trainingtracker.repositories.KnownLocationItem
import com.atrainingtracker.trainingtracker.repositories.KnownLocationsRepository
import com.google.android.gms.maps.model.LatLng
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit test suite for [KnownLocationsViewModel] linked route clusters association
 * (REQ-UI-186.3, TST-UI-139.3, ATT-1402).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class KnownLocationsViewModelRoutesTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var mockApplication: Application
    private lateinit var mockRepository: KnownLocationsRepository
    private lateinit var mockClusterRepository: WorkoutClusterRepository

    private val fakeLocationsFlow = MutableStateFlow<List<KnownLocationItem>>(emptyList())
    private val fakeClustersFlow = MutableStateFlow<List<WorkoutCluster>>(emptyList())

    private lateinit var viewModel: KnownLocationsViewModel

    private fun createCluster(
        id: Long,
        name: String,
        startLat: Double,
        startLng: Double
    ): WorkoutCluster {
        return WorkoutCluster(
            id = id,
            name = name,
            probableSportId = 1L,
            startLat = startLat,
            startLng = startLng,
            endLat = startLat,
            endLng = startLng,
            maxDispLat = startLat + 0.01,
            maxDispLng = startLng + 0.01,
            refDistance = 10000.0,
            hitCount = 5,
            bSportType = BSportType.RUN
        )
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockApplication = mockk(relaxed = true)
        mockRepository = mockk(relaxed = true)
        mockClusterRepository = mockk(relaxed = true)

        every { mockRepository.locationsFlow } returns fakeLocationsFlow
        every { mockClusterRepository.allClusters } returns fakeClustersFlow

        viewModel = KnownLocationsViewModel(
            application = mockApplication,
            repository = mockRepository,
            initialIsMetric = true,
            clusterRepository = mockClusterRepository,
            defaultDispatcher = testDispatcher
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        clearAllMocks()
    }

    @Test
    fun `clustersByLocationId maps clusters starting within location geofence`() = runTest {
        val home = KnownLocationItem(
            id = 1L,
            name = "Zuhause",
            altitude = 520.0,
            radius = 200,
            latLng = LatLng(48.137, 11.576),
            hitCount = 20,
            isLocked = true,
            source = ElevationSource.MANUAL_USER
        )
        val office = KnownLocationItem(
            id = 2L,
            name = "Büro",
            altitude = 510.0,
            radius = 200,
            latLng = LatLng(48.150, 11.600),
            hitCount = 10,
            isLocked = true,
            source = ElevationSource.MANUAL_USER
        )

        // Cluster 101 starts at Home (exact coordinate)
        val cluster101 = createCluster(101L, "Feierabendrunde", 48.137, 11.576)
        // Cluster 102 starts near Home (~100m away, within 200m)
        val cluster102 = createCluster(102L, "Morgenlauf", 48.1378, 11.576)
        // Cluster 103 starts at Office
        val cluster103 = createCluster(103L, "Mittagspause", 48.150, 11.600)
        // Cluster 104 starts far away (unmapped location)
        val cluster104 = createCluster(104L, "Alpen Trail", 47.416, 11.009)

        fakeLocationsFlow.value = listOf(home, office)
        fakeClustersFlow.value = listOf(cluster101, cluster102, cluster103, cluster104)

        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        val homeClusters = state.clustersByLocationId[1L] ?: emptyList()
        val officeClusters = state.clustersByLocationId[2L] ?: emptyList()

        assertEquals(2, homeClusters.size)
        assertTrue(homeClusters.any { it.id == 101L })
        assertTrue(homeClusters.any { it.id == 102L })

        assertEquals(1, officeClusters.size)
        assertTrue(officeClusters.any { it.id == 103L })

        // Cluster 104 is not associated with either location
        assertTrue(homeClusters.none { it.id == 104L })
        assertTrue(officeClusters.none { it.id == 104L })
    }

    @Test
    fun `clustersByLocationId returns empty mapping when no clusters match location geofence`() = runTest {
        val isolatedLocation = KnownLocationItem(
            id = 99L,
            name = "Ferienhaus",
            altitude = 800.0,
            radius = 200,
            latLng = LatLng(47.500, 10.500),
            hitCount = 2,
            isLocked = true,
            source = ElevationSource.MANUAL_USER
        )

        val cityCluster = createCluster(201L, "City Loop", 48.137, 11.576)

        fakeLocationsFlow.value = listOf(isolatedLocation)
        fakeClustersFlow.value = listOf(cityCluster)

        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        val clusters = state.clustersByLocationId[99L] ?: emptyList()
        assertTrue(clusters.isEmpty())
    }
}
