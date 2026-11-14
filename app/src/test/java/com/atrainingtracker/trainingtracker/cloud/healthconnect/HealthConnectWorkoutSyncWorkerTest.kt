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

package com.atrainingtracker.trainingtracker.cloud.healthconnect

import android.content.Context
import android.util.Log
import androidx.work.Data
import androidx.work.ListenableWorker.Result
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [HealthConnectWorkoutSyncWorker] verifying enqueue dispatch,
 * workout data resolution, and synchronization result handling (REQ-MIG-052.4, TST-MIG-050.3).
 */
class HealthConnectWorkoutSyncWorkerTest {

    private lateinit var mockContext: Context
    private lateinit var mockWorkManager: WorkManager
    private lateinit var mockWorkoutRepo: WorkoutRepository

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

        mockWorkoutRepo = mockk(relaxed = true)
        mockkObject(WorkoutRepository.Companion)
        every { WorkoutRepository.getInstance(any<Context>()) } returns mockWorkoutRepo

        mockkObject(HealthConnectManager)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testEnqueue_validWorkoutId_enqueuesOneTimeWorkRequest() {
        val requestSlot = slot<OneTimeWorkRequest>()
        every { mockWorkManager.enqueue(capture(requestSlot)) } returns mockk(relaxed = true)

        HealthConnectWorkoutSyncWorker.enqueue(mockContext, 42L)

        verify(exactly = 1) { mockWorkManager.enqueue(any<OneTimeWorkRequest>()) }
        val captured = requestSlot.captured
        assertEquals(42L, captured.workSpec.input.getLong(HealthConnectWorkoutSyncWorker.KEY_WORKOUT_ID, -1L))
        assertEquals(true, captured.tags.contains(HealthConnectWorkoutSyncWorker.TAG))
    }

    @Test
    fun testEnqueue_invalidWorkoutId_skipsEnqueue() {
        HealthConnectWorkoutSyncWorker.enqueue(mockContext, -1L)
        verify(exactly = 0) { mockWorkManager.enqueue(any<OneTimeWorkRequest>()) }
    }

    @Test
    fun testEnqueue_whenWorkManagerUnavailable_skipsEnqueue() {
        every { TrainingApplication.isWorkManagerAvailable() } returns false
        HealthConnectWorkoutSyncWorker.enqueue(mockContext, 42L)
        verify(exactly = 0) { mockWorkManager.enqueue(any<OneTimeWorkRequest>()) }
    }

    @Test
    fun testDoWork_invalidWorkoutId_returnsFailure() = runBlocking {
        val workerParams = mockk<WorkerParameters>(relaxed = true)
        every { workerParams.inputData } returns Data.Builder().putLong(HealthConnectWorkoutSyncWorker.KEY_WORKOUT_ID, -1L).build()

        val worker = HealthConnectWorkoutSyncWorker(mockContext, workerParams)
        val result = worker.doWork()

        assertEquals(Result.failure(), result)
    }

    @Test
    fun testDoWork_workoutNotFound_returnsFailure() = runBlocking {
        val workerParams = mockk<WorkerParameters>(relaxed = true)
        every { workerParams.inputData } returns Data.Builder().putLong(HealthConnectWorkoutSyncWorker.KEY_WORKOUT_ID, 100L).build()
        every { mockWorkoutRepo.getWorkoutDataDirect(100L) } returns null

        val worker = HealthConnectWorkoutSyncWorker(mockContext, workerParams)
        val result = worker.doWork()

        assertEquals(Result.failure(), result)
    }

    @Test
    fun testDoWork_syncSuccess_returnsSuccess() = runBlocking {
        val workerParams = mockk<WorkerParameters>(relaxed = true)
        every { workerParams.inputData } returns Data.Builder().putLong(HealthConnectWorkoutSyncWorker.KEY_WORKOUT_ID, 200L).build()

        val mockWorkoutData = mockk<WorkoutData>(relaxed = true)
        every { mockWorkoutRepo.getWorkoutDataDirect(200L) } returns mockWorkoutData
        coEvery { HealthConnectManager.syncWorkout(mockContext, mockWorkoutData, force = true) } returns kotlin.Result.success(Unit)

        val worker = HealthConnectWorkoutSyncWorker(mockContext, workerParams)
        val result = worker.doWork()

        assertEquals(Result.success(), result)
        coVerify(exactly = 1) { HealthConnectManager.syncWorkout(mockContext, mockWorkoutData, force = true) }
    }

    @Test
    fun testDoWork_syncFailure_returnsRetry() = runBlocking {
        val workerParams = mockk<WorkerParameters>(relaxed = true)
        every { workerParams.inputData } returns Data.Builder().putLong(HealthConnectWorkoutSyncWorker.KEY_WORKOUT_ID, 300L).build()

        val mockWorkoutData = mockk<WorkoutData>(relaxed = true)
        every { mockWorkoutRepo.getWorkoutDataDirect(300L) } returns mockWorkoutData
        coEvery { HealthConnectManager.syncWorkout(mockContext, mockWorkoutData, force = true) } returns kotlin.Result.failure(RuntimeException("Sync error"))

        val worker = HealthConnectWorkoutSyncWorker(mockContext, workerParams)
        val result = worker.doWork()

        assertEquals(Result.retry(), result)
    }
}
