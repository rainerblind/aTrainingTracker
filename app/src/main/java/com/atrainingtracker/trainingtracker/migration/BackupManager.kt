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
    const val BACKUP_FILENAME = "aTrainingTracker_backup.attbackup"

    val jvmLock = java.util.concurrent.locks.ReentrantLock()

    class BackupLock(
        private val jvmLock: java.util.concurrent.locks.ReentrantLock,
        private val fileLock: java.nio.channels.FileLock?,
        private val raf: java.io.RandomAccessFile?
    ) : AutoCloseable {
        override fun close() {
            try {
                fileLock?.release()
            } catch (_: Exception) {}
            try {
                raf?.close()
            } catch (_: Exception) {}
            if (jvmLock.isHeldByCurrentThread) {
                jvmLock.unlock()
            }
        }
    }

    fun getBackupDir(context: Context): File {
        val dir = File(context.filesDir, "backups")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun acquireLock(context: Context): BackupLock? {
        if (!jvmLock.tryLock()) {
            return null
        }
        val lockFile = File(getBackupDir(context), "backup.lock")
        var raf: java.io.RandomAccessFile? = null
        var fileLock: java.nio.channels.FileLock? = null
        try {
            raf = java.io.RandomAccessFile(lockFile, "rw")
            fileLock = raf.channel.tryLock()
            if (fileLock == null) {
                raf.close()
                jvmLock.unlock()
                return null
            }
            return BackupLock(jvmLock, fileLock, raf)
        } catch (e: Exception) {
            try { fileLock?.release() } catch (_: Exception) {}
            try { raf?.close() } catch (_: Exception) {}
            if (jvmLock.isHeldByCurrentThread) {
                jvmLock.unlock()
            }
            return null
        }
    }

    interface ProgressListener {
        fun onProgress(message: String)
    }

    fun createBackup(context: Context, listener: ProgressListener? = null): File? {
        Log.i(TAG, "Starting state bundling process...")

        val lockHeld = jvmLock.isHeldByCurrentThread
        val lock = if (!lockHeld) acquireLock(context) else null
        if (!lockHeld && lock == null) {
            Log.w(TAG, "Backup already in progress; lock could not be acquired.")
            return null
        }

        try {
            val backupDir = getBackupDir(context)
            val tempBackupFile = File(backupDir, "$BACKUP_FILENAME.tmp")
            val canonicalBackupFile = File(backupDir, BACKUP_FILENAME)

            try {
                // 1. Stale staging file cleanup
                if (tempBackupFile.exists()) {
                    tempBackupFile.delete()
                }

                // 2. Checkpoint all databases to ensure file consistency (merge WAL)
                listener?.onProgress("Checkpointing databases...")
                checkpointAndCloseAllDatabases(context)

                // 3. Define source directories
                val dataDir = context.applicationInfo.dataDir
                val databasesDir = File(dataDir, "databases")
                val sharedPrefsDir = File(dataDir, "shared_prefs")
                val datastoreDir = File(context.filesDir, "datastore")

                // 4. Zip everything into co-located temporary staging file
                listener?.onProgress("Bundling files...")
                ZipOutputStream(BufferedOutputStream(FileOutputStream(tempBackupFile))).use { zos ->
                    if (databasesDir.exists()) zipFolder(databasesDir, "databases", zos)
                    if (sharedPrefsDir.exists()) zipFolder(sharedPrefsDir, "shared_prefs", zos)
                    if (datastoreDir.exists()) zipFolder(datastoreDir, "datastore", zos)
                }

                // 5. Co-located atomic move to canonical destination file
                try {
                    java.nio.file.Files.move(
                        tempBackupFile.toPath(),
                        canonicalBackupFile.toPath(),
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "ATOMIC_MOVE failed, falling back to REPLACE_EXISTING move", e)
                    java.nio.file.Files.move(
                        tempBackupFile.toPath(),
                        canonicalBackupFile.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING
                    )
                }

                Log.i(TAG, "Backup created successfully: ${canonicalBackupFile.absolutePath}")
                return canonicalBackupFile
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create backup", e)
                return null
            } finally {
                // Guarantee cleanup of temporary staging file on any exit or crash
                if (tempBackupFile.exists()) {
                    tempBackupFile.delete()
                }
            }
        } finally {
            lock?.close()
        }
    }

    private fun checkpointAndCloseAllDatabases(context: Context) {
        try {
            // Handle Java and Kotlin database access nuances safely
            // Use TRUNCATE to flush everything to disk and clear WAL files without a hard close (ATT-289)
            val databases = listOfNotNull(
                try { WorkoutSummariesDatabaseManager.getInstance(context).getDatabase() } catch (_: Throwable) { null },
                try { WorkoutSamplesDatabaseManager.getInstance(context).getDatabase() } catch (_: Throwable) { null },
                try { LapsDatabaseManager.getInstance(context).getDatabase() } catch (_: Throwable) { null },
                try { RoutesDatabaseManager.getInstance(context).getDatabase() } catch (_: Throwable) { null },
                try { WorkoutClusterDatabaseManager.getInstance(context).getDatabase() } catch (_: Throwable) { null },
                try { KnownLocationsDatabaseManager.getInstance(context).getDatabase() } catch (_: Throwable) { null },
                try { SportTypeEquipmentLinkManager.getInstance(context).getDatabase() } catch (_: Throwable) { null },
                try { TrackingViewsDatabaseManager.getInstance(context).getDatabase() } catch (_: Throwable) { null },
                try { DevicesDatabaseManager.getInstance(context).getDatabase() } catch (_: Throwable) { null },
                try { SportTypeDatabaseManager.getInstance(context).getDatabase() } catch (_: Throwable) { null },
                try { StravaUploadDbHelper(context).writableDatabase } catch (_: Throwable) { null },
                try { ExportStatusDatabaseManager.getInstance(context)?.getDatabase() } catch (_: Throwable) { null },
                try { SegmentsDatabaseManager.getInstance(context).getDatabase() } catch (_: Throwable) { null },
                try { EquipmentDbHelper(context).writableDatabase } catch (_: Throwable) { null },
                try { ActiveDevicesDbHelper(context).writableDatabase } catch (_: Throwable) { null }
            )

            databases.forEach { db ->
                try {
                    if (db.isOpen) {
                        // Checkpoint WAL to main file and truncate log
                        db.rawQuery("PRAGMA wal_checkpoint(TRUNCATE);", null).use { 
                            if (it.moveToFirst()) {
                                Log.v(TAG, "Checkpoint TRUNCATE result: ${it.getInt(0)}")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error checkpointing database", e)
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to checkpoint databases", t)
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
