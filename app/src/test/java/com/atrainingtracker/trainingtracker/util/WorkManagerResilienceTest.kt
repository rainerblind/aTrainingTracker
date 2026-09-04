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

package com.atrainingtracker.trainingtracker.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.work.WorkManager
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.migration.BackupWorker
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests verifying resilient handling and graceful degradation when WorkManager
 * is unavailable or fails on non-standard Android 14 builds.
 *
 * Verifies REQ-STB-001 / TST-STB-001 for ATT-621.
 */
class WorkManagerResilienceTest {

    private lateinit var mockContext: Context
    private lateinit var mockPrefs: SharedPreferences

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0

        mockContext = mockk(relaxed = true)
        mockPrefs = mockk(relaxed = true)
        every { mockContext.getSharedPreferences(any(), any()) } returns mockPrefs
    }

    @After
    fun tearDown() {
        TrainingApplication.setWorkManagerAvailableForTesting(false)
        unmockkAll()
    }

    @Test
    fun testWorkManagerAvailabilityFlagToggle() {
        TrainingApplication.setWorkManagerAvailableForTesting(false)
        assertFalse("WorkManager should be unavailable when set to false", TrainingApplication.isWorkManagerAvailable())

        TrainingApplication.setWorkManagerAvailableForTesting(true)
        assertTrue("WorkManager should be available when set to true", TrainingApplication.isWorkManagerAvailable())
    }

    @Test
    fun testBackupWorkerScheduleWhenWorkManagerUnavailableDoesNotCrash() {
        TrainingApplication.setWorkManagerAvailableForTesting(false)

        // Should return early and safely without attempting WorkManager calls or throwing exceptions
        BackupWorker.schedule(mockContext)

        verify(exactly = 1) {
            Log.w("BackupWorker", "WorkManager is unavailable on this device; cannot schedule automated backups.")
        }
    }

    @Test
    fun testBackupWorkerScheduleWhenWorkManagerThrowsCatchesGracefully() {
        TrainingApplication.setWorkManagerAvailableForTesting(true)

        // WorkManager is not initialized in the test environment.
        // Calling schedule() with availability=true causes WorkManager.getInstance() to throw IllegalStateException.
        // The try-catch block in BackupWorker.schedule MUST catch it safely and log an error without throwing.
        BackupWorker.schedule(mockContext)

        verify(atLeast = 1) {
            Log.e("BackupWorker", "Failed to schedule BackupWorker with WorkManager", any())
        }
    }
}
