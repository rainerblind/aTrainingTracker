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
import android.database.sqlite.SQLiteDatabase
import android.os.Build
import android.util.Log
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Handles the creation of a complete application state bundle (.attbackup).
 * Enforces atomic snapshots, storage space pre-verification, and concurrent write safety (REQ-DAT-031).
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

    /**
     * Calculates the total size of all active database and WAL files in bytes (REQ-DAT-031.1).
     */
    fun calculateTotalDatabaseSize(context: Context): Long {
        val databasesDir = File(context.applicationInfo.dataDir, "databases")
        if (!databasesDir.exists()) return 0L
        val files = databasesDir.listFiles() ?: return 0L
        return files.filter { it.isFile && (it.name.endsWith(".db") || it.name.endsWith("-wal")) }
            .sumOf { it.length() }
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
            val tempSnapshotDir = File(backupDir, "temp_db_snapshot")

            try {
                // 1. Stale staging file & directory cleanup
                if (tempBackupFile.exists()) {
                    tempBackupFile.delete()
                }
                if (tempSnapshotDir.exists()) {
                    tempSnapshotDir.deleteRecursively()
                }

                // 2. Storage Space Pre-Verification (REQ-DAT-031.1)
                val totalDbSize = calculateTotalDatabaseSize(context)
                val usableSpace = context.filesDir.usableSpace
                val requiredSpace = (totalDbSize * 1.5).toLong()
                if (usableSpace < requiredSpace) {
                    val msg = "Insufficient storage space: required at least $requiredSpace bytes, available $usableSpace bytes"
                    Log.w(TAG, msg)
                    listener?.onProgress("Storage full: cannot create backup")
                    return null
                }

                tempSnapshotDir.mkdirs()

                // 3. Snapshot databases into isolated staging directory (REQ-DAT-031.2 / REQ-DAT-031.3)
                listener?.onProgress("Snapshotting databases...")
                snapshotDatabases(context, tempSnapshotDir, listener)

                // 4. Staging Snapshot Integrity Verification (REQ-DAT-031.5)
                listener?.onProgress("Verifying database integrity...")
                if (!verifySnapshotIntegrity(tempSnapshotDir)) {
                    Log.e(TAG, "Snapshot integrity verification failed. Aborting backup.")
                    return null
                }

                // 5. Define non-database source directories
                val dataDir = context.applicationInfo.dataDir
                val sharedPrefsDir = File(dataDir, "shared_prefs")
                val datastoreDir = File(context.filesDir, "datastore")

                // 6. Zip everything into co-located temporary staging file
                listener?.onProgress("Bundling files...")
                ZipOutputStream(BufferedOutputStream(FileOutputStream(tempBackupFile))).use { zos ->
                    if (tempSnapshotDir.exists()) zipFolder(tempSnapshotDir, "databases", zos, includeWal = true)
                    if (sharedPrefsDir.exists()) zipFolder(sharedPrefsDir, "shared_prefs", zos)
                    if (datastoreDir.exists()) zipFolder(datastoreDir, "datastore", zos)
                }

                // 7. Co-located atomic move to canonical destination file
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
                // Guarantee cleanup of temporary staging files and directories on any exit or crash
                if (tempBackupFile.exists()) {
                    tempBackupFile.delete()
                }
                if (tempSnapshotDir.exists()) {
                    tempSnapshotDir.deleteRecursively()
                }
            }
        } finally {
            lock?.close()
        }
    }

    private fun snapshotDatabases(context: Context, stagingDir: File, listener: ProgressListener?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            snapshotDatabasesViaVacuum(context, stagingDir)
        } else {
            snapshotDatabasesViaFallback(context, stagingDir)
        }
    }

    /**
     * API 30+ Primary Path: Snapshot databases via SQLite VACUUM INTO (REQ-DAT-031.2).
     * Creates an atomic, defragmented, non-WAL snapshot without blocking concurrent writers.
     */
    fun snapshotDatabasesViaVacuum(context: Context, stagingDir: File) {
        val activeDatabases = DatabaseConnectionManager.getActiveDatabases(context)
        val snapshottedFiles = mutableSetOf<String>()

        for (db in activeDatabases) {
            if (!db.isOpen) continue
            val dbPath = db.path ?: continue
            val dbFile = File(dbPath)
            val dbName = dbFile.name
            val targetFile = File(stagingDir, dbName)
            if (targetFile.exists()) targetFile.delete()

            try {
                // SQLite VACUUM INTO creates an atomic, crash-consistent standalone database file
                val escapedPath = targetFile.absolutePath.replace("'", "''")
                db.execSQL("VACUUM INTO '$escapedPath';")
                snapshottedFiles.add(dbName)
                Log.d(TAG, "Successfully snapshotted $dbName via VACUUM INTO")
            } catch (e: Exception) {
                Log.w(TAG, "VACUUM INTO failed for $dbName, falling back to copy", e)
                DatabaseConnectionManager.checkpointWithRetry(db, mode = "FULL")
                if (dbFile.exists()) {
                    dbFile.copyTo(targetFile, overwrite = true)
                    snapshottedFiles.add(dbName)
                }
            }
        }

        // Copy any remaining unmanaged .db files from databases directory if not already snapshotted
        val databasesDir = File(context.applicationInfo.dataDir, "databases")
        if (databasesDir.exists()) {
            val files = databasesDir.listFiles() ?: emptyArray()
            for (file in files) {
                if (file.isFile && file.name.endsWith(".db") && !snapshottedFiles.contains(file.name)) {
                    val targetFile = File(stagingDir, file.name)
                    file.copyTo(targetFile, overwrite = true)
                    snapshottedFiles.add(file.name)
                }
            }
        }
    }

    /**
     * API 26-29 Fallback Path: Writer-synchronized checkpoint and copy (REQ-DAT-031.3).
     */
    fun snapshotDatabasesViaFallback(context: Context, stagingDir: File) {
        val lock = DatabaseConnectionManager.databaseLock.writeLock()
        lock.lock()
        try {
            val activeDatabases = DatabaseConnectionManager.getActiveDatabases(context)
            for (db in activeDatabases) {
                if (db.isOpen) {
                    DatabaseConnectionManager.checkpointWithRetry(db, mode = "FULL")
                }
            }

            val databasesDir = File(context.applicationInfo.dataDir, "databases")
            if (databasesDir.exists()) {
                val files = databasesDir.listFiles() ?: emptyArray()
                for (file in files) {
                    if (file.isFile && (file.name.endsWith(".db") || file.name.endsWith("-wal"))) {
                        val targetFile = File(stagingDir, file.name)
                        file.copyTo(targetFile, overwrite = true)
                    }
                }
            }
        } finally {
            lock.unlock()
        }
    }

    /**
     * Verifies that all snapshotted database files pass PRAGMA integrity_check (REQ-DAT-031.5).
     */
    fun verifySnapshotIntegrity(stagingDir: File): Boolean {
        val dbFiles = stagingDir.listFiles()?.filter { it.isFile && it.name.endsWith(".db") } ?: return true
        for (dbFile in dbFiles) {
            try {
                SQLiteDatabase.openDatabase(
                    dbFile.absolutePath,
                    null,
                    SQLiteDatabase.OPEN_READONLY
                ).use { db ->
                    db.rawQuery("PRAGMA integrity_check;", null).use { cursor ->
                        if (cursor.moveToFirst()) {
                            val result = cursor.getString(0)
                            if (result != "ok") {
                                Log.e(TAG, "Integrity check failed for snapshot ${dbFile.name}: $result")
                                return false
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception during integrity check for snapshot ${dbFile.name}", e)
                return false
            }
        }
        return true
    }

    private fun zipFolder(folder: File, internalPath: String, zos: ZipOutputStream, includeWal: Boolean = false) {
        val files = folder.listFiles() ?: return
        val buffer = ByteArray(8192)
        for (file in files) {
            if (file.isDirectory) {
                zipFolder(file, "$internalPath/${file.name}", zos, includeWal)
                continue
            }
            if (file.name.endsWith("-journal") || file.name.endsWith("-shm") || (!includeWal && file.name.endsWith("-wal"))) {
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
