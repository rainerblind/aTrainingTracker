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
import com.atrainingtracker.BuildConfig
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.dropbox.core.DbxRequestConfig
import com.dropbox.core.v2.DbxClientV2
import com.dropbox.core.v2.files.WriteMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream

/**
 * Handles backup operations specifically for Dropbox.
 */
object DropboxBackupManager {
    private const val TAG = "DropboxBackupManager"
    private const val DROPBOX_BACKUP_PATH = "/Backups/aTrainingTracker_backup.attbackup"

    suspend fun uploadBackup(context: Context, backupFile: File): Boolean = withContext(Dispatchers.IO) {
        val credential = TrainingApplication.readDropboxCredential()
        if (credential == null) {
            Log.e(TAG, "No Dropbox credentials found")
            return@withContext false
        }

        try {
            val config = DbxRequestConfig(BuildConfig.DROPBOX_APP_KEY)
            val client = DbxClientV2(config, credential)

            FileInputStream(backupFile).use { inputStream ->
                client.files().uploadBuilder(DROPBOX_BACKUP_PATH)
                    .withMode(WriteMode.OVERWRITE)
                    .uploadAndFinish(inputStream)
            }
            Log.i(TAG, "Successfully uploaded backup to Dropbox")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload backup to Dropbox", e)
            false
        }
    }

    suspend fun downloadBackup(context: Context, destinationFile: File): Boolean = withContext(Dispatchers.IO) {
        val credential = TrainingApplication.readDropboxCredential()
        if (credential == null) {
            Log.e(TAG, "No Dropbox credentials found")
            return@withContext false
        }

        try {
            val config = DbxRequestConfig(BuildConfig.DROPBOX_APP_KEY)
            val client = DbxClientV2(config, credential)

            destinationFile.outputStream().use { outputStream ->
                client.files().download(DROPBOX_BACKUP_PATH).download(outputStream)
            }
            Log.i(TAG, "Successfully downloaded backup from Dropbox")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download backup from Dropbox", e)
            false
        }
    }
}
