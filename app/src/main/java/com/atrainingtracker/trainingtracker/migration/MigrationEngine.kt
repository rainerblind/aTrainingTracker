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
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import com.atrainingtracker.R
import java.io.File
import java.io.IOException

/**
 * Handles transactional restoration of application state from an .attbackup archive (REQ-MIG-041).
 * Enforces pre-restore archive validation, SQLite integrity checking, schema version bounds,
 * connection teardown, atomic rollback snapshots, and atomic inode/directory swap.
 */
object MigrationEngine {
    private const val TAG = "MigrationEngine"

    interface ProgressListener {
        fun onProgress(message: String)
    }

    sealed class ValidationResult {
        object Valid : ValidationResult()
        data class Invalid(val reason: String, @StringRes val messageResId: Int = R.string.restore_failed) : ValidationResult()
    }

    @VisibleForTesting
    var appRestarter: (Context) -> Unit = { context ->
        val intent = context.packageManager?.getLaunchIntentForPackage(context.packageName)
        val componentName = intent?.component
        if (componentName != null) {
            val mainIntent = Intent.makeRestartActivityTask(componentName)
            context.startActivity(mainIntent)
        }
        Runtime.getRuntime().exit(0)
    }

    /**
     * Inspects and validates an unpacked archive before any production data is touched (REQ-MIG-041.1, REQ-MIG-041.2).
     *
     * @param tempDir The staging directory containing extracted archive contents.
     * @return [ValidationResult.Valid] if integrity and schemas are verified, or [ValidationResult.Invalid] with reason and string resource.
     * @throws SecurityException if an illegal path traversal or unauthorized entry is inspected.
     */
    fun validateBackupArchive(tempDir: File): ValidationResult {
        val dbDir = File(tempDir, "databases")
        if (!dbDir.exists() || !dbDir.isDirectory) {
            return ValidationResult.Invalid("Archive missing databases directory", R.string.backup_restore_error_missing_databases)
        }

        val dbFiles = dbDir.listFiles { f -> f.isFile && f.extension == "db" } ?: emptyArray()
        if (dbFiles.isEmpty()) {
            return ValidationResult.Invalid("No SQLite databases found in backup archive", R.string.backup_restore_error_missing_databases)
        }

        if (!File(dbDir, "WorkoutSummaries.db").exists()) {
            return ValidationResult.Invalid("Core WorkoutSummaries.db missing from backup archive", R.string.backup_restore_error_missing_databases)
        }

        for (dbFile in dbFiles) {
            var db: SQLiteDatabase? = null
            try {
                db = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
                
                // 1. SQLite integrity check (PRAGMA integrity_check verifies B-trees, freelists, and indexes)
                db.rawQuery("PRAGMA integrity_check;", null).use { cursor ->
                    if (!cursor.moveToFirst()) {
                        return ValidationResult.Invalid("Integrity check returned empty for ${dbFile.name}", R.string.backup_restore_error_integrity_failed)
                    }
                    val checkResult = cursor.getString(0)
                    if (!checkResult.equals("ok", ignoreCase = true)) {
                        return ValidationResult.Invalid("Integrity check failed for ${dbFile.name}: $checkResult", R.string.backup_restore_error_integrity_failed)
                    }
                }

                // 2. Schema version check against supported bounds
                val version = db.version
                if (!DatabaseSchemaRegistry.isVersionSupported(dbFile.name, version)) {
                    val maxAllowed = DatabaseSchemaRegistry.getMaxSupportedVersion(dbFile.name)
                    return ValidationResult.Invalid("Database ${dbFile.name} schema version $version exceeds supported maximum $maxAllowed", R.string.backup_restore_error_schema_downgrade)
                }
            } catch (e: Exception) {
                return ValidationResult.Invalid("Failed to validate database ${dbFile.name}: ${e.message}", R.string.backup_restore_error_integrity_failed)
            } finally {
                try {
                    db?.close()
                } catch (_: Throwable) {}
            }
        }

        return ValidationResult.Valid
    }

    /**
     * Checkpoints WAL and closes active SQLite connections prior to file substitution (REQ-MIG-041.3).
     *
     * @param context Application context used to query active database singletons.
     */
    fun closeOpenDatabaseConnections(context: Context) {
        DatabaseConnectionManager.quiesceAllDatabases(context, closeConnections = true)
    }

