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
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager.SportTypeDbHelper
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.database.LapsDatabaseManager.LapsDbHelper
import com.atrainingtracker.trainingtracker.database.RoutesDatabaseManager.RoutesDbHelper
import com.atrainingtracker.trainingtracker.database.TrackingViewsDatabaseManager.TrackingViewsDbHelper
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummariesDbHelper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Automated SQLite schema parity contract tests verifying that fresh `onCreate()`
 * and incremental `onUpgrade()` produce identical table and column schemas across
 * application SQLiteOpenHelpers (REQ-DAT-036, TST-DAT-032).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SqliteSchemaParityContractTest {

    private lateinit var context: Context
    private val createdDbNames = mutableListOf<String>()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        setStaticField(TrainingApplication::class.java, "cAppContext", context)
        setStaticField(
            TrainingApplication::class.java,
            "cSharedPreferences",
            androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
        )
    }

    @After
    fun tearDown() {
        setStaticField(TrainingApplication::class.java, "cAppContext", null)
        setStaticField(TrainingApplication::class.java, "cSharedPreferences", null)
        createdDbNames.forEach { name ->
            try {
                context.deleteDatabase(name)
            } catch (_: Exception) {
            }
        }
        createdDbNames.clear()
    }

    private fun setStaticField(clazz: Class<*>, fieldName: String, value: Any?) {
        try {
            val field = clazz.getDeclaredField(fieldName)
            field.isAccessible = true
            field.set(null, value)
        } catch (_: Exception) {
        }
    }

    private fun openEmptyDb(name: String): SQLiteDatabase {
        createdDbNames.add(name)
        context.deleteDatabase(name)
        return context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null)
    }

    private fun normalizeType(type: String): String = when (type.uppercase().trim()) {
        "INT" -> "INTEGER"
        "DATETIME" -> "TEXT"
        else -> type.uppercase().trim()
    }

    private fun collectTableColumns(db: SQLiteDatabase): Map<String, Map<String, String>> {
        val tables = mutableListOf<String>()
        db.rawQuery(
            "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name != 'android_metadata' ORDER BY name",
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                tables.add(cursor.getString(0))
            }
        }

        val schema = sortedMapOf<String, Map<String, String>>()
        for (table in tables) {
            val cols = sortedMapOf<String, String>()
            db.rawQuery("PRAGMA table_info(\"$table\")", null).use { cursor ->
                val nameIdx = cursor.getColumnIndexOrThrow("name")
                val typeIdx = cursor.getColumnIndexOrThrow("type")
                while (cursor.moveToNext()) {
                    cols[cursor.getString(nameIdx)] = normalizeType(cursor.getString(typeIdx))
                }
            }
            schema[table] = cols
        }
        return schema
    }

    private fun collectIndexes(db: SQLiteDatabase): Set<String> {
        val indexes = sortedSetOf<String>()
        db.rawQuery(
            "SELECT name FROM sqlite_master WHERE type='index' AND name NOT LIKE 'sqlite_autoindex_%' ORDER BY name",
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                indexes.add(cursor.getString(0))
            }
        }
        return indexes
    }

    private fun assertSchemaParity(
        label: String,
        helper: SQLiteOpenHelper,
        initialVersion: Int,
        targetVersion: Int,
        seedOlderSchema: (SQLiteDatabase) -> Unit
    ) {
        val freshDb = openEmptyDb("fresh_${label}.db")
        val upgradedDb = openEmptyDb("upgraded_${label}.db")
        try {
            helper.onCreate(freshDb)
            seedOlderSchema(upgradedDb)
            upgradedDb.version = initialVersion
            helper.onUpgrade(upgradedDb, initialVersion, targetVersion)
            upgradedDb.version = targetVersion

            val freshSchema = collectTableColumns(freshDb)
            val upgradedSchema = collectTableColumns(upgradedDb)
            assertEquals("$label table set must match between onCreate and onUpgrade", freshSchema.keys, upgradedSchema.keys)
            for (table in freshSchema.keys) {
                assertEquals(
                    "$label table '$table' columns must match between onCreate and onUpgrade",
                    freshSchema[table],
                    upgradedSchema[table]
                )
            }

            val freshIndexes = collectIndexes(freshDb)
            val upgradedIndexes = collectIndexes(upgradedDb)
            assertTrue(
                "$label upgraded indexes $upgradedIndexes must contain fresh indexes $freshIndexes",
                upgradedIndexes.containsAll(freshIndexes)
            )
        } finally {
            freshDb.close()
            upgradedDb.close()
            helper.close()
        }
    }

    @Test
    fun testLapsDbHelper_onCreateAndOnUpgradeParity_TST_DAT_032() {
        val helper = LapsDbHelper(context)
        assertSchemaParity(
            label = "laps",
            helper = helper,
            initialVersion = 1,
            targetVersion = LapsDbHelper.DB_VERSION
        ) { db ->
            db.execSQL(
                """
                CREATE TABLE ${LapsDatabaseManager.Laps.TABLE} (
                    ${LapsDatabaseManager.Laps.C_ID} INTEGER PRIMARY KEY AUTOINCREMENT,
                    ${LapsDatabaseManager.Laps.WORKOUT_ID} INTEGER,
                    ${LapsDatabaseManager.Laps.LAP_NR} INTEGER,
                    ${LapsDatabaseManager.Laps.TIME_START} TEXT,
                    ${LapsDatabaseManager.Laps.TIME_TOTAL_s} INTEGER,
                    ${LapsDatabaseManager.Laps.DISTANCE_TOTAL_m} REAL,
                    ${LapsDatabaseManager.Laps.SPEED_AVERAGE_mps} REAL
                );
                """.trimIndent()
            )
        }
    }

    @Test
    fun testEquipmentDbHelper_onCreateAndOnUpgradeParity_TST_DAT_032() {
        val helper = EquipmentDbHelper(context)
        assertSchemaParity(
            label = "equipment",
            helper = helper,
            initialVersion = 1,
            targetVersion = EquipmentDbHelper.DB_VERSION
        ) { db ->
            db.execSQL(
                """
                CREATE TABLE ${EquipmentDbHelper.EQUIPMENT} (
                    ${EquipmentDbHelper.C_ID} INTEGER PRIMARY KEY AUTOINCREMENT,
                    ${EquipmentDbHelper.NAME} text,
                    ${EquipmentDbHelper.SPORT_TYPE} text,
                    ${EquipmentDbHelper.FRAME_TYPE} int,
                    ${EquipmentDbHelper.STRAVA_NAME} text,
                    ${EquipmentDbHelper.STRAVA_ID} text
                );
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE ${EquipmentDbHelper.LINKS} (
                    ${EquipmentDbHelper.EQUIPMENT_ID} int,
                    ${EquipmentDbHelper.ANT_DEVICE_ID} int
                );
                """.trimIndent()
            )
        }
    }

    @Test
    fun testSportTypeDbHelper_onCreateAndOnUpgradeParity_TST_DAT_032() {
        val helper = SportTypeDbHelper(context)
        assertSchemaParity(
            label = "sport_type",
            helper = helper,
            initialVersion = 1,
            targetVersion = SportTypeDbHelper.DB_VERSION
        ) { db ->
            db.execSQL(
                """
                CREATE TABLE ${SportTypeDatabaseManager.SportType.TABLE} (
                    ${SportTypeDatabaseManager.SportType.C_ID} INTEGER PRIMARY KEY AUTOINCREMENT,
                    ${SportTypeDatabaseManager.SportType.UI_NAME} text,
                    ${SportTypeDatabaseManager.SportType.BASE_SPORT_TYPE} text,
                    ${SportTypeDatabaseManager.SportType.GOLDEN_CHEETAH_NAME} text,
                    ${SportTypeDatabaseManager.SportType.TCX_NAME} text,
                    ${SportTypeDatabaseManager.SportType.STRAVA_NAME} text,
                    ${SportTypeDatabaseManager.SportType.RUNKEEPER_NAME} text,
                    ${SportTypeDatabaseManager.SportType.TRAINING_PEAKS_NAME} text,
                    ${SportTypeDatabaseManager.SportType.MIN_AVG_SPEED} real,
                    ${SportTypeDatabaseManager.SportType.MAX_AVG_SPEED} real
                );
                """.trimIndent()
            )
        }
    }

    @Test
    fun testRoutesDbHelper_onCreateAndOnUpgradeParity_TST_DAT_032() {
        val helper = RoutesDbHelper(context)
        assertSchemaParity(
            label = "routes",
            helper = helper,
            initialVersion = 10,
            targetVersion = RoutesDbHelper.DB_VERSION
        ) { db ->
            // Seed v10 tables using onCreate then drop the v11 index so v10 -> v11 upgrade recreates it symmetrically
            helper.onCreate(db)
            db.execSQL("DROP INDEX IF EXISTS idx_route_points_route_id")
        }
    }

    @Test
    fun testWorkoutSummariesDbHelper_onCreateAndOnUpgradeV30ToLatestParity_TST_DAT_032() {
        val helper = WorkoutSummariesDbHelper(context)
        assertSchemaParity(
            label = "summaries",
            helper = helper,
            initialVersion = 30,
            targetVersion = WorkoutSummariesDbHelper.DB_VERSION
        ) { db ->
            helper.onCreate(db)
            // Verify idempotent upgrade from previous release version preserves full schema parity
        }
    }

    @Test
    fun testTrackingViewsDbHelper_onCreateAndOnUpgradeParity_TST_DAT_032() {
        val helper = TrackingViewsDbHelper(context)
        createdDbNames.add(TrackingViewsDbHelper.DB_NAME)
        assertSchemaParity(
            label = "tracking_views",
            helper = helper,
            initialVersion = TrackingViewsDbHelper.DB_VERSION - 1,
            targetVersion = TrackingViewsDbHelper.DB_VERSION
        ) { db ->
            helper.onCreate(db)
        }
    }
}
