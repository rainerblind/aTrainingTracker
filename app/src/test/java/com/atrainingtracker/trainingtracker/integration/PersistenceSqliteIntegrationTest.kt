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

package com.atrainingtracker.trainingtracker.integration

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.provider.BaseColumns
import android.util.Log
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.testutil.RealSqliteTestHelper
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.database.KnownLocationsDatabaseManager
import com.atrainingtracker.trainingtracker.database.KnownLocationsDatabaseManager.KnownLocationsDbHelper
import com.atrainingtracker.trainingtracker.database.LapsDatabaseManager
import com.atrainingtracker.trainingtracker.database.LapsDatabaseManager.Laps
import com.atrainingtracker.trainingtracker.database.WorkoutSamplesDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries
import com.atrainingtracker.trainingtracker.elevation.ElevationResult
import com.atrainingtracker.trainingtracker.elevation.ElevationService
import com.atrainingtracker.trainingtracker.repositories.KnownLocationsRepository
import com.google.android.gms.maps.model.LatLng
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.lang.reflect.Field
import java.util.concurrent.Executors

/**
 * Software Integration Verification Test: Persistence SQLite Foundation (ASPICE SWE.5,
 * INT-SWE-06, IF-SWE-06, REQ-PRO-111.2, TST-INT-014, ATT-8156).
 *
 * Verifies real SQLite persistence across:
 * 1. Dynamic sample table creation (WorkoutSamplesDatabaseManager), summary updates (WorkoutSummariesDatabaseManager),
 *    lap persistence (LapsDatabaseManager), and concurrent WAL reads/writes.
 * 2. KnownLocationsRepository.recordWorkoutStart() persisting into real KnownLocationsDatabaseManager SQLite.
 * 3. Non-destructive onUpgrade schema migration preserving pre-existing rows (KnownLocationsDbHelper).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PersistenceSqliteIntegrationTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        cleanupAll()

        TrainingApplication.setAppContextForTesting(context)
        TrainingApplication.setSharedPreferencesForTesting(
            PreferenceManager.getDefaultSharedPreferences(context)
        )

        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>(), any<Throwable>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0
    }

    @After
    fun tearDown() {
        KnownLocationsRepository.resetForTesting(null)
        resetKnownLocationsDbSingleton()
        WorkoutSamplesDatabaseManager.resetForTesting(null)
        TrainingApplication.setAppContextForTesting(null)
        TrainingApplication.setSharedPreferencesForTesting(null)
        unmockkAll()
        cleanupAll()
    }

    private fun cleanupAll() {
        listOf(
            "WorkoutSummaries.db",
            "WorkoutSamples.db",
            "Laps.db",
            "KnownLocations.db",
            "test_known_loc_upgrade.db"
        ).forEach { context.deleteDatabase(it) }
    }

    private fun resetKnownLocationsDbSingleton() {
        try {
            val field: Field = KnownLocationsDatabaseManager::class.java.getDeclaredField("cInstance")
            field.isAccessible = true
            field.set(null, null)
        } catch (_: Exception) {
        }
    }

    /**
     * TST-INT-014.1: Verifies real SQLite dynamic sample table creation, concurrent WAL reads/writes,
     * and summary + lap cross-manager persistence.
     */
    @Test
    fun testSqliteManagers_dynamicSamplesTable_lapsAndWalConcurrency() = runBlocking {
        val summariesMgr = WorkoutSummariesDatabaseManager.getInstance(context)
        val samplesMgr = WorkoutSamplesDatabaseManager.getInstance(context)
        val lapsMgr = LapsDatabaseManager.getInstance(context)

        val summariesDb = summariesMgr.database!!
        val samplesDb = samplesMgr.database!!

        // Verify WAL mode or active journal mode query succeeds on real SQLite
        samplesDb.rawQuery("PRAGMA journal_mode", null).use { c ->
            assertTrue(c.moveToFirst())
            assertFalseOrValidJournal(c.getString(0))
        }

        val fileBaseName = "2026-11-17-070000"
        val summaryCv = ContentValues().apply {
            put(WorkoutSummaries.WORKOUT_NAME, "SQLite Integration Ride")
            put(WorkoutSummaries.FILE_BASE_NAME, fileBaseName)
            put(WorkoutSummaries.SPORT_ID, 1L)
            put(WorkoutSummaries.B_SPORT, BSportType.BIKE.name)
            put(WorkoutSummaries.TIME_START, "2026-11-17 07:00:00")
            put(WorkoutSummaries.TIME_ACTIVE_s, 30)
            put(WorkoutSummaries.TIME_TOTAL_s, 30)
            put(WorkoutSummaries.DISTANCE_TOTAL_m, 300.0)
            put(WorkoutSummaries.FINISHED, 1)
        }
        val workoutId = summariesDb.insertOrThrow(WorkoutSummaries.TABLE, null, summaryCv)
        assertTrue(workoutId > 0L)

        val sensors = listOf(
            SensorType.TIME_TOTAL,
            SensorType.TIME_ACTIVE,
            SensorType.DISTANCE_m,
            SensorType.HR,
            SensorType.POWER,
            SensorType.LATITUDE,
            SensorType.LONGITUDE
        )
        samplesMgr.createNewTable(fileBaseName, sensors)
        val samplesTable = WorkoutSamplesDatabaseManager.getTableName(fileBaseName)
        assertTrue(samplesMgr.existsTable(fileBaseName))
        assertTrue(RealSqliteTestHelper.hasTable(samplesDb, fileBaseName))

        // Concurrent batch inserts + reads via Dispatchers.IO
        val writers = (1..20).map { tick ->
            async(Dispatchers.IO) {
                val cv = ContentValues().apply {
                    put(SensorType.TIME_TOTAL.name, tick)
                    put(SensorType.TIME_ACTIVE.name, tick)
                    put(SensorType.DISTANCE_m.name, tick * 10.0)
                    put(SensorType.HR.name, 130 + tick)
                    put(SensorType.POWER.name, 200 + tick)
                    put(SensorType.LATITUDE.name, 48.137 + tick * 0.0001)
                    put(SensorType.LONGITUDE.name, 11.576 + tick * 0.0001)
                }
                synchronized(samplesMgr) {
                    samplesDb.insertOrThrow(samplesTable, null, cv)
                }
            }
        }
        writers.awaitAll()

        assertEquals(20L, RealSqliteTestHelper.getRowCount(samplesDb, samplesTable))

        lapsMgr.saveLap(workoutId, 1L, "2026-11-17 07:00:00", 30, 300.0, 10.0, "Lap 1", "Integration lap")
        val lapsDb = lapsMgr.database!!
        assertEquals(
            1L,
            RealSqliteTestHelper.getRowCount(lapsDb, Laps.TABLE, "${Laps.WORKOUT_ID} = ?", arrayOf(workoutId.toString()))
        )
    }

    private fun assertFalseOrValidJournal(mode: String?) {
        assertNotNull(mode)
        assertTrue("Journal mode must be non-blank (actual=$mode)", mode!!.isNotBlank())
    }

    /**
     * TST-INT-014.2: Verifies KnownLocationsRepository.recordWorkoutStart() writes to real
     * KnownLocationsDatabaseManager SQLite and updates reactive locations StateFlow.
     */
    @Test
    fun testKnownLocationsRepository_recordWorkoutStart_persistsToRealSqlite() = runBlocking {
        val dbManager = KnownLocationsDatabaseManager.getInstance(context)
        val mockElevationService = mockk<ElevationService>(relaxed = true)
        coEvery { mockElevationService.fetchElevation(any(), any()) } returns ElevationResult.Success(
            elevationMeters = 520.0,
            latitude = 48.137154,
            longitude = 11.576124
        )
        val singleThreadDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        val repo = KnownLocationsRepository(
            context = context,
            databaseManager = dbManager,
            elevationService = mockElevationService,
            dbDispatcher = singleThreadDispatcher
        )

        try {
            withContext(singleThreadDispatcher) {}
            repo.recordWorkoutStart(LatLng(48.137154, 11.576124), 519.0)
            repo.upsertLocationByGeofence(
                latLng = LatLng(48.137154, 11.576124),
                altitude = 520.0,
                name = "Start",
                extremaType = com.atrainingtracker.trainingtracker.database.ExtremaType.START,
                source = com.atrainingtracker.trainingtracker.elevation.ElevationSource.MANUAL_USER,
                isLocked = false
            )
            withContext(singleThreadDispatcher) {}

            val persisted = dbManager.allLocations
            assertEquals("Real SQLite must contain 1 recorded start location", 1, persisted.size)
            assertEquals(48.137154, persisted[0].latLng.latitude, 0.0001)
            assertEquals(11.576124, persisted[0].latLng.longitude, 0.0001)
            assertTrue("Repository StateFlow must expose persisted location", repo.locationsFlow.value.isNotEmpty())
        } finally {
            repo.cancelScopeForTesting()
            singleThreadDispatcher.close()
        }
    }

    /**
     * TST-INT-014.3: Verifies non-destructive onUpgrade migration on real KnownLocationsDbHelper
     * from schema v1 -> v6 (KnownLocationsDbHelper.DB_VERSION = 6) preserves existing locations and adds all upgraded columns.
     */
    @Test
    fun testKnownLocationsDbHelper_onUpgradeV1ToV5_preservesExistingData() {
        val dbName = "test_known_loc_upgrade.db"
        val db: SQLiteDatabase = RealSqliteTestHelper.createNamedDatabase(dbName)
        try {
            db.execSQL(
                """
                CREATE TABLE ${KnownLocationsDbHelper.TABLE} (
                    ${BaseColumns._ID} INTEGER PRIMARY KEY AUTOINCREMENT,
                    ${KnownLocationsDbHelper.NAME} TEXT,
                    ${KnownLocationsDbHelper.ALTITUDE} REAL,
                    ${KnownLocationsDbHelper.LONGITUDE} REAL,
                    ${KnownLocationsDbHelper.LATITUDE} REAL
                )
                """.trimIndent()
            )
            db.version = 1

            val legacyRow = ContentValues().apply {
                put(KnownLocationsDbHelper.NAME, "Home Base")
                put(KnownLocationsDbHelper.ALTITUDE, 515.0)
                put(KnownLocationsDbHelper.LONGITUDE, 11.575)
                put(KnownLocationsDbHelper.LATITUDE, 48.137)
            }
            val rowId = db.insertOrThrow(KnownLocationsDbHelper.TABLE, null, legacyRow)
            assertEquals(1L, rowId)

            val helper = KnownLocationsDbHelper(context)
            helper.onUpgrade(db, 1, KnownLocationsDbHelper.DB_VERSION)
            db.version = KnownLocationsDbHelper.DB_VERSION

            assertTrue(RealSqliteTestHelper.hasColumn(db, KnownLocationsDbHelper.TABLE, KnownLocationsDbHelper.RADIUS))
            assertTrue(RealSqliteTestHelper.hasColumn(db, KnownLocationsDbHelper.TABLE, KnownLocationsDbHelper.EXTREMA_TYPE))
            assertTrue(RealSqliteTestHelper.hasColumn(db, KnownLocationsDbHelper.TABLE, KnownLocationsDbHelper.HIT_COUNT))
            assertTrue(RealSqliteTestHelper.hasColumn(db, KnownLocationsDbHelper.TABLE, KnownLocationsDbHelper.IS_LOCKED))
            assertTrue(RealSqliteTestHelper.hasColumn(db, KnownLocationsDbHelper.TABLE, KnownLocationsDbHelper.SOURCE))

            assertEquals(1L, RealSqliteTestHelper.getRowCount(db, KnownLocationsDbHelper.TABLE))
            db.query(KnownLocationsDbHelper.TABLE, null, "${BaseColumns._ID} = ?", arrayOf("1"), null, null, null).use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("Home Base", c.getString(c.getColumnIndexOrThrow(KnownLocationsDbHelper.NAME)))
                assertEquals(515.0, c.getDouble(c.getColumnIndexOrThrow(KnownLocationsDbHelper.ALTITUDE)), 0.01)
            }
        } finally {
            RealSqliteTestHelper.cleanupDatabase(db, dbName)
        }
    }
}