    /**
     * Executes emergency rollback of local data using the snapshot in backup_rollback (REQ-MIG-041.4).
     * Verifies that the rollback directory contains non-empty snapshots before performing any destructive wipe.
     *
     * @param context Application context.
     * @throws IOException If file rollback fails during disk restoration.
     */
    @JvmStatic
    fun executeEmergencyRollback(context: Context) {
        val dataDir = context.applicationInfo?.dataDir?.let { File(it) } ?: context.filesDir.parentFile ?: context.filesDir
        val primaryRollbackDir = File(dataDir, "backup_rollback")
        val cacheRollbackDir = File(context.cacheDir, "backup_rollback")
        val rollbackDir = if (primaryRollbackDir.exists()) primaryRollbackDir else cacheRollbackDir

        val rollbackDb = File(rollbackDir, "databases")
        if (!rollbackDir.exists() || !rollbackDb.exists() || rollbackDb.listFiles().isNullOrEmpty()) {
            Log.w(TAG, "Emergency rollback aborted: No valid rollback snapshot found in $rollbackDir")
            return
        }

        val databasesDir = File(dataDir, "databases")
        val sharedPrefsDir = File(dataDir, "shared_prefs")
        val datastoreDir = File(context.filesDir, "datastore")

        val rollbackPrefs = File(rollbackDir, "shared_prefs")
        val rollbackDatastore = File(rollbackDir, "datastore")

        Log.w(TAG, "Restoring previous application state from rollback snapshot...")
        try {
            databasesDir.deleteRecursively()
            sharedPrefsDir.deleteRecursively()
            datastoreDir.deleteRecursively()

            rollbackDb.copyRecursively(databasesDir, overwrite = true)
            if (rollbackPrefs.exists()) rollbackPrefs.copyRecursively(sharedPrefsDir, overwrite = true)
            if (rollbackDatastore.exists()) rollbackDatastore.copyRecursively(datastoreDir, overwrite = true)

            Log.i(TAG, "Emergency rollback completed successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Critical: Error during emergency rollback", e)
        } finally {
            rollbackDir.deleteRecursively()
        }
    }

