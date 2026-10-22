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
import androidx.work.*
import com.atrainingtracker.trainingtracker.TrainingApplication
import java.util.concurrent.TimeUnit

/**
 * Background [CoroutineWorker] that periodically synchronizes athlete bikes and shoes
 * from Strava into Equipment.db via [StravaEquipmentSynchronizeThread] (REQ-EXP-019, TST-EXP-016).
 *
 * Scheduling is managed via [schedule], respecting athlete configuration in [TrainingApplication]
 * and enforcing network connectivity and battery constraints.
 */
class StravaEquipmentSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Log.i(TAG, "Starting periodic automated Strava equipment sync...")

        val stravaConnected = TrainingApplication.getStravaAccessToken() != null
        val enabled = TrainingApplication.isAutomatedStravaEquipmentSyncEnabled()

        if (!stravaConnected || !enabled) {
            Log.d(TAG, "Strava disconnected or automated sync disabled. Skipping periodic equipment sync.")
            return Result.success()
        }

        val refreshedToken = StravaHelper.getRefreshedAccessToken()
        if (refreshedToken.isNullOrBlank()) {
            Log.w(TAG, "Strava access token refresh returned null or empty. Skipping periodic equipment sync.")
            return Result.success()
        }

        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val syncThread = StravaEquipmentSynchronizeThread(applicationContext)
                syncThread.run()

                val lastUpdate = TrainingApplication.getLastUpdateTimeOfStravaEquipment()
                if (lastUpdate.equals("updating failed", ignoreCase = true)) {
                    Log.w(TAG, "Strava equipment synchronization reported failure; scheduling retry.")
                    Result.retry()
                } else {
                    Log.i(TAG, "Periodic automated Strava equipment sync completed successfully.")
                    Result.success()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during periodic Strava equipment sync", e)
                Result.retry()
            }
        }
    }

    companion object {
        private const val TAG = "StravaEquipmentSyncWork"
        const val WORK_NAME = "automated_strava_equipment_sync_work"

        /**
         * Schedules or cancels the periodic Strava equipment synchronization worker.
         * Runs periodically according to athlete preference whenever authenticated with Strava.
         *
         * @param context Application or activity context.
         */
        fun schedule(context: Context) {
            try {
                if (!TrainingApplication.isWorkManagerAvailable()) {
                    Log.w(TAG, "WorkManager is unavailable on this device; cannot schedule automated equipment sync.")
                    return
                }

                val stravaConnected = TrainingApplication.getStravaAccessToken() != null
                val enabled = TrainingApplication.isAutomatedStravaEquipmentSyncEnabled()

                if (!stravaConnected || !enabled) {
                    WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
                    Log.d(TAG, "Automated Strava equipment sync cancelled (connected=$stravaConnected, enabled=$enabled).")
                    return
                }

                val intervalDays = TrainingApplication.getStravaEquipmentSyncIntervalDays().toLongOrNull() ?: 1L

                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .build()

                val workRequest = PeriodicWorkRequestBuilder<StravaEquipmentSyncWorker>(intervalDays, TimeUnit.DAYS)
                    .setConstraints(constraints)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.HOURS)
                    .build()

                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    workRequest
                )
                Log.i(TAG, "Periodic Strava equipment sync scheduled every $intervalDays day(s).")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to schedule periodic Strava equipment sync", e)
            }
        }
    }
}
