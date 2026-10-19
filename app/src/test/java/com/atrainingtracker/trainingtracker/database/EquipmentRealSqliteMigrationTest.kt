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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Real SQLite migration verification suite for EquipmentDbHelper (TST-MIG-042.3, REQ-MIG-045).
 * Tests authentic DDL/DML execution in SQLite backed by Robolectric, verifying Retired column addition and equipment preservation.
 */
@RunWith(RobolectricTestRunner::class)
class EquipmentRealSqliteMigrationTest {

    private lateinit var context: Context
    private val dbName = "test_equipment_migration.db"
    private var db: SQLiteDatabase? = null
    private lateinit var helper: EquipmentDbHelper

    private val createV1EquipmentTable = """
        create table ${EquipmentDbHelper.EQUIPMENT} (
            ${EquipmentDbHelper.C_ID} INTEGER PRIMARY KEY AUTOINCREMENT,
            ${EquipmentDbHelper.NAME} text,
            ${EquipmentDbHelper.SPORT_TYPE} text,
            ${EquipmentDbHelper.FRAME_TYPE} int,
            ${EquipmentDbHelper.STRAVA_NAME} text,
            ${EquipmentDbHelper.STRAVA_ID} text
        )
    """.trimIndent()

    private val createLinksTable = """
        create table ${EquipmentDbHelper.LINKS} (
            ${EquipmentDbHelper.EQUIPMENT_ID} int,
            ${EquipmentDbHelper.ANT_DEVICE_ID} int
        )
    """.trimIndent()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(dbName)
        db = context.openOrCreateDatabase(dbName, Context.MODE_PRIVATE, null)
        helper = EquipmentDbHelper(context)
    }

    @After
    fun tearDown() {
        RealSqliteTestHelper.cleanupDatabase(db, dbName)
    }

    private fun scaffoldV1Schema(database: SQLiteDatabase) {
        database.execSQL(createV1EquipmentTable)
        database.execSQL(createLinksTable)
        database.version = 1
    }

    @Test
    fun testUpgradeV1ToV2AddsRetiredColumnAndPreservesEquipment() {
        val database = checkNotNull(db)
        scaffoldV1Schema(database)

        // Insert historical equipment items at v1
        val bike = ContentValues().apply {
            put(EquipmentDbHelper.NAME, "Canyon Aeroad")
            put(EquipmentDbHelper.SPORT_TYPE, "BIKE")
            put(EquipmentDbHelper.FRAME_TYPE, EquipmentDbHelper.ROAD)
            put(EquipmentDbHelper.STRAVA_ID, "b123456")
            put(EquipmentDbHelper.STRAVA_NAME, "Canyon Aeroad CF SLX")
        }
        val bikeId = database.insertOrThrow(EquipmentDbHelper.EQUIPMENT, null, bike)
        assertEquals(1L, bikeId)

        val shoe = ContentValues().apply {
            put(EquipmentDbHelper.NAME, "Nike Vaporfly")
            put(EquipmentDbHelper.SPORT_TYPE, "RUN")
            put(EquipmentDbHelper.FRAME_TYPE, 0)
        }
        val shoeId = database.insertOrThrow(EquipmentDbHelper.EQUIPMENT, null, shoe)
        assertEquals(2L, shoeId)

        // Insert link
        val link = ContentValues().apply {
            put(EquipmentDbHelper.EQUIPMENT_ID, 1)
            put(EquipmentDbHelper.ANT_DEVICE_ID, 98765)
        }
        database.insertOrThrow(EquipmentDbHelper.LINKS, null, link)

        // Execute real SQLite upgrade v1 -> v2
        helper.onUpgrade(database, 1, 2)
        database.version = 2

        // Verify Retired column exists via SQLite PRAGMA table_info
        assertTrue("Column Retired must exist after v2 upgrade", RealSqliteTestHelper.hasColumn(database, EquipmentDbHelper.EQUIPMENT, EquipmentDbHelper.RETIRED))
        val retiredCol = RealSqliteTestHelper.getColumn(database, EquipmentDbHelper.EQUIPMENT, EquipmentDbHelper.RETIRED)
        assertNotNull(retiredCol)
        assertEquals("0", retiredCol?.dfltValue)

        // Verify baseline bike preserved with Retired = 0
        database.query(EquipmentDbHelper.EQUIPMENT, null, "${EquipmentDbHelper.C_ID} = ?", arrayOf("1"), null, null, null).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Canyon Aeroad", cursor.getString(cursor.getColumnIndexOrThrow(EquipmentDbHelper.NAME)))
            assertEquals("BIKE", cursor.getString(cursor.getColumnIndexOrThrow(EquipmentDbHelper.SPORT_TYPE)))
            assertEquals(EquipmentDbHelper.ROAD, cursor.getInt(cursor.getColumnIndexOrThrow(EquipmentDbHelper.FRAME_TYPE)))
            assertEquals("b123456", cursor.getString(cursor.getColumnIndexOrThrow(EquipmentDbHelper.STRAVA_ID)))
            assertEquals(0, cursor.getInt(cursor.getColumnIndexOrThrow(EquipmentDbHelper.RETIRED)))
        }

        // Verify baseline shoe preserved with Retired = 0
        database.query(EquipmentDbHelper.EQUIPMENT, null, "${EquipmentDbHelper.C_ID} = ?", arrayOf("2"), null, null, null).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Nike Vaporfly", cursor.getString(cursor.getColumnIndexOrThrow(EquipmentDbHelper.NAME)))
            assertEquals(0, cursor.getInt(cursor.getColumnIndexOrThrow(EquipmentDbHelper.RETIRED)))
        }

        // Verify Links table and row preserved
        assertEquals(1L, RealSqliteTestHelper.getRowCount(database, EquipmentDbHelper.LINKS))

        // Insert new equipment at v2 with Retired = 1
        val retiredBike = ContentValues().apply {
            put(EquipmentDbHelper.NAME, "Old Trek MTB")
            put(EquipmentDbHelper.SPORT_TYPE, "BIKE")
            put(EquipmentDbHelper.FRAME_TYPE, EquipmentDbHelper.MTB)
            put(EquipmentDbHelper.RETIRED, 1)
        }
        val retiredId = database.insertOrThrow(EquipmentDbHelper.EQUIPMENT, null, retiredBike)
        assertEquals(3L, retiredId)

        database.query(EquipmentDbHelper.EQUIPMENT, null, "${EquipmentDbHelper.C_ID} = ?", arrayOf("3"), null, null, null).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Old Trek MTB", cursor.getString(cursor.getColumnIndexOrThrow(EquipmentDbHelper.NAME)))
            assertEquals(1, cursor.getInt(cursor.getColumnIndexOrThrow(EquipmentDbHelper.RETIRED)))
        }
    }
}
