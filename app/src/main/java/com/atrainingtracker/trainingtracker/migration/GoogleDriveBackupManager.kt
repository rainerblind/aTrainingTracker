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
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.cloud.googledrive.GoogleDriveAuthManager
import com.atrainingtracker.trainingtracker.cloud.googledrive.GoogleDriveClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Handles database backup upload and download specifically for Google Drive.
 * Files are stored under the app folder hierarchy `aTrainingTracker/Backups/`.
 */
object GoogleDriveBackupManager {
    private const val TAG = "GoogleDriveBackupManager"
    const val GOOGLE_DRIVE_BACKUP_FILENAME = "aTrainingTracker_backup.attbackup"
    val BACKUP_FOLDER_HIERARCHY = listOf("aTrainingTracker", "Backups")

    // Extensible client provider for dependency injection in unit tests
    var clientProvider: (Context) -> GoogleDriveClient = { context ->
        GoogleDriveClient(
            tokenProvider = { TrainingApplication.getGoogleDriveAuthToken() },
            tokenRefresher = { GoogleDriveAuthManager.refreshTokenSync(context.applicationContext ?: context).getOrNull() }
        )
    }

    @androidx.annotation.VisibleForTesting
    fun resetForTesting() {
        clientProvider = { context ->
            GoogleDriveClient(
                tokenProvider = { TrainingApplication.getGoogleDriveAuthToken() },
                tokenRefresher = { GoogleDriveAuthManager.refreshTokenSync(context.applicationContext ?: context).getOrNull() }
            )
        }
    }

    suspend fun uploadBackup(context: Context, backupFile: File): Boolean = withContext(Dispatchers.IO) {
        val token = TrainingApplication.getGoogleDriveAuthToken()
        if (token.isNullOrBlank() || !TrainingApplication.uploadToGoogleDrive()) {
            Log.e(TAG, "No Google Drive credentials found or Google Drive disabled")
            return@withContext false
        }

        if (!backupFile.exists()) {
            Log.e(TAG, "Backup file does not exist: ${backupFile.absolutePath}")
            return@withContext false
        }

        try {
            val client = clientProvider(context)
            val folderId = client.ensureFolderHierarchy(BACKUP_FOLDER_HIERARCHY)
            if (folderId == null) {
                Log.e(TAG, "Failed to resolve Google Drive folder hierarchy for backups")
                TrainingApplication.setGoogleDriveLastSyncStatus("FAILED")
                return@withContext false
            }

            val success = client.uploadOrOverwriteFile(
                folderId = folderId,
                fileName = GOOGLE_DRIVE_BACKUP_FILENAME,
                mimeType = "application/octet-stream",
                file = backupFile
            )

            if (success) {
                TrainingApplication.setGoogleDriveLastSyncTimestamp(System.currentTimeMillis())
                TrainingApplication.setGoogleDriveLastSyncStatus("SUCCESS")
                Log.i(TAG, "Successfully uploaded backup to Google Drive")
                true
            } else {
                TrainingApplication.setGoogleDriveLastSyncStatus("FAILED")
                Log.e(TAG, "Failed to upload backup to Google Drive")
                false
            }
        } catch (e: Exception) {
            TrainingApplication.setGoogleDriveLastSyncStatus("FAILED")
            Log.e(TAG, "Error uploading backup to Google Drive", e)
            false
        }
    }

    suspend fun downloadBackup(context: Context, destinationFile: File): Boolean = withContext(Dispatchers.IO) {
        val token = TrainingApplication.getGoogleDriveAuthToken()
        if (token.isNullOrBlank() || !TrainingApplication.uploadToGoogleDrive()) {
            Log.e(TAG, "No Google Drive credentials found or Google Drive disabled")
            return@withContext false
        }

        try {
            val client = clientProvider(context)
            val folderId = client.ensureFolderHierarchy(BACKUP_FOLDER_HIERARCHY)
            if (folderId == null) {
                Log.e(TAG, "Failed to resolve Google Drive folder hierarchy for backups")
                return@withContext false
            }

            val success = client.downloadFile(folderId, GOOGLE_DRIVE_BACKUP_FILENAME, destinationFile)
            if (success) {
                Log.i(TAG, "Successfully downloaded backup from Google Drive")
                true
            } else {
                Log.e(TAG, "Failed to download backup from Google Drive")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading backup from Google Drive", e)
            false
        }
    }
}
