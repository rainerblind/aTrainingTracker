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

package com.atrainingtracker.trainingtracker.ui.navigation

import android.app.Application
import android.util.Log
import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import androidx.lifecycle.MutableLiveData
import com.atrainingtracker.banalservice.ActivityType
import com.atrainingtracker.banalservice.ui.devices.devicedata.DeviceDataRepository
import com.atrainingtracker.trainingtracker.TrackingMode
import com.atrainingtracker.trainingtracker.activities.MainActivityWithNavigation
import com.atrainingtracker.trainingtracker.repositories.BANALServiceRepository
import com.atrainingtracker.trainingtracker.ui.tracking.ScreenMode
import com.atrainingtracker.trainingtracker.ui.tracking.TrackingViewsRepository
import com.atrainingtracker.trainingtracker.ui.tracking.trackingtabs.TrackingTabsViewModel
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * Unit test verifying [TrackingTabsViewModel] reactive state transitions and [MainActivityWithNavigation]
 * interop for tracking tab configuration and hierarchical back navigation.
 *
 * Fulfills verification requirement TST-UI-127 (ATT-1456 / REQ-UI-175).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TrackingTabsViewModelInteropTest {

    private val testDispatcher = StandardTestDispatcher()
    private val mockApplication = mockk<Application>(relaxed = true)
    private val mockTrackingViewsRepo = mockk<TrackingViewsRepository>(relaxed = true)
    private val mockBanalRepo = mockk<BANALServiceRepository>(relaxed = true)
    private val mockDevicesRepo = mockk<DeviceDataRepository>(relaxed = true)

    private val trackingModeLiveData = MutableLiveData<TrackingMode>()
    private val defaultSport = ActivityType.getDefaultActivityType()
    private val banalActivityTypeFlow = MutableStateFlow(defaultSport)

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
        every { Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0

        every { mockBanalRepo.trackingMode } returns trackingModeLiveData
        every { mockBanalRepo.activityType } returns banalActivityTypeFlow
        every { mockBanalRepo.bindToBANALService() } just Runs
        every { mockTrackingViewsRepo.getTrackingViewsFlow(any()) } returns flowOf(emptyList())
        every { mockDevicesRepo.allDevices } returns MutableStateFlow(emptyList())
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
            devicesRepository = mockDevicesRepo
        )
    }

    @Test
    fun testSportSelectionTransitionsToConfiguration() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        val job = launch { viewModel.activityType.collect() }
        testScheduler.advanceUntilIdle()

        assertEquals(ScreenMode.TRACKING, viewModel.screenMode.value)

        // Select sport for configuration
        val selectedSport = ActivityType.BIKE_POWER
        viewModel.setExplicitActivityType(selectedSport)
        viewModel.setScreenMode(ScreenMode.CONFIGURATION)

        testScheduler.advanceUntilIdle()

        assertEquals(ScreenMode.CONFIGURATION, viewModel.screenMode.value)
        assertEquals(selectedSport, viewModel.activityType.value)

        job.cancel()
    }

    @Test
    fun testHierarchicalBackNavigationFromConfigurationToPreview() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        testScheduler.advanceUntilIdle()

        viewModel.setExplicitActivityType(ActivityType.RUN_SPEED_AND_CADENCE)
        viewModel.setScreenMode(ScreenMode.CONFIGURATION)
        testScheduler.advanceUntilIdle()
        assertEquals(ScreenMode.CONFIGURATION, viewModel.screenMode.value)

        // Back press in CONFIGURATION transitions to PREVIEW
        viewModel.handleBackPressToPreview()
        testScheduler.advanceUntilIdle()

        assertEquals(ScreenMode.PREVIEW, viewModel.screenMode.value)
    }

    @Test
    fun testExitConfigurationFromPreviewReturnsToTracking() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        val job = launch { viewModel.activityType.collect() }
        testScheduler.advanceUntilIdle()

        viewModel.setExplicitActivityType(ActivityType.RUN_SPEED_AND_CADENCE)
        viewModel.setScreenMode(ScreenMode.PREVIEW)
        testScheduler.advanceUntilIdle()
        assertEquals(ScreenMode.PREVIEW, viewModel.screenMode.value)
        assertEquals(ActivityType.RUN_SPEED_AND_CADENCE, viewModel.activityType.value)

        // Exit configuration returns to TRACKING and clears explicit sport
        viewModel.exitConfiguration()
        testScheduler.advanceUntilIdle()

        assertEquals(ScreenMode.TRACKING, viewModel.screenMode.value)
        // Cleared explicit sport reverts to BANALService repository live type
        assertEquals(defaultSport, viewModel.activityType.value)

        job.cancel()
    }

    @Test
    fun testExitConfigurationSafeInTrackingMode() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        testScheduler.advanceUntilIdle()

        assertEquals(ScreenMode.TRACKING, viewModel.screenMode.value)

        // Invoking exitConfiguration when already in TRACKING is safe
        viewModel.exitConfiguration()
        testScheduler.advanceUntilIdle()

        assertEquals(ScreenMode.TRACKING, viewModel.screenMode.value)
    }

    @Test
    fun testMainActivityOnActivityTypeSelectedMethodContract() {
        val clazz = MainActivityWithNavigation::class.java

        // onActivityTypeSelected must exist with public visibility taking ActivityType parameter
        val method = clazz.getMethod("onActivityTypeSelected", ActivityType::class.java)
        assertNotNull("onActivityTypeSelected must exist on MainActivityWithNavigation", method)
        assertTrue(Modifier.isPublic(method.modifiers))
    }
}
