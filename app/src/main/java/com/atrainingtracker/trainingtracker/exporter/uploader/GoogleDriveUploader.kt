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
 */

package com.atrainingtracker.trainingtracker.exporter.uploader

import android.content.Context
import android.util.Log
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.cloud.googledrive.GoogleDriveAuthManager
import com.atrainingtracker.trainingtracker.cloud.googledrive.GoogleDriveClient
import com.atrainingtracker.trainingtracker.exporter.BaseExporter
import com.atrainingtracker.trainingtracker.exporter.ExportInfo
import com.atrainingtracker.trainingtracker.exporter.FileFormat
import java.io.File
import java.io.IOException

/**
 * Uploads generated workout activity files (FIT, TCX, GPX, CSV) to Google Drive
 * within directory `aTrainingTracker/Workouts/`.
 */
open class GoogleDriveUploader(context: Context) : BaseExporter(context) {

    companion object {
        private const val TAG = "GoogleDriveUploader"
        private val DEBUG = TrainingApplication.getDebug(true)
    }

    // Extensible for unit test mocking
    open fun createClient(): GoogleDriveClient {
        return GoogleDriveClient(
            tokenProvider = { TrainingApplication.getGoogleDriveAuthToken() },
            tokenRefresher = { GoogleDriveAuthManager.refreshTokenSync(mContext).getOrNull() }
        )
    }

    open fun getBaseFile(filename: String): File {
        return File(getBaseDirFile(mContext), filename)
    }

    public override fun doExport(exportInfo: ExportInfo): ExportResult {
        val filename = exportInfo.shortPath
        val file = getBaseFile(filename)
        if (!file.exists()) {
            return ExportResult(false, false, "Google Drive file does not exist: $file")
        }

        val token = TrainingApplication.getGoogleDriveAuthToken()
        if (token.isNullOrBlank() || !TrainingApplication.uploadToGoogleDrive()) {
            return ExportResult(false, false, "Google Drive credential is null or not linked")
        }

        try {
            val client = createClient()
            val folderId = client.ensureFolderHierarchy(listOf("aTrainingTracker", "Workouts"))
            if (folderId == null) {
                Log.e(TAG, "Failed to resolve folder hierarchy aTrainingTracker/Workouts (HTTP ${client.lastHttpCode}: ${client.lastErrorMessage})")
                val errorMsg = if (client.lastHttpCode == 401) {
                    mContext.getString(R.string.google_drive_error_auth_expired)
                } else {
                    "Failed to resolve Google Drive folder hierarchy"
                }
                return ExportResult(false, false, errorMsg)
            }

            val driveFileName = exportInfo.fileName
            val mimeType = resolveMimeType(exportInfo.fileFormat)
            val success = client.uploadOrOverwriteFile(folderId, driveFileName, mimeType, file)
            return if (success) {
                TrainingApplication.setGoogleDriveLastSyncTimestamp(System.currentTimeMillis())
                TrainingApplication.setGoogleDriveLastSyncStatus("SUCCESS")
                if (DEBUG) Log.i(TAG, "Successfully uploaded $driveFileName to Google Drive")
                ExportResult(true, false, "successfully uploaded $driveFileName to Google Drive")
            } else {
                TrainingApplication.setGoogleDriveLastSyncStatus("FAILED")
                Log.e(TAG, "Failed to upload $driveFileName to Google Drive")
                ExportResult(false, false, "Failed to upload $driveFileName to Google Drive")
            }
        } catch (e: IOException) {
            Log.e(TAG, "IOException uploading to Google Drive: ${e.message}", e)
            return ExportResult(false, false, "IOException: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Exception uploading to Google Drive: ${e.message}", e)
            return ExportResult(false, false, "Exception: ${e.message}")
        }
    }

    private fun resolveMimeType(format: FileFormat): String {
        return when (format) {
            FileFormat.FIT -> "application/vnd.ant.fit"
            FileFormat.TCX -> "application/vnd.garmin.tcx+xml"
            FileFormat.GPX -> "application/gpx+xml"
            FileFormat.CSV -> "text/csv"
            FileFormat.GC -> "application/json"
            else -> "application/octet-stream"
        }
    }
}
