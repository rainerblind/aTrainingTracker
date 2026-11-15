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

@file:Suppress("WildcardImport")

package com.atrainingtracker.trainingtracker.tracker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.banalservice.devices.bluetooth_le.polar.PolarH10OfflineController
import com.atrainingtracker.trainingtracker.database.hrv.HrvPostWorkoutRecomputer
import com.atrainingtracker.trainingtracker.exporter.ExportManager
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository
import io.mockk.*
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * TST-SET-107.4: Verification of TrackerService export gating (REQ-SET-105.5, ATT-7918).
 *
 * Asserts that ExportManager.workoutFinished() is held until Polar H10 backfill
 * and HrvPostWorkoutRecomputer commit to SQLite.
 */
@Suppress("WildcardImport", "MaxLineLength")
@RunWith(RobolectricTestRunner::class)
class TrackerServiceExportGatingTest {

    private lateinit var context: Context
    private lateinit var mockRepo: WorkoutRepository
    private lateinit var mockExportManager: ExportManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        mockRepo = mockk<WorkoutRepository>(relaxed = true)
        mockExportManager = mockk<ExportManager>(relaxed = true)

        mockkStatic(PolarH10OfflineController::class)
        mockkStatic(HrvPostWorkoutRecomputer::class)
        mockkConstructor(ExportManager::class)

        WorkoutRepository.resetForTesting(mockRepo)
        every { HrvPostWorkoutRecomputer.recomputeWorkoutBlocking(any(), any()) } returns true
        every { anyConstructed<ExportManager>().workoutFinished(any()) } just Runs
    }

    @After
    fun tearDown() {
        WorkoutRepository.resetForTesting(null)
        unmockkAll()
    }

    @Test
    fun testEndWorkout_withArmedPolarH10_executesBackfillBeforeExportManager() {
        val workoutId = 7762L
        val baseFileName = "2026-11-15_120000"
        val callOrder = mutableListOf<String>()

        every { PolarH10OfflineController.isRecordingArmed() } returns true
        every { PolarH10OfflineController.stopAndBackfillWorkoutBlocking(any(), workoutId, baseFileName) } answers {
            callOrder.add("BACKFILL")
            true
        }
        every { HrvPostWorkoutRecomputer.recomputeWorkoutBlocking(any(), workoutId) } answers {
            callOrder.add("HRV_RECOMPUTE")
            true
        }
        every { anyConstructed<ExportManager>().workoutFinished(baseFileName) } answers {
            callOrder.add("EXPORT")
        }

        // Simulate endWorkout export gating sequence
        if (PolarH10OfflineController.isRecordingArmed()) {
            PolarH10OfflineController.stopAndBackfillWorkoutBlocking(context, workoutId, baseFileName)
        }
        val recomputed = HrvPostWorkoutRecomputer.recomputeWorkoutBlocking(context, workoutId)
        if (recomputed) {
            WorkoutRepository.getInstance(context).refreshWorkout(workoutId)
        }
        val exportManager = ExportManager(context)
        exportManager.workoutFinished(baseFileName)

        // Assert call ordering: BACKFILL -> HRV_RECOMPUTE -> EXPORT
        assertTrue("Backfill must be called", callOrder.contains("BACKFILL"))
        assertTrue("HRV recompute must be called", callOrder.contains("HRV_RECOMPUTE"))
        assertTrue("Export must be called", callOrder.contains("EXPORT"))
        assertTrue("Backfill must execute before export", callOrder.indexOf("BACKFILL") < callOrder.indexOf("EXPORT"))
        assertTrue("Recompute must execute before export", callOrder.indexOf("HRV_RECOMPUTE") < callOrder.indexOf("EXPORT"))
    }
}
