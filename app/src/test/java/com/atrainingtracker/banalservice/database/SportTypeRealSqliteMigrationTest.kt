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

package com.atrainingtracker.banalservice.database

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager.SportType
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager.SportTypeDbHelper
import com.atrainingtracker.testutil.RealSqliteTestHelper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Real SQLite migration and data preservation verification suite for SportTypeDbHelper (TST-MIG-042.2, REQ-MIG-045).
 * Tests authentic DDL/DML execution in SQLite backed by Robolectric, verifying custom sport preservation and WALK migration.
 */
@RunWith(RobolectricTestRunner::class)
class SportTypeRealSqliteMigrationTest {

    private lateinit var context: Context
    private val dbName = "test_sport_type_migration.db"
    private var db: SQLiteDatabase? = null
    private lateinit var helper: SportTypeDbHelper

    private val createV1SportTypeTable = """
        create table ${SportType.TABLE} (
            ${SportType.C_ID} INTEGER PRIMARY KEY AUTOINCREMENT,
            ${SportType.UI_NAME} text,
            ${SportType.BASE_SPORT_TYPE} text,
            ${SportType.GOLDEN_CHEETAH_NAME} text,
            ${SportType.TCX_NAME} text,
            ${SportType.STRAVA_NAME} text,
            ${SportType.RUNKEEPER_NAME} text,
            ${SportType.TRAINING_PEAKS_NAME} text,
            ${SportType.MIN_AVG_SPEED} real,
            ${SportType.MAX_AVG_SPEED} real
        )
    """.trimIndent()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(dbName)
        db = context.openOrCreateDatabase(dbName, Context.MODE_PRIVATE, null)
        helper = SportTypeDbHelper(context)
    }

    @After
    fun tearDown() {
        RealSqliteTestHelper.cleanupDatabase(db, dbName)
    }

    private fun scaffoldV1Schema(database: SQLiteDatabase) {
        database.execSQL(createV1SportTypeTable)
        database.version = 1

        // Insert default sports at v1 (WALK has baseSportType = UNKNOWN)
        val defaultSports = listOf(
            Triple(1L, "Other", BSportType.UNKNOWN.name),
            Triple(2L, "Run", BSportType.RUN.name),
            Triple(3L, "Bike", BSportType.BIKE.name),
            Triple(4L, "Walk", BSportType.UNKNOWN.name),
            Triple(5L, "MTB", BSportType.BIKE.name)
        )
        for ((id, name, baseType) in defaultSports) {
            val cv = ContentValues().apply {
                put(SportType.C_ID, id)
                put(SportType.UI_NAME, name)
                put(SportType.BASE_SPORT_TYPE, baseType)
            }
            database.insertOrThrow(SportType.TABLE, null, cv)
        }
    }

    @Test
    fun testUpgradeV1ToV3PreservesCustomSportsAndUpdatesWalk() {
        val database = checkNotNull(db)
        scaffoldV1Schema(database)

        // Insert custom user-defined sport types at v1
        val customSport1 = ContentValues().apply {
            put(SportType.C_ID, 6L)
            put(SportType.UI_NAME, "Rollerblading")
            put(SportType.BASE_SPORT_TYPE, BSportType.RUN.name)
            put(SportType.STRAVA_NAME, "InlineSkate")
        }
        database.insertOrThrow(SportType.TABLE, null, customSport1)

        val customSport2 = ContentValues().apply {
            put(SportType.C_ID, 7L)
            put(SportType.UI_NAME, "Kayaking")
            put(SportType.BASE_SPORT_TYPE, BSportType.UNKNOWN.name)
            put(SportType.STRAVA_NAME, "Kayaking")
        }
        database.insertOrThrow(SportType.TABLE, null, customSport2)

        // Verify pre-upgrade row count = 7
        assertEquals(7L, RealSqliteTestHelper.getRowCount(database, SportType.TABLE))

        // Execute real SQLite upgrade v1 -> v3
        helper.onUpgrade(database, 1, 3)
        database.version = 3

        // Assert table was NOT dropped and row count is still 7
        assertTrue("Table SportTypes must exist", RealSqliteTestHelper.hasTable(database, SportType.TABLE))
        assertEquals("All 7 sport types must be preserved", 7L, RealSqliteTestHelper.getRowCount(database, SportType.TABLE))

        // Assert is_placeholder column was added
        assertTrue("Column is_placeholder must exist after upgrade", RealSqliteTestHelper.hasColumn(database, SportType.TABLE, SportType.IS_PLACEHOLDER))
        val placeholderCol = RealSqliteTestHelper.getColumn(database, SportType.TABLE, SportType.IS_PLACEHOLDER)
        assertNotNull(placeholderCol)
        assertEquals("0", placeholderCol?.dfltValue)

        // Assert WALK (_id = 4) was updated to BSportType.RUN
        database.query(SportType.TABLE, null, "${SportType.C_ID} = ?", arrayOf("4"), null, null, null).use { cursor ->
            assertTrue(cursor.moveToFirst())
            val baseTypeIdx = cursor.getColumnIndexOrThrow(SportType.BASE_SPORT_TYPE)
            val placeholderIdx = cursor.getColumnIndexOrThrow(SportType.IS_PLACEHOLDER)
            assertEquals(BSportType.RUN.name, cursor.getString(baseTypeIdx))
            assertEquals(0, cursor.getInt(placeholderIdx))
        }

        // Assert custom sport 6 is preserved intact
        database.query(SportType.TABLE, null, "${SportType.C_ID} = ?", arrayOf("6"), null, null, null).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Rollerblading", cursor.getString(cursor.getColumnIndexOrThrow(SportType.UI_NAME)))
            assertEquals(BSportType.RUN.name, cursor.getString(cursor.getColumnIndexOrThrow(SportType.BASE_SPORT_TYPE)))
            assertEquals("InlineSkate", cursor.getString(cursor.getColumnIndexOrThrow(SportType.STRAVA_NAME)))
            assertEquals(0, cursor.getInt(cursor.getColumnIndexOrThrow(SportType.IS_PLACEHOLDER)))
        }

        // Assert custom sport 7 is preserved intact
        database.query(SportType.TABLE, null, "${SportType.C_ID} = ?", arrayOf("7"), null, null, null).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Kayaking", cursor.getString(cursor.getColumnIndexOrThrow(SportType.UI_NAME)))
            assertEquals(BSportType.UNKNOWN.name, cursor.getString(cursor.getColumnIndexOrThrow(SportType.BASE_SPORT_TYPE)))
            assertEquals("Kayaking", cursor.getString(cursor.getColumnIndexOrThrow(SportType.STRAVA_NAME)))
            assertEquals(0, cursor.getInt(cursor.getColumnIndexOrThrow(SportType.IS_PLACEHOLDER)))
        }
    }

    @Test
    fun testUpgradeV2ToV3AddsPlaceholderColumnWithoutModifyingData() {
        val database = checkNotNull(db)
        scaffoldV1Schema(database)
        // Perform v1 -> v2 upgrade (WALK updated)
        helper.onUpgrade(database, 1, 2)
        database.version = 2

        // Insert custom sport at v2
        val customSport = ContentValues().apply {
            put(SportType.C_ID, 6L)
            put(SportType.UI_NAME, "Rowing")
            put(SportType.BASE_SPORT_TYPE, BSportType.UNKNOWN.name)
        }
        database.insertOrThrow(SportType.TABLE, null, customSport)

        // Upgrade v2 -> v3
        helper.onUpgrade(database, 2, 3)
        database.version = 3

        assertTrue(RealSqliteTestHelper.hasColumn(database, SportType.TABLE, SportType.IS_PLACEHOLDER))
        assertEquals(6L, RealSqliteTestHelper.getRowCount(database, SportType.TABLE))

        // Query custom sport
        database.query(SportType.TABLE, null, "${SportType.C_ID} = ?", arrayOf("6"), null, null, null).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Rowing", cursor.getString(cursor.getColumnIndexOrThrow(SportType.UI_NAME)))
            assertEquals(0, cursor.getInt(cursor.getColumnIndexOrThrow(SportType.IS_PLACEHOLDER)))
        }
    }

    @Test
    fun testDowngradeThrowsSQLiteExceptionAndDoesNotDropTable() {
        val database = checkNotNull(db)
        scaffoldV1Schema(database)
        helper.onUpgrade(database, 1, 3)
        database.version = 3

        try {
            helper.onDowngrade(database, 3, 2)
            fail("Expected SQLiteException on downgrade")
        } catch (e: SQLiteException) {
            assertTrue("Exception should explain downgrade not supported", e.message?.contains("Downgrade from 3 to 2 is not supported") == true)
        }

        // Table and data must remain intact
        assertTrue(RealSqliteTestHelper.hasTable(database, SportType.TABLE))
        assertEquals(5L, RealSqliteTestHelper.getRowCount(database, SportType.TABLE))
    }
}
