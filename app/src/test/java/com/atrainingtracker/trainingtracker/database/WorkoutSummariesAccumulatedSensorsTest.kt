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
 */

package com.atrainingtracker.trainingtracker.database

import android.content.ContentValues
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Robolectric SQLite tests verifying idempotent deduplication of `AccumulatedSensors` rows
 * across repeated saves and upserts (`REQ-TRK-084.3`, `TST-TRK-084.3`).
 */
@RunWith(RobolectricTestRunner::class)
class WorkoutSummariesAccumulatedSensorsTest {

    private lateinit var context: Context
    private lateinit var summariesDbManager: WorkoutSummariesDatabaseManager
    private lateinit var repository: WorkoutRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        WorkoutRepository.resetForTesting(null)
        summariesDbManager = WorkoutSummariesDatabaseManager.getInstance(context)
        repository = WorkoutRepository.getInstance(context)
    }

    @After
    fun tearDown() {
        WorkoutRepository.resetForTesting(null)
    }

    @Test
    fun testSaveAndAddAccumulatedSensorTypes_deduplicatesExistingRowsInSqlite() {
        val cv = ContentValues().apply {
            put(WorkoutSummaries.FILE_BASE_NAME, "2026-11-17_dedup")
            put(WorkoutSummaries.TIME_START, "2026-11-17 06:00:00")
            put(WorkoutSummaries.SPORT_ID, 1L)
        }
        val workoutId = summariesDbManager.database.insertOrThrow(WorkoutSummaries.TABLE, null, cv)

        // Initial save including duplicate elements in input iterable
        summariesDbManager.saveAccumulatedSensorTypes(
            workoutId,
            listOf(SensorType.HR, SensorType.SPEED_mps, SensorType.HR)
        )
        // Overlapping second save (simulating resume or retry)
        summariesDbManager.saveAccumulatedSensorTypes(
            workoutId,
            listOf(SensorType.SPEED_mps, SensorType.POWER)
        )
        // Overlapping repository upsert (simulating backfill / finalizeLiveSession)
        repository.addAccumulatedSensorTypes(
            workoutId,
            setOf(SensorType.POWER, SensorType.HRV_RMSSD)
        )

        val persisted = repository.getAccumulatedSensorTypes(workoutId)
        assertEquals(
            setOf(SensorType.HR, SensorType.SPEED_mps, SensorType.POWER, SensorType.HRV_RMSSD),
            persisted
        )

        // Verify raw SQLite row count has zero duplicates
        summariesDbManager.database.rawQuery(
            "SELECT COUNT(*) FROM ${WorkoutSummaries.TABLE_ACCUMULATED_SENSORS} WHERE ${WorkoutSummaries.WORKOUT_ID} = ?",
            arrayOf(workoutId.toString())
        ).use { cursor ->
            cursor.moveToFirst()
            assertEquals(4, cursor.getInt(0))
        }
    }
}
