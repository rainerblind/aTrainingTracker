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

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.testutil.RealSqliteTestHelper
import com.atrainingtracker.trainingtracker.migration.DatabaseSchemaRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Real SQLite deduplication and cascade migration verification suite
 * for EquipmentDbHelper v2 -> v3 (REQ-SET-098.4, TST-SET-098.4, Rule 62).
 */
@RunWith(RobolectricTestRunner::class)
class EquipmentDeduplicationMigrationTest {

    private lateinit var context: Context
    private val equipDbName = "test_equipment_dedup.db"
    private val summariesDbName = "test_summaries_dedup.db"
    private val sportLinksDbName = "test_sportlinks_dedup.db"

    private var equipDb: SQLiteDatabase? = null
    private var summariesDb: SQLiteDatabase? = null
    private var sportLinksDb: SQLiteDatabase? = null

    private lateinit var helper: EquipmentDbHelper

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(equipDbName)
        context.deleteDatabase(summariesDbName)
        context.deleteDatabase(sportLinksDbName)

        equipDb = context.openOrCreateDatabase(equipDbName, Context.MODE_PRIVATE, null)
        summariesDb = context.openOrCreateDatabase(summariesDbName, Context.MODE_PRIVATE, null)
        sportLinksDb = context.openOrCreateDatabase(sportLinksDbName, Context.MODE_PRIVATE, null)

        helper = object : EquipmentDbHelper(context) {
            override fun getReadableDatabase(): SQLiteDatabase = checkNotNull(equipDb)
            override fun getWritableDatabase(): SQLiteDatabase = checkNotNull(equipDb)
        }

