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
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import com.atrainingtracker.trainingtracker.TrainingApplication
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * Unit tests for [StravaSyncScheduler] verifying shared periodic WorkManager scheduling,
 * unavailability guard, disconnection/disable cancellation, interval fallback, constraints,
 * and per-worker tag/workName preservation (REQ-TRK-094.4, TST-TRK-094.4, ATT-6034).
 */
class StravaSyncSchedulerTest {

    private lateinit var mockContext: Context
    private lateinit var mockWorkManager: WorkManager

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0

        mockContext = mockk(relaxed = true)
        mockWorkManager = mockk(relaxed = true)

        mockkObject(WorkManager.Companion)
        every { WorkManager.getInstance(any()) } returns mockWorkManager

        mockkStatic(TrainingApplication::class)
        every { TrainingApplication.isWorkManagerAvailable() } returns true
        every { TrainingApplication.getStravaAccessToken() } returns "token-123"
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun schedulePeriodicSync_whenWorkManagerUnavailable_logsPerWorkerTagAndSkipsWorkManager() {
        every { TrainingApplication.isWorkManagerAvailable() } returns false

        StravaSyncScheduler.schedulePeriodicSync(
            context = mockContext,
            workerClass = StravaEquipmentSyncWorker::class.java,
            tag = "CustomTag",
            workName = "custom_work",
            entityLabel = "equipment",
            isEnabled = { true },
            getIntervalDays = { "7" }
        )

        verify(exactly = 0) { WorkManager.getInstance(any()) }
        verify(exactly = 1) {
            Log.w("CustomTag", match<String> { it.contains("WorkManager is unavailable") && it.contains("equipment") })
        }
    }

    @Test
    fun schedulePeriodicSync_whenDisconnectedOrDisabled_cancelsUniqueWork() {
        every { TrainingApplication.getStravaAccessToken() } returns null

        StravaSyncScheduler.schedulePeriodicSync(
            context = mockContext,
            workerClass = StravaEquipmentSyncWorker::class.java,
            tag = "CustomTag",
            workName = "custom_work",
            entityLabel = "routes",
            isEnabled = { true },
            getIntervalDays = { "3" }
        )

        verify(exactly = 1) { mockWorkManager.cancelUniqueWork("custom_work") }
        verify(exactly = 0) { mockWorkManager.enqueueUniquePeriodicWork(any(), any(), any()) }

        every { TrainingApplication.getStravaAccessToken() } returns "token-123"
        StravaSyncScheduler.schedulePeriodicSync(
            context = mockContext,
            workerClass = StravaEquipmentSyncWorker::class.java,
            tag = "CustomTag",
            workName = "custom_work_2",
            entityLabel = "segments",
            isEnabled = { false },
            getIntervalDays = { "3" }
        )

        verify(exactly = 1) { mockWorkManager.cancelUniqueWork("custom_work_2") }
    }

    @Test
    fun schedulePeriodicSync_whenConnectedAndEnabled_enqueuesWithNetworkBatteryConstraintsAndFallbackInterval() {
        val requestSlot = slot<PeriodicWorkRequest>()
        every {
            mockWorkManager.enqueueUniquePeriodicWork("custom_work", ExistingPeriodicWorkPolicy.UPDATE, capture(requestSlot))
        } returns mockk(relaxed = true)

        StravaSyncScheduler.schedulePeriodicSync(
            context = mockContext,
            workerClass = StravaEquipmentSyncWorker::class.java,
            tag = "CustomTag",
            workName = "custom_work",
            entityLabel = "equipment",
            isEnabled = { true },
            getIntervalDays = { "invalid-number" }
        )

        val workSpec = requestSlot.captured.workSpec
        assertEquals(NetworkType.CONNECTED, workSpec.constraints.requiredNetworkType)
        assertTrue(workSpec.constraints.requiresBatteryNotLow())
        assertEquals(TimeUnit.DAYS.toMillis(1L), workSpec.intervalDuration)
    }

    @Test
    fun schedulePeriodicSync_whenWorkManagerThrows_catchesAndLogsWithoutCrashing() {
        every { WorkManager.getInstance(any()) } throws IllegalStateException("WorkManager not initialized")

        StravaSyncScheduler.schedulePeriodicSync(
            context = mockContext,
            workerClass = StravaEquipmentSyncWorker::class.java,
            tag = "CustomTag",
            workName = "custom_work",
            entityLabel = "equipment",
            isEnabled = { true },
            getIntervalDays = { "1" }
        )

        verify(exactly = 1) {
            Log.e("CustomTag", match<String> { it.contains("Failed to schedule periodic Strava equipment sync") }, any())
        }
    }
}
