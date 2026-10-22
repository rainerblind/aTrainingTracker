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
import android.util.Log
import java.io.File

/**
 * Manages transactional journaling and disaster-recovery for full restore operations (REQ-MIG-041.4, REQ-MIG-041.5).
 * Guarantees that if the process is terminated, killed by OS, or restarted midway through
 * a restore operation, the system automatically detects the interrupted state and rolls back cleanly.
 */
object RestoreJournalManager {

    private const val TAG = "RestoreJournalManager"
    private const val JOURNAL_FILENAME = "restore_journal.state"

    enum class RestorePhase {
        IDLE,
        STAGED,     // Rollback snapshot is created and verified
        SWAPPING,   // Files are being moved into active directories
        COMMITTED   // Files are in place; ready to delete rollback snapshot
    }

    private fun getJournalFile(context: Context): File {
        val dataDir = context.applicationInfo?.dataDir?.let { File(it) } ?: context.filesDir.parentFile ?: context.filesDir
        val primaryFile = File(dataDir, JOURNAL_FILENAME)
        val legacyCacheFile = File(context.cacheDir, JOURNAL_FILENAME)
        return if (!primaryFile.exists() && legacyCacheFile.exists()) legacyCacheFile else primaryFile
    }

    @Synchronized
    fun recordPhase(context: Context, phase: RestorePhase) {
        val file = getJournalFile(context)
        if (phase == RestorePhase.IDLE) {
            if (file.exists()) file.delete()
        } else {
            try {
                file.writeText(phase.name)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to write restore journal phase: $phase", e)
            }
        }
    }

    @Synchronized
    fun getActivePhase(context: Context): RestorePhase {
        val file = getJournalFile(context)
        if (!file.exists()) return RestorePhase.IDLE
        return try {
            val text = file.readText().trim()
            RestorePhase.valueOf(text)
        } catch (_: Exception) {
            RestorePhase.IDLE
        }
    }

    @Synchronized
    fun clearJournal(context: Context) {
        val file = getJournalFile(context)
        if (file.exists()) {
            file.delete()
        }
    }

    /**
     * Startup recovery hook called from TrainingApplication.onCreate() to repair any
     * interrupted restore operations following app crashes, reboots, or process termination.
     */
    @JvmStatic
    fun recoverInterruptedRestoreIfNecessary(context: Context) {
        val phase = getActivePhase(context)
        if (phase == RestorePhase.STAGED || phase == RestorePhase.SWAPPING) {
            Log.w(TAG, "Detected interrupted restore in phase $phase; executing emergency rollback to prior state.")
            MigrationEngine.executeEmergencyRollback(context)
        }
        clearJournal(context)
        SecureZipExtractor.purgeStaleStagingDirs(context)
    }
}