        scaffoldV2Databases()
    }

    @After
    fun tearDown() {
        RealSqliteTestHelper.cleanupDatabase(equipDb, equipDbName)
        RealSqliteTestHelper.cleanupDatabase(summariesDb, summariesDbName)
        RealSqliteTestHelper.cleanupDatabase(sportLinksDb, sportLinksDbName)
    }

    private fun scaffoldV2Databases() {
        val eDb = checkNotNull(equipDb)
        eDb.execSQL(
            """
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
        )
        eDb.execSQL(
            """
            CREATE TABLE ${EquipmentDbHelper.LINKS} (
                ${EquipmentDbHelper.EQUIPMENT_ID} int,
                ${EquipmentDbHelper.ANT_DEVICE_ID} int
            )
            """.trimIndent()
        )
        eDb.version = 2

        val sDb = checkNotNull(summariesDb)
        sDb.execSQL(
            """
            CREATE TABLE ${WorkoutSummariesDatabaseManager.WorkoutSummaries.TABLE} (
                ${WorkoutSummariesDatabaseManager.WorkoutSummaries.C_ID} INTEGER PRIMARY KEY AUTOINCREMENT,
                ${WorkoutSummariesDatabaseManager.WorkoutSummaries.EQUIPMENT_ID} int
            )
            """.trimIndent()
        )

        val spDb = checkNotNull(sportLinksDb)
        spDb.execSQL(
            """
            CREATE TABLE SportTypeEquipmentLink (
                sport_type_id INTEGER,
                equipment_id INTEGER,
                PRIMARY KEY (sport_type_id, equipment_id)
            )
            """.trimIndent()
        )
    }

    @Test
    fun testDatabaseSchemaRegistry_referencesEquipmentDbVersionDirectly() {
        assertEquals(3, EquipmentDbHelper.DB_VERSION)
        assertEquals(
            EquipmentDbHelper.DB_VERSION,
            DatabaseSchemaRegistry.getMaxSupportedVersion("Equipment.db")
        )
        assertTrue(DatabaseSchemaRegistry.isVersionSupported("Equipment.db", 3))
    }

    @Test
    fun testReconcileDuplicates_mergesTriplicateEnduranceBikesAndCascadesLinks() {
        val eDb = checkNotNull(equipDb)
        val wDb = checkNotNull(summariesDb)
        val spDb = checkNotNull(sportLinksDb)

        eDb.execSQL("INSERT INTO ${EquipmentDbHelper.EQUIPMENT} (${EquipmentDbHelper.C_ID}, ${EquipmentDbHelper.NAME}, ${EquipmentDbHelper.SPORT_TYPE}, ${EquipmentDbHelper.FRAME_TYPE}, ${EquipmentDbHelper.STRAVA_NAME}, ${EquipmentDbHelper.STRAVA_ID}, ${EquipmentDbHelper.RETIRED}) VALUES (13, 'Endurance', 'BIKE', 3, null, null, 0)")
        eDb.execSQL("INSERT INTO ${EquipmentDbHelper.EQUIPMENT} (${EquipmentDbHelper.C_ID}, ${EquipmentDbHelper.NAME}, ${EquipmentDbHelper.SPORT_TYPE}, ${EquipmentDbHelper.FRAME_TYPE}, ${EquipmentDbHelper.STRAVA_NAME}, ${EquipmentDbHelper.STRAVA_ID}, ${EquipmentDbHelper.RETIRED}) VALUES (20, 'endurance ', 'BIKE', 3, 'endurance ', 'b16080279', 0)")
        eDb.execSQL("INSERT INTO ${EquipmentDbHelper.EQUIPMENT} (${EquipmentDbHelper.C_ID}, ${EquipmentDbHelper.NAME}, ${EquipmentDbHelper.SPORT_TYPE}, ${EquipmentDbHelper.FRAME_TYPE}, ${EquipmentDbHelper.STRAVA_NAME}, ${EquipmentDbHelper.STRAVA_ID}, ${EquipmentDbHelper.RETIRED}) VALUES (22, 'endurance', 'BIKE', 3, 'endurance', 'endurance', 0)")

        eDb.execSQL("INSERT INTO ${EquipmentDbHelper.LINKS} (${EquipmentDbHelper.EQUIPMENT_ID}, ${EquipmentDbHelper.ANT_DEVICE_ID}) VALUES (13, 100)")
        eDb.execSQL("INSERT INTO ${EquipmentDbHelper.LINKS} (${EquipmentDbHelper.EQUIPMENT_ID}, ${EquipmentDbHelper.ANT_DEVICE_ID}) VALUES (20, 200)")
        eDb.execSQL("INSERT INTO ${EquipmentDbHelper.LINKS} (${EquipmentDbHelper.EQUIPMENT_ID}, ${EquipmentDbHelper.ANT_DEVICE_ID}) VALUES (22, 100)")

        spDb.execSQL("INSERT INTO SportTypeEquipmentLink (sport_type_id, equipment_id) VALUES (1, 13)")
        spDb.execSQL("INSERT INTO SportTypeEquipmentLink (sport_type_id, equipment_id) VALUES (2, 20)")
        spDb.execSQL("INSERT INTO SportTypeEquipmentLink (sport_type_id, equipment_id) VALUES (1, 22)")

        wDb.execSQL("INSERT INTO ${WorkoutSummariesDatabaseManager.WorkoutSummaries.TABLE} (${WorkoutSummariesDatabaseManager.WorkoutSummaries.EQUIPMENT_ID}) VALUES (13)")
        wDb.execSQL("INSERT INTO ${WorkoutSummariesDatabaseManager.WorkoutSummaries.TABLE} (${WorkoutSummariesDatabaseManager.WorkoutSummaries.EQUIPMENT_ID}) VALUES (20)")
        wDb.execSQL("INSERT INTO ${WorkoutSummariesDatabaseManager.WorkoutSummaries.TABLE} (${WorkoutSummariesDatabaseManager.WorkoutSummaries.EQUIPMENT_ID}) VALUES (22)")

        val pruned = EquipmentReconciliationHelper.reconcileDuplicates(
            context,
            equipmentDb = eDb,
            summariesDb = wDb,
            sportTypeDb = spDb
        )
        assertEquals(2, pruned)

        eDb.query(EquipmentDbHelper.EQUIPMENT, null, null, null, null, null, null).use { cursor ->
            assertEquals(1, cursor.count)
            assertTrue(cursor.moveToFirst())
            assertEquals(13L, cursor.getLong(cursor.getColumnIndexOrThrow(EquipmentDbHelper.C_ID)))
            assertEquals("Endurance", cursor.getString(cursor.getColumnIndexOrThrow(EquipmentDbHelper.NAME)))
            assertEquals("b16080279", cursor.getString(cursor.getColumnIndexOrThrow(EquipmentDbHelper.STRAVA_ID)))
            assertEquals(0, cursor.getInt(cursor.getColumnIndexOrThrow(EquipmentDbHelper.RETIRED)))
        }

        eDb.query(EquipmentDbHelper.LINKS, null, null, null, null, null, "${EquipmentDbHelper.ANT_DEVICE_ID} ASC").use { cursor ->
            assertEquals(2, cursor.count)
            cursor.moveToNext()
            assertEquals(13L, cursor.getLong(cursor.getColumnIndexOrThrow(EquipmentDbHelper.EQUIPMENT_ID)))
            assertEquals(100L, cursor.getLong(cursor.getColumnIndexOrThrow(EquipmentDbHelper.ANT_DEVICE_ID)))
            cursor.moveToNext()
            assertEquals(13L, cursor.getLong(cursor.getColumnIndexOrThrow(EquipmentDbHelper.EQUIPMENT_ID)))
            assertEquals(200L, cursor.getLong(cursor.getColumnIndexOrThrow(EquipmentDbHelper.ANT_DEVICE_ID)))
        }

        spDb.query("SportTypeEquipmentLink", null, null, null, null, null, "sport_type_id ASC").use { cursor ->
            assertEquals(2, cursor.count)
            cursor.moveToNext()
            assertEquals(1L, cursor.getLong(0))
            assertEquals(13L, cursor.getLong(1))
            cursor.moveToNext()
            assertEquals(2L, cursor.getLong(0))
            assertEquals(13L, cursor.getLong(1))
        }

        wDb.query(WorkoutSummariesDatabaseManager.WorkoutSummaries.TABLE, arrayOf(WorkoutSummariesDatabaseManager.WorkoutSummaries.EQUIPMENT_ID), null, null, null, null, null).use { cursor ->
            assertEquals(3, cursor.count)
            while (cursor.moveToNext()) {
                assertEquals(13L, cursor.getLong(0))
            }
        }
    }

    @Test
    fun testReconcileDuplicates_retiredStravaGearPreservedWhenDuplicateRetired() {
        val eDb = checkNotNull(equipDb)
        eDb.execSQL("INSERT INTO ${EquipmentDbHelper.EQUIPMENT} (${EquipmentDbHelper.C_ID}, ${EquipmentDbHelper.NAME}, ${EquipmentDbHelper.SPORT_TYPE}, ${EquipmentDbHelper.FRAME_TYPE}, ${EquipmentDbHelper.STRAVA_NAME}, ${EquipmentDbHelper.STRAVA_ID}, ${EquipmentDbHelper.RETIRED}) VALUES (21, '2Danger', 'BIKE', 1, '2Danger', '2Danger', 0)")
        eDb.execSQL("INSERT INTO ${EquipmentDbHelper.EQUIPMENT} (${EquipmentDbHelper.C_ID}, ${EquipmentDbHelper.NAME}, ${EquipmentDbHelper.SPORT_TYPE}, ${EquipmentDbHelper.FRAME_TYPE}, ${EquipmentDbHelper.STRAVA_NAME}, ${EquipmentDbHelper.STRAVA_ID}, ${EquipmentDbHelper.RETIRED}) VALUES (30, '2Danger', 'BIKE', 1, '2Danger', 'b137835', 1)")

        val pruned = EquipmentReconciliationHelper.reconcileDuplicates(
            context,
            equipmentDb = eDb,
            summariesDb = checkNotNull(summariesDb),
            sportTypeDb = checkNotNull(sportLinksDb)
        )
        assertEquals(1, pruned)

        eDb.query(EquipmentDbHelper.EQUIPMENT, null, null, null, null, null, null).use { cursor ->
            assertEquals(1, cursor.count)
            assertTrue(cursor.moveToFirst())
            assertEquals(21L, cursor.getLong(cursor.getColumnIndexOrThrow(EquipmentDbHelper.C_ID)))
            assertEquals("2Danger", cursor.getString(cursor.getColumnIndexOrThrow(EquipmentDbHelper.NAME)))
            assertEquals("b137835", cursor.getString(cursor.getColumnIndexOrThrow(EquipmentDbHelper.STRAVA_ID)))
            assertEquals(1, cursor.getInt(cursor.getColumnIndexOrThrow(EquipmentDbHelper.RETIRED)))
        }
    }
}
