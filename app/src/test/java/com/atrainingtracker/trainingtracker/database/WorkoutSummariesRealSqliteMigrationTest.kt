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

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.testutil.RealSqliteTestHelper
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummariesDbHelper
import com.atrainingtracker.trainingtracker.migration.DatabaseSchemaRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Real SQLite migration and persistence verification suite for WorkoutSummariesDbHelper (TST-MIG-042.1, REQ-MIG-045).
 * Tests authentic DDL/DML execution in SQLite backed by Robolectric instead of string-matched mocks.
 */
@RunWith(RobolectricTestRunner::class)
class WorkoutSummariesRealSqliteMigrationTest {

    private lateinit var context: Context
    private val dbName = "test_workout_summaries_migration.db"
    private var db: SQLiteDatabase? = null
    private lateinit var helper: WorkoutSummariesDbHelper

    private val createV22WorkoutSummariesTable = """
        CREATE TABLE ${WorkoutSummaries.TABLE} (
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
            ${WorkoutSummaries.ASCENDING} int,
            ${WorkoutSummaries.DESCENDING} int,
            ${WorkoutSummaries.COMMUTE} int,
            ${WorkoutSummaries.TRAINER} int,
            ${WorkoutSummaries.MAP_POLYLINE} text,
            ${WorkoutSummaries.UPLOAD_TO_STRAVA} int,
            ${WorkoutSummaries.DISTANCE_STREAM} text,
            ${WorkoutSummaries.ALTITUDE_STREAM} text,
            ${WorkoutSummaries.CLUSTER_ID} int DEFAULT -1,
            ${WorkoutSummaries.EXTREMA_VALUES_CALCULATED} int,
            ${WorkoutSummaries.BOUND_MIN_LAT} real,
            ${WorkoutSummaries.BOUND_MIN_LNG} real,
            ${WorkoutSummaries.BOUND_MAX_LAT} real,
            ${WorkoutSummaries.BOUND_MAX_LNG} real
        )
    """.trimIndent()

    private val createV22ExtremaTable = """
        CREATE TABLE ${WorkoutSummaries.TABLE_EXTREMA_VALUES} (
            ${WorkoutSummaries.C_ID} INTEGER PRIMARY KEY AUTOINCREMENT,
            ${WorkoutSummaries.WORKOUT_ID} int,
            ${WorkoutSummaries.EXTREMA_TYPE} text,
            ${WorkoutSummaries.SENSOR_TYPE} text,
            ${WorkoutSummaries.VALUE} real,
            ${WorkoutSummaries.LATITUDE} real,
            ${WorkoutSummaries.LONGITUDE} real,
            ${WorkoutSummaries.SAMPLES_COLUMN_ID} int
        )
    """.trimIndent()

