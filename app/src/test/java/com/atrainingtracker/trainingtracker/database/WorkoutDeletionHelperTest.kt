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

package com.atrainingtracker.trainingtracker.database

import android.content.Context
import android.util.Log
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Automated unit tests verifying that WorkoutDeletionHelper delegates to WorkoutRepository (REQ-DAT-042.5, REQ-DAT-010, TST-DAT-004 / ATT-296).
 */
class WorkoutDeletionHelperTest {

    private val mockContext = mockk<Context>(relaxed = true)
    private val mockWorkoutRepository = mockk<WorkoutRepository>(relaxed = true)

    private lateinit var deletionHelper: WorkoutDeletionHelper

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0

        every { mockContext.applicationContext } returns mockContext

        deletionHelper = WorkoutDeletionHelper(mockContext, mockWorkoutRepository)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    /**
     * Verifies that deleteWorkout delegates to WorkoutRepository.deleteWorkoutCascade.
     */
    @Test
    fun testDeleteWorkout_delegatesToWorkoutRepositoryCascade() {
        val workoutId = 123L
        every { mockWorkoutRepository.deleteWorkoutCascade(workoutId) } returns true

        val success = deletionHelper.deleteWorkout(workoutId)

        assertTrue("deleteWorkout should return true", success)
        verify(exactly = 1) { mockWorkoutRepository.deleteWorkoutCascade(workoutId) }
    }

    /**
     * Verifies that deleteOldWorkouts delegates to WorkoutRepository.deleteOldWorkoutsCascade.
     */
    @Test
    fun testDeleteOldWorkouts_delegatesToWorkoutRepositoryCascade() {
        val daysToKeep = 365
        every { mockWorkoutRepository.deleteOldWorkoutsCascade(eq(daysToKeep), any()) } returns true

        val success = deletionHelper.deleteOldWorkouts(daysToKeep) { }

        assertTrue(success)
        verify(exactly = 1) { mockWorkoutRepository.deleteOldWorkoutsCascade(eq(daysToKeep), any()) }
    }

    /**
     * Verifies that deleteOldWorkouts with DeletionProgressCallback delegates properly.
     */
    @Test
    fun testDeleteOldWorkouts_withDeletionProgressCallback_delegatesToRepository() {
        val daysToKeep = 30
        val callback = WorkoutDeletionHelper.DeletionProgressCallback { _, _, _ -> }
        every { mockWorkoutRepository.deleteOldWorkoutsCascade(daysToKeep, callback) } returns true

        val success = deletionHelper.deleteOldWorkouts(daysToKeep, callback)

        assertTrue(success)
        verify(exactly = 1) { mockWorkoutRepository.deleteOldWorkoutsCascade(daysToKeep, callback) }
    }
}
