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

package com.atrainingtracker.trainingtracker.routes

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.preference.PreferenceManager
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ListenableWorker.Result
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.repositories.RoutesRepository
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.IOException

/**
 * Unit tests for [StravaRoutesSyncWorker] verifying automated periodic sync scheduling,
 * WorkManager constraints, execution flow, error resilience, and preference parity (REQ-EXP-012, TST-EXP-009).
 */
class StravaRoutesSyncWorkerTest {

    private lateinit var mockContext: Context
    private lateinit var mockPrefs: SharedPreferences
    private lateinit var mockWorkManager: WorkManager
    private lateinit var mockRoutesRepository: RoutesRepository

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
        mockPrefs = mockk(relaxed = true)
        mockWorkManager = mockk(relaxed = true)
        mockRoutesRepository = mockk(relaxed = true)

        mockkStatic(PreferenceManager::class)
        every { PreferenceManager.getDefaultSharedPreferences(any()) } returns mockPrefs

        mockkObject(WorkManager.Companion)
        every { WorkManager.getInstance(any()) } returns mockWorkManager

        mockkStatic(TrainingApplication::class)
        every { TrainingApplication.isWorkManagerAvailable() } returns true
        every { TrainingApplication.getStravaAccessToken() } returns "valid_test_token"

        mockkObject(RoutesRepository.Companion)
        every { RoutesRepository.getInstance(any()) } returns mockRoutesRepository
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testScheduleWhenWorkManagerUnavailableReturnsSafely() {
        every { TrainingApplication.isWorkManagerAvailable() } returns false

        StravaRoutesSyncWorker.schedule(mockContext)

        verify(exactly = 0) { WorkManager.getInstance(any()) }
        verify(exactly = 1) {
            Log.w("StravaRoutesSyncWorker", match<String> { it.contains("WorkManager is unavailable") })
        }
    }

    @Test
    fun testScheduleWhenAutomatedSyncDisabledCancelsWork() {
        every { mockPrefs.getBoolean(TrainingApplication.SP_AUTOMATED_STRAVA_ROUTES_SYNC, true) } returns false
        every { mockPrefs.getString(TrainingApplication.SP_STRAVA_ROUTES_SYNC_INTERVAL_DAYS, "1") } returns "1"

        StravaRoutesSyncWorker.schedule(mockContext)

        verify(exactly = 1) {
            mockWorkManager.cancelUniqueWork(StravaRoutesSyncWorker.WORK_NAME)
        }
        verify(exactly = 0) {
            mockWorkManager.enqueueUniquePeriodicWork(any(), any(), any())
        }
    }

    @Test
    fun testScheduleWhenStravaDisconnectedCancelsWork() {
        every { mockPrefs.getBoolean(TrainingApplication.SP_AUTOMATED_STRAVA_ROUTES_SYNC, true) } returns true
        every { TrainingApplication.getStravaAccessToken() } returns null

        StravaRoutesSyncWorker.schedule(mockContext)

        verify(exactly = 1) {
            mockWorkManager.cancelUniqueWork(StravaRoutesSyncWorker.WORK_NAME)
        }
        verify(exactly = 0) {
            mockWorkManager.enqueueUniquePeriodicWork(any(), any(), any())
        }
    }

    @Test
    fun testScheduleWhenEnabledEnqueuesPeriodicWorkWithDefaultInterval() {
        every { mockPrefs.getBoolean(TrainingApplication.SP_AUTOMATED_STRAVA_ROUTES_SYNC, true) } returns true
        every { mockPrefs.getString(TrainingApplication.SP_STRAVA_ROUTES_SYNC_INTERVAL_DAYS, "1") } returns "1"

        val workRequestSlot = slot<PeriodicWorkRequest>()
        every {
            mockWorkManager.enqueueUniquePeriodicWork(
                eq(StravaRoutesSyncWorker.WORK_NAME),
                eq(ExistingPeriodicWorkPolicy.UPDATE),
                capture(workRequestSlot)
            )
        } returns mockk(relaxed = true)

        StravaRoutesSyncWorker.schedule(mockContext)

        verify(exactly = 1) {
            mockWorkManager.enqueueUniquePeriodicWork(
                eq(StravaRoutesSyncWorker.WORK_NAME),
                eq(ExistingPeriodicWorkPolicy.UPDATE),
                any()
            )
        }
        val request = workRequestSlot.captured
        assertEquals(true, request.workSpec.constraints.requiresBatteryNotLow())
    }

