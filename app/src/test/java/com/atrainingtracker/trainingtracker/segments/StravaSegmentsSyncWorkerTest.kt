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

package com.atrainingtracker.trainingtracker.segments

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.preference.PreferenceManager
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ListenableWorker.Result
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.TrainingApplication
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.IOException

/**
 * Unit tests for [StravaSegmentsSyncWorker] verifying automated periodic sync scheduling,
 * WorkManager constraints, execution flow, error resilience, and preference parity (REQ-EXP-011, TST-EXP-008).
 */
class StravaSegmentsSyncWorkerTest {

    private lateinit var mockContext: Context
    private lateinit var mockPrefs: SharedPreferences
    private lateinit var mockWorkManager: WorkManager
    private lateinit var mockSegmentsRepository: SegmentsRepository

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
        mockSegmentsRepository = mockk(relaxed = true)

        mockkStatic(PreferenceManager::class)
        every { PreferenceManager.getDefaultSharedPreferences(any()) } returns mockPrefs

        mockkObject(WorkManager.Companion)
        every { WorkManager.getInstance(any()) } returns mockWorkManager

        mockkStatic(TrainingApplication::class)
        every { TrainingApplication.isWorkManagerAvailable() } returns true
        every { TrainingApplication.getStravaAccessToken() } returns "valid_test_token"
        every { TrainingApplication.setLastUpdateTimeOfStravaSegments(any()) } just Runs

        mockkObject(SegmentsRepository.Companion)
        every { SegmentsRepository.getInstance(any()) } returns mockSegmentsRepository
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testScheduleWhenWorkManagerUnavailableReturnsSafely() {
        every { TrainingApplication.isWorkManagerAvailable() } returns false

        StravaSegmentsSyncWorker.schedule(mockContext)

        verify(exactly = 0) { WorkManager.getInstance(any()) }
        verify(exactly = 1) {
            Log.w("StravaSegmentsSyncWork", match<String> { it.contains("WorkManager is unavailable") })
        }
    }

    @Test
    fun testScheduleWhenStravaDisconnectedCancelsWork() {
        every { TrainingApplication.getStravaAccessToken() } returns null

        StravaSegmentsSyncWorker.schedule(mockContext)

        verify(exactly = 1) {
            mockWorkManager.cancelUniqueWork(StravaSegmentsSyncWorker.WORK_NAME)
        }
        verify(exactly = 0) {
            mockWorkManager.enqueueUniquePeriodicWork(any(), any(), any())
        }
    }

    @Test
    fun testScheduleWhenConnectedEnqueuesPeriodicWorkWith1DayInterval() {
        every { TrainingApplication.getStravaAccessToken() } returns "valid_token"

        StravaSegmentsSyncWorker.schedule(mockContext)

        val workRequestSlot = slot<PeriodicWorkRequest>()
        verify(exactly = 1) {
            mockWorkManager.enqueueUniquePeriodicWork(
                eq(StravaSegmentsSyncWorker.WORK_NAME),
                eq(ExistingPeriodicWorkPolicy.UPDATE),
                capture(workRequestSlot)
            )
        }

        val capturedWorkSpec = workRequestSlot.captured.workSpec
        assertEquals(true, capturedWorkSpec.constraints.requiresBatteryNotLow())
        assertEquals(androidx.work.NetworkType.CONNECTED, capturedWorkSpec.constraints.requiredNetworkType)
        assertEquals(1000L * 60 * 60 * 24, capturedWorkSpec.intervalDuration)
    }

    @Test
    fun testScheduleWhenWorkManagerThrowsLogsErrorGracefully() {
        every { TrainingApplication.getStravaAccessToken() } returns "valid_token"
        every { WorkManager.getInstance(any()) } throws IllegalStateException("WorkManager not initialized")

        StravaSegmentsSyncWorker.schedule(mockContext)

        verify(atLeast = 1) {
            Log.e("StravaSegmentsSyncWork", match<String> { it.contains("Failed to schedule") }, any<Throwable>())
        }
    }

    @Test
    fun testDoWorkWhenStravaDisconnectedPrunesTTLAndReturnsSuccessWithoutSyncing() = runBlocking {
        every { TrainingApplication.getStravaAccessToken() } returns null

        val workerParams = mockk<WorkerParameters>(relaxed = true)
        val worker = StravaSegmentsSyncWorker(mockContext, workerParams)

        val result = worker.doWork()

        assertEquals(Result.success(), result)
        verify(exactly = 1) { mockSegmentsRepository.pruneExpiredSegments() }
        coVerify(exactly = 0) { mockSegmentsRepository.syncStarredSegments(any()) }
        verify(exactly = 0) { TrainingApplication.setLastUpdateTimeOfStravaSegments(any()) }
    }

    @Test
    fun testDoWorkWhenConnectedSyncsSegmentsAndUpdatesTimestamp() = runBlocking {
        every { TrainingApplication.getStravaAccessToken() } returns "valid_token"
        coEvery { mockSegmentsRepository.syncStarredSegments(BSportType.UNKNOWN) } just Runs

        val workerParams = mockk<WorkerParameters>(relaxed = true)
        val worker = StravaSegmentsSyncWorker(mockContext, workerParams)

        val result = worker.doWork()

        assertEquals(Result.success(), result)
        verify(exactly = 1) { mockSegmentsRepository.pruneExpiredSegments() }
        coVerify(exactly = 1) { mockSegmentsRepository.syncStarredSegments(BSportType.UNKNOWN) }
        verify(atLeast = 1) { TrainingApplication.setLastUpdateTimeOfStravaSegments(any()) }
    }

    @Test
    fun testDoWorkWhenSyncThrowsReturnsRetry() = runBlocking {
        every { TrainingApplication.getStravaAccessToken() } returns "valid_token"
        coEvery { mockSegmentsRepository.syncStarredSegments(BSportType.UNKNOWN) } throws IOException("Network timeout")

        val workerParams = mockk<WorkerParameters>(relaxed = true)
        val worker = StravaSegmentsSyncWorker(mockContext, workerParams)

        val result = worker.doWork()

        assertEquals(Result.retry(), result)
        verify(exactly = 1) { mockSegmentsRepository.pruneExpiredSegments() }
        coVerify(exactly = 1) { mockSegmentsRepository.syncStarredSegments(BSportType.UNKNOWN) }
    }
}
