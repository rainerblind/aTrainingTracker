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
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import com.atrainingtracker.trainingtracker.TrainingApplication
import java.util.concurrent.TimeUnit

/**
 * Shared periodic WorkManager scheduler for automated Strava background synchronization workers
 * (equipment, routes, and starred segments) (REQ-TRK-094.4, TST-TRK-094.4, ATT-6034).
 */
object StravaSyncScheduler {
    fun schedulePeriodicSync(
        context: Context,
        workerClass: Class<out ListenableWorker>,
        tag: String,
        workName: String,
        entityLabel: String,
        isEnabled: () -> Boolean,
        getIntervalDays: () -> String
    ) {
        try {
            if (!TrainingApplication.isWorkManagerAvailable()) {
                Log.w(tag, "WorkManager is unavailable on this device; cannot schedule automated $entityLabel sync.")
                return
            }

            val stravaConnected = TrainingApplication.getStravaAccessToken() != null
            val enabled = isEnabled()

            if (!stravaConnected || !enabled) {
                WorkManager.getInstance(context).cancelUniqueWork(workName)
                Log.d(tag, "Automated Strava $entityLabel sync cancelled (connected=$stravaConnected, enabled=$enabled).")
                return
            }

            val intervalDays = getIntervalDays().toLongOrNull() ?: 1L

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

            val workRequest = PeriodicWorkRequest.Builder(workerClass, intervalDays, TimeUnit.DAYS)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.HOURS)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                workName,
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
            )
            Log.i(tag, "Periodic Strava $entityLabel sync scheduled every $intervalDays day(s).")
        } catch (e: Exception) {
            Log.e(tag, "Failed to schedule periodic Strava $entityLabel sync", e)
        }
    }
}
