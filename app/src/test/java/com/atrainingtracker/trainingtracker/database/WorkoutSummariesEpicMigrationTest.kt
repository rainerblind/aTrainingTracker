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

package com.atrainingtracker.trainingtracker.database

import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.testutil.RealSqliteTestHelper
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummariesDbHelper
import com.atrainingtracker.trainingtracker.migration.DatabaseSchemaRegistry
import com.atrainingtracker.trainingtracker.migration.LegacyImportEngine
import com.atrainingtracker.trainingtracker.migration.parsers.ParsedWorkoutData
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime

/**
 * Real Robolectric SQLite migration, `onCreate`/`onUpgrade` schema parity, and repository
 * persistence tests for the `epic` workout classification flag (`REQ-DAT-050`, `TST-DAT-050`, `ATT-9021`).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class WorkoutSummariesEpicMigrationTest {

    private lateinit var app: Application
    private lateinit var context: Context
    private val openedDbs = mutableListOf<SQLiteDatabase>()
    private val dbNames = mutableListOf<String>()

    private val createV31WorkoutSummariesTableWithoutEpic = """
        create table ${WorkoutSummaries.TABLE} (
            ${WorkoutSummaries.C_ID} INTEGER PRIMARY KEY AUTOINCREMENT,
            ${WorkoutSummaries.WORKOUT_NAME} text,
            ${WorkoutSummaries.FILE_BASE_NAME} text,
            ${WorkoutSummaries.DESCRIPTION} text,
            ${WorkoutSummaries.GOAL} text,
            ${WorkoutSummaries.METHOD} text,
            ${WorkoutSummaries.B_SPORT} text,
            ${WorkoutSummaries.SPORT_ID} int,
            ${WorkoutSummaries.EQUIPMENT_ID} int,
            ${WorkoutSummaries.TIME_START} DATETIME DEFAULT CURRENT_TIMESTAMP,
            ${WorkoutSummaries.TIME_ACTIVE_s} int,
            ${WorkoutSummaries.TIME_TOTAL_s} int,
            ${WorkoutSummaries.DISTANCE_TOTAL_m} real,
            ${WorkoutSummaries.SPEED_AVERAGE_mps} real,
            ${WorkoutSummaries.GC_DATA} text,
            ${WorkoutSummaries.CALORIES} int,
            ${WorkoutSummaries.LAPS} int,
            ${WorkoutSummaries.FINISHED} int,
            ${WorkoutSummaries.COMMUTE} int,
            ${WorkoutSummaries.TRAINER} int,
            ${WorkoutSummaries.UPLOAD_TO_STRAVA} int DEFAULT -1,
            ${WorkoutSummaries.ASCENDING} int,
            ${WorkoutSummaries.DESCENDING} int,
            ${WorkoutSummaries.MAP_POLYLINE} text,
            ${WorkoutSummaries.DISTANCE_STREAM} text,
            ${WorkoutSummaries.ALTITUDE_STREAM} text,
            ${WorkoutSummaries.CLUSTER_ID} int DEFAULT -1,
            ${WorkoutSummaries.EXTREMA_VALUES_CALCULATED} int,
            ${WorkoutSummaries.BOUND_MIN_LAT} real,
            ${WorkoutSummaries.BOUND_MIN_LNG} real,
            ${WorkoutSummaries.BOUND_MAX_LAT} real,
            ${WorkoutSummaries.BOUND_MAX_LNG} real,
            ${WorkoutSummaries.RACE} int DEFAULT 0,
            ${WorkoutSummaries.SOURCE} text DEFAULT 'TRACKED',
            ${WorkoutSummaries.RECORDING_ERRORS} text,
            ${WorkoutSummaries.RPE} int,
            ${WorkoutSummaries.FEEL} int,
            ${WorkoutSummaries.POLAR_DEVICE_ID} text,
            ${WorkoutSummaries.POLAR_EXERCISE_ID} text,
            ${WorkoutSummaries.POLAR_EXPECTED_START_MS} int DEFAULT 0,
            ${WorkoutSummaries.POLAR_EXPECTED_END_MS} int DEFAULT 0,
            ${WorkoutSummaries.POLAR_SYNC_PENDING} int DEFAULT 0
        )
    """.trimIndent()

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        context = app
        WorkoutRepository.resetForTesting(null)
        WorkoutSummariesDatabaseManager.setInstanceForTesting(null)
    }

    @After
    fun tearDown() {
        openedDbs.forEach { db ->
            if (db.isOpen) db.close()
        }
        openedDbs.clear()
        WorkoutRepository.resetForTesting(null)
        WorkoutSummariesDatabaseManager.setInstanceForTesting(null)
        dbNames.forEach { name -> context.deleteDatabase(name) }
        dbNames.clear()
        context.deleteDatabase(WorkoutSummariesDbHelper.DB_NAME)
    }

    private fun openTestDb(name: String): SQLiteDatabase {
        dbNames.add(name)
        context.deleteDatabase(name)
        val db = context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null)
        openedDbs.add(db)
        return db
    }

    @Test
    fun testDbVersionColumnConstantAndRegistryParity_tstDat050_1() {
        assertEquals("epic", WorkoutSummaries.EPIC)
        assertEquals(32, WorkoutSummariesDbHelper.DB_VERSION)
        assertEquals(
            32,
            DatabaseSchemaRegistry.getMaxSupportedVersion(WorkoutSummariesDbHelper.DB_NAME)
        )
        assertTrue(DatabaseSchemaRegistry.isVersionSupported(WorkoutSummariesDbHelper.DB_NAME, 32))
        assertFalse(DatabaseSchemaRegistry.isVersionSupported(WorkoutSummariesDbHelper.DB_NAME, 33))
    }

    @Test
    fun testGenuinePreV32UpgradeToV32_preservesRowsDefaultsZeroAndMatchesOnCreate_tstDat050_1() {
        val helper = WorkoutSummariesDbHelper(context)

        // 1. Seed a genuine pre-v32 WorkoutSummaries table lacking the `epic` column
        val upgradedDb = openTestDb("epic_upgrade_v31_to_v32.db")
        upgradedDb.execSQL(createV31WorkoutSummariesTableWithoutEpic)
        upgradedDb.execSQL(WorkoutSummariesDbHelper.CREATE_TABLE_EXTREMA_VALUES)
        upgradedDb.execSQL(WorkoutSummariesDbHelper.CREATE_TABLE_ACCUMULATED_SENSORS)
        upgradedDb.execSQL(WorkoutSummariesDbHelper.CREATE_TABLE_WORKOUT_PHOTOS)
        upgradedDb.execSQL(WorkoutSummariesDbHelper.CREATE_TABLE_CARDIO_POWER_DYNAMICS)

        assertFalse(
            "Pre-v32 table must genuinely lack 'epic' before migration",
            RealSqliteTestHelper.hasColumn(upgradedDb, WorkoutSummaries.TABLE, WorkoutSummaries.EPIC)
        )

        val existingRowId = upgradedDb.insertOrThrow(
            WorkoutSummaries.TABLE,
            null,
            ContentValues().apply {
                put(WorkoutSummaries.WORKOUT_NAME, "Alpenbrevet Classic")
                put(WorkoutSummaries.FILE_BASE_NAME, "2026-11-23-07-00-00")
                put(WorkoutSummaries.B_SPORT, BSportType.BIKE.name)
                put(WorkoutSummaries.SPORT_ID, 2L)
                put(WorkoutSummaries.COMMUTE, 0)
                put(WorkoutSummaries.TRAINER, 0)
                put(WorkoutSummaries.RACE, 1)
                put(WorkoutSummaries.FINISHED, 1)
            }
        )
        assertTrue(existingRowId > 0)

        // 2. Execute v31 -> v32 upgrade
        helper.onUpgrade(upgradedDb, 31, 32)

        assertTrue(
            "Upgraded v32 table must contain 'epic'",
            RealSqliteTestHelper.hasColumn(upgradedDb, WorkoutSummaries.TABLE, WorkoutSummaries.EPIC)
        )
        val epicCol = RealSqliteTestHelper.getColumn(upgradedDb, WorkoutSummaries.TABLE, WorkoutSummaries.EPIC)
        assertNotNull("Column info for 'epic' must exist", epicCol)
        assertEquals("0", epicCol!!.dfltValue)

        // Existing row preserved with default epic = 0 (false) and intact race = 1
        upgradedDb.query(
            WorkoutSummaries.TABLE,
            arrayOf(WorkoutSummaries.WORKOUT_NAME, WorkoutSummaries.RACE, WorkoutSummaries.EPIC),
            "${WorkoutSummaries.C_ID} = ?",
            arrayOf(existingRowId.toString()),
            null, null, null
        ).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Alpenbrevet Classic", c.getString(0))
            assertEquals(1, c.getInt(1))
            assertEquals(0, c.getInt(2))
        }

        // Updating epic = 1 persists alongside race = 1
        val updated = upgradedDb.update(
            WorkoutSummaries.TABLE,
            ContentValues().apply { put(WorkoutSummaries.EPIC, 1) },
            "${WorkoutSummaries.C_ID} = ?",
            arrayOf(existingRowId.toString())
        )
        assertEquals(1, updated)
        upgradedDb.query(
            WorkoutSummaries.TABLE,
            arrayOf(WorkoutSummaries.RACE, WorkoutSummaries.EPIC),
            "${WorkoutSummaries.C_ID} = ?",
            arrayOf(existingRowId.toString()),
            null, null, null
        ).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(1, c.getInt(0))
            assertEquals(1, c.getInt(1))
        }

        // Idempotent re-run must not throw
        helper.onUpgrade(upgradedDb, 31, 32)

        // 3. Verify 100% column schema parity against a fresh v32 onCreate database
        val freshDb = openTestDb("epic_fresh_v32.db")
        helper.onCreate(freshDb)

        val freshCols = RealSqliteTestHelper.getTableColumns(freshDb, WorkoutSummaries.TABLE)
            .associateBy { it.name.lowercase() }
        val upgradedCols = RealSqliteTestHelper.getTableColumns(upgradedDb, WorkoutSummaries.TABLE)
            .associateBy { it.name.lowercase() }

        assertEquals(
            "Upgraded v31->v32 column set must match fresh onCreate v32 column set",
            freshCols.keys,
            upgradedCols.keys
        )
        freshCols.forEach { (colName, freshMeta) ->
            val upgradedMeta = upgradedCols[colName]!!
            assertEquals("Type mismatch for $colName", freshMeta.type.lowercase(), upgradedMeta.type.lowercase())
            assertEquals("Default mismatch for $colName", freshMeta.dfltValue, upgradedMeta.dfltValue)
        }
    }

    @Test
    fun testUpdateWorkoutDataAndRepositoryExportHeaderPersistEpic_tstDat050_1_and_2() {
        val manager = WorkoutSummariesDatabaseManager.getInstance(context)
        val repo = WorkoutRepository.getInstance(app)

        // 1. Insert imported workout with isEpic = true and isRace = true simultaneously
        val importedId = repo.insertImportedWorkout(
            fileBaseName = "epic_race_2026_11_23",
            workoutName = "Ötztaler Radmarathon",
            timeStart = "2026-11-23 06:30:00",
            sportId = 2L,
            bSportType = BSportType.BIKE,
            equipmentId = -1L,
            workoutNotes = "227km 5500m",
            goal = "Finish strong",
            isCommute = false,
            source = WorkoutSource.FIT.name,
            uploadToStrava = false,
            isTrainer = false,
            isRace = true,
            isEpic = true
        )
        assertTrue(importedId > 0)

        val headerTrue = repo.getWorkoutExportHeader("epic_race_2026_11_23")
        assertNotNull(headerTrue)
        assertTrue("getWorkoutExportHeader must read epic = true", headerTrue!!.epic)
        assertTrue("getWorkoutExportHeader must read race = true simultaneously", headerTrue.race)

        // 2. Toggle epic off via updateWorkoutData while preserving race = true
        val editedOff = WorkoutData(
            id = importedId,
            finished = true,
            fileBaseName = "epic_race_2026_11_23",
            workoutName = "Ötztaler Radmarathon",
            sportId = 2L,
            sportName = "Bike",
            bSportType = BSportType.BIKE,
            startTimeS = 1795415400L,
            formattedDate = "2026-11-23",
            formattedTime = "06:30",
            localDateTime = LocalDateTime.of(2026, 11, 23, 6, 30),
            equipmentName = null,
            equipmentId = -1L,
            commute = false,
            trainer = false,
            race = true,
            epic = false,
            mapPolyline = "",
            encodedAltitudes = "",
            encodedDistances = "",
            uploadToStrava = 0,
            totalDistance = 227000.0,
            maxDisplacement = null,
            activeTimeSec = 28800L,
            totalTimeSec = 29400L,
            avgSpeedMps = 7.88,
            ascentMeters = 5500L,
            descentMeters = 5500L,
            minAltitude = null,
            maxAltitude = null,
            description = "227km 5500m",
            goal = "Finish strong",
            method = null,
            stravaSportName = "Ride"
        )
        manager.updateWorkoutData(editedOff)

        val headerFalse = repo.getWorkoutExportHeader("epic_race_2026_11_23")
        assertNotNull(headerFalse)
        assertFalse("updateWorkoutData must persist epic = false as 0", headerFalse!!.epic)
        assertTrue("race must remain true when epic toggled off", headerFalse.race)

        // 3. Toggle epic back on via updateWorkoutData
        manager.updateWorkoutData(editedOff.copy(epic = true))
        val headerTrueAgain = repo.getWorkoutExportHeader("epic_race_2026_11_23")
        assertNotNull(headerTrueAgain)
        assertTrue("updateWorkoutData must persist epic = true as 1", headerTrueAgain!!.epic)
        assertTrue("race must remain true alongside epic = true", headerTrueAgain.race)
    }

    @Test
    fun testLegacyImportEnginePersistParsedWorkout_persistsIsEpicIntoRealSqlite_tstDat050_3() = runBlocking {
        val repo = WorkoutRepository.getInstance(app)
        val parsed = ParsedWorkoutData(
            workoutName = "Stelvio Gran Fondo",
            workoutNotes = "Epic alpine day",
            bSportType = BSportType.BIKE,
            resolvedSportId = 2L,
            isCommute = false,
            isTrainer = false,
            isRace = true,
            isEpic = true,
            firstTime = "2026-11-23 07:15:00",
            lastTime = "2026-11-23 12:45:00"
        )

        val result = LegacyImportEngine.persistParsedWorkout(
            context = context,
            baseFileName = "2026-11-23-07-15-00_stelvio",
            parsedWorkout = parsed,
            source = WorkoutSource.FIT,
            uploadToStrava = false,
            listener = null
        )

        assertEquals(
            "LegacyImportEngine.persistParsedWorkout must succeed",
            LegacyImportEngine.ImportStatus.SUCCESS,
            result.status
        )
        assertNotNull("Imported workoutId must not be null", result.workoutId)

        val persistedHeader = repo.getWorkoutExportHeader("2026-11-23-07-15-00_stelvio")
        assertNotNull("Export header must be readable after FIT import", persistedHeader)
        assertTrue("Imported workout must persist epic = true in SQLite", persistedHeader!!.epic)
        assertTrue("Imported workout must persist race = true simultaneously", persistedHeader.race)
        assertFalse("Imported workout must keep commute = false", persistedHeader.commute)
        assertFalse("Imported workout must keep trainer = false", persistedHeader.indoorTrainerSession)
    }
}
