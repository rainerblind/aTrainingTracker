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

package com.atrainingtracker.trainingtracker.database

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.testutil.RealSqliteTestHelper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * SQLite migration and CRUD tests for WheelCircumferenceHistory (REQ-SEN-030.3, TST-SEN-030.3, ATT-7753).
 */
@RunWith(RobolectricTestRunner::class)
class WheelCircumferenceHistoryDbTest {

    private lateinit var context: Context
    private val dbName = "test_wheel_circumference_history.db"
    private var db: SQLiteDatabase? = null
    private lateinit var helper: EquipmentDbHelper

    private val createV4EquipmentTable = """
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

    private val createV4LinksTable = """
        CREATE TABLE ${EquipmentDbHelper.LINKS} (
            ${EquipmentDbHelper.EQUIPMENT_ID} int,
            ${EquipmentDbHelper.ANT_DEVICE_ID} int,
            ${EquipmentDbHelper.WHEEL_POSITION} text default 'UNSPECIFIED'
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
    fun testWheelCircumferenceHistory_sqliteUpgradeV4ToV5AndQuery() {
        val database = checkNotNull(db)
        database.execSQL(createV4EquipmentTable)
        database.execSQL(createV4LinksTable)
        database.version = 4

        // Insert bike and link in v4 schema
        val bike = ContentValues().apply {
            put(EquipmentDbHelper.NAME, "Pinarello Dogma")
            put(EquipmentDbHelper.SPORT_TYPE, "BIKE")
            put(EquipmentDbHelper.FRAME_TYPE, EquipmentDbHelper.ROAD)
        }
        val bikeId = database.insertOrThrow(EquipmentDbHelper.EQUIPMENT, null, bike)
        assertEquals(1L, bikeId)

        // Upgrade v4 -> v5
        helper.onUpgrade(database, 4, 5)
        database.version = 5

        // Verify WheelCircumferenceHistory table exists
        assertTrue(
            "WheelCircumferenceHistory table must exist after v5 upgrade",
            RealSqliteTestHelper.hasTable(database, EquipmentDbHelper.TABLE_WHEEL_CIRCUMFERENCE_HISTORY)
        )

        // Insert historical calibration records out of order
        val id1 = helper.addCircumferenceHistory(bikeId, 2000L, WheelPosition.FRONT, 2103, WheelCircumferenceRecord.SOURCE_GNSS_AUTO)
        val id2 = helper.addCircumferenceHistory(bikeId, 1000L, WheelPosition.FRONT, 2105, WheelCircumferenceRecord.SOURCE_MANUAL_RESET)
        val id3 = helper.addCircumferenceHistory(bikeId, 1000L, WheelPosition.REAR, 2102, WheelCircumferenceRecord.SOURCE_MANUAL_RESET)
        val id4 = helper.addCircumferenceHistory(bikeId, 2000L, WheelPosition.REAR, 2098, WheelCircumferenceRecord.SOURCE_GNSS_AUTO)

        assertTrue(id1 > 0)
        assertTrue(id2 > 0)
        assertTrue(id3 > 0)
        assertTrue(id4 > 0)

        // Query history for bikeId (should return sorted chronologically by timestamp ASC)
        val history = helper.getCircumferenceHistory(bikeId)
        assertEquals(4, history.size)
        assertEquals(1000L, history[0].timestampMs)
        assertEquals(1000L, history[1].timestampMs)
        assertEquals(2000L, history[2].timestampMs)
        assertEquals(2000L, history[3].timestampMs)

        // Clear history for bikeId
        val deleted = helper.clearCircumferenceHistory(bikeId)
        assertEquals(4, deleted)
        assertTrue(helper.getCircumferenceHistory(bikeId).isEmpty())
    }

    @Test
    fun testGetWheelPositionForDevice_returnsPersistedPosition() {
        val eqId = helper.addEquipment("Trek Emonda", EquipmentDbHelper.ROAD, emptyList())
        helper.addDeviceLink(eqId, 501L, WheelPosition.FRONT)
        helper.addDeviceLink(eqId, 502L, WheelPosition.REAR)

        assertEquals(WheelPosition.FRONT, helper.getWheelPositionForDevice(501L))
        assertEquals(WheelPosition.REAR, helper.getWheelPositionForDevice(502L))
        assertEquals(WheelPosition.UNSPECIFIED, helper.getWheelPositionForDevice(999L))
    }

    @Test
    fun testDeleteEquipment_cascadesCircumferenceHistoryCleanup() {
        val database = checkNotNull(db)
        database.execSQL(createV4EquipmentTable)
        database.execSQL(createV4LinksTable)
        helper.onUpgrade(database, 4, 5)

        val eqId = helper.addEquipment("Canyon Aeroad", EquipmentDbHelper.ROAD, emptyList())
        val id1 = helper.addCircumferenceHistory(eqId, 1000L, WheelPosition.FRONT, 2105, WheelCircumferenceRecord.SOURCE_GNSS_AUTO)
        val id2 = helper.addCircumferenceHistory(eqId, 2000L, WheelPosition.REAR, 2095, WheelCircumferenceRecord.SOURCE_GNSS_AUTO)
        assertTrue(id1 > 0)
        assertTrue(id2 > 0)
        assertEquals(2, helper.getCircumferenceHistory(eqId).size)

        // Delete equipment must cascade to WheelCircumferenceHistory (Finding 5)
        helper.deleteEquipment(eqId)
        assertTrue("Circumference history must be wiped when equipment is deleted", helper.getCircumferenceHistory(eqId).isEmpty())
    }

    @Test
    fun testGetWheelPositionForDevice_scopedByEquipmentIdAndActiveEquipment() {
        val database = checkNotNull(db)
        database.execSQL(createV4EquipmentTable)
        database.execSQL(createV4LinksTable)
        helper.onUpgrade(database, 4, 5)

        val bike1 = helper.addEquipment("Bike One", EquipmentDbHelper.ROAD, emptyList())
        val bike2 = helper.addEquipment("Bike Two", EquipmentDbHelper.CROSS, emptyList())

        // Same sensor linked with different positions on different bikes
        helper.addDeviceLink(bike1, 601L, WheelPosition.FRONT)
        helper.addDeviceLink(bike2, 601L, WheelPosition.REAR)

        // Scoped lookup (Finding 2)
        assertEquals(WheelPosition.FRONT, helper.getWheelPositionForDevice(bike1, 601L))
        assertEquals(WheelPosition.REAR, helper.getWheelPositionForDevice(bike2, 601L))

        // Retire bike 1, unscoped lookup returns the position from active bike 2
        helper.setEquipmentRetired(bike1, true)
        assertEquals(WheelPosition.REAR, helper.getWheelPositionForDevice(601L))
    }
}
