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
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.testutil.RealSqliteTestHelper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Unit tests verifying case-insensitive, whitespace-trimmed equipment lookups
 * and local name preservation during Strava updates (REQ-SET-098.1, REQ-SET-098.2, TST-SET-098.1, TST-SET-098.3).
 */
@RunWith(RobolectricTestRunner::class)
class EquipmentDbHelperNormalizationTest {

    private lateinit var context: Context
    private val dbName = "test_equipment_norm.db"
    private var db: SQLiteDatabase? = null
    private lateinit var helper: EquipmentDbHelper

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(dbName)
        db = context.openOrCreateDatabase(dbName, Context.MODE_PRIVATE, null)
        helper = object : EquipmentDbHelper(context) {
            override fun getReadableDatabase(): SQLiteDatabase = checkNotNull(db)
            override fun getWritableDatabase(): SQLiteDatabase = checkNotNull(db)
        }
        helper.onCreate(checkNotNull(db))
    }

    @After
    fun tearDown() {
        RealSqliteTestHelper.cleanupDatabase(db, dbName)
    }

    @Test
    fun testGetEquipmentId_caseInsensitiveAndTrimmed_matchesExistingEquipment() {
        val cv = ContentValues().apply {
            put(EquipmentDbHelper.NAME, "Endurance")
            put(EquipmentDbHelper.SPORT_TYPE, "BIKE")
            put(EquipmentDbHelper.FRAME_TYPE, EquipmentDbHelper.ROAD)
        }
        val id = checkNotNull(db).insertOrThrow(EquipmentDbHelper.EQUIPMENT, null, cv)
        assertTrue(id > 0)

        assertEquals(id, helper.getEquipmentId("Endurance"))
        assertEquals(id, helper.getEquipmentId("endurance"))
        assertEquals(id, helper.getEquipmentId("endurance "))
        assertEquals(id, helper.getEquipmentId("  ENDURANCE  "))
    }

    @Test
    fun testGetEquipmentId_matchesStravaNameWhenDifferentFromLocalName() {
        val cv = ContentValues().apply {
            put(EquipmentDbHelper.NAME, "My Commuter")
            put(EquipmentDbHelper.STRAVA_NAME, "Specialized Sirrus")
            put(EquipmentDbHelper.SPORT_TYPE, "BIKE")
            put(EquipmentDbHelper.FRAME_TYPE, EquipmentDbHelper.CROSS)
        }
        val id = checkNotNull(db).insertOrThrow(EquipmentDbHelper.EQUIPMENT, null, cv)

        assertEquals(id, helper.getEquipmentId("My Commuter"))
        assertEquals(id, helper.getEquipmentId("specialized sirrus"))
        assertEquals(id, helper.getEquipmentId(" Specialized Sirrus "))
    }

    @Test
    fun testAddOrUpdateStravaGear_preservesLocalNameWhenMatchingExistingGear() {
        val cv = ContentValues().apply {
            put(EquipmentDbHelper.NAME, "Endurance")
            put(EquipmentDbHelper.SPORT_TYPE, "BIKE")
            put(EquipmentDbHelper.FRAME_TYPE, EquipmentDbHelper.ROAD)
        }
        val localId = checkNotNull(db).insertOrThrow(EquipmentDbHelper.EQUIPMENT, null, cv)

        val resolvedId = helper.addOrUpdateStravaGear(
            "b16080279",
            "endurance ",
            EquipmentDbHelper.ROAD,
            BSportType.BIKE.name,
            false
        )

        assertEquals(localId, resolvedId)

        checkNotNull(db).query(EquipmentDbHelper.EQUIPMENT, null, "${EquipmentDbHelper.C_ID} = ?", arrayOf(localId.toString()), null, null, null).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Endurance", cursor.getString(cursor.getColumnIndexOrThrow(EquipmentDbHelper.NAME)))
            assertEquals("endurance", cursor.getString(cursor.getColumnIndexOrThrow(EquipmentDbHelper.STRAVA_NAME)))
            assertEquals("b16080279", cursor.getString(cursor.getColumnIndexOrThrow(EquipmentDbHelper.STRAVA_ID)))
        }
    }

    @Test
    fun testAddOrUpdateStravaGear_withNullStravaId_insertsUnlinkedGearWithoutPollution() {
        val newId = helper.addOrUpdateStravaGear(
            null,
            "Saucony Xodus",
            0,
            BSportType.RUN.name,
            false
        )
        assertTrue(newId > 0)

        checkNotNull(db).query(EquipmentDbHelper.EQUIPMENT, null, "${EquipmentDbHelper.C_ID} = ?", arrayOf(newId.toString()), null, null, null).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Saucony Xodus", cursor.getString(cursor.getColumnIndexOrThrow(EquipmentDbHelper.NAME)))
            assertNull(cursor.getString(cursor.getColumnIndexOrThrow(EquipmentDbHelper.STRAVA_ID)))
        }
    }
}
