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
import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository coordinating workout beat-to-beat RR persistence and retrieval (`REQ-PER-034.1`).
 */
class WorkoutRrRepository private constructor(
    private val databaseManagerProvider: () -> WorkoutRrDatabaseManager
) {
    constructor(databaseManager: WorkoutRrDatabaseManager) : this({ databaseManager })

    private val databaseManager: WorkoutRrDatabaseManager
        get() = databaseManagerProvider()

    fun insertIntervalsBlocking(intervals: List<WorkoutRrInterval>): Int =
        databaseManager.insertIntervals(intervals)

    fun getIntervalsForWorkoutBlocking(workoutId: Long): List<WorkoutRrInterval> =
        databaseManager.getIntervalsForWorkout(workoutId)

    fun deleteWorkoutBlocking(workoutId: Long): Int =
        databaseManager.deleteWorkout(workoutId)

    fun getMaxBeatIndexBlocking(workoutId: Long): Int =
        databaseManager.getMaxBeatIndex(workoutId)

    suspend fun getMaxBeatIndex(workoutId: Long): Int = withContext(Dispatchers.IO) {
        databaseManager.getMaxBeatIndex(workoutId)
    }

    suspend fun insertIntervals(intervals: List<WorkoutRrInterval>): Int = withContext(Dispatchers.IO) {
        databaseManager.insertIntervals(intervals)
    }

    suspend fun getIntervalsForWorkout(workoutId: Long): List<WorkoutRrInterval> = withContext(Dispatchers.IO) {
        databaseManager.getIntervalsForWorkout(workoutId)
    }

    suspend fun hasIntervalsForWorkout(workoutId: Long): Boolean = withContext(Dispatchers.IO) {
        databaseManager.hasIntervalsForWorkout(workoutId)
    }

    suspend fun deleteWorkout(workoutId: Long): Int = withContext(Dispatchers.IO) {
        databaseManager.deleteWorkout(workoutId)
    }

    suspend fun deleteAll(): Int = withContext(Dispatchers.IO) {
        databaseManager.deleteAll()
    }

    companion object {
        @Volatile
        private var instance: WorkoutRrRepository? = null

        @JvmStatic
        fun getInstance(context: Context): WorkoutRrRepository {
            val appContext = try {
                context.applicationContext ?: context
            } catch (_: Throwable) {
                context
            }
            return instance ?: synchronized(this) {
                instance ?: WorkoutRrRepository {
                    WorkoutRrDatabaseManager.getInstance(appContext)
                }.also { instance = it }
            }
        }

        @JvmStatic
        @VisibleForTesting
        fun resetForTesting(newInstance: WorkoutRrRepository? = null) {
            instance = newInstance
        }
    }
}
