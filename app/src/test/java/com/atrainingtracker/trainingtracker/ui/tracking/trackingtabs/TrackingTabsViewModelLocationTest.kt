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

package com.atrainingtracker.trainingtracker.ui.tracking.trackingtabs

import android.app.Application
import android.location.Location
import android.util.Log
import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import androidx.lifecycle.MutableLiveData
import com.atrainingtracker.banalservice.ActivityType
import com.atrainingtracker.banalservice.ui.devices.devicedata.DeviceDataRepository
import com.atrainingtracker.trainingtracker.TrackingMode
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.elevation.ElevationSource
import com.atrainingtracker.trainingtracker.repositories.BANALServiceRepository
import com.atrainingtracker.trainingtracker.repositories.KnownLocationItem
import com.atrainingtracker.trainingtracker.repositories.KnownLocationsRepository
import com.atrainingtracker.trainingtracker.ui.tracking.TrackingViewsRepository
import com.google.android.gms.maps.model.LatLng
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import kotlin.math.*

/**
 * Unit test suite for [TrackingTabsViewModel] verifying favorite location (Lieblingsort)
 * proximity detection, reference altitude feedback, toggle preference decoupling,
 * and nearest-candidate resolution (REQ-UI-183, TST-UI-136, ATT-1399).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TrackingTabsViewModelLocationTest {

    private val testDispatcher = StandardTestDispatcher()
    private val mockApplication = mockk<Application>(relaxed = true)
    private val mockTrackingViewsRepo = mockk<TrackingViewsRepository>(relaxed = true)
    private val mockBanalRepo = mockk<BANALServiceRepository>(relaxed = true)
    private val mockDevicesRepo = mockk<DeviceDataRepository>(relaxed = true)
    private val mockKnownLocationsRepo = mockk<KnownLocationsRepository>(relaxed = true)

    private val trackingModeLiveData = MutableLiveData<TrackingMode>()
    private val activityTypeFlow = MutableStateFlow(ActivityType.getDefaultActivityType())
    private val currentLocationFlow = MutableStateFlow<LatLng?>(null)
    private val knownLocationsFlow = MutableStateFlow<List<KnownLocationItem>>(emptyList())
    private val isAltimeterCalibratedFlow = MutableStateFlow(true)

    private var registeredDisplaySettingsListener: TrainingApplication.OnDisplaySettingsChangeListener? = null
    private var isFeedbackEnabledPreference = true

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        ArchTaskExecutor.getInstance().setDelegate(object : TaskExecutor() {
            override fun executeOnDiskIO(runnable: Runnable) = runnable.run()
            override fun postToMainThread(runnable: Runnable) = runnable.run()
            override fun isMainThread(): Boolean = true
        })

        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0

        isFeedbackEnabledPreference = true
        mockkStatic(TrainingApplication::class)
        every { TrainingApplication.isLieblingsortCockpitFeedbackEnabled() } answers { isFeedbackEnabledPreference }
        every { TrainingApplication.addDisplaySettingsChangeListener(any()) } answers {
            registeredDisplaySettingsListener = firstArg()
        }
        every { TrainingApplication.removeDisplaySettingsChangeListener(any()) } answers {
            registeredDisplaySettingsListener = null
        }

        mockkStatic(Location::class)
        every { Location.distanceBetween(any(), any(), any(), any(), any()) } answers {
            val lat1 = arg<Double>(0)
            val lon1 = arg<Double>(1)
            val lat2 = arg<Double>(2)
            val lon2 = arg<Double>(3)
            val results = arg<FloatArray>(4)
            results[0] = calculateHaversineDistance(lat1, lon1, lat2, lon2)
        }

        isAltimeterCalibratedFlow.value = true
        every { mockBanalRepo.trackingMode } returns trackingModeLiveData
        every { mockBanalRepo.activityType } returns activityTypeFlow
        every { mockBanalRepo.currentLocation } returns currentLocationFlow
        every { mockBanalRepo.isAltimeterCalibrated } returns isAltimeterCalibratedFlow
        every { mockBanalRepo.calibrateAltimeter(any()) } returns true
        every { mockBanalRepo.bindToBANALService() } just Runs
        every { mockBanalRepo.unbindFromBANALService() } just Runs
        every { mockTrackingViewsRepo.getTrackingViewsFlow(any()) } returns flowOf(emptyList())
        every { mockDevicesRepo.allDevices } returns MutableStateFlow(emptyList())
        every { mockKnownLocationsRepo.locationsFlow } returns knownLocationsFlow
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        ArchTaskExecutor.getInstance().setDelegate(null)
        unmockkAll()
    }

    private fun createViewModel(): TrackingTabsViewModel {
        return TrackingTabsViewModel(
            application = mockApplication,
            trackingViewsRepository = mockTrackingViewsRepo,
            banalServiceRepository = mockBanalRepo,
            devicesRepository = mockDevicesRepo,
            knownLocationsRepository = mockKnownLocationsRepo
        )
    }

    private fun calculateHaversineDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val r = 6371000.0 // Earth radius in meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return (r * c).toFloat()
    }

    @Test
    fun testInsideGeofenceEmitsLocationCalibrationStatus() = runTest {
        val locationItem = KnownLocationItem(
            id = 1L,
            name = "Haus",
            altitude = 520.0,
            radius = 200,
            latLng = LatLng(48.137154, 11.576124),
            hitCount = 10,
            isLocked = true,
            source = ElevationSource.MANUAL_USER
        )
        knownLocationsFlow.value = listOf(locationItem)
        // Set user position 50m away from Munich Marienplatz
        currentLocationFlow.value = LatLng(48.137300, 11.576500)

        val viewModel = createViewModel()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.locationCalibrationStatus.collect {}
        }

        testScheduler.advanceUntilIdle()

        val status = viewModel.locationCalibrationStatus.value
        assertNotNull("Expected non-null calibration status when inside geofence", status)
        assertEquals(1L, status?.locationId)
        assertEquals("Haus", status?.locationName)
        assertEquals(520.0, status?.referenceAltitude ?: 0.0, 0.01)
        assertTrue(status?.isCalibrated == true)
        assertEquals(ElevationSource.MANUAL_USER, status?.source)

        job.cancel()
    }

    /**
     * TST-UI-153.3: Verifies that entering a known location geofence dispatches altimeter calibration,
     * reflects standby uncalibrated status while sensor warms up, and transitions to calibrated status.
     */
    @Test
    fun testInsideGeofenceDispatchesAltimeterCalibrationAndReflectsCalibratedState() = runTest {
        val locationItem = KnownLocationItem(
            id = 1L,
            name = "Haus",
            altitude = 520.0,
            radius = 200,
            latLng = LatLng(48.137154, 11.576124),
            hitCount = 10,
            isLocked = true,
            source = ElevationSource.MANUAL_USER
        )
        knownLocationsFlow.value = listOf(locationItem)
        isAltimeterCalibratedFlow.value = false
        currentLocationFlow.value = LatLng(48.137300, 11.576500)

        val viewModel = createViewModel()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.locationCalibrationStatus.collect {}
        }

        testScheduler.advanceUntilIdle()

        val statusBefore = viewModel.locationCalibrationStatus.value
        assertNotNull(statusBefore)
        assertEquals("Haus", statusBefore?.locationName)
        assertEquals(520.0, statusBefore?.referenceAltitude ?: 0.0, 0.01)
        assertFalse("Initially altimeter is uncalibrated", statusBefore?.isCalibrated ?: true)
        verify { mockBanalRepo.calibrateAltimeter(520.0) }

        // When altimeter warms up / completes calibration
        isAltimeterCalibratedFlow.value = true
        testScheduler.advanceUntilIdle()

        val statusAfter = viewModel.locationCalibrationStatus.value
        assertTrue("Reflects calibrated state once altimeter confirms calibration", statusAfter?.isCalibrated == true)

        job.cancel()
    }

    @Test
    fun testOutsideGeofenceEmitsNull() = runTest {
        val locationItem = KnownLocationItem(
            id = 2L,
            name = "Büro",
            altitude = 480.0,
            radius = 150,
            latLng = LatLng(48.137154, 11.576124),
            hitCount = 5,
            isLocked = false,
            source = ElevationSource.AUTO_LEARNED
        )
        knownLocationsFlow.value = listOf(locationItem)
        // Set user position 1000m away
        currentLocationFlow.value = LatLng(48.145000, 11.576124)

        val viewModel = createViewModel()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.locationCalibrationStatus.collect {}
        }

        testScheduler.advanceUntilIdle()

        val status = viewModel.locationCalibrationStatus.value
        assertNull("Expected null calibration status when outside geofence", status)

        job.cancel()
    }

    @Test
    fun testMovingFromInsideToOutsideGeofenceUpdatesStatus() = runTest {
        val locationItem = KnownLocationItem(
            id = 3L,
            name = "Trainingsstrecke",
            altitude = 310.0,
            radius = 200,
            latLng = LatLng(48.137154, 11.576124),
            hitCount = 20,
            isLocked = true,
            source = ElevationSource.MANUAL_USER
        )
        knownLocationsFlow.value = listOf(locationItem)
        currentLocationFlow.value = LatLng(48.137160, 11.576130)

        val viewModel = createViewModel()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.locationCalibrationStatus.collect {}
        }

        testScheduler.advanceUntilIdle()
        assertNotNull(viewModel.locationCalibrationStatus.value)
        assertEquals("Trainingsstrecke", viewModel.locationCalibrationStatus.value?.locationName)

        // Move athlete far outside geofence (1000m away)
        currentLocationFlow.value = LatLng(48.146000, 11.576130)
        testScheduler.advanceUntilIdle()

        assertNull("Status must be null when moving outside geofence", viewModel.locationCalibrationStatus.value)

        // Move athlete back inside geofence
        currentLocationFlow.value = LatLng(48.137160, 11.576130)
        testScheduler.advanceUntilIdle()

        assertNotNull("Status must be restored when moving back inside geofence", viewModel.locationCalibrationStatus.value)

        job.cancel()
    }

    @Test
    fun testMultipleOverlappingLocationsSelectsClosest() = runTest {
        // Center position: (48.137154, 11.576124)
        val userPos = LatLng(48.137154, 11.576124)

        val closerItem = KnownLocationItem(
            id = 10L,
            name = "Closer Location",
            altitude = 500.0,
            radius = 300,
            latLng = LatLng(48.137250, 11.576124), // ~10m away
            hitCount = 2,
            isLocked = true,
            source = ElevationSource.MANUAL_USER
        )
        val fartherItem = KnownLocationItem(
            id = 20L,
            name = "Farther Location",
            altitude = 550.0,
            radius = 300,
            latLng = LatLng(48.138000, 11.576124), // ~94m away
            hitCount = 50,
            isLocked = false,
            source = ElevationSource.AUTO_LEARNED
        )

        knownLocationsFlow.value = listOf(fartherItem, closerItem)
        currentLocationFlow.value = userPos

        val viewModel = createViewModel()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.locationCalibrationStatus.collect {}
        }

        testScheduler.advanceUntilIdle()

        val status = viewModel.locationCalibrationStatus.value
        assertNotNull(status)
        assertEquals(10L, status?.locationId)
        assertEquals("Closer Location", status?.locationName)
        assertEquals(500.0, status?.referenceAltitude ?: 0.0, 0.01)

        job.cancel()
    }

    @Test
    fun testNullCurrentLocationEmitsNull() = runTest {
        val locationItem = KnownLocationItem(
            id = 4L,
            name = "Halle",
            altitude = 400.0,
            radius = 200,
            latLng = LatLng(48.137154, 11.576124),
            hitCount = 1,
            isLocked = false,
            source = ElevationSource.AUTO_LEARNED
        )
        knownLocationsFlow.value = listOf(locationItem)
        currentLocationFlow.value = null

        val viewModel = createViewModel()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.locationCalibrationStatus.collect {}
        }

        testScheduler.advanceUntilIdle()

        assertNull("Status must be null when current GPS position is null", viewModel.locationCalibrationStatus.value)

        job.cancel()
    }

    /**
     * TST-CON-008.4: Altimeter calibration dispatch is gated to geofence transitions.
     * Emitting repeated location updates inside the same geofence must not flood calibrateAltimeter.
     */
    @Test
    fun testCalibrationGating_dispatchesOnlyOnGeofenceTransition() = runTest {
        val homeLocation = KnownLocationItem(
            id = 100L,
            name = "Zu Hause",
            altitude = 507.0,
            radius = 100,
            latLng = LatLng(48.137154, 11.576124),
            hitCount = 10,
            isLocked = true,
            source = ElevationSource.INTERNET_DEM
        )
        knownLocationsFlow.value = listOf(homeLocation)
        isAltimeterCalibratedFlow.value = true
        currentLocationFlow.value = LatLng(48.137154, 11.576124) // Inside geofence

        val viewModel = createViewModel()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.locationCalibrationStatus.collect {}
        }
        testScheduler.advanceUntilIdle()

        // Verify calibrateAltimeter called once on initial entry
        verify(exactly = 1) { mockBanalRepo.calibrateAltimeter(507.0) }

        // Emit 5 sequential GPS location updates while remaining inside the same geofence
        for (i in 1..5) {
            currentLocationFlow.value = LatLng(48.137154 + (i * 0.00001), 11.576124)
            testScheduler.advanceUntilIdle()
        }

        // calibrateAltimeter must STILL have been called exactly 1 time (not 6 times!)
        verify(exactly = 1) { mockBanalRepo.calibrateAltimeter(507.0) }

        // Move outside geofence
        currentLocationFlow.value = LatLng(48.150000, 11.576124)
        testScheduler.advanceUntilIdle()
        assertNull(viewModel.locationCalibrationStatus.value)

        // Move back inside geofence (geofence transition)
        currentLocationFlow.value = LatLng(48.137154, 11.576124)
        testScheduler.advanceUntilIdle()

        // calibrateAltimeter must now have been called a 2nd time (on re-entry transition)
        verify(exactly = 2) { mockBanalRepo.calibrateAltimeter(507.0) }

        job.cancel()
    }
}