    private val createAccumulatedSensorsTable = """
        CREATE TABLE ${WorkoutSummaries.TABLE_ACCUMULATED_SENSORS} (
            ${WorkoutSummaries.C_ID} INTEGER PRIMARY KEY AUTOINCREMENT,
            ${WorkoutSummaries.WORKOUT_ID} int,
            ${WorkoutSummaries.SENSOR_TYPE} text
        )
    """.trimIndent()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(dbName)
        db = context.openOrCreateDatabase(dbName, Context.MODE_PRIVATE, null)
        helper = WorkoutSummariesDbHelper(context)
    }

    @After
    fun tearDown() {
        RealSqliteTestHelper.cleanupDatabase(db, dbName)
    }

    private fun scaffoldV22Schema(database: SQLiteDatabase) {
        database.execSQL(createV22WorkoutSummariesTable)
        database.execSQL(createV22ExtremaTable)
        database.execSQL(createAccumulatedSensorsTable)
        database.execSQL("CREATE INDEX IF NOT EXISTS idx_extrema_sensor_extrema ON ${WorkoutSummaries.TABLE_EXTREMA_VALUES} (${WorkoutSummaries.SENSOR_TYPE}, ${WorkoutSummaries.EXTREMA_TYPE})")
        database.version = 22
    }

    @Test
    fun testIncrementalUpgradeV22ToV23AddsRaceColumn() {
        val database = checkNotNull(db)
        scaffoldV22Schema(database)

        // Insert historical workout row before upgrade
        val cv = ContentValues().apply {
            put(WorkoutSummaries.WORKOUT_NAME, "Fast 10k Run")
            put(WorkoutSummaries.DISTANCE_TOTAL_m, 10000.0)
            put(WorkoutSummaries.TIME_TOTAL_s, 3000)
        }
        val insertedId = database.insertOrThrow(WorkoutSummaries.TABLE, null, cv)
        assertEquals(1L, insertedId)

        // Perform incremental upgrade v22 -> v23
        helper.onUpgrade(database, 22, 23)
        database.version = 23

        // Verify column metadata in real SQLite
        assertTrue("Column race must exist after v23 upgrade", RealSqliteTestHelper.hasColumn(database, WorkoutSummaries.TABLE, WorkoutSummaries.RACE))
        val raceCol = RealSqliteTestHelper.getColumn(database, WorkoutSummaries.TABLE, WorkoutSummaries.RACE)
        assertNotNull(raceCol)
        assertEquals("0", raceCol?.dfltValue)

        // Verify data preservation
        database.query(WorkoutSummaries.TABLE, null, "${WorkoutSummaries.C_ID} = ?", arrayOf("1"), null, null, null).use { cursor ->
            assertTrue(cursor.moveToFirst())
            val nameIdx = cursor.getColumnIndexOrThrow(WorkoutSummaries.WORKOUT_NAME)
            val distIdx = cursor.getColumnIndexOrThrow(WorkoutSummaries.DISTANCE_TOTAL_m)
            val raceIdx = cursor.getColumnIndexOrThrow(WorkoutSummaries.RACE)

            assertEquals("Fast 10k Run", cursor.getString(nameIdx))
            assertEquals(10000.0, cursor.getDouble(distIdx), 0.001)
            assertEquals(0, cursor.getInt(raceIdx))
        }
    }

    @Test
    fun testIncrementalUpgradeV23ToV24AddsSourceColumn() {
        val database = checkNotNull(db)
        scaffoldV22Schema(database)
        helper.onUpgrade(database, 22, 23)
        database.version = 23

        // Insert row at v23 with race = 1
        val cv = ContentValues().apply {
            put(WorkoutSummaries.WORKOUT_NAME, "Marathon Race")
            put(WorkoutSummaries.RACE, 1)
        }
        database.insertOrThrow(WorkoutSummaries.TABLE, null, cv)

        // Perform incremental upgrade v23 -> v24
        helper.onUpgrade(database, 23, 24)
        database.version = 24

        // Verify source column in real SQLite
        assertTrue("Column source must exist after v24 upgrade", RealSqliteTestHelper.hasColumn(database, WorkoutSummaries.TABLE, WorkoutSummaries.SOURCE))
        val sourceCol = RealSqliteTestHelper.getColumn(database, WorkoutSummaries.TABLE, WorkoutSummaries.SOURCE)
        assertNotNull(sourceCol)
        assertEquals("'TRACKED'", sourceCol?.dfltValue)

        // Verify pre-existing row received default 'TRACKED' and kept race = 1
        database.query(WorkoutSummaries.TABLE, null, "${WorkoutSummaries.C_ID} = ?", arrayOf("1"), null, null, null).use { cursor ->
            assertTrue(cursor.moveToFirst())
            val nameIdx = cursor.getColumnIndexOrThrow(WorkoutSummaries.WORKOUT_NAME)
            val raceIdx = cursor.getColumnIndexOrThrow(WorkoutSummaries.RACE)
            val sourceIdx = cursor.getColumnIndexOrThrow(WorkoutSummaries.SOURCE)

            assertEquals("Marathon Race", cursor.getString(nameIdx))
            assertEquals(1, cursor.getInt(raceIdx))
            assertEquals("TRACKED", cursor.getString(sourceIdx))
        }
    }

    @Test
    fun testIncrementalUpgradeV24ToV25AddsCountColumnToExtrema() {
        val database = checkNotNull(db)
        scaffoldV22Schema(database)
        helper.onUpgrade(database, 22, 24)
        database.version = 24

        // Insert historical extrema row at v24
        val cv = ContentValues().apply {
            put(WorkoutSummaries.WORKOUT_ID, 1)
            put(WorkoutSummaries.SENSOR_TYPE, "HEART_RATE")
            put(WorkoutSummaries.EXTREMA_TYPE, "MAX")
            put(WorkoutSummaries.VALUE, 185.0)
        }
        database.insertOrThrow(WorkoutSummaries.TABLE_EXTREMA_VALUES, null, cv)

        // Perform incremental upgrade v24 -> v25
        helper.onUpgrade(database, 24, 25)
        database.version = 25

        // Verify count column in real SQLite
        assertTrue("Column count must exist on extrema_values", RealSqliteTestHelper.hasColumn(database, WorkoutSummaries.TABLE_EXTREMA_VALUES, WorkoutSummaries.COUNT))

        // Verify pre-existing row retains value and count is null
        database.query(WorkoutSummaries.TABLE_EXTREMA_VALUES, null, "${WorkoutSummaries.C_ID} = ?", arrayOf("1"), null, null, null).use { cursor ->
            assertTrue(cursor.moveToFirst())
            val valIdx = cursor.getColumnIndexOrThrow(WorkoutSummaries.VALUE)
            val countIdx = cursor.getColumnIndexOrThrow(WorkoutSummaries.COUNT)

            assertEquals(185.0, cursor.getDouble(valIdx), 0.001)
            assertTrue("Historical count should be null", cursor.isNull(countIdx))
        }
    }

    @Test
    fun testFullUpgradeV22ToV25PreservesDataAndAllowsNewV25Inserts() {
        val database = checkNotNull(db)
        scaffoldV22Schema(database)

        // Insert baseline workout and extrema rows at v22
        val workoutCv = ContentValues().apply {
            put(WorkoutSummaries.WORKOUT_NAME, "Sunday Long Ride")
            put(WorkoutSummaries.DISTANCE_TOTAL_m, 65432.1)
            put(WorkoutSummaries.TIME_TOTAL_s, 7200)
            put(WorkoutSummaries.CALORIES, 1450)
        }
        database.insertOrThrow(WorkoutSummaries.TABLE, null, workoutCv)

        val extremaCv = ContentValues().apply {
            put(WorkoutSummaries.WORKOUT_ID, 1)
            put(WorkoutSummaries.SENSOR_TYPE, "CADENCE")
            put(WorkoutSummaries.EXTREMA_TYPE, "AVG")
            put(WorkoutSummaries.VALUE, 88.0)
        }
        database.insertOrThrow(WorkoutSummaries.TABLE_EXTREMA_VALUES, null, extremaCv)

        // Upgrade directly from v22 to v25
        helper.onUpgrade(database, 22, 25)
        database.version = 25

        // Verify all 3 newly added columns exist in real SQLite
        assertTrue(RealSqliteTestHelper.hasColumn(database, WorkoutSummaries.TABLE, WorkoutSummaries.RACE))
        assertTrue(RealSqliteTestHelper.hasColumn(database, WorkoutSummaries.TABLE, WorkoutSummaries.SOURCE))
        assertTrue(RealSqliteTestHelper.hasColumn(database, WorkoutSummaries.TABLE_EXTREMA_VALUES, WorkoutSummaries.COUNT))

        // Verify baseline row preserved with defaults
        database.query(WorkoutSummaries.TABLE, null, "${WorkoutSummaries.C_ID} = ?", arrayOf("1"), null, null, null).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Sunday Long Ride", cursor.getString(cursor.getColumnIndexOrThrow(WorkoutSummaries.WORKOUT_NAME)))
            assertEquals(65432.1, cursor.getDouble(cursor.getColumnIndexOrThrow(WorkoutSummaries.DISTANCE_TOTAL_m)), 0.001)
            assertEquals(7200, cursor.getInt(cursor.getColumnIndexOrThrow(WorkoutSummaries.TIME_TOTAL_s)))
            assertEquals(1450, cursor.getInt(cursor.getColumnIndexOrThrow(WorkoutSummaries.CALORIES)))
            assertEquals(0, cursor.getInt(cursor.getColumnIndexOrThrow(WorkoutSummaries.RACE)))
            assertEquals("TRACKED", cursor.getString(cursor.getColumnIndexOrThrow(WorkoutSummaries.SOURCE)))
        }

        // Insert new v25 row with all new columns explicitly populated
        val newWorkoutCv = ContentValues().apply {
            put(WorkoutSummaries.WORKOUT_NAME, "Imported Race")
            put(WorkoutSummaries.DISTANCE_TOTAL_m, 42195.0)
            put(WorkoutSummaries.RACE, 1)
            put(WorkoutSummaries.SOURCE, "STRAVA")
        }
        val newId = database.insertOrThrow(WorkoutSummaries.TABLE, null, newWorkoutCv)
        assertEquals(2L, newId)

        val newExtremaCv = ContentValues().apply {
            put(WorkoutSummaries.WORKOUT_ID, 2)
            put(WorkoutSummaries.SENSOR_TYPE, "POWER")
            put(WorkoutSummaries.EXTREMA_TYPE, "MAX")
            put(WorkoutSummaries.VALUE, 950.0)
            put(WorkoutSummaries.COUNT, 3600)
        }
        database.insertOrThrow(WorkoutSummaries.TABLE_EXTREMA_VALUES, null, newExtremaCv)

        // Query new row
        database.query(WorkoutSummaries.TABLE, null, "${WorkoutSummaries.C_ID} = ?", arrayOf("2"), null, null, null).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Imported Race", cursor.getString(cursor.getColumnIndexOrThrow(WorkoutSummaries.WORKOUT_NAME)))
            assertEquals(1, cursor.getInt(cursor.getColumnIndexOrThrow(WorkoutSummaries.RACE)))
            assertEquals("STRAVA", cursor.getString(cursor.getColumnIndexOrThrow(WorkoutSummaries.SOURCE)))
        }

        database.query(WorkoutSummaries.TABLE_EXTREMA_VALUES, null, "${WorkoutSummaries.WORKOUT_ID} = ?", arrayOf("2"), null, null, null).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(950.0, cursor.getDouble(cursor.getColumnIndexOrThrow(WorkoutSummaries.VALUE)), 0.001)
            assertEquals(3600, cursor.getInt(cursor.getColumnIndexOrThrow(WorkoutSummaries.COUNT)))
        }
    }

    @Test
    fun testLegacyUpgradeV3ToV25RealSqlite() {
        val database = checkNotNull(db)

        // Scaffold v3 table
        database.execSQL("""
            CREATE TABLE ${WorkoutSummaries.TABLE} (
                ${WorkoutSummaries.C_ID} INTEGER PRIMARY KEY AUTOINCREMENT,
                ${WorkoutSummaries.WORKOUT_NAME} text,
                ${WorkoutSummaries.FILE_BASE_NAME} text,
                ${WorkoutSummaries.DESCRIPTION} text,
                ${WorkoutSummaries.GOAL} text,
                ${WorkoutSummaries.METHOD} text,
                sport text,
                ${WorkoutSummaries.TIME_START} DATETIME DEFAULT CURRENT_TIMESTAMP,
                ${WorkoutSummaries.TIME_ACTIVE_s} int,
                ${WorkoutSummaries.TIME_TOTAL_s} int,
                ${WorkoutSummaries.DISTANCE_TOTAL_m} real,
                ${WorkoutSummaries.SPEED_AVERAGE_mps} real,
                ${WorkoutSummaries.GC_DATA} text,
                ${WorkoutSummaries.CALORIES} int
            )
        """.trimIndent())
        database.version = 3

        // Insert legacy v3 row
        val cv = ContentValues().apply {
            put(WorkoutSummaries.WORKOUT_NAME, "Legacy Morning Run")
            put("sport", "RUNNING")
            put(WorkoutSummaries.DISTANCE_TOTAL_m, 5000.0)
            put(WorkoutSummaries.CALORIES, 350)
        }
        val insertedId = database.insertOrThrow(WorkoutSummaries.TABLE, null, cv)
        assertEquals(1L, insertedId)

        // Perform full upgrade v3 -> v25
        helper.onUpgrade(database, 3, 25)
        database.version = 25

        // Verify newly added columns exist
        assertTrue(RealSqliteTestHelper.hasColumn(database, WorkoutSummaries.TABLE, WorkoutSummaries.SPORT_ID))
        assertTrue(RealSqliteTestHelper.hasColumn(database, WorkoutSummaries.TABLE, WorkoutSummaries.B_SPORT))
        assertTrue(RealSqliteTestHelper.hasColumn(database, WorkoutSummaries.TABLE, WorkoutSummaries.RACE))
        assertTrue(RealSqliteTestHelper.hasColumn(database, WorkoutSummaries.TABLE, WorkoutSummaries.SOURCE))
        assertTrue(RealSqliteTestHelper.hasColumn(database, WorkoutSummaries.TABLE_EXTREMA_VALUES, WorkoutSummaries.COUNT))

        // Verify data preservation and column population
        database.query(WorkoutSummaries.TABLE, null, "${WorkoutSummaries.C_ID} = ?", arrayOf("1"), null, null, null).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Legacy Morning Run", cursor.getString(cursor.getColumnIndexOrThrow(WorkoutSummaries.WORKOUT_NAME)))
            assertEquals(5000.0, cursor.getDouble(cursor.getColumnIndexOrThrow(WorkoutSummaries.DISTANCE_TOTAL_m)), 0.001)
            assertEquals(350, cursor.getInt(cursor.getColumnIndexOrThrow(WorkoutSummaries.CALORIES)))
            assertEquals(0, cursor.getInt(cursor.getColumnIndexOrThrow(WorkoutSummaries.RACE)))
            assertEquals("TRACKED", cursor.getString(cursor.getColumnIndexOrThrow(WorkoutSummaries.SOURCE)))
        }
    }

    @Test
    fun testLegacyUpgradeV10ToV25SyntaxAndColumnGuards() {
        val database = checkNotNull(db)

        // Scaffold v10 tables
        database.execSQL("""
            CREATE TABLE ${WorkoutSummaries.TABLE} (
                ${WorkoutSummaries.C_ID} INTEGER PRIMARY KEY AUTOINCREMENT,
                ${WorkoutSummaries.WORKOUT_NAME} text,
                ${WorkoutSummaries.FILE_BASE_NAME} text,
                ${WorkoutSummaries.DESCRIPTION} text,
                ${WorkoutSummaries.GOAL} text,
                ${WorkoutSummaries.METHOD} text,
                sport text,
                ${WorkoutSummaries.TIME_START} DATETIME DEFAULT CURRENT_TIMESTAMP,
                ${WorkoutSummaries.TIME_ACTIVE_s} int,
                ${WorkoutSummaries.TIME_TOTAL_s} int,
                ${WorkoutSummaries.DISTANCE_TOTAL_m} real,
                ${WorkoutSummaries.SPEED_AVERAGE_mps} real,
                ${WorkoutSummaries.GC_DATA} text,
                ${WorkoutSummaries.CALORIES} int,
                ${WorkoutSummaries.COMMUTE} int,
                ${WorkoutSummaries.TRAINER} int,
                ${WorkoutSummaries.ASCENDING} int,
                ${WorkoutSummaries.DESCENDING} int,
                ${WorkoutSummaries.EXTREMA_VALUES_CALCULATED} int
            )
        """.trimIndent())
        database.execSQL(createV22ExtremaTable)
        database.execSQL(createAccumulatedSensorsTable)

        // v10 WorkoutNamePatterns table
        database.execSQL("""
            CREATE TABLE WorkoutNamePatterns (
                _id INTEGER PRIMARY KEY AUTOINCREMENT,
                sportId int,
                startLocationName text,
                endLocationName text,
                fancyName text,
                addCounter int,
                counter int,
                addVia int
            )
        """.trimIndent())
        database.version = 10

        val patternCv = ContentValues().apply {
            put("sportId", 1)
            put("fancyName", "Morning Commute")
        }
        database.insertOrThrow("WorkoutNamePatterns", null, patternCv)

        // Perform full upgrade v10 -> v25 (must not crash with syntax error near '?')
        helper.onUpgrade(database, 10, 25)
        database.version = 25

        // Verify columns and data
        assertTrue(RealSqliteTestHelper.hasColumn(database, "WorkoutNamePatterns", "sportId"))
        database.query("WorkoutNamePatterns", null, "_id = ?", arrayOf("1"), null, null, null).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Morning Commute", cursor.getString(cursor.getColumnIndexOrThrow("fancyName")))
        }

        assertTrue(RealSqliteTestHelper.hasColumn(database, WorkoutSummaries.TABLE, WorkoutSummaries.RACE))
        assertTrue(RealSqliteTestHelper.hasColumn(database, WorkoutSummaries.TABLE, WorkoutSummaries.SOURCE))
    }

    @Test
    fun testDatabaseOpenIsInstantaneousWithoutSynchronousBackfill() {
        val database = checkNotNull(db)
        scaffoldV22Schema(database)

        // Measure onUpgrade duration - DDL-only must be instantaneous (< 500ms)
        val startTime = System.currentTimeMillis()
        helper.onUpgrade(database, 22, 25)
        val durationMs = System.currentTimeMillis() - startTime

        assertTrue("onUpgrade DDL should execute instantaneously (took ${durationMs}ms)", durationMs < 500)
    }

    @Test
    fun testIncrementalUpgradeV26ToV27CreatesWorkoutPhotosTableAndIndex() {
        val database = checkNotNull(db)
        scaffoldV22Schema(database)
        helper.onUpgrade(database, 22, 26)
        database.version = 26

        // Upgrade v26 -> v27
        helper.onUpgrade(database, 26, 27)
        database.version = 27

        // Verify table and columns
        assertTrue("Table workout_photos must exist", RealSqliteTestHelper.hasTable(database, WorkoutSummaries.TABLE_WORKOUT_PHOTOS))
        assertTrue(RealSqliteTestHelper.hasColumn(database, WorkoutSummaries.TABLE_WORKOUT_PHOTOS, WorkoutSummaries.C_ID))
        assertTrue(RealSqliteTestHelper.hasColumn(database, WorkoutSummaries.TABLE_WORKOUT_PHOTOS, WorkoutSummaries.WORKOUT_ID))
        assertTrue(RealSqliteTestHelper.hasColumn(database, WorkoutSummaries.TABLE_WORKOUT_PHOTOS, WorkoutSummaries.PHOTO_FILE_PATH))
        assertTrue(RealSqliteTestHelper.hasColumn(database, WorkoutSummaries.TABLE_WORKOUT_PHOTOS, WorkoutSummaries.PHOTO_TAKEN_AT_MS))
        assertTrue(RealSqliteTestHelper.hasColumn(database, WorkoutSummaries.TABLE_WORKOUT_PHOTOS, WorkoutSummaries.PHOTO_LATITUDE))
        assertTrue(RealSqliteTestHelper.hasColumn(database, WorkoutSummaries.TABLE_WORKOUT_PHOTOS, WorkoutSummaries.PHOTO_LONGITUDE))
        assertTrue(RealSqliteTestHelper.hasColumn(database, WorkoutSummaries.TABLE_WORKOUT_PHOTOS, WorkoutSummaries.PHOTO_CAPTION))

        // Verify photo insertion and querying
        val photoCv = ContentValues().apply {
            put(WorkoutSummaries.WORKOUT_ID, 1L)
            put(WorkoutSummaries.PHOTO_FILE_PATH, "/data/user/0/test/files/workout_photos/1/photo_1.jpg")
            put(WorkoutSummaries.PHOTO_TAKEN_AT_MS, 1716000000000L)
            put(WorkoutSummaries.PHOTO_LATITUDE, 48.137154)
            put(WorkoutSummaries.PHOTO_LONGITUDE, 11.576124)
            put(WorkoutSummaries.PHOTO_CAPTION, "Summit view")
        }
        val insertedPhotoId = database.insertOrThrow(WorkoutSummaries.TABLE_WORKOUT_PHOTOS, null, photoCv)
        assertTrue(insertedPhotoId > 0)

        database.query(WorkoutSummaries.TABLE_WORKOUT_PHOTOS, null, "${WorkoutSummaries.C_ID} = ?", arrayOf(insertedPhotoId.toString()), null, null, null).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("/data/user/0/test/files/workout_photos/1/photo_1.jpg", cursor.getString(cursor.getColumnIndexOrThrow(WorkoutSummaries.PHOTO_FILE_PATH)))
            assertEquals(1716000000000L, cursor.getLong(cursor.getColumnIndexOrThrow(WorkoutSummaries.PHOTO_TAKEN_AT_MS)))
            assertEquals(48.137154, cursor.getDouble(cursor.getColumnIndexOrThrow(WorkoutSummaries.PHOTO_LATITUDE)), 0.00001)
            assertEquals(11.576124, cursor.getDouble(cursor.getColumnIndexOrThrow(WorkoutSummaries.PHOTO_LONGITUDE)), 0.00001)
            assertEquals("Summit view", cursor.getString(cursor.getColumnIndexOrThrow(WorkoutSummaries.PHOTO_CAPTION)))
        }
    }

    @Test
    fun testIncrementalUpgradeV27ToV28AddsRecordingErrorsColumn() {
        val database = checkNotNull(db)
        scaffoldV22Schema(database)
        helper.onUpgrade(database, 22, 27)
        database.version = 27

        // Insert workout at v27
        val cv = ContentValues().apply {
            put(WorkoutSummaries.WORKOUT_NAME, "Trail Run v27")
            put(WorkoutSummaries.DISTANCE_TOTAL_m, 12000.0)
        }
        val insertedId = database.insertOrThrow(WorkoutSummaries.TABLE, null, cv)
        assertEquals(1L, insertedId)

        // Upgrade v27 -> v28
        helper.onUpgrade(database, 27, 28)
        database.version = 28

        // Verify column exists in real SQLite
        assertTrue("Column recordingErrors must exist after v28 upgrade", RealSqliteTestHelper.hasColumn(database, WorkoutSummaries.TABLE, WorkoutSummaries.RECORDING_ERRORS))
        val col = RealSqliteTestHelper.getColumn(database, WorkoutSummaries.TABLE, WorkoutSummaries.RECORDING_ERRORS)
        assertNotNull(col)
        assertNull("Default value should be null", col?.dfltValue)

        // Verify existing workout has null recordingErrors
        database.query(WorkoutSummaries.TABLE, null, "${WorkoutSummaries.C_ID} = ?", arrayOf("1"), null, null, null).use { cursor ->
            assertTrue(cursor.moveToFirst())
            val errIdx = cursor.getColumnIndexOrThrow(WorkoutSummaries.RECORDING_ERRORS)
            assertTrue("Existing row must have null recordingErrors", cursor.isNull(errIdx))
        }

        // Test updating recordingErrors
        val errPayload = """[{"stage":"SAMPLE_WRITE","count":5}]"""
        val updateCv = ContentValues().apply {
            put(WorkoutSummaries.RECORDING_ERRORS, errPayload)
        }
        database.update(WorkoutSummaries.TABLE, updateCv, "${WorkoutSummaries.C_ID} = ?", arrayOf("1"))

        database.query(WorkoutSummaries.TABLE, null, "${WorkoutSummaries.C_ID} = ?", arrayOf("1"), null, null, null).use { cursor ->
            assertTrue(cursor.moveToFirst())
            val errIdx = cursor.getColumnIndexOrThrow(WorkoutSummaries.RECORDING_ERRORS)
            assertEquals(errPayload, cursor.getString(errIdx))
        }
    }

    @Test
    fun testIncrementalUpgradeV28ToV29CreatesCardioPowerDynamicsTable() {
        val database = checkNotNull(db)
        scaffoldV22Schema(database)
        helper.onUpgrade(database, 22, 28)
        database.version = 28

        // Insert workout at v28
        val cv = ContentValues().apply {
            put(WorkoutSummaries.WORKOUT_NAME, "Tempo Ride v28")
            put(WorkoutSummaries.DISTANCE_TOTAL_m, 35000.0)
        }
        val insertedWorkoutId = database.insertOrThrow(WorkoutSummaries.TABLE, null, cv)
        assertEquals(1L, insertedWorkoutId)

        // Upgrade v28 -> v29
        helper.onUpgrade(database, 28, 29)
        database.version = 29

        // Verify table and columns exist in real SQLite
        assertTrue("Table cardio_power_dynamics must exist", RealSqliteTestHelper.hasTable(database, WorkoutSummaries.TABLE_CARDIO_POWER_DYNAMICS))
        assertTrue(RealSqliteTestHelper.hasColumn(database, WorkoutSummaries.TABLE_CARDIO_POWER_DYNAMICS, WorkoutSummaries.CPD_WORKOUT_ID))
        assertTrue(RealSqliteTestHelper.hasColumn(database, WorkoutSummaries.TABLE_CARDIO_POWER_DYNAMICS, WorkoutSummaries.CPD_TAU_START))
        assertTrue(RealSqliteTestHelper.hasColumn(database, WorkoutSummaries.TABLE_CARDIO_POWER_DYNAMICS, WorkoutSummaries.CPD_HR0_START))
        assertTrue(RealSqliteTestHelper.hasColumn(database, WorkoutSummaries.TABLE_CARDIO_POWER_DYNAMICS, WorkoutSummaries.CPD_K_START))
        assertTrue(RealSqliteTestHelper.hasColumn(database, WorkoutSummaries.TABLE_CARDIO_POWER_DYNAMICS, WorkoutSummaries.CPD_HR200_START))
        assertTrue(RealSqliteTestHelper.hasColumn(database, WorkoutSummaries.TABLE_CARDIO_POWER_DYNAMICS, WorkoutSummaries.CPD_R2_START))
        assertTrue(RealSqliteTestHelper.hasColumn(database, WorkoutSummaries.TABLE_CARDIO_POWER_DYNAMICS, WorkoutSummaries.CPD_RMSE_START))
        assertTrue(RealSqliteTestHelper.hasColumn(database, WorkoutSummaries.TABLE_CARDIO_POWER_DYNAMICS, WorkoutSummaries.CPD_TAU_END))
        assertTrue(RealSqliteTestHelper.hasColumn(database, WorkoutSummaries.TABLE_CARDIO_POWER_DYNAMICS, WorkoutSummaries.CPD_HR0_END))
        assertTrue(RealSqliteTestHelper.hasColumn(database, WorkoutSummaries.TABLE_CARDIO_POWER_DYNAMICS, WorkoutSummaries.CPD_K_END))
        assertTrue(RealSqliteTestHelper.hasColumn(database, WorkoutSummaries.TABLE_CARDIO_POWER_DYNAMICS, WorkoutSummaries.CPD_HR200_END))
        assertTrue(RealSqliteTestHelper.hasColumn(database, WorkoutSummaries.TABLE_CARDIO_POWER_DYNAMICS, WorkoutSummaries.CPD_R2_END))
        assertTrue(RealSqliteTestHelper.hasColumn(database, WorkoutSummaries.TABLE_CARDIO_POWER_DYNAMICS, WorkoutSummaries.CPD_RMSE_END))
        assertTrue(RealSqliteTestHelper.hasColumn(database, WorkoutSummaries.TABLE_CARDIO_POWER_DYNAMICS, WorkoutSummaries.CPD_DELTA_HR200))
        assertTrue(RealSqliteTestHelper.hasColumn(database, WorkoutSummaries.TABLE_CARDIO_POWER_DYNAMICS, WorkoutSummaries.CPD_DELTA_TAU))
        assertTrue(RealSqliteTestHelper.hasColumn(database, WorkoutSummaries.TABLE_CARDIO_POWER_DYNAMICS, WorkoutSummaries.CPD_DELTA_HR0))

        // Verify insertion and querying of dynamics
        val record = CardioPowerDynamicsRecord(
            workoutId = insertedWorkoutId,
            tauStart = 34.5,
            hr0Start = 86.2,
            kStart = 0.215,
            hr200Start = 129.2,
            r2Start = 0.85,
            rmseStart = 2.1,
            tauEnd = 48.0,
            hr0End = 98.0,
            kEnd = 0.235,
            hr200End = 145.0,
            r2End = 0.82,
            rmseEnd = 2.4,
            deltaHr200 = 15.8,
            deltaTau = 13.5,
            deltaHr0 = 11.8
        )
        val manager = WorkoutSummariesDatabaseManager.getInstance(context)
        manager.saveCardioPowerDynamics(insertedWorkoutId, record)

        val retrieved = manager.getCardioPowerDynamics(insertedWorkoutId)
        assertNotNull(retrieved)
        val r = checkNotNull(retrieved)
        assertEquals(insertedWorkoutId, r.workoutId)
        assertEquals(34.5, r.tauStart, 0.001)
        assertEquals(86.2, r.hr0Start, 0.001)
        assertEquals(0.215, r.kStart, 0.001)
        assertEquals(129.2, r.hr200Start, 0.001)
        assertEquals(0.85, r.r2Start, 0.001)
        assertEquals(2.1, r.rmseStart, 0.001)
        assertEquals(48.0, r.tauEnd ?: 0.0, 0.001)
        assertEquals(145.0, r.hr200End ?: 0.0, 0.001)
        assertEquals(15.8, r.deltaHr200 ?: 0.0, 0.001)

        // Verify deletion
        manager.deleteCardioPowerDynamics(insertedWorkoutId)
        assertNull(manager.getCardioPowerDynamics(insertedWorkoutId))
    }

    @Test
    fun testIncrementalUpgradeV29ToV30AddsRpeAndFeelColumns() {
        val database = checkNotNull(db)
        scaffoldV22Schema(database)
        helper.onUpgrade(database, 22, 29)
        database.version = 29

        // Insert workout at v29
        val cv = ContentValues().apply {
            put(WorkoutSummaries.WORKOUT_NAME, "Tempo Ride v29")
            put(WorkoutSummaries.DISTANCE_TOTAL_m, 35000.0)
        }
        val insertedWorkoutId = database.insertOrThrow(WorkoutSummaries.TABLE, null, cv)
        assertEquals(1L, insertedWorkoutId)

        // Upgrade v29 -> v30
        helper.onUpgrade(database, 29, 30)
        database.version = 30

        // Verify columns exist in real SQLite
        assertTrue(RealSqliteTestHelper.hasColumn(database, WorkoutSummaries.TABLE, WorkoutSummaries.RPE))
        assertTrue(RealSqliteTestHelper.hasColumn(database, WorkoutSummaries.TABLE, WorkoutSummaries.FEEL))

        // Existing row must have null rpe and feel
        database.query(WorkoutSummaries.TABLE, null, "${WorkoutSummaries.C_ID} = ?", arrayOf("1"), null, null, null).use { cursor ->
            assertTrue(cursor.moveToFirst())
            val rpeIdx = cursor.getColumnIndexOrThrow(WorkoutSummaries.RPE)
            val feelIdx = cursor.getColumnIndexOrThrow(WorkoutSummaries.FEEL)
            assertTrue("Existing row must have null rpe", cursor.isNull(rpeIdx))
            assertTrue("Existing row must have null feel", cursor.isNull(feelIdx))
        }

        // Test updating rpe and feel
        val updateCv = ContentValues().apply {
            put(WorkoutSummaries.RPE, 7)
            put(WorkoutSummaries.FEEL, 4)
        }
        database.update(WorkoutSummaries.TABLE, updateCv, "${WorkoutSummaries.C_ID} = ?", arrayOf("1"))

        database.query(WorkoutSummaries.TABLE, null, "${WorkoutSummaries.C_ID} = ?", arrayOf("1"), null, null, null).use { cursor ->
            assertTrue(cursor.moveToFirst())
            val rpeIdx = cursor.getColumnIndexOrThrow(WorkoutSummaries.RPE)
            val feelIdx = cursor.getColumnIndexOrThrow(WorkoutSummaries.FEEL)
            assertEquals(7, cursor.getInt(rpeIdx))
            assertEquals(4, cursor.getInt(feelIdx))
        }
    }

    @Test
    fun testSchemaRegistryMatchesV30() {
        assertEquals(30, DatabaseSchemaRegistry.getMaxSupportedVersion("WorkoutSummaries.db"))
    }
}

