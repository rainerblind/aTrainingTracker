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

package com.atrainingtracker.trainingtracker.routes

import android.content.Context
import android.util.Log
import androidx.preference.PreferenceManager
import androidx.work.*
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.repositories.RoutesRepository
import java.util.concurrent.TimeUnit

/**
 * Background [CoroutineWorker] that periodically synchronizes Strava routes
 * via [RoutesRepository.syncRoutesFromStrava] (REQ-EXP-012, TST-EXP-009).
 *
 * Scheduling is managed via [schedule], respecting athlete configuration in [TrainingApplication]
 * and enforcing network connectivity and battery constraints.
 */
class StravaRoutesSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Log.i(TAG, "Starting periodic automated Strava routes sync...")

        // Always enforce 7-day TTL cache retention for Strava routes (Section 6.2 compliance)
        RoutesRepository.getInstance(applicationContext).pruneExpiredRoutes()

        val prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        val automatedEnabled = prefs.getBoolean(TrainingApplication.SP_AUTOMATED_STRAVA_ROUTES_SYNC, true)
        val stravaConnected = TrainingApplication.getStravaAccessToken() != null

        if (!automatedEnabled || !stravaConnected) {
            Log.d(TAG, "Automated Strava routes sync disabled or Strava disconnected. Skipping.")
            return Result.success()
        }

        return try {
            val repository = RoutesRepository.getInstance(applicationContext)
            val success = repository.syncRoutesFromStrava()

            if (success) {
                Log.i(TAG, "Periodic automated Strava routes sync completed successfully.")
                Result.success()
            } else {
                Log.w(TAG, "Periodic automated Strava routes sync returned false; scheduling retry.")
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during periodic Strava routes sync", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "StravaRoutesSyncWorker"
        const val WORK_NAME = "automated_strava_routes_sync_work"

        /**
         * Schedules or cancels the periodic Strava routes synchronization worker
         * according to the persistent preferences in [TrainingApplication].
         *
         * @param context Application or activity context.
         */
        fun schedule(context: Context) {
            try {
                if (!TrainingApplication.isWorkManagerAvailable()) {
                    Log.w(TAG, "WorkManager is unavailable on this device; cannot schedule automated routes sync.")
                    return
                }

                val prefs = PreferenceManager.getDefaultSharedPreferences(context)
                val automatedEnabled = prefs.getBoolean(TrainingApplication.SP_AUTOMATED_STRAVA_ROUTES_SYNC, true)
                val intervalDays = prefs.getString(TrainingApplication.SP_STRAVA_ROUTES_SYNC_INTERVAL_DAYS, "1")?.toLongOrNull() ?: 1L
                val stravaConnected = TrainingApplication.getStravaAccessToken() != null

                if (!automatedEnabled || !stravaConnected) {
                    WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
                    Log.d(TAG, "Automated Strava routes sync cancelled (enabled=$automatedEnabled, connected=$stravaConnected).")
                    return
                }

                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .build()

                val workRequest = PeriodicWorkRequestBuilder<StravaRoutesSyncWorker>(intervalDays, TimeUnit.DAYS)
                    .setConstraints(constraints)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.HOURS)
                    .build()

                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    workRequest
                )
                Log.i(TAG, "Periodic Strava routes sync scheduled every $intervalDays day(s).")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to schedule periodic Strava routes sync", e)
            }
        }
    }
}
