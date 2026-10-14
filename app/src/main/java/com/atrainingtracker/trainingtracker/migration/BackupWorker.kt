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
        val automatedEnabled = prefs.getBoolean("automated_backups", true)
        val dropboxConnected = TrainingApplication.uploadToDropbox()
        val googleDriveConnected = TrainingApplication.uploadToGoogleDrive() && TrainingApplication.uploadBackupToGoogleDrive()

        if (!automatedEnabled) {
            Log.d(TAG, "Automated backups disabled. Skipping.")
            return Result.success()
        }

        val lock = BackupManager.acquireLock(applicationContext)
        if (lock == null) {
            Log.w(TAG, "Backup already in progress by manual operation; yielding with retry.")
            return Result.retry()
        }

        return lock.use {
            val backupFile = BackupManager.createBackup(applicationContext)
            if (backupFile != null) {
                if (!dropboxConnected && !googleDriveConnected) {
                    Log.i(TAG, "Local backup created successfully without cloud providers.")
                    prefs.edit(commit = true) {
                        putLong("last_backup_timestamp", System.currentTimeMillis())
                        putString("last_backup_status", "LOCAL_SUCCESS")
                    }
                    return@use Result.success()
                }

                var anyUploadSuccess = false
                var anyUploadFailed = false

                if (dropboxConnected) {
                    val dbxSuccess = DropboxBackupManager.uploadBackup(applicationContext, backupFile)
                    if (dbxSuccess) {
                        anyUploadSuccess = true
                    } else {
                        anyUploadFailed = true
                    }
                }

                if (googleDriveConnected) {
                    val gdSuccess = GoogleDriveBackupManager.uploadBackup(applicationContext, backupFile)
                    if (gdSuccess) {
                        anyUploadSuccess = true
                    } else {
                        anyUploadFailed = true
                    }
                }

                if (anyUploadSuccess) {
                    prefs.edit(commit = true) {
                        putLong("last_backup_timestamp", System.currentTimeMillis())
                        putString("last_backup_status", if (anyUploadFailed) "PARTIAL_SUCCESS" else "SUCCESS")
                    }
                    Result.success()
                } else {
                    prefs.edit(commit = true) {
                        putString("last_backup_status", "Upload failed")
                    }
                    Result.retry()
                }
            } else {
                prefs.edit(commit = true) {
                    putString("last_backup_status", "Local backup creation failed")
                }
                Result.retry()
            }
        }
    }

    companion object {
        private const val TAG = "BackupWorker"
        private const val WORK_NAME = "automated_backup_work"

        fun schedule(context: Context) {
            try {
                if (!TrainingApplication.isWorkManagerAvailable()) {
                    Log.w(TAG, "WorkManager is unavailable on this device; cannot schedule automated backups.")
                    return
                }

                val prefs = PreferenceManager.getDefaultSharedPreferences(context)
                val automatedEnabled = prefs.getBoolean("automated_backups", true)
                val intervalDays = prefs.getString("backup_interval_days", "1")?.toLong() ?: 1L

                // ATT-3472: One-time queue sanitization to break legacy REPLACE loop for existing installations
                val isQueueMigrated = prefs.getBoolean("workmanager_backup_policy_migrated_v1", false)
                if (!isQueueMigrated) {
                    WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
                    prefs.edit().putBoolean("workmanager_backup_policy_migrated_v1", true).apply()
                }

                if (!automatedEnabled) {
                    WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
                    return
                }

                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.UNMETERED) // Only on Wi-Fi
                    .setRequiresBatteryNotLow(true)
                    .build()

                val workRequest = PeriodicWorkRequestBuilder<BackupWorker>(intervalDays, TimeUnit.DAYS)
                    .setConstraints(constraints)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.HOURS)
                    .build()

                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    workRequest
                )
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to schedule BackupWorker with WorkManager", t)
            }
        }
    }
}
