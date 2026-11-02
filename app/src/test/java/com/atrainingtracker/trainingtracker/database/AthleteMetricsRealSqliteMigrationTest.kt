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
import com.atrainingtracker.trainingtracker.migration.DatabaseSchemaRegistry
import com.atrainingtracker.trainingtracker.settings.SettingsDataStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Real SQLite migration verification suite for [AthleteMetricsDbHelper] (TST-SET-082, REQ-SET-082).
 * Verifies v1 -> v2 migration, schema parity between onCreate and onUpgrade (Rule 64),
 * and DatabaseSchemaRegistry contract (Rule 62).
 */
@RunWith(RobolectricTestRunner::class)
class AthleteMetricsRealSqliteMigrationTest {

    private lateinit var context: Context
    private val dbMigrateName = "test_athlete_metrics_migrate.db"
    private val dbFreshName = "test_athlete_metrics_fresh.db"
    private var dbMigrate: SQLiteDatabase? = null
    private var dbFresh: SQLiteDatabase? = null
    private lateinit var helper: AthleteMetricsDbHelper

    private val createV1AthleteMetricsTable = """
        create table ${AthleteMetricsDbHelper.TABLE_HISTORY} (
            ${AthleteMetricsDbHelper.C_ID} INTEGER PRIMARY KEY AUTOINCREMENT,
            ${AthleteMetricsDbHelper.TIMESTAMP_MS} INTEGER NOT NULL,
            ${AthleteMetricsDbHelper.METRIC_TYPE} TEXT NOT NULL,
            ${AthleteMetricsDbHelper.VALUE} REAL NOT NULL CHECK(${AthleteMetricsDbHelper.VALUE} > 0)
        )
    """.trimIndent()

