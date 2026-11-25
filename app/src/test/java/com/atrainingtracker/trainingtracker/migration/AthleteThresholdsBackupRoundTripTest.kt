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
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.trainingtracker.database.AthleteMetricType
import com.atrainingtracker.trainingtracker.database.AthleteMetricsDatabaseManager
import com.atrainingtracker.trainingtracker.database.AthleteMetricsDbHelper
import com.atrainingtracker.trainingtracker.settings.SettingsDataStore
import com.atrainingtracker.trainingtracker.ui.athleteprofile.AthleteMetricsRepository
import com.atrainingtracker.trainingtracker.ui.athleteprofile.SqliteAthleteMetricsDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Real SQLite + Preferences DataStore `.attbackup` round-trip verification for all 7 new
 * sport-specific physiological thresholds and unknown future enum token forward-compatibility
 * (`REQ-SET-135.5`, `TST-SET-135.5`).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AthleteThresholdsBackupRoundTripTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        resetSingletons()
        MigrationEngine.appRestarter = {}
    }

    @After
    fun tearDown() {
        resetSingletons()
        context.deleteDatabase(AthleteMetricsDbHelper.DB_NAME)
        context.deleteDatabase("WorkoutSummaries.db")
        File(context.filesDir, "datastore").deleteRecursively()
    }

    private fun resetSingletons() {
        runCatching {
            val field = AthleteMetricsDatabaseManager::class.java.getDeclaredField("INSTANCE")
            field.isAccessible = true
            (field.get(null) as? AthleteMetricsDatabaseManager)?.database?.close()
            field.set(null, null)
        }
        runCatching {
            val field = AthleteMetricsRepository::class.java.getDeclaredField("INSTANCE")
            field.isAccessible = true
            field.set(null, null)
        }
        SettingsDataStore.setInstanceForTesting(null)
    }

    @Test
    fun attbackupRoundTrip_preservesAllSportSpecificThresholdsAndIgnoresUnknownFutureEnumTokens_TST_SET_135_5() = runBlocking {
        val datastoreDir = File(context.filesDir, "datastore").apply { mkdirs() }
        val livePrefsFile = File(datastoreDir, "user_settings.preferences_pb")
        livePrefsFile.delete()

        val writerScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val writerDataStore = PreferenceDataStoreFactory.create(
            scope = writerScope,
            produceFile = { livePrefsFile }
        )
        val settingsDataStore = SettingsDataStore.createForTesting(context, writerDataStore, writerScope)
        val dbManager = AthleteMetricsDatabaseManager.getInstance(context)
        val repository = AthleteMetricsRepository(
            dataSource = SqliteAthleteMetricsDataSource(dbManager),
            settingsDataStore = settingsDataStore,
            ioDispatcher = Dispatchers.Unconfined
        )

        val t1 = 1_790_812_800_000L
        val t2 = 1_792_022_400_000L
        repository.addEntry(AthleteMetricType.HR_MAX_RUN_BPM, 192.0, t1)
        repository.addEntry(AthleteMetricType.HR_MAX_BIKE_BPM, 185.0, t1)
        repository.addEntry(AthleteMetricType.LTHR_RUN_BPM, 173.0, t1)
        repository.addEntry(AthleteMetricType.LTHR_BIKE_BPM, 167.0, t1)
        repository.addEntry(AthleteMetricType.LT1_HR_RUN_BPM, 149.0, t1)
        repository.addEntry(AthleteMetricType.LT1_HR_BIKE_BPM, 142.0, t1)
        repository.addEntry(AthleteMetricType.LT1_PWR_BIKE_WATTS, 212.0, t1)
        repository.addEntry(AthleteMetricType.LTHR_RUN_BPM, 175.0, t2)

        // Inject a synthetic future enum token row directly into SQLite to prove forward-compatibility
        dbManager.database.insertOrThrow(
            AthleteMetricsDbHelper.TABLE_HISTORY,
            null,
            ContentValues().apply {
                put(AthleteMetricsDbHelper.TIMESTAMP_MS, t2 + 1000L)
                put(AthleteMetricsDbHelper.METRIC_TYPE, "FUTURE_VO2MAX_ML_KG")
                put(AthleteMetricsDbHelper.VALUE, 64.5)
            }
        )

        val expectedEntries = dbManager.getAllEntries()
        assertEquals("Unknown future row must be silently ignored by getAllEntries", 8, expectedEntries.size)

        // Ensure DataStore flushed before creating backup
        assertEquals(175, settingsDataStore.lthrRunFlow.first())
        writerScope.cancel()

        // Ensure core WorkoutSummaries.db exists (mandatory archive validation check in MigrationEngine)
        context.openOrCreateDatabase("WorkoutSummaries.db", Context.MODE_PRIVATE, null).use { coreDb ->
            coreDb.version = 1
            coreDb.execSQL("CREATE TABLE IF NOT EXISTS WorkoutSummaries (_id INTEGER PRIMARY KEY);")
        }

        // 1. Export full .attbackup archive
        val backupFile = BackupManager.createBackup(context)
        assertNotNull("Backup file must be created", backupFile)
        assertTrue(backupFile!!.exists() && backupFile.length() > 0L)

        // 2. Wipe live AthleteMetrics.db, WorkoutSummaries.db, and DataStore completely
        resetSingletons()
        context.deleteDatabase(AthleteMetricsDbHelper.DB_NAME)
        context.deleteDatabase("WorkoutSummaries.db")
        datastoreDir.deleteRecursively()

        // 3. Restore via MigrationEngine
        val restoredOk = MigrationEngine.performFullRestore(context, backupFile)
        assertTrue("MigrationEngine.performFullRestore must succeed", restoredOk)

        // 4. Verify AthleteMetrics.db rows match identically & unknown token stays non-fatal
        val restoredDbManager = AthleteMetricsDatabaseManager.getInstance(context)
        val restoredEntries = restoredDbManager.getAllEntries()
        assertEquals(expectedEntries.size, restoredEntries.size)
        for (i in expectedEntries.indices) {
            assertEquals(expectedEntries[i].metricType, restoredEntries[i].metricType)
            assertEquals(expectedEntries[i].timestampMs, restoredEntries[i].timestampMs)
            assertEquals(expectedEntries[i].value, restoredEntries[i].value, 1e-6)
        }
        // Confirm the unknown future row is preserved on disk and ignored safely
        SQLiteDatabase.openDatabase(
            context.getDatabasePath(AthleteMetricsDbHelper.DB_NAME).absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY
        ).use { rawDb ->
            rawDb.rawQuery("SELECT COUNT(*) FROM ${AthleteMetricsDbHelper.TABLE_HISTORY}", null).use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(9, c.getInt(0))
            }
        }

        // 5. Verify restored DataStore file preserves all 7 sport threshold keys identically
        val restoredCopy = tempFolder.newFile("verified_restored.preferences_pb")
        File(File(context.filesDir, "datastore"), "user_settings.preferences_pb").copyTo(restoredCopy, overwrite = true)
        val readerScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val readerDataStore = PreferenceDataStoreFactory.create(
            scope = readerScope,
            produceFile = { restoredCopy }
        )
        val restoredSettings = SettingsDataStore.createForTesting(context, readerDataStore, readerScope)
        assertEquals(192, restoredSettings.hrMaxRunFlow.first())
        assertEquals(185, restoredSettings.hrMaxBikeFlow.first())
        assertEquals(192, restoredSettings.hrMaxFlow.first())
        assertEquals(175, restoredSettings.lthrRunFlow.first())
        assertEquals(167, restoredSettings.lthrBikeFlow.first())
        assertEquals(149, restoredSettings.lt1HrRunFlow.first())
        assertEquals(142, restoredSettings.lt1HrBikeFlow.first())
        assertEquals(212, restoredSettings.lt1PwrBikeFlow.first())
        readerScope.cancel()
    }
}
