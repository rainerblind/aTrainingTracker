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

package com.atrainingtracker.trainingtracker.ui.tracking.tracking

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import com.atrainingtracker.banalservice.ActivityType
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.filters.FilteredSensorData
import com.atrainingtracker.trainingtracker.database.RouteSource
import com.atrainingtracker.trainingtracker.database.RouteSummary
import com.atrainingtracker.trainingtracker.database.RouteWithPath
import com.atrainingtracker.trainingtracker.repositories.BANALServiceRepository
import com.atrainingtracker.trainingtracker.repositories.RoutesRepository
import com.atrainingtracker.trainingtracker.segments.LiveSegment
import com.atrainingtracker.trainingtracker.segments.LiveSegmentData
import com.atrainingtracker.trainingtracker.segments.LiveSegmentMath
import com.atrainingtracker.trainingtracker.segments.LiveSegmentStatus
import com.atrainingtracker.trainingtracker.segments.LiveSegmentsRepository
import com.atrainingtracker.trainingtracker.segments.SegmentSummary
import com.atrainingtracker.trainingtracker.segments.SegmentWithPath
import com.atrainingtracker.trainingtracker.settings.SettingsDataStore
import com.atrainingtracker.trainingtracker.settings.TuningPreferencesDefaults
import com.atrainingtracker.trainingtracker.settings.ZoneDisplayOptions
import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import com.atrainingtracker.trainingtracker.ui.map.calculateFollowMeTargetZoom
import com.atrainingtracker.trainingtracker.ui.map.stepFollowMeSmoothing
import com.atrainingtracker.trainingtracker.ui.tracking.TrackingViewInfo
import com.atrainingtracker.trainingtracker.ui.tracking.TrackingViewsRepository
import com.google.android.gms.maps.model.LatLng
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Unit and contract verification suite for ATT-3775 (`REQ-MAP-052`, `TST-MAP-054`):
 * - Reactive subscription to `banalServiceRepository.currentSpeed` and `currentBearing` in `TrackingViewModel.observeMapState()`
 *   in both `READY` (empty `currentTrack`) and `TRACKING` (non-empty `currentTrack`) modes without requiring `currentTrack` emissions (Rule 39).
 * - Referential stability (`===`) of `mapSegments` and `mapRoutes` across 1-Hz `currentSpeed` and `currentBearing` emissions (`REQ-MAP-051`).
 * - Continuous Follow-Me EMA convergence (`stepFollowMeSmoothing`) in `MapBehaviors.kt` across steady low, cruising, and high speeds and bearing wrap-around.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TrackingViewModelMapStateTest {

    private val testDispatcher = StandardTestDispatcher()
    private val mockApplication = mockk<Application>(relaxed = true)
    private val mockContext = mockk<Context>(relaxed = true)
    private val mockTrackingViewsRepo = mockk<TrackingViewsRepository>(relaxed = true)
    private val mockBanalRepo = mockk<BANALServiceRepository>(relaxed = true)
    private val mockLiveSegmentsRepo = mockk<LiveSegmentsRepository>(relaxed = true)
    private val mockRoutesRepo = mockk<RoutesRepository>(relaxed = true)
    private val mockPrefs = mockk<SharedPreferences>(relaxed = true)

    private val liveSegmentsFlow = MutableStateFlow(emptyList<LiveSegment>())
    private val bSportTypeFlow = MutableStateFlow(BSportType.BIKE)
    private val currentTrackFlow = MutableStateFlow(emptyList<LatLng>())
    private val currentSpeedFlow = MutableStateFlow<Double?>(null)
    private val currentBearingFlow = MutableStateFlow<Double?>(null)
    private val allRoutesFlow = MutableStateFlow(emptyList<RouteWithPath>())
    private val activeNavigatedRouteIdFlow = MutableStateFlow<Long?>(null)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        mockkStatic(Log::class)
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0

        mockkStatic(PreferenceManager::class)
        every { PreferenceManager.getDefaultSharedPreferences(any()) } returns mockPrefs

        mockkStatic(ContextCompat::class)
        every { ContextCompat.getColor(any(), any()) } returns 0xFF00FF

        mockkConstructor(SettingsDataStore::class)
        every { anyConstructed<SettingsDataStore>().getZoneDisplayOptionsFlow(any()) } returns flowOf(ZoneDisplayOptions())

        every { mockApplication.applicationContext } returns mockContext
        every { mockApplication.getString(any()) } returns "Start"
        every { mockLiveSegmentsRepo.liveSegments } returns liveSegmentsFlow
        every { mockBanalRepo.bSportType } returns bSportTypeFlow
        every { mockBanalRepo.activityType } returns MutableStateFlow(ActivityType.getDefaultActivityType())
        every { mockBanalRepo.currentTrack } returns currentTrackFlow
        every { mockBanalRepo.currentSpeed } returns currentSpeedFlow
        every { mockBanalRepo.currentBearing } returns currentBearingFlow
        every { mockBanalRepo.allFilteredSensorData } returns MutableStateFlow(emptyList<FilteredSensorData<*>>())
        every { mockBanalRepo.currentPathPoints } returns MutableStateFlow(emptyList<PathPoint>())
        every { mockRoutesRepo.allRoutes } returns allRoutesFlow
        every { mockRoutesRepo.activeNavigatedRouteId } returns activeNavigatedRouteIdFlow
        coEvery { mockTrackingViewsRepo.getActivityTypeForView(any()) } returns ActivityType.getDefaultActivityType()
        every {
            mockTrackingViewsRepo.getTrackingViewInfoFlow(any())
        } returns flowOf(
            TrackingViewInfo(
                tabViewId = 1L,
                name = "Map Tab",
                showLapButton = false,
                showMap = true,
                showLiveSegments = true,
                showElevationProfile = false
            )
        )
        every { mockTrackingViewsRepo.getSensorFieldConfigsForView(any()) } returns flowOf(emptyList())
    }

    private val createdViewModels = mutableListOf<TrackingViewModel>()

    @After
    fun tearDown() {
        createdViewModels.forEach { vm ->
            vm.viewModelScope.cancel()
        }
        createdViewModels.clear()
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun createViewModel(): TrackingViewModel {
        return TrackingViewModel(
            application = mockApplication,
            trackingViewsRepository = mockTrackingViewsRepo,
            banalServiceRepository = mockBanalRepo,
            liveSegmentsRepository = mockLiveSegmentsRepo,
            routesRepository = mockRoutesRepo,
            viewId = 1L
        ).also { createdViewModels.add(it) }
    }

    @Test
    fun testObserveMapState_reactsImmediatelyToCurrentSpeedAndCurrentBearingWithoutCurrentTrackEmission() = runTest {
        val samplePath = listOf(
            PathPoint(0.0, LatLng(48.1, 11.1), 500.0),
            PathPoint(250.0, LatLng(48.2, 11.2), 510.0)
        )
        allRoutesFlow.value = listOf(
            RouteWithPath(
                summary = RouteSummary(
                    id = 7L,
                    externalId = "r-7",
                    name = "Test Route",
                    description = "",
                    isSelected = true,
                    distance = 250.0,
                    elevationGain = 10.0,
                    bSportType = BSportType.BIKE,
                    source = RouteSource.LOCAL_GPX
                ),
                path = samplePath
            )
        )
        liveSegmentsFlow.value = listOf(
            LiveSegment(
                staticData = SegmentWithPath(
                    summary = SegmentSummary(
                        stravaId = 99L,
                        name = "Test Segment",
                        bSportType = BSportType.BIKE,
                        climbCategory_raw = 4,
                        climbCategory = "4",
                        prTime_raw = 60,
                        prTime = "01:00",
                        city = "Munich",
                        distance = "250 m",
                        distance_raw = 250.0,
                        averageGrade_raw = 4.0,
                        averageGrade = "4.0%",
                        maxGrade = "6.0%",
                        elevationGain_raw = 10.0,
                        elevationGain = "10 m",
                        elevationMin = "500 m",
                        elevationMax = "510 m",
                        map_polyline = ""
                    ),
                    path = samplePath
                ),
                liveData = LiveSegmentData(segmentStatus = LiveSegmentStatus.APPROACHING),
                math = mockk<LiveSegmentMath>(relaxed = true)
            )
        )

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        // Initial READY state (currentTrack is empty, speed and bearing are null -> 0f)
        assertTrue(viewModel.mapState.value.showMap)
        assertTrue(viewModel.mapState.value.currentTrack.isEmpty())
        assertEquals(0.0f, viewModel.mapState.value.userSpeed, 0.001f)
        assertEquals(0.0f, viewModel.mapState.value.userBearing, 0.001f)
        val initialSegmentsRef = viewModel.mapState.value.mapSegments
        val initialRoutesRef = viewModel.mapState.value.mapRoutes

        // 1. Emit speed and bearing in READY mode (currentTrack does NOT emit)
        currentSpeedFlow.value = 8.5
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(8.5f, viewModel.mapState.value.userSpeed, 0.001f)
        assertEquals(8.5f, viewModel.uiState.value.userSpeed, 0.001f)

        currentBearingFlow.value = 135.0
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(135.0f, viewModel.mapState.value.userBearing, 0.001f)
        assertEquals(135.0f, viewModel.uiState.value.userBearing, 0.001f)

        // Static layer lists must remain referentially identical across speed/bearing emissions (REQ-MAP-051)
        assertSame(initialSegmentsRef, viewModel.mapState.value.mapSegments)
        assertSame(initialRoutesRef, viewModel.mapState.value.mapRoutes)

        // 2. Enter TRACKING mode with one point, then update speed & bearing WITHOUT emitting on currentTrack
        currentTrackFlow.value = listOf(LatLng(48.137, 11.575))
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, viewModel.mapState.value.currentTrack.size)

        currentSpeedFlow.value = 14.2
        currentBearingFlow.value = 270.5
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(14.2f, viewModel.mapState.value.userSpeed, 0.001f)
        assertEquals(270.5f, viewModel.mapState.value.userBearing, 0.001f)
        assertEquals(14.2f, viewModel.uiState.value.userSpeed, 0.001f)
        assertEquals(270.5f, viewModel.uiState.value.userBearing, 0.001f)

        // 3. Emit null speed/bearing -> safely resets to 0.0f
        currentSpeedFlow.value = null
        currentBearingFlow.value = null
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(0.0f, viewModel.mapState.value.userSpeed, 0.001f)
        assertEquals(0.0f, viewModel.mapState.value.userBearing, 0.001f)
    }

    @Test
    fun testStepFollowMeSmoothing_convergesToTargetZoomAndBearingUnderSteadyInputs() {
        // Case A: Low steady speed (2.5 m/s = 9.0 km/h, target zoom = 19.10f) and 350 -> 10 deg wrap-around bearing
        var filteredSpeed = 0.0f
        var filteredBearing = 350.0f
        var activeZoom = TuningPreferencesDefaults.DEFAULT_MAP_FOLLOW_ME_INITIAL_ZOOM // 20.0f
        val targetSpeedLow = 2.5f
        val targetBearing = 10.0f

        var steps = 0
        var converged = false
        while (!converged && steps < 100) {
            val step = stepFollowMeSmoothing(
                currentFilteredSpeed = filteredSpeed,
                currentFilteredBearing = filteredBearing,
                currentActiveZoom = activeZoom,
                targetSpeed = targetSpeedLow,
                targetBearing = targetBearing
            )
            filteredSpeed = step.filteredSpeed
            filteredBearing = step.filteredBearing
            activeZoom = step.activeZoom
            converged = step.isConverged
            steps++
        }

        assertTrue("stepFollowMeSmoothing must converge within bounded steps (took $steps)", converged)
        assertEquals(targetSpeedLow, filteredSpeed, 0.001f)
        assertEquals(targetBearing, filteredBearing, 0.001f)
        val expectedZoomLow = calculateFollowMeTargetZoom(targetSpeedLow)
        assertEquals(expectedZoomLow, activeZoom, 0.01f)

        // Case B: High steady speed (11.111f m/s = 40.0 km/h, target zoom = 17.00f)
        converged = false
        steps = 0
        val targetSpeedHigh = 40.0f / 3.6f
        while (!converged && steps < 100) {
            val step = stepFollowMeSmoothing(
                currentFilteredSpeed = filteredSpeed,
                currentFilteredBearing = filteredBearing,
                currentActiveZoom = activeZoom,
                targetSpeed = targetSpeedHigh,
                targetBearing = 180.0f
            )
            filteredSpeed = step.filteredSpeed
            filteredBearing = step.filteredBearing
            activeZoom = step.activeZoom
            converged = step.isConverged
            steps++
        }

        assertTrue("High-speed smoothing must converge within bounded steps", converged)
        assertEquals(targetSpeedHigh, filteredSpeed, 0.001f)
        assertEquals(180.0f, filteredBearing, 0.001f)
        assertEquals(17.0f, activeZoom, 0.02f)
    }

    @Test
    fun testSourceContract_noStaticValueReadsInObserveMapStateAndConvergenceLoopPresent() {
        val cwd = File(System.getProperty("user.dir") ?: ".")
        val root = if (File(cwd, "app/src/main").exists()) cwd else cwd.parentFile ?: cwd

        val viewModelSource = File(
            root,
            "app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/TrackingViewModel.kt"
        ).readText()
        assertFalse(
            "TrackingViewModel must never read currentSpeed.value statically (Rule 39)",
            viewModelSource.contains("currentSpeed.value")
        )
        assertFalse(
            "TrackingViewModel must never read currentBearing.value statically (Rule 39)",
            viewModelSource.contains("currentBearing.value")
        )
        assertTrue(
            "TrackingViewModel must subscribe to banalServiceRepository.currentSpeed",
            viewModelSource.contains("banalServiceRepository.currentSpeed")
        )
        assertTrue(
            "TrackingViewModel must subscribe to banalServiceRepository.currentBearing",
            viewModelSource.contains("banalServiceRepository.currentBearing")
        )

        val mapBehaviorsSource = File(
            root,
            "app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapBehaviors.kt"
        ).readText()
        assertTrue(
            "MapBehaviors.kt must define stepFollowMeSmoothing",
            mapBehaviorsSource.contains("fun stepFollowMeSmoothing(")
        )
        assertTrue(
            "followMeController must loop until step.isConverged",
            mapBehaviorsSource.contains("while (!step.isConverged)")
        )
    }
}