    private val createV1Index = """
        create index idx_athlete_metrics_type_time on ${AthleteMetricsDbHelper.TABLE_HISTORY} (
            ${AthleteMetricsDbHelper.METRIC_TYPE},
            ${AthleteMetricsDbHelper.TIMESTAMP_MS} DESC,
            ${AthleteMetricsDbHelper.C_ID} DESC
        )
    """.trimIndent()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(dbMigrateName)
        context.deleteDatabase(dbFreshName)
        dbMigrate = context.openOrCreateDatabase(dbMigrateName, Context.MODE_PRIVATE, null)
        dbFresh = context.openOrCreateDatabase(dbFreshName, Context.MODE_PRIVATE, null)
        helper = AthleteMetricsDbHelper(context)
    }

    @After
    fun tearDown() {
        RealSqliteTestHelper.cleanupDatabase(dbMigrate, dbMigrateName)
        RealSqliteTestHelper.cleanupDatabase(dbFresh, dbFreshName)
    }

    private fun scaffoldV1Schema(database: SQLiteDatabase) {
        database.execSQL(createV1AthleteMetricsTable)
        database.execSQL(createV1Index)
        database.version = 1
    }

    @Test
    fun testDatabaseSchemaRegistryMaxSupportedVersionMatchesDbHelperConstant() {
        val registryVersion = DatabaseSchemaRegistry.getMaxSupportedVersion("AthleteMetrics.db")
        assertEquals(AthleteMetricsDbHelper.DB_VERSION, registryVersion)
        assertEquals(2, AthleteMetricsDbHelper.DB_VERSION)
    }

    @Test
    fun testUpgradeV1ToV2CreatesZoneHistoryTableAndPreservesAthleteMetrics() {
        val database = checkNotNull(dbMigrate)
        scaffoldV1Schema(database)

        // Insert historical metric at v1
        val v1Metric = ContentValues().apply {
            put(AthleteMetricsDbHelper.TIMESTAMP_MS, 1_700_000_000_000L)
            put(AthleteMetricsDbHelper.METRIC_TYPE, "WEIGHT_KG")
            put(AthleteMetricsDbHelper.VALUE, 72.5)
        }
        val metricId = database.insertOrThrow(AthleteMetricsDbHelper.TABLE_HISTORY, null, v1Metric)
        assertEquals(1L, metricId)

        // Upgrade v1 -> v2
        helper.onUpgrade(database, 1, 2)
        database.version = 2

        // Verify zone_history table exists
        assertTrue(
            "zone_history table must exist after v2 upgrade",
            RealSqliteTestHelper.hasTable(database, AthleteMetricsDbHelper.TABLE_ZONE_HISTORY)
        )

        // Verify columns of zone_history
        assertTrue(RealSqliteTestHelper.hasColumn(database, AthleteMetricsDbHelper.TABLE_ZONE_HISTORY, AthleteMetricsDbHelper.C_ID))
        assertTrue(RealSqliteTestHelper.hasColumn(database, AthleteMetricsDbHelper.TABLE_ZONE_HISTORY, AthleteMetricsDbHelper.TIMESTAMP_MS))
        assertTrue(RealSqliteTestHelper.hasColumn(database, AthleteMetricsDbHelper.TABLE_ZONE_HISTORY, AthleteMetricsDbHelper.ZONE_TYPE))
        assertTrue(RealSqliteTestHelper.hasColumn(database, AthleteMetricsDbHelper.TABLE_ZONE_HISTORY, AthleteMetricsDbHelper.BOUNDARIES))

        // Verify baseline metric preserved
        database.query(
            AthleteMetricsDbHelper.TABLE_HISTORY,
            null,
            "${AthleteMetricsDbHelper.C_ID} = ?",
            arrayOf("1"),
            null,
            null,
            null
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1_700_000_000_000L, cursor.getLong(cursor.getColumnIndexOrThrow(AthleteMetricsDbHelper.TIMESTAMP_MS)))
            assertEquals("WEIGHT_KG", cursor.getString(cursor.getColumnIndexOrThrow(AthleteMetricsDbHelper.METRIC_TYPE)))
            assertEquals(72.5, cursor.getDouble(cursor.getColumnIndexOrThrow(AthleteMetricsDbHelper.VALUE)), 1e-6)
        }

        // Insert and query zone snapshot in v2
        val zoneValues = ContentValues().apply {
            put(AthleteMetricsDbHelper.TIMESTAMP_MS, 1_700_100_000_000L)
            put(AthleteMetricsDbHelper.ZONE_TYPE, "HR_RUN")
            put(AthleteMetricsDbHelper.BOUNDARIES, "130,145,160,175")
        }
        val zoneId = database.insertOrThrow(AthleteMetricsDbHelper.TABLE_ZONE_HISTORY, null, zoneValues)
        assertEquals(1L, zoneId)

        database.query(
            AthleteMetricsDbHelper.TABLE_ZONE_HISTORY,
            null,
            "${AthleteMetricsDbHelper.C_ID} = ?",
            arrayOf("1"),
            null,
            null,
            null
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            val entry = ZoneSnapshotEntry.fromCursor(cursor)
            assertEquals(1L, entry.id)
            assertEquals(1_700_100_000_000L, entry.timestampMs)
            assertEquals(SettingsDataStore.ZoneType.HR_RUN, entry.zoneType)
            assertEquals(listOf(130, 145, 160, 175), entry.boundaries)
            assertTrue(entry.isMonotonic())
        }
    }

    @Test
    fun testSchemaParityBetweenOnCreateAndOnUpgrade() {
        val migratedDb = checkNotNull(dbMigrate)
        scaffoldV1Schema(migratedDb)
        helper.onUpgrade(migratedDb, 1, 2)
        migratedDb.version = 2

        val freshDb = checkNotNull(dbFresh)
        helper.onCreate(freshDb)
        freshDb.version = 2

        // Verify both databases have athlete_metrics and zone_history tables
        assertTrue(RealSqliteTestHelper.hasTable(migratedDb, AthleteMetricsDbHelper.TABLE_HISTORY))
        assertTrue(RealSqliteTestHelper.hasTable(freshDb, AthleteMetricsDbHelper.TABLE_HISTORY))
        assertTrue(RealSqliteTestHelper.hasTable(migratedDb, AthleteMetricsDbHelper.TABLE_ZONE_HISTORY))
        assertTrue(RealSqliteTestHelper.hasTable(freshDb, AthleteMetricsDbHelper.TABLE_ZONE_HISTORY))

        // Compare column metadata for zone_history table
        val migratedCols = RealSqliteTestHelper.getTableColumns(migratedDb, AthleteMetricsDbHelper.TABLE_ZONE_HISTORY).associateBy { it.name }
        val freshCols = RealSqliteTestHelper.getTableColumns(freshDb, AthleteMetricsDbHelper.TABLE_ZONE_HISTORY).associateBy { it.name }

        assertEquals(freshCols.size, migratedCols.size)
        freshCols.forEach { (name, freshCol) ->
            val migratedCol = migratedCols[name]
            assertNotNull("Migrated schema missing column $name", migratedCol)
            assertEquals("Column type mismatch for $name", freshCol.type.lowercase(), migratedCol?.type?.lowercase())
            assertEquals("Column notnull mismatch for $name", freshCol.notNull, migratedCol?.notNull)
            assertEquals("Column pk mismatch for $name", freshCol.pk, migratedCol?.pk)
        }
    }
}
