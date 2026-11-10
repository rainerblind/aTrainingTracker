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

import android.app.Application
import android.content.SharedPreferences
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.climbs.Climb
import com.atrainingtracker.trainingtracker.climbs.ClimbCategory
import com.atrainingtracker.trainingtracker.database.ClimbsDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutCluster
import com.atrainingtracker.trainingtracker.database.WorkoutClusterRepository
import com.atrainingtracker.trainingtracker.elevation.ElevationSource
import com.atrainingtracker.trainingtracker.repositories.BANALServiceRepository
import com.atrainingtracker.trainingtracker.repositories.KnownLocationItem
import com.atrainingtracker.trainingtracker.repositories.KnownLocationsRepository
import com.atrainingtracker.trainingtracker.repositories.RoutesRepository
import com.atrainingtracker.trainingtracker.segments.SegmentsRepository
import com.google.android.gms.maps.model.LatLng
import io.mockk.clearAllMocks
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import com.atrainingtracker.trainingtracker.database.RouteSource
import com.atrainingtracker.trainingtracker.database.RouteSummary
import com.atrainingtracker.trainingtracker.database.RouteWithPath
import com.atrainingtracker.trainingtracker.database.WorkoutClusterStats
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit test suite for [MapFragmentWithTrackViewModel].
 *
 * Traceability: REQ-UI-180, TST-UI-132.1, TST-UI-132.2.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MapFragmentWithTrackViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var mockApplication: Application
    private lateinit var mockKnownLocationsRepository: KnownLocationsRepository
    private lateinit var mockBanalRepository: BANALServiceRepository
    private lateinit var mockSegmentsRepository: SegmentsRepository
    private lateinit var mockRoutesRepository: RoutesRepository
    private lateinit var mockWorkoutClusterRepository: WorkoutClusterRepository

    private val fakeLocationsFlow = MutableStateFlow<List<KnownLocationItem>>(emptyList())
    private val fakeSportTypeFlow = MutableStateFlow(BSportType.UNKNOWN)
    private val fakeCurrentTrackFlow = MutableStateFlow<List<LatLng>>(emptyList())
    private val fakeRoutesFlow = MutableStateFlow<List<com.atrainingtracker.trainingtracker.database.RouteWithPath>>(emptyList())
    private val fakeActiveNavigatedRouteIdFlow = MutableStateFlow<Long?>(null)
    private val fakeClustersFlow = MutableStateFlow<List<WorkoutCluster>>(emptyList())
    private val fakeClusterStatsFlow = MutableStateFlow<Map<Long, WorkoutClusterStats>>(emptyMap())

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockApplication = mockk(relaxed = true)
        mockKnownLocationsRepository = mockk(relaxed = true)
        mockBanalRepository = mockk(relaxed = true)
        mockSegmentsRepository = mockk(relaxed = true)
        mockRoutesRepository = mockk(relaxed = true)
        mockWorkoutClusterRepository = mockk(relaxed = true)

        every { mockKnownLocationsRepository.locationsFlow } returns fakeLocationsFlow
        every { mockBanalRepository.bSportType } returns fakeSportTypeFlow
        every { mockBanalRepository.currentTrack } returns fakeCurrentTrackFlow
        every { mockBanalRepository.currentLocation } returns MutableStateFlow(null)
        every { mockSegmentsRepository.allSegmentsWithPath } returns MutableStateFlow(emptyList())
        every { mockRoutesRepository.allRoutes } returns fakeRoutesFlow
        every { mockRoutesRepository.activeNavigatedRouteId } returns fakeActiveNavigatedRouteIdFlow
        every { mockWorkoutClusterRepository.allClusters } returns fakeClustersFlow
        every { mockWorkoutClusterRepository.clusterStats } returns fakeClusterStatsFlow
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        clearAllMocks()
    }

    private fun createViewModel(
        prefs: SharedPreferences? = null,
        climbsDbManager: ClimbsDatabaseManager? = null
    ): MapFragmentWithTrackViewModel {
        return MapFragmentWithTrackViewModel(
            application = mockApplication,
            knownLocationsRepository = mockKnownLocationsRepository,
            banalRepo = mockBanalRepository,
            segmentsRepo = mockSegmentsRepository,
            routesRepo = mockRoutesRepository,
            workoutClusterRepo = mockWorkoutClusterRepository,
            climbsDbManager = climbsDbManager,
            prefs = prefs,
            defaultDispatcher = testDispatcher
        )
    }

    @Test
    fun testUiState_combinesKnownLocationsFlow() = runTest {
        val sampleLocations = listOf(
            KnownLocationItem(
                id = 1L,
                name = "Home",
                altitude = 500.0,
                radius = 200,
                latLng = LatLng(48.1, 11.5),
                hitCount = 10,
                isLocked = true,
                source = ElevationSource.MANUAL_USER
            ),
            KnownLocationItem(
                id = 2L,
                name = "Park",
                altitude = 510.0,
                radius = 150,
                latLng = LatLng(48.2, 11.6),
                hitCount = 5,
                isLocked = false,
                source = ElevationSource.INTERNET_DEM
            )
        )
        fakeLocationsFlow.value = sampleLocations

        val viewModel = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }

        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.knownLocations.size)
        assertEquals("Home", viewModel.uiState.value.knownLocations[0].name)
        assertEquals(200, viewModel.uiState.value.knownLocations[0].radius)
        assertEquals("Park", viewModel.uiState.value.knownLocations[1].name)
        assertEquals(150, viewModel.uiState.value.knownLocations[1].radius)
    }

    @Test
    fun testUiState_combinesClimbsFlow() = runTest {
        val mockClimbsDb = mockk<ClimbsDatabaseManager>(relaxed = true)
        val sampleClimbs = listOf(
            Climb(
                id = 101L,
                name = "Col du Galibier",
                startLat = 45.06,
                startLng = 6.40,
                endLat = 45.06,
                endLng = 6.41,
                distanceMeters = 8500.0,
                elevationGainMeters = 585.0,
                avgGradePercent = 6.9,
                maxGradePercent = 10.1,
                category = ClimbCategory.CAT_1
            )
        )
        io.mockk.coEvery { mockClimbsDb.getAllClimbs() } returns sampleClimbs

        val mockPrefs = mockk<SharedPreferences>(relaxed = true)
        every { mockPrefs.getStringSet(MapFragmentWithTrackViewModel.PREF_GENERAL_MAP_ENABLED_LAYERS, null) } returns setOf("CLIMBS")

        val viewModel = createViewModel(prefs = mockPrefs, climbsDbManager = mockClimbsDb)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }

        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.climbs.size)
        assertEquals("Col du Galibier", viewModel.uiState.value.climbs[0].name)
    }

    @Test
    fun testUpdateKnownLocation_delegatesToRepository() = runTest {
        val viewModel = createViewModel()

        viewModel.updateKnownLocation(
            id = 42L,
            name = "Trailhead",
            altitude = 550.0,
            radius = 300,
            source = ElevationSource.MANUAL_USER
        )

        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) {
            mockKnownLocationsRepository.updateLocation(42L, "Trailhead", 550.0, 300, ElevationSource.MANUAL_USER)
        }
    }

    /**
     * Verifies that when a route is actively navigated (activeNavigatedRouteId matches route summary id),
     * uiState.routes maps that route with isActiveNavigation = true (prominent width and solid pattern),
     * while other selected routes remain passive (standard width and dashed pattern).
     *
     * Traceability: REQ-MAP-040, TST-MAP-042 (ATT-2940).
     */
    @Test
    fun testUiState_mapsActiveNavigatedRouteWithProminence() = runTest {
        val samplePath = listOf(
            PathPoint(distance = 0.0, latLng = LatLng(48.0, 11.0), altitude = 500.0),
            PathPoint(distance = 100.0, latLng = LatLng(48.001, 11.001), altitude = 505.0)
        )
        val routeActive = RouteWithPath(
            summary = RouteSummary(
                id = 101L,
                externalId = "ext_101",
                name = "Active Route",
                description = "Active route description",
                isSelected = true,
                distance = 5000.0,
                elevationGain = 200.0,
                bSportType = BSportType.BIKE,
                source = RouteSource.LOCAL_GPX
            ),
            path = samplePath
        )
        val routePassive = RouteWithPath(
            summary = RouteSummary(
                id = 102L,
                externalId = "ext_102",
                name = "Passive Route",
                description = "Passive route description",
                isSelected = true,
                distance = 3000.0,
                elevationGain = 100.0,
                bSportType = BSportType.BIKE,
                source = RouteSource.LOCAL_GPX
            ),
            path = samplePath
        )

        fakeRoutesFlow.value = listOf(routeActive, routePassive)
        fakeActiveNavigatedRouteIdFlow.value = 101L

        val viewModel = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }

        testDispatcher.scheduler.advanceUntilIdle()

        val uiRoutes = viewModel.uiState.value.routes
        assertEquals(2, uiRoutes.size)

        val activeMapRoute = uiRoutes.first { it.id == 101L }
        assertTrue("Actively navigated route must have isActiveNavigation = true", activeMapRoute.isActiveNavigation)
        assertEquals(26f, activeMapRoute.width, 0.001f)
        assertNull("Actively navigated route must have null pattern (solid line)", activeMapRoute.pattern)

        val passiveMapRoute = uiRoutes.first { it.id == 102L }
        assertFalse("Passive route must have isActiveNavigation = false", passiveMapRoute.isActiveNavigation)
        assertEquals(10f, passiveMapRoute.width, 0.001f)
        assertNotNull("Passive route must retain dash pattern", passiveMapRoute.pattern)
    }

    @Test
    fun testUiState_combinesClustersFlow() = runTest {
        val sampleCluster = WorkoutCluster(
            id = 55L,
            name = "Morning River Run",
            probableSportId = 1L,
            startLat = 48.1,
            startLng = 11.5,
            endLat = 48.2,
            endLng = 11.6,
            maxDispLat = 48.2,
            maxDispLng = 11.6,
            refDistance = 3500.0,
            hitCount = 8,
            bSportType = BSportType.RUN,
            routePolyline = com.google.maps.android.PolyUtil.encode(listOf(LatLng(48.1, 11.5), LatLng(48.2, 11.6)))
        )
        val stats = WorkoutClusterStats(
            clusterId = 55L,
            workoutCount = 8,
            lastHitTimestampS = 1700000000L,
            bestDurationSec = 900L,
            totalDistanceMeters = 28000.0,
            totalAscentMeters = 240L
        )
        fakeClustersFlow.value = listOf(sampleCluster)
        fakeClusterStatsFlow.value = mapOf(55L to stats)

        val viewModel = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }

        testDispatcher.scheduler.advanceUntilIdle()

        // By default, clusters are disabled (ATT-5074)
        assertEquals("Clusters must be empty when layer is disabled by default", 0, viewModel.uiState.value.clusters.size)

        // Toggle clusters on
        viewModel.toggleLayer(GeneralMapLayer.CLUSTERS)
        testDispatcher.scheduler.advanceUntilIdle()

        val clusters = viewModel.uiState.value.clusters
        assertEquals(1, clusters.size)
        assertEquals(55L, clusters[0].id)
        assertEquals("Morning River Run", clusters[0].name)
        assertEquals(BSportType.RUN, clusters[0].bSportType)
        assertEquals(8, clusters[0].hitCount)
        assertEquals(2, clusters[0].latLngs.size)
        assertEquals(stats, clusters[0].stats)
        assertEquals(900L, clusters[0].stats?.bestDurationSec)
        assertEquals(30L, clusters[0].stats?.avgAscentMeters)
    }
}
