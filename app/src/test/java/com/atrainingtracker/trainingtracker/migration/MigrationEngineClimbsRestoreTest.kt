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
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.trainingtracker.database.ClimbsDbHelper
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * TST-MAP-068.3: Unit test verifying that MigrationEngine full restore resets [WorkoutRepository.PREF_CLIMBS_WORKOUT_BACKFILL_V1]
 * when restoring a legacy backup archive that lacks `Climbs.db` (REQ-MAP-066.3, ATT-6691).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigrationEngineClimbsRestoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context
    private var restarterCalled = false

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        restarterCalled = false
        MigrationEngine.appRestarter = { restarterCalled = true }
    }

    @After
    fun tearDown() {
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
    }

    @Test
    fun testPerformFullRestore_whenClimbsDbMissing_resetsClimbsBackfillPref() {
        // Pre-set the backfill flag as true in live preferences
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putBoolean(WorkoutRepository.PREF_CLIMBS_WORKOUT_BACKFILL_V1, true)
            .commit()

        val archiveFile = tempFolder.newFile("legacy_backup_without_climbs.attbackup")
        val dbFile = tempFolder.newFile("WorkoutSummaries.db")
        val db = context.openOrCreateDatabase(dbFile.absolutePath, Context.MODE_PRIVATE, null)
        db.version = 1
        db.execSQL("CREATE TABLE IF NOT EXISTS workouts (_id INTEGER PRIMARY KEY);")
        db.close()

        ZipOutputStream(BufferedOutputStream(FileOutputStream(archiveFile))).use { zos ->
            zos.putNextEntry(ZipEntry("databases/WorkoutSummaries.db"))
            zos.write(dbFile.readBytes())
            zos.closeEntry()

            // Restored preferences containing backfill flag marked as true
            val prefContent = "<map><boolean name=\"${WorkoutRepository.PREF_CLIMBS_WORKOUT_BACKFILL_V1}\" value=\"true\" /><string name=\"athlete_name\">Rainer</string></map>"
            val prefsName = "${context.packageName}_preferences.xml"
            zos.putNextEntry(ZipEntry("shared_prefs/$prefsName"))
            zos.write(prefContent.toByteArray())
            zos.closeEntry()
        }

        val success = MigrationEngine.performFullRestore(context, archiveFile)
        assertTrue("Restore should succeed", success)
        assertTrue("Restarter should be triggered", restarterCalled)

        val defaultPrefs = PreferenceManager.getDefaultSharedPreferences(context)
        // Flag must be cleared/reset so historical climb backfill runs on subsequent app launch
        assertFalse(
            "climbs_workout_backfill_v1 must be reset when Climbs.db is absent from restored archive",
            defaultPrefs.getBoolean(WorkoutRepository.PREF_CLIMBS_WORKOUT_BACKFILL_V1, false)
        )
    }

    @Test
    fun testPerformFullRestore_whenClimbsDbPresent_preservesClimbsBackfillPref() {
        // Pre-set the backfill flag as true in live preferences
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putBoolean(WorkoutRepository.PREF_CLIMBS_WORKOUT_BACKFILL_V1, true)
            .commit()

        val archiveFile = tempFolder.newFile("modern_backup_with_climbs.attbackup")
        val dbFile = tempFolder.newFile("WorkoutSummaries.db")
        val db1 = context.openOrCreateDatabase(dbFile.absolutePath, Context.MODE_PRIVATE, null)
        db1.version = 1
        db1.execSQL("CREATE TABLE IF NOT EXISTS workouts (_id INTEGER PRIMARY KEY);")
        db1.close()

        val climbsDbFile = tempFolder.newFile(ClimbsDbHelper.DATABASE_NAME)
        val db2 = context.openOrCreateDatabase(climbsDbFile.absolutePath, Context.MODE_PRIVATE, null)
        db2.version = 1
        db2.execSQL("CREATE TABLE IF NOT EXISTS climbs (_id INTEGER PRIMARY KEY);")
        db2.close()

        ZipOutputStream(BufferedOutputStream(FileOutputStream(archiveFile))).use { zos ->
            zos.putNextEntry(ZipEntry("databases/WorkoutSummaries.db"))
            zos.write(dbFile.readBytes())
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("databases/${ClimbsDbHelper.DATABASE_NAME}"))
            zos.write(climbsDbFile.readBytes())
            zos.closeEntry()

            val prefContent = "<map><boolean name=\"${WorkoutRepository.PREF_CLIMBS_WORKOUT_BACKFILL_V1}\" value=\"true\" /></map>"
            val prefsName = "${context.packageName}_preferences.xml"
            zos.putNextEntry(ZipEntry("shared_prefs/$prefsName"))
            zos.write(prefContent.toByteArray())
            zos.closeEntry()
        }

        val success = MigrationEngine.performFullRestore(context, archiveFile)
        assertTrue("Restore should succeed", success)

        val defaultPrefs = PreferenceManager.getDefaultSharedPreferences(context)
        assertTrue(
            "climbs_workout_backfill_v1 should remain true when Climbs.db is present in archive",
            defaultPrefs.getBoolean(WorkoutRepository.PREF_CLIMBS_WORKOUT_BACKFILL_V1, false)
        )
    }
}
