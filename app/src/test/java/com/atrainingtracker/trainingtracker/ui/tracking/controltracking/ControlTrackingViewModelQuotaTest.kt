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
import android.content.Intent
import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager
import com.atrainingtracker.banalservice.ui.devices.devicedata.DeviceDataRepository
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.monetization.SensorQuotaReconciliationManager
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
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Unit and contract tests for pre-tracking quota interception in [ControlTrackingViewModel]
 * (REQ-SET-084.5, TST-SET-084.11).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ControlTrackingViewModelQuotaTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var application: Application
    private lateinit var mockDeviceDataRepository: DeviceDataRepository
    private lateinit var mockBanalRepo: BANALServiceRepository
    private lateinit var mockDevicesDb: DevicesDatabaseManager
    private lateinit var mockWorkoutSummariesDb: WorkoutSummariesDatabaseManager
    private lateinit var mockReconciliationManager: SensorQuotaReconciliationManager

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        ArchTaskExecutor.getInstance().setDelegate(object : TaskExecutor() {
            override fun executeOnDiskIO(runnable: Runnable) = runnable.run()
            override fun postToMainThread(runnable: Runnable) = runnable.run()
            override fun isMainThread(): Boolean = true
        })

        mockDeviceDataRepository = mockk(relaxed = true)
        mockkObject(DeviceDataRepository.Companion)
        every { DeviceDataRepository.getInstance(any()) } returns mockDeviceDataRepository
        every { mockDeviceDataRepository.allDevices } returns MutableStateFlow(emptyList())

        mockBanalRepo = mockk(relaxed = true)
        mockkObject(BANALServiceRepository.Companion)
        every { BANALServiceRepository.getInstance(any()) } returns mockBanalRepo
        every { mockBanalRepo.activeRemoteDevicesIds } returns MutableStateFlow(emptyList())

        mockDevicesDb = mockk(relaxed = true)
        mockkStatic(DevicesDatabaseManager::class)
        every { DevicesDatabaseManager.getInstance(any()) } returns mockDevicesDb
        every { mockDevicesDb.hasPairedRemoteDevices() } returns false

        mockWorkoutSummariesDb = mockk(relaxed = true)
        mockkStatic(WorkoutSummariesDatabaseManager::class)
        every { WorkoutSummariesDatabaseManager.getInstance(any()) } returns mockWorkoutSummariesDb
        every { mockWorkoutSummariesDb.hasUnfinishedWorkout() } returns false

        mockReconciliationManager = mockk(relaxed = true)
        mockkObject(SensorQuotaReconciliationManager.Companion)
        every { SensorQuotaReconciliationManager.getInstance(any()) } returns mockReconciliationManager

        mockkConstructor(Intent::class)
        every { anyConstructed<Intent>().setPackage(any()) } answers { self as Intent }
        every { anyConstructed<Intent>().action } returns TrainingApplication.REQUEST_START_TRACKING
        every { anyConstructed<Intent>().`package` } returns "com.atrainingtracker"

        mockkStatic(TrainingApplication::class)
        every { TrainingApplication.isTracking() } returns false

        application = mockk(relaxed = true)
        every { application.packageName } returns "com.atrainingtracker"
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        ArchTaskExecutor.getInstance().setDelegate(null)
        unmockkAll()
        SensorQuotaReconciliationManager.setInstanceForTesting(null)
    }

    @Test
    fun testOnStartTracking_suppressesBroadcast_whenReconciliationTriggered() = runTest {
        every { mockReconciliationManager.checkDowngradeReconciliation() } returns true

        val viewModel = ControlTrackingViewModel(application, mockReconciliationManager)
        viewModel.onStartTracking()

        verify(exactly = 0) { application.sendBroadcast(any()) }
    }

    @Test
    fun testOnStartTracking_sendsBroadcast_whenReconciliationNotRequired() = runTest {
        every { mockReconciliationManager.checkDowngradeReconciliation() } returns false

        val viewModel = ControlTrackingViewModel(application, mockReconciliationManager)
        viewModel.onStartTracking()

        val slot = slot<Intent>()
        verify(exactly = 1) { application.sendBroadcast(capture(slot)) }
        org.junit.Assert.assertEquals(TrainingApplication.REQUEST_START_TRACKING, slot.captured.action)
    }

    @Test
    fun testOnSearchClicked_suppressesSearch_whenReconciliationTriggered() = runTest {
        every { mockReconciliationManager.checkDowngradeReconciliation() } returns true

        val viewModel = ControlTrackingViewModel(application, mockReconciliationManager)
        viewModel.onSearchClicked()

        verify(exactly = 0) { mockBanalRepo.startSearchingForPairedDevices() }
    }

    @Test
    fun testOnSearchClicked_startsSearch_whenReconciliationNotRequired() = runTest {
        every { mockReconciliationManager.checkDowngradeReconciliation() } returns false

        val viewModel = ControlTrackingViewModel(application, mockReconciliationManager)
        viewModel.onSearchClicked()

        verify(exactly = 1) { mockBanalRepo.startSearchingForPairedDevices() }
    }
}
