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

package com.atrainingtracker.trainingtracker.onlinecommunities.strava

import android.content.Context
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ListenableWorker.Result
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.atrainingtracker.trainingtracker.TrainingApplication
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [StravaEquipmentSyncWorker] verifying automated periodic sync scheduling,
 * WorkManager constraints, execution flow, error resilience, and preference parity (REQ-EXP-019, TST-EXP-016).
 */
class StravaEquipmentSyncWorkerTest {

    private lateinit var mockContext: Context
    private lateinit var mockWorkManager: WorkManager

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.v(any<String>(), any<String>()) } returns 0
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0

        mockContext = mockk(relaxed = true)
        every { mockContext.applicationContext } returns mockContext
        mockWorkManager = mockk(relaxed = true)

        mockkObject(WorkManager.Companion)
        every { WorkManager.getInstance(any()) } returns mockWorkManager

        mockkStatic(TrainingApplication::class)
        every { TrainingApplication.isWorkManagerAvailable() } returns true
        every { TrainingApplication.getStravaAccessToken() } returns "valid_test_token"
        every { TrainingApplication.isAutomatedStravaEquipmentSyncEnabled() } returns true
        every { TrainingApplication.getStravaEquipmentSyncIntervalDays() } returns "1"
        every { TrainingApplication.getDebug(any()) } returns false
        every { TrainingApplication.setLastUpdateTimeOfStravaEquipment(any()) } just Runs

        mockkStatic(StravaHelper::class)
        every { StravaHelper.getRefreshedAccessToken() } returns "valid_refreshed_token"

        Class.forName(StravaEquipmentSynchronizeThread::class.java.name)
        mockkConstructor(StravaEquipmentSynchronizeThread::class)
        every { anyConstructed<StravaEquipmentSynchronizeThread>().run() } just Runs
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testScheduleWhenWorkManagerUnavailableReturnsSafely() {
        every { TrainingApplication.isWorkManagerAvailable() } returns false

        StravaEquipmentSyncWorker.schedule(mockContext)

        verify(exactly = 0) { WorkManager.getInstance(any()) }
        verify(exactly = 1) {
            Log.w("StravaEquipmentSyncWork", match<String> { it.contains("WorkManager is unavailable") })
        }
    }

    @Test
    fun testScheduleWhenStravaDisconnectedCancelsWork() {
        every { TrainingApplication.getStravaAccessToken() } returns null

        StravaEquipmentSyncWorker.schedule(mockContext)

        verify(exactly = 1) {
            mockWorkManager.cancelUniqueWork(StravaEquipmentSyncWorker.WORK_NAME)
        }
        verify(exactly = 0) {
            mockWorkManager.enqueueUniquePeriodicWork(any(), any(), any())
        }
    }

    @Test
    fun testScheduleWhenAutomatedSyncDisabledCancelsWork() {
        every { TrainingApplication.getStravaAccessToken() } returns "valid_token"
        every { TrainingApplication.isAutomatedStravaEquipmentSyncEnabled() } returns false

        StravaEquipmentSyncWorker.schedule(mockContext)

        verify(exactly = 1) {
            mockWorkManager.cancelUniqueWork(StravaEquipmentSyncWorker.WORK_NAME)
        }
        verify(exactly = 0) {
            mockWorkManager.enqueueUniquePeriodicWork(any(), any(), any())
        }
    }

    @Test
    fun testScheduleWhenConnectedEnqueuesPeriodicWorkWith1DayInterval() {
        every { TrainingApplication.getStravaAccessToken() } returns "valid_test_token"

        val workRequestSlot = slot<PeriodicWorkRequest>()
        every {
            mockWorkManager.enqueueUniquePeriodicWork(
                eq(StravaEquipmentSyncWorker.WORK_NAME),
                eq(ExistingPeriodicWorkPolicy.UPDATE),
                capture(workRequestSlot)
            )
        } returns mockk(relaxed = true)

        StravaEquipmentSyncWorker.schedule(mockContext)

        verify(exactly = 1) {
            mockWorkManager.enqueueUniquePeriodicWork(
                eq(StravaEquipmentSyncWorker.WORK_NAME),
                eq(ExistingPeriodicWorkPolicy.UPDATE),
                any()
            )
        }
        val request = workRequestSlot.captured
        assertEquals(true, request.workSpec.constraints.requiresBatteryNotLow())
        assertEquals(androidx.work.NetworkType.CONNECTED, request.workSpec.constraints.requiredNetworkType)
        assertEquals(1000L * 60 * 60 * 24, request.workSpec.intervalDuration)
    }

    @Test
    fun testScheduleRespectsCustomIntervalDays() {
        every { TrainingApplication.getStravaAccessToken() } returns "valid_test_token"
        every { TrainingApplication.isAutomatedStravaEquipmentSyncEnabled() } returns true
        every { TrainingApplication.getStravaEquipmentSyncIntervalDays() } returns "5"

        val workRequestSlot = slot<PeriodicWorkRequest>()
        every {
            mockWorkManager.enqueueUniquePeriodicWork(
                eq(StravaEquipmentSyncWorker.WORK_NAME),
                eq(ExistingPeriodicWorkPolicy.UPDATE),
                capture(workRequestSlot)
            )
        } returns mockk(relaxed = true)

        StravaEquipmentSyncWorker.schedule(mockContext)

        val request = workRequestSlot.captured
        assertEquals(1000L * 60 * 60 * 24 * 5, request.workSpec.intervalDuration)
    }

    @Test
    fun testDoWorkWhenStravaDisconnectedReturnsSuccessWithoutSync() = runBlocking {
        every { TrainingApplication.getStravaAccessToken() } returns null

        val worker = StravaEquipmentSyncWorker(mockContext, mockk<WorkerParameters>(relaxed = true))
        val result = worker.doWork()

        assertEquals(Result.success(), result)
    }

    @Test
    fun testDoWorkWhenAutomatedSyncDisabledReturnsSuccessWithoutSync() = runBlocking {
        every { TrainingApplication.getStravaAccessToken() } returns "valid_test_token"
        every { TrainingApplication.isAutomatedStravaEquipmentSyncEnabled() } returns false

        val worker = StravaEquipmentSyncWorker(mockContext, mockk<WorkerParameters>(relaxed = true))
        val result = worker.doWork()

        assertEquals(Result.success(), result)
    }

    @Test
    fun testDoWorkWhenTokenRefreshReturnsNullReturnsSuccessWithoutSync() = runBlocking {
        every { TrainingApplication.getStravaAccessToken() } returns "valid_test_token"
        every { TrainingApplication.isAutomatedStravaEquipmentSyncEnabled() } returns true
        every { StravaHelper.getRefreshedAccessToken() } returns null

        val worker = StravaEquipmentSyncWorker(mockContext, mockk<WorkerParameters>(relaxed = true))
        val result = worker.doWork()

        assertEquals(Result.success(), result)
    }

    @Test
    fun testDoWorkWhenConnectedRunsSyncThreadAndReturnsSuccess() = runBlocking {
        every { TrainingApplication.getStravaAccessToken() } returns "valid_test_token"
        every { TrainingApplication.getLastUpdateTimeOfStravaEquipment() } returns "14.10.2026 14:00:00"

        val worker = StravaEquipmentSyncWorker(mockContext, mockk<WorkerParameters>(relaxed = true))
        val result = worker.doWork()

        assertEquals(Result.success(), result)
        verify(exactly = 1) { anyConstructed<StravaEquipmentSynchronizeThread>().run() }
    }

    @Test
    fun testDoWorkWhenSyncFailsReturnsRetry() = runBlocking {
        every { TrainingApplication.getStravaAccessToken() } returns "valid_test_token"
        every { TrainingApplication.getLastUpdateTimeOfStravaEquipment() } returns "updating failed"

        val worker = StravaEquipmentSyncWorker(mockContext, mockk<WorkerParameters>(relaxed = true))
        val result = worker.doWork()

        assertEquals(Result.retry(), result)
        verify(exactly = 1) { anyConstructed<StravaEquipmentSynchronizeThread>().run() }
    }

    @Test
    fun testDoWorkWhenExceptionThrownReturnsRetry() = runBlocking {
        every { TrainingApplication.getStravaAccessToken() } returns "valid_test_token"
        every { anyConstructed<StravaEquipmentSynchronizeThread>().run() } throws RuntimeException("Network crash")

        val worker = StravaEquipmentSyncWorker(mockContext, mockk<WorkerParameters>(relaxed = true))
        val result = worker.doWork()

        assertEquals(Result.retry(), result)
    }
}
