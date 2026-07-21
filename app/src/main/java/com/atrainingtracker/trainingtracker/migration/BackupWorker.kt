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

package com.atrainingtracker.trainingtracker.migration

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import androidx.work.*
import com.atrainingtracker.trainingtracker.TrainingApplication
import java.util.concurrent.TimeUnit

class BackupWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Log.i(TAG, "Starting periodic automated backup...")
        
        val prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        val automatedEnabled = prefs.getBoolean("automated_backups", false)
        val dropboxConnected = TrainingApplication.uploadToDropbox()

        if (!automatedEnabled || !dropboxConnected) {
            Log.d(TAG, "Automated backups disabled or Dropbox not connected. Skipping.")
            return Result.success()
        }

        val backupFile = BackupManager.createBackup(applicationContext)
        return if (backupFile != null) {
            val success = DropboxBackupManager.uploadBackup(applicationContext, backupFile)
            if (success) {
                prefs.edit(commit = true) {
                    putLong("last_backup_timestamp", System.currentTimeMillis())
                }
                Result.success()
            } else {
                Result.retry()
            }
        } else {
            Result.failure()
        }
    }

    companion object {
        private const val TAG = "BackupWorker"
        private const val WORK_NAME = "automated_backup_work"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.UNMETERED) // Only on Wi-Fi
                .setRequiresBatteryNotLow(true)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<BackupWorker>(3, TimeUnit.DAYS)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.HOURS)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
        }
    }
}
