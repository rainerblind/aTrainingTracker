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
import com.atrainingtracker.trainingtracker.migration.DatabaseSchemaRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * TST-PER-042.1: Automated unit tests verifying WorkoutRrDatabaseManager CRUD,
 * batch insertion, indexed lookup, cascade deletion, and DatabaseSchemaRegistry registration.
 */
@RunWith(RobolectricTestRunner::class)
class WorkoutRrDatabaseManagerTest {

    private lateinit var context: Context
    private lateinit var dbManager: WorkoutRrDatabaseManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        WorkoutRrDatabaseManager.resetForTesting(null)
        dbManager = WorkoutRrDatabaseManager.getInstance(context)
        dbManager.deleteAll()
    }

    @After
    fun tearDown() {
        dbManager.deleteAll()
        WorkoutRrDatabaseManager.resetForTesting(null)
    }

    @Test
    fun databaseSchemaRegistry_referencesWorkoutRrDbVersionDirectly_Rule62() {
        val supportedVersion = DatabaseSchemaRegistry.getMaxSupportedVersion(WorkoutRrDbHelper.DB_NAME)
        assertNotNull("WorkoutRr.db must be registered in DatabaseSchemaRegistry", supportedVersion)
        assertEquals(
            "DatabaseSchemaRegistry must match WorkoutRrDbHelper.DB_VERSION directly (Rule 62)",
            WorkoutRrDbHelper.DB_VERSION,
            supportedVersion
        )

        assertTrue(DatabaseSchemaRegistry.isVersionSupported(WorkoutRrDbHelper.DB_NAME, 1))
        assertFalse(DatabaseSchemaRegistry.isVersionSupported(WorkoutRrDbHelper.DB_NAME, 2))
    }

    @Test
    fun batchInsertAndQuery_preservesMonotonicOrderAndFields() {
        val workoutId = 42L
        val intervals = listOf(
            WorkoutRrInterval(workoutId = workoutId, beatIndex = 0, beatTimestampMs = 1000L, rrMs = 800f, artifactFlag = 0, gapBeats = 0),
            WorkoutRrInterval(workoutId = workoutId, beatIndex = 1, beatTimestampMs = 1800L, rrMs = 820f, artifactFlag = 0, gapBeats = 0),
            WorkoutRrInterval(workoutId = workoutId, beatIndex = 2, beatTimestampMs = 2620L, rrMs = 790f, artifactFlag = 1, gapBeats = 0),
            WorkoutRrInterval(workoutId = workoutId, beatIndex = 3, beatTimestampMs = 4500L, rrMs = 810f, artifactFlag = 5, gapBeats = 1)
        )

        val inserted = dbManager.insertIntervals(intervals)
        assertEquals(4, inserted)

        assertTrue(dbManager.hasIntervalsForWorkout(workoutId))
        assertFalse(dbManager.hasIntervalsForWorkout(999L))

        val queried = dbManager.getIntervalsForWorkout(workoutId)
        assertEquals(4, queried.size)

        for (i in intervals.indices) {
            val expected = intervals[i]
            val actual = queried[i]
            assertEquals(expected.workoutId, actual.workoutId)
            assertEquals(expected.beatIndex, actual.beatIndex)
            assertEquals(expected.beatTimestampMs, actual.beatTimestampMs)
            assertEquals(expected.rrMs, actual.rrMs, 0.001f)
            assertEquals(expected.artifactFlag, actual.artifactFlag)
            assertEquals(expected.gapBeats, actual.gapBeats)
        }
    }

    @Test
    fun deleteWorkout_removesOnlyTargetWorkoutIntervals() {
        val w1 = 101L
        val w2 = 102L

        dbManager.insertIntervals(listOf(
            WorkoutRrInterval(workoutId = w1, beatIndex = 0, beatTimestampMs = 1000L, rrMs = 800f)
        ))
        dbManager.insertIntervals(listOf(
            WorkoutRrInterval(workoutId = w2, beatIndex = 0, beatTimestampMs = 2000L, rrMs = 850f)
        ))

        assertEquals(1, dbManager.getIntervalsForWorkout(w1).size)
        assertEquals(1, dbManager.getIntervalsForWorkout(w2).size)

        val deletedCount = dbManager.deleteWorkout(w1)
        assertEquals(1, deletedCount)

        assertEquals(0, dbManager.getIntervalsForWorkout(w1).size)
        assertEquals(1, dbManager.getIntervalsForWorkout(w2).size)
    }

    @Test
    fun emptyListInsert_returnsZeroWithoutError() {
        val inserted = dbManager.insertIntervals(emptyList())
        assertEquals(0, inserted)
    }
}
