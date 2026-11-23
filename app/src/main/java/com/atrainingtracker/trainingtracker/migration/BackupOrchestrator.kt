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
import com.atrainingtracker.trainingtracker.TrainingApplication
import java.io.File

/**
 * Shared orchestration engine for local `.attbackup` creation, multi-cloud uploads
 * (Dropbox and Google Drive), process-safe [BackupManager.acquireLock] coordination,
 * and `last_backup_*` SharedPreferences persistence across [BackupWorker] and
 * [BackupRestoreViewModel.uploadToCloud] (`REQ-MIG-036`, `REQ-MIG-061.5`).
 */
object BackupOrchestrator {

    private const val TAG = "BackupOrchestrator"

    enum class BackupOutcome {
        NO_CLOUD_PROVIDER,
        LOCK_CONTENTION,
        LOCAL_FAILED,
        LOCAL_SUCCESS,
        CLOUD_SUCCESS,
        CLOUD_PARTIAL_SUCCESS,
        CLOUD_FAILED
    }

    data class BackupExecutionResult(
        val outcome: BackupOutcome,
        val backupFile: File? = null,
        val targets: Map<String, Boolean> = emptyMap(),
        val statusString: String = ""
    )

    /**
     * Formats per-target backup results into the canonical semicolon-separated persistence string
     * (e.g. `"local:SUCCESS;dropbox:SUCCESS;google_drive:FAILED"`).
     */
    fun formatTargets(targets: Map<String, Boolean>): String {
        return targets.entries.joinToString(";") { "${it.key}:${if (it.value) "SUCCESS" else "FAILED"}" }
    }

    /**
     * Executes an atomic local backup and optional cloud upload across all enabled cloud providers,
     * persisting diagnostic status keys (`last_backup_timestamp`, `last_backup_status`,
     * `last_backup_targets`) synchronously via `edit(commit = true)`.
     *
     * @param context Context used for persistence and backup creation.
     * @param requireCloudProvider If `true` (manual cloud backup), returns [BackupOutcome.NO_CLOUD_PROVIDER]
     *   before acquiring the backup lock when neither Dropbox nor Google Drive backup is connected.
     * @param onProgress Optional progress callback for interactive UI updates; when `null` (background worker),
     *   invokes the 1-arg [BackupManager.createBackup] overload.
     */
    suspend fun executeBackupAndUpload(
        context: Context,
        requireCloudProvider: Boolean,
        onProgress: ((String) -> Unit)? = null
    ): BackupExecutionResult {
        val dropboxConnected = TrainingApplication.uploadToDropbox() &&
            TrainingApplication.uploadBackupToDropbox() &&
            TrainingApplication.readDropboxCredential() != null
        val googleDriveConnected = TrainingApplication.uploadToGoogleDrive() &&
            TrainingApplication.uploadBackupToGoogleDrive()

        if (requireCloudProvider && !dropboxConnected && !googleDriveConnected) {
            return BackupExecutionResult(outcome = BackupOutcome.NO_CLOUD_PROVIDER)
        }

        onProgress?.invoke("Creating backup...")

        val lock = BackupManager.acquireLock(context)
        if (lock == null) {
            Log.w(TAG, "Backup already in progress; lock could not be acquired.")
            return BackupExecutionResult(outcome = BackupOutcome.LOCK_CONTENTION)
        }

        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        return lock.use {
            val backupFile = if (onProgress != null) {
                BackupManager.createBackup(
                    context,
                    object : BackupManager.ProgressListener {
                        override fun onProgress(message: String) {
                            onProgress.invoke(message)
                        }
                    }
                )
            } else {
                BackupManager.createBackup(context)
            }

            val targets = linkedMapOf<String, Boolean>()
            targets["local"] = (backupFile != null)

            if (backupFile == null) {
                val targetsStr = formatTargets(targets)
                prefs.edit(commit = true) {
                    putString("last_backup_status", "Local backup creation failed")
                    putString("last_backup_targets", targetsStr)
                }
                return@use BackupExecutionResult(
                    outcome = BackupOutcome.LOCAL_FAILED,
                    backupFile = null,
                    targets = targets,
                    statusString = "Local backup creation failed"
                )
            }

            if (!dropboxConnected && !googleDriveConnected) {
                Log.i(TAG, "Local backup created successfully without cloud providers.")
                val targetsStr = formatTargets(targets)
                prefs.edit(commit = true) {
                    putLong("last_backup_timestamp", System.currentTimeMillis())
                    putString("last_backup_status", "LOCAL_SUCCESS")
                    putString("last_backup_targets", targetsStr)
                }
                return@use BackupExecutionResult(
                    outcome = BackupOutcome.LOCAL_SUCCESS,
                    backupFile = backupFile,
                    targets = targets,
                    statusString = "LOCAL_SUCCESS"
                )
            }

            var anyUploadSuccess = false
            var anyUploadFailed = false

            if (dropboxConnected) {
                onProgress?.invoke("Uploading to Dropbox...")
                val dbxSuccess = DropboxBackupManager.uploadBackup(context, backupFile)
                targets["dropbox"] = dbxSuccess
                if (dbxSuccess) anyUploadSuccess = true else anyUploadFailed = true
            }

            if (googleDriveConnected) {
                onProgress?.invoke("Uploading to Google Drive...")
                val gdSuccess = GoogleDriveBackupManager.uploadBackup(context, backupFile)
                targets["google_drive"] = gdSuccess
                if (gdSuccess) anyUploadSuccess = true else anyUploadFailed = true
            }

            val targetsStr = formatTargets(targets)
            val status = if (anyUploadSuccess) {
                if (anyUploadFailed) "PARTIAL_SUCCESS" else "SUCCESS"
            } else {
                "Upload failed"
            }

            prefs.edit(commit = true) {
                if (anyUploadSuccess) {
                    putLong("last_backup_timestamp", System.currentTimeMillis())
                }
                putString("last_backup_status", status)
                putString("last_backup_targets", targetsStr)
            }

            val outcome = when {
                anyUploadSuccess && !anyUploadFailed -> BackupOutcome.CLOUD_SUCCESS
                anyUploadSuccess -> BackupOutcome.CLOUD_PARTIAL_SUCCESS
                else -> BackupOutcome.CLOUD_FAILED
            }

            BackupExecutionResult(
                outcome = outcome,
                backupFile = backupFile,
                targets = targets,
                statusString = status
            )
        }
    }
}
