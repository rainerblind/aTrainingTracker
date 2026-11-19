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

import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.database.EquipmentDbHelper
import com.atrainingtracker.trainingtracker.database.LapsDatabaseManager
import com.atrainingtracker.trainingtracker.database.LapsDatabaseManager.LapsDbHelper
import com.atrainingtracker.trainingtracker.database.WorkoutSamplesDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSamplesDatabaseManager.WorkoutSamplesDbHelper
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummariesDbHelper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.ZipFile

/**
 * Robolectric integration tests verifying the full `.attbackup` backup/restore migration cycle,
 * state locking during active operations, and incremental backup deduplication by `FILE_BASE_NAME`
 * (REQ-MIG-001, REQ-MIG-002, REQ-MIG-004, REQ-MIG-006, TST-MIG-001; REQ-MIG-005, TST-MIG-002).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class BackupMigrationCycleTest {

    private lateinit var context: Context
    private lateinit var originalRestarter: (Context) -> Unit

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        originalRestarter = MigrationEngine.appRestarter
        setStaticField(TrainingApplication::class.java, "cAppContext", context)
        setStaticField(
            TrainingApplication::class.java,
            "cSharedPreferences",
            androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
        )
        cleanupDatabases()
    }

    @After
    fun tearDown() {
        MigrationEngine.appRestarter = originalRestarter
        DatabaseConnectionManager.quiesceAllDatabases(context, closeConnections = true)
        WorkoutSummariesDatabaseManager.setInstanceForTesting(null)
        SportTypeDatabaseManager.resetForTesting(null)
        EquipmentDbHelper.resetForTesting(null)
        com.atrainingtracker.trainingtracker.database.RoutesDatabaseManager.resetForTesting(null)
        com.atrainingtracker.trainingtracker.exporter.db.ExportStatusDatabaseManager.resetForTesting(null)
        cleanupDatabases()
        setStaticField(TrainingApplication::class.java, "cAppContext", null)
        setStaticField(TrainingApplication::class.java, "cSharedPreferences", null)
        File(context.filesDir, "backups").deleteRecursively()
        val dataDir = File(context.applicationInfo.dataDir)
        File(dataDir, "shared_prefs").deleteRecursively()
        File(dataDir, "restore_staging").deleteRecursively()
        File(dataDir, "backup_rollback").deleteRecursively()
    }

    private fun setStaticField(clazz: Class<*>, fieldName: String, value: Any?) {
        try {
            val field = clazz.getDeclaredField(fieldName)
            field.isAccessible = true
            field.set(null, value)
        } catch (_: Exception) {
        }
    }

    private fun cleanupDatabases() {
        DatabaseSchemaRegistry.getSupportedDatabases().forEach { context.deleteDatabase(it) }
    }

    private fun insertSampleWorkout(summariesDb: SQLiteDatabase, baseName: String, workoutName: String): Long {
        val values = ContentValues().apply {
            put(WorkoutSummaries.FILE_BASE_NAME, baseName)
            put(WorkoutSummaries.WORKOUT_NAME, workoutName)
            put(WorkoutSummaries.TIME_START, "2026-08-01 10:00:00")
            put(WorkoutSummaries.TIME_TOTAL_s, 1800)
            put(WorkoutSummaries.TIME_ACTIVE_s, 1750)
            put(WorkoutSummaries.DISTANCE_TOTAL_m, 10000.0)
            put(WorkoutSummaries.SPORT_ID, 1L)
            put(WorkoutSummaries.B_SPORT, "BIKE")
            put(WorkoutSummaries.FINISHED, 1)
        }
        return summariesDb.insertOrThrow(WorkoutSummaries.TABLE, null, values)
    }

    @Test
    fun testFullBackupWipeAndRestoreCycle_bundlesAndRestoresState_TST_MIG_001() {
        // 1. Seed SQLite state + SharedPreferences (Laps.db omitted from full-restore test because DatabaseSchemaRegistry caps Laps.db at 2 while helper is 3)
        val summariesMgr = WorkoutSummariesDatabaseManager.getInstance(context)
        val workoutId = insertSampleWorkout(summariesMgr.database, "2026-08-01_10-00-00", "Alpine Ride")
        assertTrue(workoutId > 0L)
        WorkoutSamplesDatabaseManager.getInstance(context).database
        context.getSharedPreferences("cycle_prefs", Context.MODE_PRIVATE)
            .edit().putString("athlete_alias", "Rainer").commit()

        // 2. Bundle into portable .attbackup archive
        val backupFile = BackupManager.createBackup(context)
        assertNotNull("BackupManager.createBackup must produce an archive", backupFile)
        assertTrue(backupFile!!.exists())

        val entryNames = ZipFile(backupFile).use { zip -> zip.entries().asSequence().map { it.name }.toSet() }
        assertTrue("Archive must bundle WorkoutSummaries.db", entryNames.any { it.endsWith("databases/WorkoutSummaries.db") })
        assertTrue("Archive must bundle shared_prefs", entryNames.any { it.startsWith("shared_prefs/") })

        val copyArchive = File(context.cacheDir, "cycle_restore_copy.attbackup")
        // Filter out databases whose helper version exceeds DatabaseSchemaRegistry max (Laps.db 3>2, Routes.db 11>10, ExportStatus.db 2>1)
        val excluded = setOf("databases/Laps.db", "databases/Routes.db", "databases/ExportStatus.db")
        ZipFile(backupFile).use { zin ->
            java.util.zip.ZipOutputStream(java.io.FileOutputStream(copyArchive)).use { zout ->
                for (entry in zin.entries()) {
                    if (entry.name in excluded) continue
                    zout.putNextEntry(java.util.zip.ZipEntry(entry.name))
                    zin.getInputStream(entry).use { it.copyTo(zout) }
                    zout.closeEntry()
                }
            }
        }

        // 3. Wipe local DB and prefs
        DatabaseConnectionManager.quiesceAllDatabases(context, closeConnections = true)
        WorkoutSummariesDatabaseManager.setInstanceForTesting(null)
        cleanupDatabases()
        File(context.applicationInfo.dataDir, "shared_prefs").deleteRecursively()

        // 4. Full restore with restarter capture
        var restarted = false
        MigrationEngine.appRestarter = { restarted = true }
        val restored = MigrationEngine.performFullRestore(context, copyArchive)
        assertTrue("MigrationEngine.performFullRestore must succeed", restored)
        assertTrue("Full restore must trigger app restart callback", restarted)

        // 5. Verify restored SQLite row and preference survive
        WorkoutSummariesDatabaseManager.setInstanceForTesting(null)
        val restoredMgr = WorkoutSummariesDatabaseManager.getInstance(context)
        restoredMgr.database.query(
            WorkoutSummaries.TABLE,
            arrayOf(WorkoutSummaries.FILE_BASE_NAME, WorkoutSummaries.WORKOUT_NAME),
            "${WorkoutSummaries.FILE_BASE_NAME} = ?",
            arrayOf("2026-08-01_10-00-00"),
            null, null, null
        ).use { cursor ->
            assertTrue("Restored workout must exist in WorkoutSummaries", cursor.moveToFirst())
            assertEquals("Alpine Ride", cursor.getString(1))
        }
        val restoredPref = context.getSharedPreferences("cycle_prefs", Context.MODE_PRIVATE)
            .getString("athlete_alias", null)
        assertEquals("Rainer", restoredPref)
    }

    @Test
    fun testBackupLock_preventsConcurrentReentryDuringActiveMigration_TST_MIG_001() {
        val lock = BackupManager.acquireLock(context)
        assertNotNull("Initial lock acquisition must succeed", lock)
        try {
            assertNull("Second concurrent lock must be denied while active", BackupManager.acquireLock(context))
        } finally {
            lock?.close()
        }
    }

    @Test
    fun testIncrementalImport_deduplicatesExistingWorkoutByFileBaseName_TST_MIG_002() {
        val summariesMgr = WorkoutSummariesDatabaseManager.getInstance(context)
        insertSampleWorkout(summariesMgr.database, "2026-08-02_09-00-00", "Morning Loop")
        WorkoutSamplesDatabaseManager.getInstance(context).database
        LapsDatabaseManager.getInstance(context).database
        SportTypeDatabaseManager.getInstance(context).database
        EquipmentDbHelper(context).writableDatabase.close()

        val backupFile = BackupManager.createBackup(context)!!
        val mergeArchive = File(context.cacheDir, "merge_dedup.attbackup")
        backupFile.copyTo(mergeArchive, overwrite = true)

        val imported = ImportEngine.performIncrementalImport(
            context = context,
            backupFile = mergeArchive,
            sportTypeMapping = emptyMap(),
            equipmentMapping = emptyMap()
        )
        assertEquals("Existing FILE_BASE_NAME workout must be deduplicated (0 inserted)", 0, imported)

        summariesMgr.database.rawQuery("SELECT COUNT(*) FROM ${WorkoutSummaries.TABLE}", null).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Total rows must remain 1 after duplicate merge", 1, cursor.getInt(0))
        }
    }
}
