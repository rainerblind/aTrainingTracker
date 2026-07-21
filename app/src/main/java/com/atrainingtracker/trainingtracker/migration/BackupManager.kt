/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.atrainingtracker.trainingtracker.migration

import android.content.Context
import android.util.Log
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager
import com.atrainingtracker.trainingtracker.database.*
import com.atrainingtracker.trainingtracker.exporter.db.ExportStatusDatabaseManager
import com.atrainingtracker.trainingtracker.exporter.db.StravaUploadDbHelper
import com.atrainingtracker.trainingtracker.segments.SegmentsDatabaseManager
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Handles the creation of a complete application state bundle (.attbackup).
 */
object BackupManager {
    private const val TAG = "BackupManager"
    private const val BACKUP_FILENAME = "aTrainingTracker_backup.attbackup"

    fun createBackup(context: Context): File? {
        Log.i(TAG, "Starting state bundling process...")
        
        try {
            checkpointAndCloseAllDatabases(context)

            val dataDir = context.applicationInfo.dataDir
            val databasesDir = File(dataDir, "databases")
            val sharedPrefsDir = File(dataDir, "shared_prefs")
            val datastoreDir = File(context.filesDir, "datastore")

            val backupFile = File(context.cacheDir, BACKUP_FILENAME)
            if (backupFile.exists()) backupFile.delete()

            ZipOutputStream(BufferedOutputStream(FileOutputStream(backupFile))).use { zos ->
                if (databasesDir.exists()) zipFolder(databasesDir, "databases", zos)
                if (sharedPrefsDir.exists()) zipFolder(sharedPrefsDir, "shared_prefs", zos)
                if (datastoreDir.exists()) zipFolder(datastoreDir, "datastore", zos)
            }

            Log.i(TAG, "Backup created successfully: ${backupFile.absolutePath}")
            return backupFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create backup", e)
            return null
        }
    }

    private fun checkpointAndCloseAllDatabases(context: Context) {
        // Handle Java and Kotlin database access nuances
        val databases = listOfNotNull(
            WorkoutSummariesDatabaseManager.getInstance(context).database,
            WorkoutSamplesDatabaseManager.getInstance(context).database,
            LapsDatabaseManager.getInstance(context).database,
            RoutesDatabaseManager.getInstance(context).provideBackupDatabase(),
            WorkoutClusterDatabaseManager.getInstance(context).provideBackupDatabase(),
            KnownLocationsDatabaseManager.getInstance(context).database,
            SportTypeEquipmentLinkManager.getInstance(context).provideBackupDatabase(),
            TrackingViewsDatabaseManager.getInstance(context).database,
            DevicesDatabaseManager.getInstance(context).database,
            SportTypeDatabaseManager.getInstance(context).database,
            StravaUploadDbHelper(context).writableDatabase,
            ExportStatusDatabaseManager.getInstance(context).database,
            SegmentsDatabaseManager.getInstance(context).database,
            EquipmentDbHelper(context).writableDatabase,
            ActiveDevicesDbHelper(context).writableDatabase
        )

        databases.forEach { db ->
            try {
                if (db.isOpen) {
                    db.rawQuery("PRAGMA wal_checkpoint(FULL);", null).use { it.moveToFirst() }
                    db.close()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error closing database", e)
            }
        }
    }

    private fun zipFolder(folder: File, internalPath: String, zos: ZipOutputStream) {
        val files = folder.listFiles() ?: return
        val buffer = ByteArray(8192)
        for (file in files) {
            if (file.isDirectory) {
                zipFolder(file, "$internalPath/${file.name}", zos)
                continue
            }
            if (file.name.endsWith("-journal") || file.name.endsWith("-shm") || file.name.endsWith("-wal")) {
                continue
            }
            val entryPath = "$internalPath/${file.name}"
            zos.putNextEntry(ZipEntry(entryPath))
            FileInputStream(file).use { fis ->
                BufferedInputStream(fis).use { bis ->
                    var bytesRead: Int
                    while (bis.read(buffer).also { bytesRead = it } != -1) {
                        zos.write(buffer, 0, bytesRead)
                    }
                }
            }
            zos.closeEntry()
        }
    }
}
