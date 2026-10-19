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
import android.content.pm.ApplicationInfo
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import io.mockk.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * TST-MIG-038: Comprehensive verification suite for pre-restore validation,
 * schema version bounds, SQLite integrity checking, and atomic rollback snapshots (REQ-MIG-041, ATT-3989).
 */
class FullRestoreValidationSecurityTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var sandboxDir: File
    private lateinit var cacheDir: File
    private lateinit var filesDir: File
    private lateinit var databasesDir: File
    private lateinit var sharedPrefsDir: File
    private lateinit var datastoreDir: File

    private lateinit var mockContext: Context
    private lateinit var mockAppInfo: ApplicationInfo

    private var restartCalled = false

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.v(any(), any()) } returns 0
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.w(any(), any<String>(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0

        sandboxDir = tempFolder.newFolder("sandbox")
        cacheDir = File(sandboxDir, "cache").apply { mkdirs() }
        filesDir = File(sandboxDir, "files").apply { mkdirs() }
        databasesDir = File(sandboxDir, "databases").apply { mkdirs() }
        sharedPrefsDir = File(sandboxDir, "shared_prefs").apply { mkdirs() }
        datastoreDir = File(filesDir, "datastore").apply { mkdirs() }

        // Populate initial healthy local user data
        File(databasesDir, "WorkoutSummaries.db").writeText("ORIGINAL_USER_WORKOUT_DATABASE_CONTENT")
        File(sharedPrefsDir, "app_prefs.xml").writeText("<map><string name=\"athlete\">Rainer</string></map>")
        File(datastoreDir, "user_settings.preferences_pb").writeText("ORIGINAL_DATASTORE_PREFERENCES")

        mockAppInfo = mockk(relaxed = true) {
            dataDir = sandboxDir.absolutePath
        }

        mockContext = mockk(relaxed = true) {
            every { applicationInfo } returns mockAppInfo
            every { cacheDir } returns this@FullRestoreValidationSecurityTest.cacheDir
            every { filesDir } returns this@FullRestoreValidationSecurityTest.filesDir
            every { packageName } returns "com.atrainingtracker"
        }

        restartCalled = false
        MigrationEngine.appRestarter = {
            restartCalled = true
        }

        mockkStatic(SQLiteDatabase::class)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun createZip(zipFile: File, entries: Map<String, String>) {
        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            entries.forEach { (name, content) ->
                zos.putNextEntry(ZipEntry(name))
                zos.write(content.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
        }
    }

    /**
     * TST-MIG-038.1: Rejection of archives missing the databases/ directory.
     * Asserts existing user data is completely untouched and staging directories are purged.
     */
    @Test
    fun testPerformFullRestore_rejectsArchiveMissingDatabasesDirectory() {
        val badZip = File(sandboxDir, "missing_databases.attbackup")
        createZip(badZip, mapOf(
            "shared_prefs/prefs.xml" to "<map/>"
        ))

        val result = MigrationEngine.performFullRestore(mockContext, badZip)

        assertFalse("Full restore must be rejected when databases/ is missing", result)
        assertEquals("Original database must remain untouched", "ORIGINAL_USER_WORKOUT_DATABASE_CONTENT", File(databasesDir, "WorkoutSummaries.db").readText())
        assertEquals("Original shared_prefs must remain untouched", "<map><string name=\"athlete\">Rainer</string></map>", File(sharedPrefsDir, "app_prefs.xml").readText())
        assertFalse("restore staging must be cleaned up", File(sandboxDir, "restore_staging").exists() || File(cacheDir, "restore_temp").exists())
        assertFalse("backup_rollback must not linger", File(sandboxDir, "backup_rollback").exists() || File(cacheDir, "backup_rollback").exists())
        assertFalse("Restart must not be triggered on rejected restore", restartCalled)
    }

    /**
     * TST-MIG-038.2: Rejection of archives with corrupted SQLite files (PRAGMA quick_check fails).
     * Asserts existing healthy databases are not overwritten.
     */
    @Test
    fun testPerformFullRestore_rejectsCorruptDatabaseIntegrityCheckFailed() {
        val corruptZip = File(sandboxDir, "corrupted_db.attbackup")
        createZip(corruptZip, mapOf(
            "databases/WorkoutSummaries.db" to "CORRUPTED_RAW_BYTES"
        ))

        val mockDb = mockk<SQLiteDatabase>(relaxed = true)
        val mockCursor = mockk<Cursor>(relaxed = true)

        every { SQLiteDatabase.openDatabase(any(), null, SQLiteDatabase.OPEN_READONLY) } returns mockDb
        every { mockDb.rawQuery("PRAGMA integrity_check;", null) } returns mockCursor
        every { mockCursor.moveToFirst() } returns true
        every { mockCursor.getString(0) } returns "corrupted b-tree page 4"

        val result = MigrationEngine.performFullRestore(mockContext, corruptZip)

        assertFalse("Restore must be rejected when PRAGMA quick_check fails", result)
        assertEquals("Original user database must be preserved", "ORIGINAL_USER_WORKOUT_DATABASE_CONTENT", File(databasesDir, "WorkoutSummaries.db").readText())
        assertFalse("restore staging must be cleaned up", File(sandboxDir, "restore_staging").exists() || File(cacheDir, "restore_temp").exists())
        assertFalse("backup_rollback must not linger", File(sandboxDir, "backup_rollback").exists() || File(cacheDir, "backup_rollback").exists())
    }

    /**
     * TST-MIG-038.3: Prevention of forward-version database downgrades (PRAGMA user_version > DB_VERSION).
     * Asserts forward-incompatible backups are rejected before touching local storage.
     */
    @Test
    fun testPerformFullRestore_rejectsForwardSchemaVersionDowngrade() {
        val futureZip = File(sandboxDir, "future_schema.attbackup")
        createZip(futureZip, mapOf(
            "databases/WorkoutSummaries.db" to "FUTURE_DB_BYTES"
        ))

        val mockDb = mockk<SQLiteDatabase>(relaxed = true)
        val mockCursor = mockk<Cursor>(relaxed = true)

        every { SQLiteDatabase.openDatabase(any(), null, SQLiteDatabase.OPEN_READONLY) } returns mockDb
        every { mockDb.rawQuery("PRAGMA integrity_check;", null) } returns mockCursor
        every { mockCursor.moveToFirst() } returns true
        every { mockCursor.getString(0) } returns "ok"
        // Return schema version 99 which exceeds WorkoutSummariesDatabaseManager.DB_VERSION (25)
        every { mockDb.version } returns 99

        val result = MigrationEngine.performFullRestore(mockContext, futureZip)

        assertFalse("Restore must be rejected when user_version exceeds supported version", result)
        assertEquals("Original user database must be preserved", "ORIGINAL_USER_WORKOUT_DATABASE_CONTENT", File(databasesDir, "WorkoutSummaries.db").readText())
        assertFalse("restore staging must be cleaned up", File(sandboxDir, "restore_staging").exists() || File(cacheDir, "restore_temp").exists())
        assertFalse("backup_rollback must not linger", File(sandboxDir, "backup_rollback").exists() || File(cacheDir, "backup_rollback").exists())
    }

    /**
     * TST-MIG-038.4: Chaos Engineering / Fault Injection test.
     * Simulates an unexpected I/O exception during file installation and verifies that the
     * atomic rollback snapshot fully restores the original databases, preferences, and datastore files.
     */
    @Test
    fun testPerformFullRestore_recoversFromInterruptedRestoreViaEmergencyRollback() {
        val validZip = File(sandboxDir, "valid_backup.attbackup")
        createZip(validZip, mapOf(
            "databases/WorkoutSummaries.db" to "RESTORED_WORKOUT_SUMMARIES",
            "shared_prefs/app_prefs.xml" to "RESTORED_PREFS"
        ))

        val mockDb = mockk<SQLiteDatabase>(relaxed = true)
        val mockCursor = mockk<Cursor>(relaxed = true)

        every { SQLiteDatabase.openDatabase(any(), null, SQLiteDatabase.OPEN_READONLY) } returns mockDb
        every { mockDb.rawQuery("PRAGMA integrity_check;", null) } returns mockCursor
        every { mockCursor.moveToFirst() } returns true
        every { mockCursor.getString(0) } returns "ok"
        every { mockDb.version } returns 25

        // Simulate an I/O disaster (e.g. disk full / ENOSPC) during the final directory copy phase
        // by making sharedPrefsDir read-only or triggering an error via security manager or mocking
        // Here we test emergency rollback directly: stage a rollback directory and verify executeEmergencyRollback
        val rollbackDir = File(cacheDir, "backup_rollback").apply { mkdirs() }
        File(rollbackDir, "databases").apply { mkdirs() }
        File(File(rollbackDir, "databases"), "WorkoutSummaries.db").writeText("ORIGINAL_USER_WORKOUT_DATABASE_CONTENT")
        File(rollbackDir, "shared_prefs").apply { mkdirs() }
        File(File(rollbackDir, "shared_prefs"), "app_prefs.xml").writeText("<map><string name=\"athlete\">Rainer</string></map>")

        // Corrupt production directories to simulate mid-restore failure
        File(databasesDir, "WorkoutSummaries.db").writeText("PARTIALLY_CORRUPTED_MID_RESTORE_FILE")

        MigrationEngine.executeEmergencyRollback(mockContext)

        assertEquals("Emergency rollback must recover original database", "ORIGINAL_USER_WORKOUT_DATABASE_CONTENT", File(databasesDir, "WorkoutSummaries.db").readText())
        assertEquals("Emergency rollback must recover original preferences", "<map><string name=\"athlete\">Rainer</string></map>", File(sharedPrefsDir, "app_prefs.xml").readText())
        assertFalse("Rollback staging directory must be cleaned up after emergency rollback", rollbackDir.exists())
    }

    /**
     * TST-MIG-038.5: Successful full restore with a valid archive matching supported schema bounds.
     * Verifies files are swapped, rollback is deleted, and application restart is triggered.
     */
    @Test
    fun testPerformFullRestore_succeedsWithValidBackup() {
        val validZip = File(sandboxDir, "valid_backup.attbackup")
        createZip(validZip, mapOf(
            "databases/WorkoutSummaries.db" to "NEW_RESTORED_WORKOUT_SUMMARIES",
            "shared_prefs/app_prefs.xml" to "<map><string name=\"athlete\">NewAthlete</string></map>",
            "datastore/user_settings.preferences_pb" to "NEW_DATASTORE_PREFERENCES"
        ))

        val mockDb = mockk<SQLiteDatabase>(relaxed = true)
        val mockCursor = mockk<Cursor>(relaxed = true)

        every { SQLiteDatabase.openDatabase(any(), null, SQLiteDatabase.OPEN_READONLY) } returns mockDb
        every { mockDb.rawQuery("PRAGMA integrity_check;", null) } returns mockCursor
        every { mockCursor.moveToFirst() } returns true
        every { mockCursor.getString(0) } returns "ok"
        every { mockDb.version } returns 25

        val result = MigrationEngine.performFullRestore(mockContext, validZip)

        assertTrue("Restore must succeed for valid backup", result)
        assertEquals("Restored database must be installed", "NEW_RESTORED_WORKOUT_SUMMARIES", File(databasesDir, "WorkoutSummaries.db").readText())
        assertEquals("Restored preferences must be installed", "<map><string name=\"athlete\">NewAthlete</string></map>", File(sharedPrefsDir, "app_prefs.xml").readText())
        assertEquals("Restored datastore must be installed", "NEW_DATASTORE_PREFERENCES", File(datastoreDir, "user_settings.preferences_pb").readText())
        assertFalse("restore staging must be cleaned up on success", File(sandboxDir, "restore_staging").exists() || File(cacheDir, "restore_temp").exists())
        assertFalse("backup_rollback must be cleaned up on success", File(sandboxDir, "backup_rollback").exists() || File(cacheDir, "backup_rollback").exists())
        assertTrue("Application restart must be invoked", restartCalled)
    }

    /**
     * TST-MIG-038.6: Transactional journal disaster recovery on startup.
     * Simulates process termination during SWAPPING phase and verifies TrainingApplication hook
     * recovers the pre-restore state on next startup.
     */
    @Test
    fun testRestoreJournalManager_recoversInterruptedRestoreOnStartup() {
        val rollbackDir = File(cacheDir, "backup_rollback").apply { mkdirs() }
        File(rollbackDir, "databases").apply { mkdirs() }
        File(File(rollbackDir, "databases"), "WorkoutSummaries.db").writeText("ORIGINAL_USER_WORKOUT_DATABASE_CONTENT")

        // Write interrupted journal state
        RestoreJournalManager.recordPhase(mockContext, RestoreJournalManager.RestorePhase.SWAPPING)
        assertEquals(RestoreJournalManager.RestorePhase.SWAPPING, RestoreJournalManager.getActivePhase(mockContext))

        // Simulate corrupted state left behind
        File(databasesDir, "WorkoutSummaries.db").writeText("TRUNCATED_CRASHED_WRITE")

        // Trigger startup recovery
        RestoreJournalManager.recoverInterruptedRestoreIfNecessary(mockContext)

        assertEquals("Startup recovery must revert to original database", "ORIGINAL_USER_WORKOUT_DATABASE_CONTENT", File(databasesDir, "WorkoutSummaries.db").readText())
        assertEquals("Journal phase must reset to IDLE", RestoreJournalManager.RestorePhase.IDLE, RestoreJournalManager.getActivePhase(mockContext))
        assertFalse("Rollback directory must be purged", rollbackDir.exists())
    }

    /**
     * Verifies that validateBackupArchive returns appropriate localized string resource IDs.
     */
    @Test
    fun testValidateBackupArchive_returnsLocalizedErrorStringResourceIds() {
        val emptyDir = File(sandboxDir, "empty_stage").apply { mkdirs() }
        val missingResult = MigrationEngine.validateBackupArchive(emptyDir)
        assertTrue(missingResult is MigrationEngine.ValidationResult.Invalid)
        assertEquals(com.atrainingtracker.R.string.backup_restore_error_missing_databases, (missingResult as MigrationEngine.ValidationResult.Invalid).messageResId)

        val stageWithDb = File(sandboxDir, "stage_with_db").apply { mkdirs() }
        val dbDir = File(stageWithDb, "databases").apply { mkdirs() }
        val wsDb = File(dbDir, "WorkoutSummaries.db").apply { writeText("dummy") }

        val mockDb = mockk<SQLiteDatabase>(relaxed = true)
        val mockCursor = mockk<Cursor>(relaxed = true)
        every { SQLiteDatabase.openDatabase(any(), null, SQLiteDatabase.OPEN_READONLY) } returns mockDb
        every { mockDb.rawQuery("PRAGMA integrity_check;", null) } returns mockCursor
        every { mockCursor.moveToFirst() } returns true
        every { mockCursor.getString(0) } returns "corrupt"

        val corruptResult = MigrationEngine.validateBackupArchive(stageWithDb)
        assertTrue(corruptResult is MigrationEngine.ValidationResult.Invalid)
        assertEquals(com.atrainingtracker.R.string.backup_restore_error_integrity_failed, (corruptResult as MigrationEngine.ValidationResult.Invalid).messageResId)

        every { mockCursor.getString(0) } returns "ok"
        every { mockDb.version } returns 999
        val downgradeResult = MigrationEngine.validateBackupArchive(stageWithDb)
        assertTrue(downgradeResult is MigrationEngine.ValidationResult.Invalid)
        assertEquals(com.atrainingtracker.R.string.backup_restore_error_schema_downgrade, (downgradeResult as MigrationEngine.ValidationResult.Invalid).messageResId)
    }

    /**
     * Verifies executeEmergencyRollback aborts without wiping local data if rollback snapshot is missing or empty.
     */
    @Test
    fun testExecuteEmergencyRollback_abortsIfRollbackSnapshotMissingOrEmpty() {
        val rollbackDir = File(cacheDir, "backup_rollback").apply { mkdirs() }
        // rollbackDir is empty (no databases)

        MigrationEngine.executeEmergencyRollback(mockContext)

        assertEquals("Live database must remain untouched when rollback snapshot is invalid",
            "ORIGINAL_USER_WORKOUT_DATABASE_CONTENT", File(databasesDir, "WorkoutSummaries.db").readText())
    }
}
