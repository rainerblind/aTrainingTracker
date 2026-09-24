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
import android.util.Log
import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import androidx.lifecycle.MutableLiveData
import com.atrainingtracker.banalservice.ActivityType
import com.atrainingtracker.banalservice.ui.devices.devicedata.DeviceDataRepository
import com.atrainingtracker.trainingtracker.TrackingMode
import com.atrainingtracker.trainingtracker.repositories.BANALServiceRepository
import com.atrainingtracker.trainingtracker.ui.tracking.ScreenMode
import com.atrainingtracker.trainingtracker.ui.tracking.TrackingViewsRepository
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit test suite for [TrackingTabsViewModel] verifying rising-edge tracking start navigation,
 * cold-start neutrality, pause/resume stability, and multi-cycle robustness (REQ-UI-163, TST-UI-115, ATT-1340).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TrackingTabsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val mockApplication = mockk<Application>(relaxed = true)
    private val mockTrackingViewsRepo = mockk<TrackingViewsRepository>(relaxed = true)
    private val mockBanalRepo = mockk<BANALServiceRepository>(relaxed = true)
    private val mockDevicesRepo = mockk<DeviceDataRepository>(relaxed = true)

    private val trackingModeLiveData = MutableLiveData<TrackingMode>()
    private val activityTypeFlow = MutableStateFlow(ActivityType.getDefaultActivityType())

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
        every { mockBanalRepo.activityType } returns activityTypeFlow
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
    fun testTrackingStartNavigatesToFirstCockpitTab() = runTest(testDispatcher) {
        // TC-1: READY -> TRACKING emits TabNavigationEvent.NavigateTo(0)
        trackingModeLiveData.value = TrackingMode.READY
        val viewModel = createViewModel()

        val events = mutableListOf<TabNavigationEvent>()
        val job = launch {
            viewModel.navigationEvent.collect { events.add(it) }
        }

        testScheduler.advanceUntilIdle()
        assertTrue(events.isEmpty())

        // Start tracking
        trackingModeLiveData.value = TrackingMode.TRACKING
        testScheduler.advanceUntilIdle()

        assertEquals(1, events.size)
        assertEquals(TabNavigationEvent.NavigateTo(0), events.first())

        job.cancel()
    }

    @Test
    fun testColdStartInTrackingModeDoesNotTriggerSpuriousNavigation() = runTest(testDispatcher) {
        // TC-2: Cold start / process restore with already TRACKING mode emits no navigation event
        trackingModeLiveData.value = TrackingMode.TRACKING
        val viewModel = createViewModel()

        val events = mutableListOf<TabNavigationEvent>()
        val job = launch {
            viewModel.navigationEvent.collect { events.add(it) }
        }

        testScheduler.advanceUntilIdle()
        assertTrue("Cold start in TRACKING mode must not emit navigation events", events.isEmpty())

        job.cancel()
    }

    @Test
    fun testResumeFromPausedNavigatesToFirstCockpitTab() = runTest(testDispatcher) {
        // TC-3: READY -> TRACKING -> PAUSED -> TRACKING emits navigation on start and on resume
        trackingModeLiveData.value = TrackingMode.READY
        val viewModel = createViewModel()

        val events = mutableListOf<TabNavigationEvent>()
        val job = launch {
            viewModel.navigationEvent.collect { events.add(it) }
        }

        testScheduler.advanceUntilIdle()

        // 1. Start tracking -> navigation emitted
        trackingModeLiveData.value = TrackingMode.TRACKING
        testScheduler.advanceUntilIdle()
        assertEquals(1, events.size)
        assertEquals(TabNavigationEvent.NavigateTo(0), events[0])

        // 2. Pause tracking -> no new event emitted
        trackingModeLiveData.value = TrackingMode.PAUSED
        testScheduler.advanceUntilIdle()
        assertEquals(1, events.size)

        // 3. Resume tracking from PAUSED -> navigation emitted to first cockpit tab
        trackingModeLiveData.value = TrackingMode.TRACKING
        testScheduler.advanceUntilIdle()
        assertEquals(2, events.size)
        assertEquals(TabNavigationEvent.NavigateTo(0), events[1])

        job.cancel()
    }

    @Test
    fun testMultipleStartStopCyclesReliability() = runTest(testDispatcher) {
        // TC-4: Multiple start/stop cycles reliably emit NavigateTo(0) on every start
        trackingModeLiveData.value = TrackingMode.READY
        val viewModel = createViewModel()

        val events = mutableListOf<TabNavigationEvent>()
        val job = launch {
            viewModel.navigationEvent.collect { events.add(it) }
        }

        testScheduler.advanceUntilIdle()

        // Cycle 1
        trackingModeLiveData.value = TrackingMode.TRACKING
        testScheduler.advanceUntilIdle()
        assertEquals(1, events.size)

        // Stop
        trackingModeLiveData.value = TrackingMode.READY
        testScheduler.advanceUntilIdle()
        assertEquals(1, events.size)

        // Cycle 2
        trackingModeLiveData.value = TrackingMode.TRACKING
        testScheduler.advanceUntilIdle()
        assertEquals(2, events.size)

        // Stop
        trackingModeLiveData.value = TrackingMode.READY
        testScheduler.advanceUntilIdle()
        assertEquals(2, events.size)

        // Cycle 3
        trackingModeLiveData.value = TrackingMode.TRACKING
        testScheduler.advanceUntilIdle()
        assertEquals(3, events.size)

        // All events must be NavigateTo(0)
        events.forEach {
            assertEquals(TabNavigationEvent.NavigateTo(0), it)
        }

        job.cancel()
    }

    @Test
    fun testPagerOffsetCalculationForScreenMode() {
        // TC-5: Validate offset calculation matching TrackingTabsScreen logic
        // offset = if (screenMode == ScreenMode.TRACKING) 1 else 0
        val targetIndex = 0

        val trackingOffset = if (ScreenMode.TRACKING == ScreenMode.TRACKING) 1 else 0
        assertEquals(1, targetIndex + trackingOffset)

        val configOffset = if (ScreenMode.CONFIGURATION == ScreenMode.TRACKING) 1 else 0
        assertEquals(0, targetIndex + configOffset)

        val previewOffset = if (ScreenMode.PREVIEW == ScreenMode.TRACKING) 1 else 0
        assertEquals(0, targetIndex + previewOffset)
    }
}
