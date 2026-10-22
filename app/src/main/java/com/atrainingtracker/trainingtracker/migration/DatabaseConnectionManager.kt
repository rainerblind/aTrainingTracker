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
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0/>.
 */

package com.atrainingtracker.trainingtracker.migration

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager
import com.atrainingtracker.trainingtracker.database.*
import com.atrainingtracker.trainingtracker.exporter.db.ExportStatusDatabaseManager
import com.atrainingtracker.trainingtracker.exporter.db.StravaUploadDbHelper
import com.atrainingtracker.trainingtracker.segments.SegmentsDatabaseManager

/**
 * Manages tracking, checkpointing, and safe quiescence of SQLite database connections
 * during migration and restore workflows (REQ-MIG-041.3).
 */
object DatabaseConnectionManager {
    private const val TAG = "DatabaseConnectionManager"

    @JvmField
    val databaseLock = java.util.concurrent.locks.ReentrantReadWriteLock()

    @JvmStatic
    fun checkpointAll(context: Context) {
        quiesceAllDatabases(context, closeConnections = false)
    }

    /**
     * Executes PRAGMA wal_checkpoint with retry backoff when BUSY (result == 1) is returned.
     * Retries up to maxRetries times with exponential backoff (10ms, 25ms, 50ms).
     */
    @JvmStatic
    fun checkpointWithRetry(db: SQLiteDatabase, mode: String = "TRUNCATE", maxRetries: Int = 3): Boolean {
        if (!db.isOpen) return false
        val backoffMs = longArrayOf(10L, 25L, 50L)
        for (attempt in 0..maxRetries) {
            try {
                db.rawQuery("PRAGMA wal_checkpoint($mode);", null).use { cursor ->
                    if (cursor.moveToFirst()) {
                        val busy = cursor.getInt(0)
                        if (busy == 0) {
                            Log.v(TAG, "WAL Checkpoint $mode succeeded on attempt $attempt")
                            return true
                        } else {
                            Log.w(TAG, "WAL Checkpoint $mode returned BUSY on attempt $attempt")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "WAL Checkpoint $mode exception on attempt $attempt", e)
            }
            if (attempt < maxRetries) {
                val delay = if (attempt < backoffMs.size) backoffMs[attempt] else 50L
                try {
                    Thread.sleep(delay)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                }
            }
        }
        return false
    }

    /**
     * Safely checkpoints write-ahead logs (WAL) to main database files using TRUNCATE,
     * and optionally closes open database handles to prevent file locks during replacement.
     */
    @JvmStatic
    fun quiesceAllDatabases(context: Context, closeConnections: Boolean = true) {
        try {
            val databases = getActiveDatabases(context)
            for (db in databases) {
                try {
                    if (db.isOpen) {
                        checkpointWithRetry(db, mode = "TRUNCATE")
                        if (closeConnections) {
                            db.close()
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error checkpointing/quiescing database", e)
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to quiesce databases", t)
        }
    }

    @JvmStatic
    fun getActiveDatabases(context: Context): List<SQLiteDatabase> {
        return listOfNotNull(
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
            try { StravaUploadDbHelper.getInstance(context).writableDatabase } catch (_: Throwable) { null },
            try { ExportStatusDatabaseManager.getInstance(context)?.getDatabase() } catch (_: Throwable) { null },
            try { SegmentsDatabaseManager.getInstance(context).getDatabase() } catch (_: Throwable) { null },
            try { EquipmentDbHelper.getInstance(context).writableDatabase } catch (_: Throwable) { null },
            try { ActiveDevicesDbHelper.getInstance(context).writableDatabase } catch (_: Throwable) { null }
        )
    }
}