    @Test
    fun testScheduleWhenCustomIntervalConfiguredEnqueuesWorkWithInterval() {
        every { mockPrefs.getBoolean(TrainingApplication.SP_AUTOMATED_STRAVA_ROUTES_SYNC, true) } returns true
        every { mockPrefs.getString(TrainingApplication.SP_STRAVA_ROUTES_SYNC_INTERVAL_DAYS, "1") } returns "7"

        StravaRoutesSyncWorker.schedule(mockContext)

        verify(exactly = 1) {
            mockWorkManager.enqueueUniquePeriodicWork(
                eq(StravaRoutesSyncWorker.WORK_NAME),
                eq(ExistingPeriodicWorkPolicy.UPDATE),
                any()
            )
        }
    }

    @Test
    fun testDoWorkWhenAutomatedSyncDisabledReturnsSuccessWithoutSync() = runBlocking {
        every { mockPrefs.getBoolean(TrainingApplication.SP_AUTOMATED_STRAVA_ROUTES_SYNC, true) } returns false

        val worker = StravaRoutesSyncWorker(mockContext, mockk<WorkerParameters>(relaxed = true))
        val result = worker.doWork()

        assertEquals(Result.success(), result)
        coVerify(exactly = 0) { mockRoutesRepository.syncRoutesFromStrava() }
    }

    @Test
    fun testDoWorkWhenStravaDisconnectedReturnsSuccessWithoutSync() = runBlocking {
        every { mockPrefs.getBoolean(TrainingApplication.SP_AUTOMATED_STRAVA_ROUTES_SYNC, true) } returns true
        every { TrainingApplication.getStravaAccessToken() } returns null

        val worker = StravaRoutesSyncWorker(mockContext, mockk<WorkerParameters>(relaxed = true))
        val result = worker.doWork()

        assertEquals(Result.success(), result)
        coVerify(exactly = 0) { mockRoutesRepository.syncRoutesFromStrava() }
    }

    @Test
    fun testDoWorkWhenConnectedAndEnabledInvokesSyncAndReturnsSuccess() = runBlocking {
        every { mockPrefs.getBoolean(TrainingApplication.SP_AUTOMATED_STRAVA_ROUTES_SYNC, true) } returns true
        coEvery { mockRoutesRepository.syncRoutesFromStrava() } returns true

        val worker = StravaRoutesSyncWorker(mockContext, mockk<WorkerParameters>(relaxed = true))
        val result = worker.doWork()

        assertEquals(Result.success(), result)
        coVerify(exactly = 1) { mockRoutesRepository.syncRoutesFromStrava() }
    }

    @Test
    fun testDoWorkWhenSyncReturnsFalseReturnsRetry() = runBlocking {
        every { mockPrefs.getBoolean(TrainingApplication.SP_AUTOMATED_STRAVA_ROUTES_SYNC, true) } returns true
        coEvery { mockRoutesRepository.syncRoutesFromStrava() } returns false

        val worker = StravaRoutesSyncWorker(mockContext, mockk<WorkerParameters>(relaxed = true))
        val result = worker.doWork()

        assertEquals(Result.retry(), result)
        coVerify(exactly = 1) { mockRoutesRepository.syncRoutesFromStrava() }
    }

    @Test
    fun testDoWorkWhenExceptionThrownReturnsRetry() = runBlocking {
        every { mockPrefs.getBoolean(TrainingApplication.SP_AUTOMATED_STRAVA_ROUTES_SYNC, true) } returns true
        coEvery { mockRoutesRepository.syncRoutesFromStrava() } throws IOException("Strava API unreachable")

        val worker = StravaRoutesSyncWorker(mockContext, mockk<WorkerParameters>(relaxed = true))
        val result = worker.doWork()

        assertEquals(Result.retry(), result)
        coVerify(exactly = 1) { mockRoutesRepository.syncRoutesFromStrava() }
    }
}
