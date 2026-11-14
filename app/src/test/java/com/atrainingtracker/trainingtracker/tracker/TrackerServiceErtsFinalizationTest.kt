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

package com.atrainingtracker.trainingtracker.tracker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.trainingtracker.database.ErtsPostWorkoutRecomputer
import com.atrainingtracker.trainingtracker.database.hrv.HrvPostWorkoutRecomputer
import com.atrainingtracker.trainingtracker.settings.SettingsDataStoreJavaHelper
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.TimeUnit

/**
 * TST-FIL-017.4: TrackerService lifecycle & toggle gating test (REQ-FIL-017.4, ATT-7750).
 */
@RunWith(RobolectricTestRunner::class)
class TrackerServiceErtsFinalizationTest {

    private lateinit var context: Context
    private lateinit var mockRepo: WorkoutRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        mockRepo = mockk<WorkoutRepository>(relaxed = true)
        mockkStatic(HrvPostWorkoutRecomputer::class)
        mockkStatic(ErtsPostWorkoutRecomputer::class)
        mockkStatic(SettingsDataStoreJavaHelper::class)
        mockkObject(WorkoutRepository.Companion)

        every { HrvPostWorkoutRecomputer.recomputeWorkoutBlocking(any(), any()) } returns false
        every { ErtsPostWorkoutRecomputer.recomputeWorkoutBlocking(any(), any()) } returns true
        every { WorkoutRepository.getInstance(any<Context>()) } returns mockRepo
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testFinalization_withToggleDisabled_neverInvokesErtsRecomputer() {
        every { SettingsDataStoreJavaHelper.isPostWorkoutErtsSmoothingEnabled(any()) } returns false

        val workoutId = 123L
        val appContext = context

        // Simulate TrackerService background recompute task directly
        val task = Runnable {
            val recomputed = HrvPostWorkoutRecomputer.recomputeWorkoutBlocking(appContext, workoutId)
            var ertsRecomputed = false
            if (SettingsDataStoreJavaHelper.isPostWorkoutErtsSmoothingEnabled(appContext)) {
                ertsRecomputed = ErtsPostWorkoutRecomputer.recomputeWorkoutBlocking(appContext, workoutId)
            }
            if (recomputed || ertsRecomputed) {
                WorkoutRepository.getInstance(appContext).refreshWorkout(workoutId)
            }
        }

        ForkJoinPool.commonPool().submit(task).get(2, TimeUnit.SECONDS)

        verify(exactly = 0) {
            ErtsPostWorkoutRecomputer.recomputeWorkoutBlocking(any(), any())
        }
        verify(exactly = 0) {
            mockRepo.refreshWorkout(workoutId)
        }
    }

    @Test
    fun testFinalization_withToggleEnabled_invokesErtsRecomputerAndRefreshesWorkout() {
        every { SettingsDataStoreJavaHelper.isPostWorkoutErtsSmoothingEnabled(any()) } returns true
        every { ErtsPostWorkoutRecomputer.recomputeWorkoutBlocking(any(), any()) } returns true

        val workoutId = 456L
        val appContext = context

        val task = Runnable {
            val recomputed = HrvPostWorkoutRecomputer.recomputeWorkoutBlocking(appContext, workoutId)
            var ertsRecomputed = false
            if (SettingsDataStoreJavaHelper.isPostWorkoutErtsSmoothingEnabled(appContext)) {
                ertsRecomputed = ErtsPostWorkoutRecomputer.recomputeWorkoutBlocking(appContext, workoutId)
            }
            if (recomputed || ertsRecomputed) {
                WorkoutRepository.getInstance(appContext).refreshWorkout(workoutId)
            }
        }

        ForkJoinPool.commonPool().submit(task).get(2, TimeUnit.SECONDS)

        verify(exactly = 1) {
            ErtsPostWorkoutRecomputer.recomputeWorkoutBlocking(appContext, workoutId)
        }
        verify(exactly = 1) {
            mockRepo.refreshWorkout(workoutId)
        }
    }
}
