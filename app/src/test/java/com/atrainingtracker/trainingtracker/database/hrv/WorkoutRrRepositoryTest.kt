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

package com.atrainingtracker.trainingtracker.database.hrv

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * TST-PER-042.1 / TST-PER-042.2: Unit tests for [WorkoutRrRepository] CRUD,
 * max beat counter lookup, and blocking/suspending bridge methods (REQ-PER-034.1, REQ-PER-034.2).
 */
@RunWith(RobolectricTestRunner::class)
class WorkoutRrRepositoryTest {

    private lateinit var context: Context
    private lateinit var repository: WorkoutRrRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        WorkoutRrDatabaseManager.resetForTesting(null)
        WorkoutRrRepository.resetForTesting(null)
        repository = WorkoutRrRepository.getInstance(context)
        runBlocking { repository.deleteAll() }
    }

    @After
    fun tearDown() {
        runBlocking { repository.deleteAll() }
        WorkoutRrRepository.resetForTesting(null)
        WorkoutRrDatabaseManager.resetForTesting(null)
    }

    @Test
    fun repository_getMaxBeatIndex_andBlockingBridge() = runBlocking {
        val workoutId = 55L
        assertEquals(-1, repository.getMaxBeatIndex(workoutId))
        assertEquals(-1, repository.getMaxBeatIndexBlocking(workoutId))

        val intervals = listOf(
            WorkoutRrInterval(workoutId = workoutId, beatIndex = 0, beatTimestampMs = 1000L, rrMs = 800f),
            WorkoutRrInterval(workoutId = workoutId, beatIndex = 12, beatTimestampMs = 10000L, rrMs = 820f),
            WorkoutRrInterval(workoutId = workoutId, beatIndex = 4, beatTimestampMs = 4000L, rrMs = 810f)
        )

        repository.insertIntervals(intervals)

        assertEquals(12, repository.getMaxBeatIndex(workoutId))
        assertEquals(12, repository.getMaxBeatIndexBlocking(workoutId))

        assertTrue(repository.hasIntervalsForWorkout(workoutId))
        assertEquals(3, repository.getIntervalsForWorkout(workoutId).size)
        assertEquals(3, repository.getIntervalsForWorkoutBlocking(workoutId).size)

        repository.deleteWorkout(workoutId)
        assertFalse(repository.hasIntervalsForWorkout(workoutId))
    }
}
