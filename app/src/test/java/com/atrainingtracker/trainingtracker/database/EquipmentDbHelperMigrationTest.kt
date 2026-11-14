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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Migration and CRUD tests for EquipmentDbHelper database version 4 (wheel_position column) (REQ-SEN-029, TST-SEN-029.1).
 */
@RunWith(RobolectricTestRunner::class)
class EquipmentDbHelperMigrationTest {

    private lateinit var context: Context
    private val dbName = "test_equipment_wheel_migration.db"
    private var db: SQLiteDatabase? = null
    private lateinit var helper: EquipmentDbHelper

    private val createV3EquipmentTable = """
        CREATE TABLE ${EquipmentDbHelper.EQUIPMENT} (
            ${EquipmentDbHelper.C_ID} INTEGER PRIMARY KEY AUTOINCREMENT,
            ${EquipmentDbHelper.NAME} text,
            ${EquipmentDbHelper.SPORT_TYPE} text,
            ${EquipmentDbHelper.FRAME_TYPE} int,
            ${EquipmentDbHelper.STRAVA_NAME} text,
            ${EquipmentDbHelper.STRAVA_ID} text,
            ${EquipmentDbHelper.RETIRED} int default 0
        )
    """.trimIndent()

    private val createV3LinksTable = """
        CREATE TABLE ${EquipmentDbHelper.LINKS} (
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

    @Test
    fun testUpgradeV3ToV4AddsWheelPositionColumnAndPreservesExistingLinks() {
        val database = checkNotNull(db)
        database.execSQL(createV3EquipmentTable)
        database.execSQL(createV3LinksTable)
        database.version = 3

        // Insert equipment and pre-existing links without wheel_position
        val bike = ContentValues().apply {
            put(EquipmentDbHelper.NAME, "Road Aero Bike")
            put(EquipmentDbHelper.SPORT_TYPE, "BIKE")
            put(EquipmentDbHelper.FRAME_TYPE, EquipmentDbHelper.ROAD)
        }
        val bikeId = database.insertOrThrow(EquipmentDbHelper.EQUIPMENT, null, bike)
        assertEquals(1L, bikeId)

        val linkV3 = ContentValues().apply {
            put(EquipmentDbHelper.EQUIPMENT_ID, 1)
            put(EquipmentDbHelper.ANT_DEVICE_ID, 1001)
        }
        database.insertOrThrow(EquipmentDbHelper.LINKS, null, linkV3)

        // Upgrade v3 -> v4
        helper.onUpgrade(database, 3, 4)
        database.version = 4

        // Verify wheel_position column exists in links table
        assertTrue("Column wheel_position must exist in links table after v4 upgrade",
            RealSqliteTestHelper.hasColumn(database, EquipmentDbHelper.LINKS, EquipmentDbHelper.WHEEL_POSITION))

        // Existing link should have null or UNSPECIFIED wheel_position
        database.query(EquipmentDbHelper.LINKS, null, "${EquipmentDbHelper.EQUIPMENT_ID} = ?", arrayOf("1"), null, null, null).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1001L, cursor.getLong(cursor.getColumnIndexOrThrow(EquipmentDbHelper.ANT_DEVICE_ID)))
            val posStr = cursor.getString(cursor.getColumnIndexOrThrow(EquipmentDbHelper.WHEEL_POSITION))
            val parsedPos = WheelPosition.fromString(posStr)
            assertEquals(WheelPosition.UNSPECIFIED, parsedPos)
        }
    }

    @Test
    fun testWheelPositionCrudOperations() {
        // Test direct helper methods on fresh v4 DB
        val eqId = helper.addEquipment("Gravel Canyon", EquipmentDbHelper.ROAD, emptyList())
        assertTrue(eqId > 0)

        // Add front wheel link
        helper.addDeviceLink(eqId, 201L, WheelPosition.FRONT)
        assertEquals(WheelPosition.FRONT, helper.getWheelPosition(eqId, 201L))

        // Add rear wheel link
        helper.addDeviceLink(eqId, 202L, WheelPosition.REAR)
        assertEquals(WheelPosition.REAR, helper.getWheelPosition(eqId, 202L))

        // Update front to unspecified
        helper.updateDeviceLinkWheelPosition(eqId, 201L, WheelPosition.UNSPECIFIED)
        assertEquals(WheelPosition.UNSPECIFIED, helper.getWheelPosition(eqId, 201L))

        // Map lookup
        val positions = helper.getDeviceWheelPositionsForEquipment(eqId)
        assertEquals(2, positions.size)
        assertEquals(WheelPosition.UNSPECIFIED, positions[201L])
        assertEquals(WheelPosition.REAR, positions[202L])

        // Unlink device
        helper.removeDeviceLink(eqId, 201L)
        assertNull(helper.getWheelPosition(eqId, 201L))
    }
}
