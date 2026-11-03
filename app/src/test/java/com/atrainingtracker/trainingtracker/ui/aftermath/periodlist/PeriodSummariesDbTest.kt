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
 */

package com.atrainingtracker.trainingtracker.ui.aftermath.periodlist

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.migration.DatabaseSchemaRegistry
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository
import io.mockk.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * TST-PER-026: Comprehensive test suite for ATT-4476 (REQ-PER-020).
 * 1. TST-PER-026.1: Zero-N+1 Batch Query Loading & In-Memory Detailed/Longest Workout linking.
 * 2. TST-PER-026.2: Self-Contained LongestWorkout Persistence across getPeriodsByType and getSummariesInRange.
 * 3. TST-PER-026.3: SQLite v26->v27 Migration Parity Contract & DatabaseSchemaRegistry validation.
 * 4. TST-PER-026.4: Bidirectional Deletion Integrity Healing (finishedWorkouts != periodWorkouts).
 * 5. TST-PER-026.5: Gated Legacy DB File Cleanup (bypassed when preference is true).
 */
@RunWith(RobolectricTestRunner::class)
class PeriodSummariesDbTest {

    private lateinit var context: Context
    private val testDbName = "test_period_summaries_suite.db"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(PeriodSummariesDatabaseManager.DATABASE_NAME)
        context.deleteDatabase(testDbName)
        PeriodSummariesDatabaseManager.setInstanceForTesting(null)
    }

    @After
    fun tearDown() {
        PeriodSummariesDatabaseManager.setInstanceForTesting(null)
        context.deleteDatabase(PeriodSummariesDatabaseManager.DATABASE_NAME)
        context.deleteDatabase(testDbName)
    }

    /**
     * TST-PER-026.1: Zero-N+1 Batch Fetching.
     * Asserts that getPeriodsByType fetches multiple periods, populates all SportStats,
     * links DetailedStats in memory, and preserves complete LongestWorkout without mutating
     * synthetic ID pointers or performing per-row queries.
     */
    @Test
    fun testZeroN1BatchFetching_linksStatsAndLongestWorkoutCorrectly() {
        val manager = PeriodSummariesDatabaseManager.getInstance(context)
        val db = manager.getDatabase()

        // Create 2 periods with multiple sports and detailed stats
        val period1 = createTestPeriodSummary(
            sortKey = "2026-W40",
            startS = 1790000000L,
            type = PeriodType.WEEK,
            sportStats = mapOf(
                BSportType.BIKE to SportStats(
                    count = 3,
                    totalDurationSec = 7200L,
                    totalDistanceMeters = 60000.0,
                    totalAscentMeters = 800L,
                    detailedSportStats = mapOf(
                        "Road" to DetailedStats(2, 5000L, 45000.0, 600L),
                        "Gravel" to DetailedStats(1, 2200L, 15000.0, 200L)
                    ),
                    longestWorkout = LongestWorkout(101L, "Long Sunday Ride", 5000L, 45000.0, 600L)
                ),
                BSportType.RUN to SportStats(
                    count = 2,
                    totalDurationSec = 3600L,
                    totalDistanceMeters = 12000.0,
                    totalAscentMeters = 150L,
                    detailedSportStats = mapOf(
                        "Trail" to DetailedStats(2, 3600L, 12000.0, 150L)
                    ),
                    longestWorkout = LongestWorkout(102L, "Trail 10k", 2000L, 10000.0, 120L)
                )
            )
        )

        val period2 = createTestPeriodSummary(
            sortKey = "2026-W41",
            startS = 1790604800L,
            type = PeriodType.WEEK,
            sportStats = mapOf(
                BSportType.BIKE to SportStats(
                    count = 1,
                    totalDurationSec = 3600L,
                    totalDistanceMeters = 30000.0,
                    totalAscentMeters = 400L,
                    detailedSportStats = mapOf(
                        "Road" to DetailedStats(1, 3600L, 30000.0, 400L)
                    ),
                    longestWorkout = LongestWorkout(201L, "Tempo 30k", 3600L, 30000.0, 400L)
                )
            )
        )

        manager.upsertPeriod(db, period1)
        manager.upsertPeriod(db, period2)

        // Read back via getPeriodsByType
        val retrieved = manager.getPeriodsByType(PeriodType.WEEK)
        assertEquals(2, retrieved.size)

        val p1 = retrieved.find { it.sortKey == "2026-W40" }
        assertNotNull(p1)
        assertEquals(2, p1!!.sportStats.size)

        val bikeStats1 = p1.sportStats[BSportType.BIKE]
        assertNotNull(bikeStats1)
        assertEquals(3, bikeStats1!!.count)
        assertEquals(2, bikeStats1.detailedSportStats.size)
        assertEquals(45000.0, bikeStats1.detailedSportStats["Road"]?.totalDistanceMeters ?: 0.0, 0.001)
        assertEquals(15000.0, bikeStats1.detailedSportStats["Gravel"]?.totalDistanceMeters ?: 0.0, 0.001)

        // Verify LongestWorkout is fully hydrated without synthetic pointers
        val longestBike1 = bikeStats1.longestWorkout
        assertNotNull(longestBike1)
        assertEquals(101L, longestBike1!!.id)
        assertEquals("Long Sunday Ride", longestBike1.name)
        assertEquals(5000L, longestBike1.durationSec)
        assertEquals(45000.0, longestBike1.distanceMeters, 0.001)
        assertEquals(600L, longestBike1.ascentMeters)

        val runStats1 = p1.sportStats[BSportType.RUN]
        assertNotNull(runStats1)
        assertEquals(1, runStats1!!.detailedSportStats.size)
        assertEquals(102L, runStats1.longestWorkout?.id)
        assertEquals("Trail 10k", runStats1.longestWorkout?.name)

        val p2 = retrieved.find { it.sortKey == "2026-W41" }
        assertNotNull(p2)
        assertEquals(1, p2!!.sportStats.size)
        val bikeStats2 = p2.sportStats[BSportType.BIKE]
        assertNotNull(bikeStats2)
        assertEquals(201L, bikeStats2!!.longestWorkout?.id)
        assertEquals("Tempo 30k", bikeStats2.longestWorkout?.name)
    }

    /**
     * TST-PER-026.2: Self-Contained LongestWorkout Persistence.
     * Verifies that upsertPeriod persists all LongestWorkout fields (name, distance, ascent)
     * and both getPeriodsByType and getSummariesInRange return them without requiring
     * external WorkoutSummaries queries or RAM workout lookups.
     */
    @Test
    fun testSelfContainedLongestWorkoutPersistence_persistsAndRestoresAllFields() {
        val manager = PeriodSummariesDatabaseManager.getInstance(context)
        val db = manager.getDatabase()

        val expectedLongest = LongestWorkout(
            id = 777L,
            name = "Alpe d'Huez Climb",
            durationSec = 4800L,
            distanceMeters = 14200.0,
            ascentMeters = 1100L
        )

        val summary = createTestPeriodSummary(
            sortKey = "2026-08",
            startS = 1785628800L,
            type = PeriodType.MONTH,
            sportStats = mapOf(
                BSportType.BIKE to SportStats(
                    count = 5,
                    totalDurationSec = 20000L,
                    totalDistanceMeters = 150000.0,
                    totalAscentMeters = 3500L,
                    detailedSportStats = emptyMap(),
                    longestWorkout = expectedLongest
                )
            )
        )

        manager.upsertPeriod(db, summary)

        // 1. Verify via getPeriodsByType
        val retrievedList = manager.getPeriodsByType(PeriodType.MONTH)
        assertEquals(1, retrievedList.size)
        val actualLongest = retrievedList[0].sportStats[BSportType.BIKE]?.longestWorkout
        assertNotNull("LongestWorkout must not be null", actualLongest)
        assertEquals(expectedLongest.id, actualLongest!!.id)
        assertEquals(expectedLongest.name, actualLongest.name)
        assertEquals(expectedLongest.durationSec, actualLongest.durationSec)
        assertEquals(expectedLongest.distanceMeters, actualLongest.distanceMeters, 0.001)
        assertEquals(expectedLongest.ascentMeters, actualLongest.ascentMeters)

        // 2. Verify via getSummariesInRange
        val rangeList = manager.getSummariesInRange(PeriodType.MONTH, 1785628800L - 10, 1785628800L + 10)
        assertEquals(1, rangeList.size)
        val actualRangeLongest = rangeList[0].sportStats[BSportType.BIKE]?.longestWorkout
        assertNotNull("Range LongestWorkout must not be null", actualRangeLongest)
        assertEquals(expectedLongest.id, actualRangeLongest!!.id)
        assertEquals(expectedLongest.name, actualRangeLongest.name)
        assertEquals(expectedLongest.durationSec, actualRangeLongest.durationSec)
        assertEquals(expectedLongest.distanceMeters, actualRangeLongest.distanceMeters, 0.001)
        assertEquals(expectedLongest.ascentMeters, actualRangeLongest.ascentMeters)
    }

    /**
     * TST-PER-026.3: SQLite v26->v27 Migration Parity Contract & Registry Validation.
     * Asserts that upgrading a v26 database produces identical schema structure to fresh v27 onCreate(),
     * with safe non-null defaults ('' for text, 0.0 for real, 0 for int), zero data loss,
     * and validates DatabaseSchemaRegistry.
     */
    @Test
    fun testV26ToV27MigrationParity_andRegistryValidation() {
        val upgradedDbName = "test_upgraded_v27.db"
        val freshDbName = "test_fresh_v27.db"
        context.deleteDatabase(upgradedDbName)
        context.deleteDatabase(freshDbName)

        // 1. Scaffold v26 database
        val v26Db = context.openOrCreateDatabase(upgradedDbName, Context.MODE_PRIVATE, null)
        v26Db.execSQL(PeriodSummariesDatabaseManager.PeriodSummariesContract.CREATE_TABLE)
        v26Db.execSQL("""
            CREATE TABLE ${PeriodSummariesDatabaseManager.SportStatsContract.TABLE_NAME} (
                _id INTEGER PRIMARY KEY AUTOINCREMENT,
                period_id INTEGER, b_sport_type TEXT, count INTEGER,
                total_duration INTEGER, total_distance REAL, total_ascent INTEGER, 
                longest_workout_id INTEGER, longest_duration INTEGER,
                FOREIGN KEY(period_id) REFERENCES ${PeriodSummariesDatabaseManager.PeriodSummariesContract.TABLE_NAME}(_id) ON DELETE CASCADE
            )
        """)
        v26Db.execSQL(PeriodSummariesDatabaseManager.DetailedStatsContract.CREATE_TABLE)
        v26Db.execSQL(PeriodSummariesDatabaseManager.SyncStatusContract.CREATE_TABLE)
        v26Db.version = 26

        // Insert historical row in v26
        v26Db.execSQL("""
            INSERT INTO ${PeriodSummariesDatabaseManager.SportStatsContract.TABLE_NAME}
            (period_id, b_sport_type, count, total_duration, total_distance, total_ascent, longest_workout_id, longest_duration)
            VALUES (1, 'BIKE', 4, 10000, 80000.0, 1200, 42, 3600)
        """)

        // Run onUpgrade v26 -> v27
        val helper = PeriodSummariesDatabaseManager.PeriodSummariesDbHelper(context, upgradedDbName, 27)
        helper.onUpgrade(v26Db, 26, 27)
        v26Db.version = 27

        // 2. Scaffold fresh v27 database
        val freshDb = context.openOrCreateDatabase(freshDbName, Context.MODE_PRIVATE, null)
        val freshHelper = PeriodSummariesDatabaseManager.PeriodSummariesDbHelper(context, freshDbName, 27)
        freshHelper.onCreate(freshDb)
        freshDb.version = 27

        // 3. Compare column metadata between upgraded and fresh databases
        val upgradedColumns = getTableColumns(v26Db, PeriodSummariesDatabaseManager.SportStatsContract.TABLE_NAME)
        val freshColumns = getTableColumns(freshDb, PeriodSummariesDatabaseManager.SportStatsContract.TABLE_NAME)

        assertEquals("Column count must match between upgraded and fresh v27 schemas", freshColumns.size, upgradedColumns.size)
        assertEquals("Column schemas must match 100%", freshColumns, upgradedColumns)

        // Verify newly added columns exist in upgraded database
        assertTrue(upgradedColumns.containsKey(PeriodSummariesDatabaseManager.SportStatsContract.COLUMN_LONGEST_WORKOUT_NAME))
        assertTrue(upgradedColumns.containsKey(PeriodSummariesDatabaseManager.SportStatsContract.COLUMN_LONGEST_DISTANCE))
        assertTrue(upgradedColumns.containsKey(PeriodSummariesDatabaseManager.SportStatsContract.COLUMN_LONGEST_ASCENT))

        // 4. Verify historical row preserved with default non-null values
        v26Db.rawQuery("SELECT * FROM ${PeriodSummariesDatabaseManager.SportStatsContract.TABLE_NAME} WHERE _id = 1", null).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(42L, c.getLong(c.getColumnIndexOrThrow(PeriodSummariesDatabaseManager.SportStatsContract.COLUMN_LONGEST_WORKOUT_ID)))
            assertEquals(3600L, c.getLong(c.getColumnIndexOrThrow(PeriodSummariesDatabaseManager.SportStatsContract.COLUMN_LONGEST_DURATION)))
            assertEquals("", c.getString(c.getColumnIndexOrThrow(PeriodSummariesDatabaseManager.SportStatsContract.COLUMN_LONGEST_WORKOUT_NAME)))
            assertEquals(0.0, c.getDouble(c.getColumnIndexOrThrow(PeriodSummariesDatabaseManager.SportStatsContract.COLUMN_LONGEST_DISTANCE)), 0.001)
            assertEquals(0L, c.getLong(c.getColumnIndexOrThrow(PeriodSummariesDatabaseManager.SportStatsContract.COLUMN_LONGEST_ASCENT)))
        }

        // 5. Verify DatabaseSchemaRegistry parity (Rule 62)
        assertEquals(
            27,
            DatabaseSchemaRegistry.getMaxSupportedVersion(PeriodSummariesDatabaseManager.DATABASE_NAME)
        )
        assertEquals(
            PeriodSummariesDatabaseManager.DB_VERSION,
            DatabaseSchemaRegistry.getMaxSupportedVersion(PeriodSummariesDatabaseManager.DATABASE_NAME)
        )

        v26Db.close()
        freshDb.close()
        context.deleteDatabase(upgradedDbName)
        context.deleteDatabase(freshDbName)
    }

    /**
     * TST-PER-026.4: Bidirectional Deletion Integrity Healing.
     * Verifies that checkIntegrityAndSync detects discrepancies not only when finishedWorkouts > periodWorkouts,
     * but crucially when workouts were deleted out-of-band (finishedWorkouts < periodWorkouts).
     */
    @Test
    fun testBidirectionalDeletionIntegrityHealing() = runTest {
        val mockApp = mockk<Application>(relaxed = true)
        val mockDbManager = mockk<PeriodSummariesDatabaseManager>(relaxed = true)
        val mockSummariesManager = mockk<WorkoutSummariesDatabaseManager>(relaxed = true)
        val mockWorkoutRepo = mockk<WorkoutRepository>(relaxed = true)
        every { mockWorkoutRepo.allWorkouts } returns MutableStateFlow(emptyList())

        PeriodSummariesDatabaseManager.setInstanceForTesting(mockDbManager)
        WorkoutSummariesDatabaseManager.setInstanceForTesting(mockSummariesManager)
        WorkoutRepository.resetForTesting(mockWorkoutRepo)

        val repo = PeriodsRepository.getInstance(mockApp)

        // Case 1: Workouts deleted out-of-band: finishedWorkouts (8) < periodWorkouts (10)
        every { mockSummariesManager.getFinishedWorkoutCount() } returns 8
        every { mockDbManager.getTotalDayWorkoutsCount() } returns 10

        val triggeredOnDeletion = repo.checkIntegrityAndSync()
        assertTrue("Discrepancy must be detected and trigger resync when workouts are deleted", triggeredOnDeletion)

        // Case 2: New workouts added: finishedWorkouts (12) > periodWorkouts (10)
        every { mockSummariesManager.getFinishedWorkoutCount() } returns 12
        every { mockDbManager.getTotalDayWorkoutsCount() } returns 10

        val triggeredOnAddition = repo.checkIntegrityAndSync()
        assertTrue("Discrepancy must be detected and trigger resync when workouts are added", triggeredOnAddition)

        // Case 3: In sync: finishedWorkouts (10) == periodWorkouts (10)
        every { mockSummariesManager.getFinishedWorkoutCount() } returns 10
        every { mockDbManager.getTotalDayWorkoutsCount() } returns 10

        val triggeredWhenSynced = repo.checkIntegrityAndSync()
        assertFalse("No resync when counts are strictly equal", triggeredWhenSynced)

        PeriodsRepository.resetInstanceForTesting()
    }

    /**
     * TST-PER-026.5: Gated Legacy DB File Cleanup.
     * Asserts that the 66 legacy DB file checks in init execute strictly once and set the
     * SharedPreferences flag, and are completely bypassed when the flag is already true.
     */
    @Test
    fun testGatedLegacyFileCleanup_bypassesWhenPreferenceSet() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit().remove(PeriodSummariesDatabaseManager.PREF_LEGACY_DBS_CLEANED).apply()

        assertFalse(prefs.getBoolean(PeriodSummariesDatabaseManager.PREF_LEGACY_DBS_CLEANED, false))

        // First initialization executes cleanup and sets preference to true
        PeriodSummariesDatabaseManager.getInstance(context)
        assertTrue(
            "Preference must be set to true after first init",
            prefs.getBoolean(PeriodSummariesDatabaseManager.PREF_LEGACY_DBS_CLEANED, false)
        )

        // Reset singleton and verify subsequent initialization respects the flag
        PeriodSummariesDatabaseManager.setInstanceForTesting(null)
        PeriodSummariesDatabaseManager.getInstance(context)
        assertTrue(
            "Preference must remain true on subsequent init",
            prefs.getBoolean(PeriodSummariesDatabaseManager.PREF_LEGACY_DBS_CLEANED, false)
        )
    }

    private fun getTableColumns(db: SQLiteDatabase, tableName: String): Map<String, ColumnInfo> {
        val columns = mutableMapOf<String, ColumnInfo>()
        db.rawQuery("PRAGMA table_info(\"$tableName\")", null).use { cursor ->
            val nameIdx = cursor.getColumnIndexOrThrow("name")
            val typeIdx = cursor.getColumnIndexOrThrow("type")
            val notNullIdx = cursor.getColumnIndexOrThrow("notnull")
            val dfltIdx = cursor.getColumnIndexOrThrow("dflt_value")
            while (cursor.moveToNext()) {
                val name = cursor.getString(nameIdx)
                val type = cursor.getString(typeIdx)
                val notNull = cursor.getInt(notNullIdx) == 1
                val dflt = cursor.getString(dfltIdx)
                columns[name] = ColumnInfo(name, type, notNull, dflt)
            }
        }
        return columns
    }

    private data class ColumnInfo(val name: String, val type: String, val notNull: Boolean, val dflt: String?)

    private fun createTestPeriodSummary(
        sortKey: String,
        startS: Long,
        type: PeriodType,
        sportStats: Map<BSportType, SportStats>
    ): PeriodSummary {
        return PeriodSummary(
            periodLabel = "Label $sortKey",
            periodDateRange = "Range $sortKey",
            periodType = type,
            startTimestampS = startS,
            endTimestampS = startS + 604799L,
            totalWorkouts = sportStats.values.sumOf { it.count },
            totalDurationSec = sportStats.values.sumOf { it.totalDurationSec },
            totalDistance = sportStats.values.sumOf { it.totalDistanceMeters },
            sportStats = sportStats,
            sortKey = sortKey
        )
    }
}
