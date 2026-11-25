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
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager.SportType
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager.SportTypeDbHelper
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.migration.DatabaseSchemaRegistry
import com.atrainingtracker.trainingtracker.repositories.SportTypesRepository
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Real SQLite contract tests for prepared Swimming SportType template, idempotent
 * creation, zero walk-speed collision, and DatabaseSchemaRegistry parity (REQ-SET-107.1,
 * REQ-SET-107.2, TST-SET-109.1, TST-SET-109.2).
 */
@RunWith(RobolectricTestRunner::class)
class SportTypeSwimTemplateTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        val baseContext = ApplicationProvider.getApplicationContext<Context>()
        context = spyk(baseContext)
        every { context.applicationContext } returns context
        every { context.getString(R.string.sport_type_other) } returns "Other"
        every { context.getString(R.string.sport_type_run) } returns "Run"
        every { context.getString(R.string.sport_type_bike) } returns "Bike"
        every { context.getString(R.string.sport_type_walk) } returns "Walk"
        every { context.getString(R.string.sport_type_MTB) } returns "MTB"
        every { context.getString(R.string.sport_type_swim) } returns "Swimming"

        mockkStatic(TrainingApplication::class)
        every { TrainingApplication.getAppContext() } returns context
        every { TrainingApplication.getMinWalkSpeed_mps() } returns 0.5
        every { TrainingApplication.getMaxWalkSpeed_mps() } returns 2.0
        every { TrainingApplication.getMaxRunSpeed_mps() } returns 6.0
        every { TrainingApplication.getMaxMTBSpeed_mps() } returns 7.0
        every { TrainingApplication.getMaxBikeSpeed_mps() } returns 15.0

        SportTypesRepository.resetForTesting(null)
        SportTypeDatabaseManager.resetForTesting(null)
        context.deleteDatabase(SportTypeDbHelper.DB_NAME)
    }

    @After
    fun tearDown() {
        SportTypesRepository.resetForTesting(null)
        SportTypeDatabaseManager.resetForTesting(null)
        context.deleteDatabase(SportTypeDbHelper.DB_NAME)
        unmockkAll()
    }

    @Test
    fun testFreshDatabase_seedsOnlyFiveDefaultsWithoutSwimming() {
        val dbManager = SportTypeDatabaseManager.getInstance(context)
        val ids = dbManager.sportTypesIdList
        assertEquals("Fresh SportType.db must seed strictly 5 default sport types", 5, ids.size)
        assertFalse("Fresh install must not activate Swimming by default", dbManager.isSwimSportTypeActivated)
        assertEquals(
            "DatabaseSchemaRegistry must match SportTypeDbHelper.DB_VERSION",
            SportTypeDbHelper.DB_VERSION,
            DatabaseSchemaRegistry.getMaxSupportedVersion(SportTypeDbHelper.DB_NAME)
        )
    }

    @Test
    fun testEnsureSwimSportTypeExists_isIdempotentAndLocalized() {
        val repo = SportTypesRepository.getInstance(context)
        assertFalse(repo.isSwimSportTypeActivated())

        val firstId = repo.ensureSwimSportTypeExists()
        assertTrue("Inserted Swim ID must exceed 5", firstId > SportTypeDatabaseManager.SYSTEM_SPORT_ID_THRESHOLD)
        assertTrue(repo.isSwimSportTypeActivated())

        val secondId = repo.ensureSwimSportTypeExists()
        assertEquals("Repeated ensureSwimSportTypeExists() must return existing ID", firstId, secondId)

        val dbManager = SportTypeDatabaseManager.getInstance(context)
        assertEquals(6, dbManager.sportTypesIdList.size)
        assertEquals(context.getString(R.string.sport_type_swim), dbManager.getUIName(firstId))
        assertEquals(BSportType.UNKNOWN, dbManager.getBSportType(firstId))
        assertEquals("Swim", dbManager.getStravaName(firstId))
        assertEquals("swim", dbManager.getGcName(firstId))
        assertEquals("Other", dbManager.getTcxName(firstId))
        assertEquals(0.0, dbManager.getMinSpeed(firstId), 0.0001)
        assertEquals(0.0, dbManager.getMaxSpeed(firstId), 0.0001)
    }

    @Test
    fun testEnsureSwimSportTypeExists_reusesPreExistingUserSwimRow() {
        val dbManager = SportTypeDatabaseManager.getInstance(context)
        val cv = ContentValues().apply {
            put(SportType.UI_NAME, "Pool Swim")
            put(SportType.BASE_SPORT_TYPE, BSportType.UNKNOWN.name)
            put(SportType.GOLDEN_CHEETAH_NAME, "custom_swim")
            put(SportType.TCX_NAME, "Other")
            put(SportType.STRAVA_NAME, "Swim")
            put(SportType.RUNKEEPER_NAME, "Swimming")
            put(SportType.TRAINING_PEAKS_NAME, "Swim")
            put(SportType.MIN_AVG_SPEED, 0.0)
            put(SportType.MAX_AVG_SPEED, 0.0)
            put(SportType.IS_PLACEHOLDER, 0)
        }
        val userSwimId = dbManager.database.insert(SportType.TABLE, null, cv)
        assertTrue(userSwimId > 5L)

        val resolvedId = SportTypesRepository.getInstance(context).ensureSwimSportTypeExists()
        assertEquals("Existing user Swim row must be reused without duplicates", userSwimId, resolvedId)
        assertEquals(6, dbManager.sportTypesIdList.size)
    }

    @Test
    fun testZeroWalkSpeedCollision_whenSwimmingActivated() {
        val dbManager = SportTypeDatabaseManager.getInstance(context)
        val swimId = dbManager.ensureSwimSportTypeExists()

        val walkResolvedIds = dbManager.getSportTypesIdList(BSportType.UNKNOWN, 1.4)
        assertFalse("1.4 m/s land walk must never resolve to Swimming", walkResolvedIds.contains(swimId))
        assertTrue("1.4 m/s land walk must resolve to WALK (ID 4)", walkResolvedIds.contains(4L))

        val zeroSpeedResolvedIds = dbManager.getSportTypesIdList(BSportType.UNKNOWN, 0.0)
        assertFalse("0.0 m/s workout must never match Swimming [0.0, 0.0)", zeroSpeedResolvedIds.contains(swimId))
        assertTrue("0.0 m/s workout must resolve to OTHER (ID 1)", zeroSpeedResolvedIds.contains(1L))
    }

    /**
     * TST-INT-002 (REQ-EXP-006) & TST-INT-003 (REQ-EXP-007): Real SQLite round-trip
     * verifying saving "- No upload -" (null stravaName) and new Strava sport type "GravelRide".
     */
    @Test
    fun testStravaSportTypeMapping_noUploadAndGravelRideRoundTrip_TST_INT_002_TST_INT_003() {
        val dbManager = SportTypeDatabaseManager.getInstance(context)

        // TST-INT-002: "- No upload -" persists null Strava name
        val privateId = dbManager.updateSportType(
            -1L, "Private Mobility", BSportType.UNKNOWN, 0.0, 2.0, null, "Other", "workout"
        )
        assertTrue("Inserted custom sport ID must be positive", privateId > 0L)
        org.junit.Assert.assertNull(dbManager.getStravaName(privateId))

        // TST-INT-003: "GravelRide" persists and round-trips via getStravaName and lookup
        val gravelId = dbManager.updateSportType(
            -1L, "Gravel Bike", BSportType.BIKE, 2.0, 12.0, "GravelRide", "Biking", "bike"
        )
        assertTrue("Inserted Gravel Bike sport ID must be positive", gravelId > 0L)
        assertEquals("GravelRide", dbManager.getStravaName(gravelId))
        assertEquals(gravelId, dbManager.getSportTypeIdFromStravaName("GravelRide"))

        val stravaValues = context.resources.getStringArray(R.array.Strava_Sport_Types_Strava_Names)
        assertTrue("arrays.xml must include GravelRide", stravaValues.contains("GravelRide"))
    }

    /**
     * TST-SET-134.1 (REQ-SET-134.1): SportTypesRepository.sportTypesChanged emits strictly on real
     * SQLite mutations (ensureSwimSportTypeExists insert, updateSportType, delete) and stays silent on no-ops.
     */
    @Test
    fun testEnsureSwimSportTypeExists_updateAndDelete_emitSportTypesChangedStrictlyOnMutation_TST_SET_134_1() = runBlocking<Unit> {
        val repo = SportTypesRepository.getInstance(context)
        val dbManager = SportTypeDatabaseManager.getInstance(context)
        var events = 0
        val collector = launch(Dispatchers.Unconfined) {
            SportTypesRepository.sportTypesChanged.collect { events++ }
        }
        try {
            // Initial Swim activation inserts row -> 1 event
            val swimId = repo.ensureSwimSportTypeExists()
            assertTrue(swimId > SportTypeDatabaseManager.SYSTEM_SPORT_ID_THRESHOLD)
            assertEquals("Initial ensureSwimSportTypeExists() insert must emit 1 event", 1, events)

            // Idempotent repeat -> 0 additional events
            assertEquals(swimId, repo.ensureSwimSportTypeExists())
            assertEquals("Idempotent ensureSwimSportTypeExists() no-op must emit 0 extra events", 1, events)

            // Custom insert -> +1 event
            val customId = dbManager.updateSportType(-1L, "Aqua Jog", BSportType.UNKNOWN, 0.0, 0.0, "Workout", "Other", "walk")
            assertTrue(customId > 0L)
            assertEquals("Insert via updateSportType(-1L) must emit event", 2, events)

            // Update -> +1 event
            dbManager.updateSportType(customId, "Aqua Jogging", BSportType.UNKNOWN, 0.0, 0.0, "Workout", "Other", "walk")
            assertEquals("Update via updateSportType(id) must emit event", 3, events)

            // Delete custom -> +1 event; delete protected system sport -> 0 extra events
            dbManager.delete(customId)
            assertEquals("Delete of custom sport must emit event", 4, events)
            dbManager.delete(1L)
            assertEquals("Protected system sport delete must emit 0 extra events", 4, events)
        } finally {
            collector.cancel()
        }
    }

    /**
     * TST-SET-134.1 (REQ-SET-134.1): SportTypeReconciliationHelper.reconcileOrphanSportTypes()
     * emits sportTypesChanged strictly when insertedCount > 0 and stays silent when insertedCount == 0.
     */
    @Test
    fun testReconcileOrphanSportTypes_emitsSportTypesChangedStrictlyWhenInserted_TST_SET_134_1() = runBlocking<Unit> {
        val dbManager = SportTypeDatabaseManager.getInstance(context)
        val summariesDb = android.database.sqlite.SQLiteDatabase.create(null)
        val equipmentDb = android.database.sqlite.SQLiteDatabase.create(null)
        val clusterDb = android.database.sqlite.SQLiteDatabase.create(null)
        summariesDb.execSQL("CREATE TABLE WorkoutSummaries (sportId INTEGER)")
        equipmentDb.execSQL("CREATE TABLE SportTypeEquipmentLink (sport_type_id INTEGER)")
        clusterDb.execSQL("CREATE TABLE RouteClusters (probable_sport_id INTEGER)")

        var events = 0
        val collector = launch(Dispatchers.Unconfined) {
            SportTypesRepository.sportTypesChanged.collect { events++ }
        }
        try {
            // No orphan IDs -> insertedCount == 0 -> 0 events
            val zeroInserted = SportTypeReconciliationHelper.reconcileOrphanSportTypes(
                context = context,
                sportDb = dbManager.database,
                summariesDb = summariesDb,
                equipmentDb = equipmentDb,
                clusterDb = clusterDb,
                dispatcher = Dispatchers.Unconfined
            )
            assertEquals(0, zeroInserted)
            assertEquals("Zero inserted placeholders must emit 0 events", 0, events)

            // Add an orphan sportId > 5 -> insertedCount == 1 -> +1 event
            summariesDb.execSQL("INSERT INTO WorkoutSummaries (sportId) VALUES (42)")
            val oneInserted = SportTypeReconciliationHelper.reconcileOrphanSportTypes(
                context = context,
                sportDb = dbManager.database,
                summariesDb = summariesDb,
                equipmentDb = equipmentDb,
                clusterDb = clusterDb,
                dispatcher = Dispatchers.Unconfined
            )
            assertEquals(1, oneInserted)
            assertEquals("Inserting 1 orphan placeholder must emit 1 event", 1, events)

            // Re-running with same orphan already present -> insertedCount == 0 -> 0 extra events
            val repeatInserted = SportTypeReconciliationHelper.reconcileOrphanSportTypes(
                context = context,
                sportDb = dbManager.database,
                summariesDb = summariesDb,
                equipmentDb = equipmentDb,
                clusterDb = clusterDb,
                dispatcher = Dispatchers.Unconfined
            )
            assertEquals(0, repeatInserted)
            assertEquals("Idempotent re-run must emit 0 extra events", 1, events)
        } finally {
            collector.cancel()
            summariesDb.close()
            equipmentDb.close()
            clusterDb.close()
        }
    }
}
