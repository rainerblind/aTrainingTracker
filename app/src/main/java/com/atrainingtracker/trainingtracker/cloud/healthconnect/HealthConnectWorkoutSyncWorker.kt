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
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Background WorkManager worker relaying newly auto-imported workouts
 * to Android Health Connect asynchronously (REQ-MIG-052.4).
 */
class HealthConnectWorkoutSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val workoutId = inputData.getLong(KEY_WORKOUT_ID, -1L)
        if (workoutId <= 0L) {
            Log.e(TAG, "Invalid workoutId: $workoutId")
            return@withContext Result.failure()
        }

        if (!TrainingApplication.isWorkManagerAvailable()) {
            Log.w(TAG, "WorkManager is unavailable on this device")
            return@withContext Result.failure()
        }

        try {
            val workoutRepo = WorkoutRepository.getInstance(applicationContext)
            val workoutData = workoutRepo.getWorkoutDataDirect(workoutId)
            if (workoutData == null) {
                Log.w(TAG, "Workout $workoutId not found in repository")
                return@withContext Result.failure()
            }

            val syncResult = HealthConnectManager.syncWorkout(applicationContext, workoutData, force = true)
            if (syncResult.isSuccess) {
                Log.i(TAG, "Successfully synced workout $workoutId to Health Connect")
                Result.success()
            } else {
                Log.w(TAG, "Health Connect sync failed for workout $workoutId: ${syncResult.exceptionOrNull()?.message}")
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during Health Connect workout sync for $workoutId", e)
            Result.retry()
        }
    }

    companion object {
        const val KEY_WORKOUT_ID = "workout_id"
        const val TAG = "HealthConnectWorkoutSyncWorker"

        /**
         * Enqueues an immediate one-time background sync request for the given [workoutId].
         */
        @JvmStatic
        fun enqueue(context: Context, workoutId: Long) {
            if (workoutId <= 0L) return
            try {
                if (!TrainingApplication.isWorkManagerAvailable()) {
                    Log.w(TAG, "WorkManager unavailable, skipping Health Connect sync enqueue")
                    return
                }
                val request = OneTimeWorkRequestBuilder<HealthConnectWorkoutSyncWorker>()
                    .setInputData(workDataOf(KEY_WORKOUT_ID to workoutId))
                    .addTag(TAG)
                    .build()
                WorkManager.getInstance(context).enqueue(request)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to enqueue HealthConnectWorkoutSyncWorker for workout $workoutId", e)
            }
        }
    }
}
