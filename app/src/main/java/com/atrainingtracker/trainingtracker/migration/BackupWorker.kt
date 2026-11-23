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

        if (!automatedEnabled) {
            Log.d(TAG, "Automated backups disabled. Skipping.")
            return Result.success()
        }

        val execution = BackupOrchestrator.executeBackupAndUpload(
            context = applicationContext,
            requireCloudProvider = false
        )
        return when (execution.outcome) {
            BackupOrchestrator.BackupOutcome.LOCAL_SUCCESS,
            BackupOrchestrator.BackupOutcome.CLOUD_SUCCESS,
            BackupOrchestrator.BackupOutcome.CLOUD_PARTIAL_SUCCESS -> Result.success()
            BackupOrchestrator.BackupOutcome.LOCK_CONTENTION -> {
                Log.w(TAG, "Backup already in progress by manual operation; yielding with retry.")
                Result.retry()
            }
            BackupOrchestrator.BackupOutcome.LOCAL_FAILED,
            BackupOrchestrator.BackupOutcome.CLOUD_FAILED,
            BackupOrchestrator.BackupOutcome.NO_CLOUD_PROVIDER -> Result.retry()
        }
    }

    companion object {
        private const val TAG = "BackupWorker"
        private const val WORK_NAME = "automated_backup_work"

        fun formatTargets(targets: Map<String, Boolean>): String {
            return BackupOrchestrator.formatTargets(targets)
        }

        fun buildConstraints(context: Context): Constraints {
            val dropboxBackupEnabled = TrainingApplication.uploadToDropbox() &&
                TrainingApplication.uploadBackupToDropbox() &&
                TrainingApplication.readDropboxCredential() != null
            val googleDriveBackupEnabled = TrainingApplication.uploadToGoogleDrive() &&
                TrainingApplication.uploadBackupToGoogleDrive()

            val cloudBackupEnabled = dropboxBackupEnabled || googleDriveBackupEnabled
            val requiresWifi = (dropboxBackupEnabled && TrainingApplication.uploadToDropboxOnlyOnWifi()) ||
                (googleDriveBackupEnabled && TrainingApplication.uploadToGoogleDriveOnlyOnWifi())

            val networkType = when {
                !cloudBackupEnabled -> NetworkType.NOT_REQUIRED
                requiresWifi -> NetworkType.UNMETERED
                else -> NetworkType.CONNECTED
            }

            return Constraints.Builder()
                .setRequiredNetworkType(networkType)
                .setRequiresBatteryNotLow(true)
                .build()
        }

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

                val constraints = buildConstraints(context)

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
