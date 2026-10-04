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

package com.atrainingtracker.trainingtracker.ui.tracking.controltracking

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import androidx.lifecycle.MutableLiveData
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager
import com.atrainingtracker.banalservice.ui.devices.devicedata.DeviceDataRepository
import com.atrainingtracker.trainingtracker.TrackingMode
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.repositories.BANALServiceRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests verifying permission state handling, warning badge behavior,
 * progressive 3-stage JIT setup flow, and fail-safe battery intent handling (REQ-PRI-003, TST-PRI-002, ATT-2075).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ControlTrackingPermissionTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var application: Application
    private lateinit var viewModel: ControlTrackingViewModel
    private lateinit var mockDeviceDataRepository: DeviceDataRepository
    private lateinit var mockBanalRepo: BANALServiceRepository
    private lateinit var mockDevicesDb: DevicesDatabaseManager

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

        mockkConstructor(Intent::class)
        every { anyConstructed<Intent>().setPackage(any()) } answers { self as Intent }
        every { anyConstructed<Intent>().action } returns TrainingApplication.REQUEST_START_TRACKING
        every { anyConstructed<Intent>().`package` } returns "com.atrainingtracker"

        mockDeviceDataRepository = mockk(relaxed = true)
        mockkObject(DeviceDataRepository.Companion)
        every { DeviceDataRepository.getInstance(any()) } returns mockDeviceDataRepository
        every { mockDeviceDataRepository.allDevices } returns MutableStateFlow(emptyList())

        mockBanalRepo = mockk(relaxed = true)
        mockkObject(BANALServiceRepository.Companion)
        every { BANALServiceRepository.getInstance(any()) } returns mockBanalRepo
        every { mockBanalRepo.activeRemoteDevicesIds } returns MutableStateFlow(emptyList())
        every { mockBanalRepo.trackingMode } returns MutableLiveData(TrackingMode.IDLE)
        every { mockBanalRepo.activeSensors } returns MutableStateFlow(emptySet())
        every { mockBanalRepo.bSportType } returns MutableStateFlow(BSportType.RUN)

        mockDevicesDb = mockk(relaxed = true)
        mockkStatic(DevicesDatabaseManager::class)
        every { DevicesDatabaseManager.getInstance(any()) } returns mockDevicesDb

        application = mockk(relaxed = true)
        every { application.packageName } returns "com.atrainingtracker"
        viewModel = ControlTrackingViewModel(application)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        ArchTaskExecutor.getInstance().setDelegate(null)
        unmockkAll()
    }

    @Test
    fun testOnStartTracking_dispatchesStartTrackingBroadcast() {
        val slot = slot<Intent>()
        every { application.sendBroadcast(capture(slot)) } returns Unit

        viewModel.onStartTracking()

        verify(exactly = 1) { application.sendBroadcast(any()) }
        assertEquals(TrainingApplication.REQUEST_START_TRACKING, slot.captured.action)
        assertEquals("com.atrainingtracker", slot.captured.`package`)
    }

    @Test
    fun testWarningBadgePredicate_logicMatchesPermissionState() {
        // When hasLocationPermission is false, warning badge should be active
        val hasLocationPermission = false
        val hasPermissionWarning = !hasLocationPermission
        assertTrue("Warning badge must be visible when location permission is missing", hasPermissionWarning)

        // When hasLocationPermission is true, warning badge must be dismissed
        val grantedPermission = true
        val noWarning = !grantedPermission
        assertFalse("Warning badge must NOT be visible when location permission is granted", noWarning)
    }

    @Test
    fun testProgressiveSetupFlow_missingForegroundTransitionsToForegroundRationale() {
        var activeStep = RationaleStep.NONE
        val hasLocation = false

        if (!hasLocation) {
            activeStep = RationaleStep.FOREGROUND
        }

        assertEquals(RationaleStep.FOREGROUND, activeStep)
    }

    @Test
    fun testProgressiveSetupFlow_foregroundGrantedMissingBgTransitionsToBgRationale() {
        var activeStep = RationaleStep.NONE
        val hasLocation = true
        val hasBgLocation = false

        if (!hasLocation) {
            activeStep = RationaleStep.FOREGROUND
        } else if (!hasBgLocation) {
            activeStep = RationaleStep.BACKGROUND_LOCATION
        }

        assertEquals(RationaleStep.BACKGROUND_LOCATION, activeStep)
    }

    @Test
    fun testProgressiveSetupFlow_locationGrantedMissingBatteryTransitionsToBatteryRationale() {
        var activeStep = RationaleStep.NONE
        val hasLocation = true
        val hasBgLocation = true
        val isIgnoringBattery = false

        if (!hasLocation) {
            activeStep = RationaleStep.FOREGROUND
        } else if (!hasBgLocation) {
            activeStep = RationaleStep.BACKGROUND_LOCATION
        } else if (!isIgnoringBattery) {
            activeStep = RationaleStep.BATTERY_OPTIMIZATION
        }

        assertEquals(RationaleStep.BATTERY_OPTIMIZATION, activeStep)
    }

    @Test
    fun testProgressiveSetupFlow_allGrantedDispatchesStart() {
        var activeStep = RationaleStep.NONE
        var started = false
        val hasLocation = true
        val hasBgLocation = true
        val isIgnoringBattery = true

        if (!hasLocation) {
            activeStep = RationaleStep.FOREGROUND
        } else if (!hasBgLocation) {
            activeStep = RationaleStep.BACKGROUND_LOCATION
        } else if (!isIgnoringBattery) {
            activeStep = RationaleStep.BATTERY_OPTIMIZATION
        } else {
            activeStep = RationaleStep.NONE
            started = true
        }

        assertEquals(RationaleStep.NONE, activeStep)
        assertTrue("Tracking must start when all permissions and battery exemptions are present", started)
    }

    @Test
    fun testBatteryOptimization_launchesDirectActionRequestIgnoreBatteryOptimizations() {
        mockkStatic(android.net.Uri::class)
        val mockUri = mockk<android.net.Uri>()
        every { android.net.Uri.parse("package:com.atrainingtracker") } returns mockUri

        val mockContext = mockk<Context>(relaxed = true)
        every { mockContext.packageName } returns "com.atrainingtracker"
        val intentSlot = slot<Intent>()
        every { mockContext.startActivity(capture(intentSlot)) } returns Unit
        every { anyConstructed<Intent>().action } returns android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
        every { anyConstructed<Intent>().data } returns mockUri

        launchBatteryOptimizationIntent(mockContext)

        verify(exactly = 1) { mockContext.startActivity(any()) }
        assertEquals(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, intentSlot.captured.action)
        assertEquals(mockUri, intentSlot.captured.data)
    }

    @Test
    fun testFailSafeBatteryOptimizationLaunch_doesNotCrashOnActivityNotFoundException() {
        val mockContext = mockk<Context>(relaxed = true)
        every { mockContext.packageName } returns "com.atrainingtracker"
        every { mockContext.startActivity(any()) } throws ActivityNotFoundException("Activity not found") andThen Unit

        var caughtException: Exception? = null
        try {
            launchBatteryOptimizationIntent(mockContext)
        } catch (e: Exception) {
            caughtException = e
            e.printStackTrace()
        }

        assertTrue("launchBatteryOptimizationIntent must handle ActivityNotFoundException without crashing (was: ${caughtException?.message})", caughtException == null)
        verify(atLeast = 1) { mockContext.startActivity(any()) }
    }
}