    /**
     * Performs a transactional full restore: pre-validates content, checkpoints open databases,
     * creates an atomic rollback snapshot, replaces content via atomic directory rename/swap, and restarts the application (REQ-MIG-041).
     *
     * @param context Application context.
     * @param backupFile The archive file to restore.
     * @param listener Optional progress observer.
     * @return True if restore and restart succeeded, false if rejected or failed.
     * @throws SecurityException If the archive breaches path traversal security bounds.
     * @throws IOException If file system swap operations encounter unrecoverable errors.
     */
    fun performFullRestore(context: Context, backupFile: File, listener: ProgressListener? = null): Boolean {
        Log.i(TAG, "Starting full restore from ${backupFile.absolutePath}")

        val dataDir = context.applicationInfo?.dataDir?.let { File(it) } ?: context.filesDir.parentFile ?: context.filesDir
        val tempDir = File(dataDir, "restore_staging")
        val rollbackDir = File(dataDir, "backup_rollback")

        try {
            // 1. Unzip to temporary directory
            listener?.onProgress("Unpacking archive...")
            if (tempDir.exists()) tempDir.deleteRecursively()
            tempDir.mkdirs()

            SecureZipExtractor.extractBackupArchive(backupFile, tempDir)

            // 2. Pre-validate archive structure, integrity, and schema versions (REQ-MIG-041.1, REQ-MIG-041.2)
            listener?.onProgress("Validating backup content...")
            val validation = validateBackupArchive(tempDir)
            if (validation is ValidationResult.Invalid) {
                Log.e(TAG, "Restore rejected during pre-validation: ${validation.reason}")
                tempDir.deleteRecursively()
                return false
            }

            // 3. Checkpoint WAL and close active database singletons (REQ-MIG-041.3)
            listener?.onProgress("Closing database connections...")
            closeOpenDatabaseConnections(context)

            // 4. Create rollback snapshot before modifying any local state (REQ-MIG-041.4)
            listener?.onProgress("Creating rollback snapshot...")
            if (rollbackDir.exists()) rollbackDir.deleteRecursively()
            rollbackDir.mkdirs()

            val databasesDir = File(dataDir, "databases")
            val sharedPrefsDir = File(dataDir, "shared_prefs")
            val datastoreDir = File(context.filesDir, "datastore")

            val rollbackDb = File(rollbackDir, "databases")
            val rollbackPrefs = File(rollbackDir, "shared_prefs")
            val rollbackDatastore = File(rollbackDir, "datastore")

            if (databasesDir.exists()) databasesDir.copyRecursively(rollbackDb, overwrite = true)
            if (sharedPrefsDir.exists()) sharedPrefsDir.copyRecursively(rollbackPrefs, overwrite = true)
            if (datastoreDir.exists()) datastoreDir.copyRecursively(rollbackDatastore, overwrite = true)

            // Verify rollback snapshot validity before touching live data
            if (databasesDir.exists() && (rollbackDb.listFiles()?.isEmpty() != false)) {
                throw IOException("Failed to create valid rollback snapshot for databases")
            }

            RestoreJournalManager.recordPhase(context, RestoreJournalManager.RestorePhase.STAGED)

            // 5. Execute state swap using atomic directory rename pattern (REQ-MIG-041.4)
            listener?.onProgress("Installing restored data...")
            RestoreJournalManager.recordPhase(context, RestoreJournalManager.RestorePhase.SWAPPING)

            val dbTrash = File(dataDir, "databases_staging_trash")
            val prefsTrash = File(dataDir, "shared_prefs_staging_trash")
            val datastoreTrash = File(context.filesDir, "datastore_staging_trash")

            if (dbTrash.exists()) dbTrash.deleteRecursively()
            if (prefsTrash.exists()) prefsTrash.deleteRecursively()
            if (datastoreTrash.exists()) datastoreTrash.deleteRecursively()

            try {
                // Move current live data into staging trash
                if (databasesDir.exists() && !databasesDir.renameTo(dbTrash)) {
                    databasesDir.copyRecursively(dbTrash, overwrite = true)
                    databasesDir.deleteRecursively()
                }
                if (sharedPrefsDir.exists() && !sharedPrefsDir.renameTo(prefsTrash)) {
                    sharedPrefsDir.copyRecursively(prefsTrash, overwrite = true)
                    sharedPrefsDir.deleteRecursively()
                }
                if (datastoreDir.exists() && !datastoreDir.renameTo(datastoreTrash)) {
                    datastoreDir.copyRecursively(datastoreTrash, overwrite = true)
                    datastoreDir.deleteRecursively()
                }

                // Move restored directories into production paths
                val restoredDatabases = File(tempDir, "databases")
                if (!restoredDatabases.renameTo(databasesDir)) {
                    restoredDatabases.copyRecursively(databasesDir, overwrite = true)
                }

                val restoredPrefs = File(tempDir, "shared_prefs")
                if (restoredPrefs.exists()) {
                    // ATT-4023 / REQ-DAT-032.3: Discard auth_credentials_prefs.xml and purge legacy credential keys
                    com.atrainingtracker.trainingtracker.security.AuthCredentialsStore.sanitizeStagedPreferencesDirectory(restoredPrefs)
                    if (!restoredPrefs.renameTo(sharedPrefsDir)) {
                        restoredPrefs.copyRecursively(sharedPrefsDir, overwrite = true)
                    }
                }

                val restoredDatastore = File(tempDir, "datastore")
                if (restoredDatastore.exists()) {
                    if (!restoredDatastore.renameTo(datastoreDir)) {
                        restoredDatastore.copyRecursively(datastoreDir, overwrite = true)
                    }
                }

                // Everything swapped successfully, purge staging trash
                dbTrash.deleteRecursively()
                prefsTrash.deleteRecursively()
                datastoreTrash.deleteRecursively()

                // ATT-4023 / REQ-DAT-032.3: Enforce clean re-authentication invariant across application preferences
                com.atrainingtracker.trainingtracker.security.AuthCredentialsStore.sanitizeAfterRestore(context)
            } catch (swapError: Throwable) {
                Log.e(TAG, "Error during atomic swap, attempting immediate recovery from staging trash", swapError)
                if (dbTrash.exists()) {
                    databasesDir.deleteRecursively()
                    if (!dbTrash.renameTo(databasesDir)) {
                        dbTrash.copyRecursively(databasesDir, overwrite = true)
                    }
                }
                if (prefsTrash.exists()) {
                    sharedPrefsDir.deleteRecursively()
                    if (!prefsTrash.renameTo(sharedPrefsDir)) {
                        prefsTrash.copyRecursively(sharedPrefsDir, overwrite = true)
                    }
                }
                if (datastoreTrash.exists()) {
                    datastoreDir.deleteRecursively()
                    if (!datastoreTrash.renameTo(datastoreDir)) {
                        datastoreTrash.copyRecursively(datastoreDir, overwrite = true)
                    }
                }
                throw swapError
            }

            // 6. Commit and finalize
            RestoreJournalManager.recordPhase(context, RestoreJournalManager.RestorePhase.COMMITTED)
            rollbackDir.deleteRecursively()
            tempDir.deleteRecursively()
            RestoreJournalManager.clearJournal(context)

            Log.i(TAG, "Restore successful. Restarting application...")
            listener?.onProgress("Restarting application...")
            appRestarter(context)
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Restore failed, reverting to previous state", e)
            executeEmergencyRollback(context)
            RestoreJournalManager.clearJournal(context)
            if (tempDir.exists()) tempDir.deleteRecursively()
            return false
        }
    }
}
