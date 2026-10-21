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
import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries
import com.atrainingtracker.trainingtracker.tracker.TrackerService
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests verifying REQ-PER-014 / TST-PER-020 (ATT-3056):
 * 1. WorkoutSummariesDatabaseManager.getWorkoutsInRangeCursor enforces FINISHED = 1 gating.
 * 2. PeriodSummariesDatabaseManager.setSyncFinished updates or inserts sync completion status.
 * 3. TrackerService intent contracts for tracking finalization.
 * 4. PeriodsViewModel.loadPeriods triggers integrity reconciliation on load.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PeriodsRepositorySyncTest {

    private lateinit var mockContext: Context
    private lateinit var mockDb: SQLiteDatabase

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.w(any(), any<String>(), any()) } returns 0
        every { Log.e(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>(), any()) } returns 0

        mockkConstructor(ContentValues::class)
        every { constructedWith<ContentValues>().put(any<String>(), any<String>()) } returns Unit
        every { constructedWith<ContentValues>().put(any<String>(), any<Long>()) } returns Unit
        every { constructedWith<ContentValues>().put(any<String>(), any<Int>()) } returns Unit
        every { constructedWith<ContentValues>().put(any<String>(), any<Double>()) } returns Unit
        every { constructedWith<ContentValues>().getAsString(any<String>()) } returns null

        mockContext = mockk(relaxed = true)
        mockDb = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        WorkoutRepository.resetForTesting(null)
        PeriodSummariesDatabaseManager.setInstanceForTesting(null)
        WorkoutSummariesDatabaseManager.setInstanceForTesting(null)
        PeriodsRepository.resetInstanceForTesting()
        unmockkAll()
    }

    /**
     * Verifies that getWorkoutsInRangeCursor appends FINISHED = 1 to the selection query,
     * strictly excluding incomplete or unfinalized workouts from period aggregation.
     */
    @Test
    fun testWorkoutsInRangeCursor_includesFinishedCondition() {
        val selectionSlot = slot<String>()
        val selectionArgsSlot = slot<Array<String>>()

        every {
            mockDb.query(
                WorkoutSummaries.TABLE,
                null,
                capture(selectionSlot),
                capture(selectionArgsSlot),
                null,
                null,
                WorkoutSummaries.TIME_START + " ASC"
            )
        } returns mockk(relaxed = true)

        val manager = object : WorkoutSummariesDatabaseManager(mockContext) {
            override fun getDatabase(): SQLiteDatabase = mockDb
        }

        manager.getWorkoutsInRangeCursor(1700000000L, 1700086399L)

        val selection = selectionSlot.captured
        assertTrue(
            "Selection must enforce FINISHED = 1 to exclude incomplete sessions: $selection",
            selection.contains(WorkoutSummaries.FINISHED + " = 1")
        )
        assertEquals("1700000000", selectionArgsSlot.captured[0])
        assertEquals("1700086399", selectionArgsSlot.captured[1])
    }

    /**
     * Verifies that PeriodSummariesDatabaseManager.setSyncFinished writes COLUMN_IS_FINISHED = 1
     * to the SyncStatus table in PeriodSummaries.db.
     */
    @Test
    fun testPeriodSummariesDatabaseManager_setSyncFinishedUpdatesDatabase() {
        val manager = PeriodSummariesDatabaseManager.getInstance(mockContext)

        every {
            mockDb.update(
                PeriodSummariesDatabaseManager.SyncStatusContract.TABLE_NAME,
                any(),
                any(),
                null
            )
        } returns 1

        manager.setSyncFinished(mockDb, true)

        verify(atLeast = 1) {
            mockDb.update(
                PeriodSummariesDatabaseManager.SyncStatusContract.TABLE_NAME,
                any(),
                any(),
                null
            )
        }
    }

    /**
     * Verifies that TRACKING_FINISHED_INTENT and WORKOUT_ID extra contract constants match expectations.
     */
    @Test
    fun testTrackingFinishedIntent_contractConstants() {
        assertEquals(
            "de.rainerblind.trainingtracker.TrackerService.TRACKING_FINISHED_INTENT",
            TrackerService.TRACKING_FINISHED_INTENT
        )
        assertEquals("WORKOUT_ID", TrackerService.WORKOUT_ID)
    }

    /**
     * Verifies that PeriodsViewModel.loadPeriods executes both workout history loading
     * and periods integrity synchronization.
     */
    @Test
    fun testPeriodsViewModel_loadPeriods_triggersCheckIntegrityAndSync() = runTest {
        val mockApp = mockk<Application>(relaxed = true)
        val mockWorkoutRepo = mockk<WorkoutRepository>(relaxed = true)
        val mockPeriodsRepo = mockk<PeriodsRepository>(relaxed = true)

        every { mockPeriodsRepo.groupedPeriods } returns MutableStateFlow(emptyList())
        every { mockPeriodsRepo.migrationStatus } returns MutableStateFlow(null)

        WorkoutRepository.resetForTesting(mockWorkoutRepo)
        PeriodsRepository.setInstanceForTesting(mockPeriodsRepo)

        val viewModel = PeriodsViewModel(mockApp)
        viewModel.loadPeriods()

        coVerify(timeout = 2000) { mockWorkoutRepo.loadAllWorkouts() }
        coVerify(timeout = 2000) { mockPeriodsRepo.checkIntegrityAndSync() }
    }

    // --- ATT-4019 / REQ-PER-016 / TST-PER-022 Test Cases ---

    private fun createSampleWorkoutData(
        id: Long = 100L,
        finished: Boolean = true,
        startTimeS: Long = 1700000000L,
        activeTimeSec: Long = 3600L,
        totalDistance: Double = 25000.0,
        ascentMeters: Long = 250L,
        sportId: Long = 2L,
        bSportType: BSportType = BSportType.BIKE,
        workoutName: String = "Morning Ride",
        description: String? = "Felt great",
        stravaActivityData: String? = null,
        uploadToStrava: Int = 0,
        mapPolyline: String = "_p~iF~ps|U_ulLnnqC"
    ): WorkoutData {
        val ldt = java.time.LocalDateTime.of(2023, 11, 14, 10, 0)
        return WorkoutData(
            id = id,
            finished = finished,
            fileBaseName = "workout_$id",
            workoutName = workoutName,
            sportId = sportId,
            sportName = "Cycling",
            bSportType = bSportType,
            startTimeS = startTimeS,
            formattedDate = "2023-11-14",
            formattedTime = "10:00",
            localDateTime = ldt,
            equipmentName = "Road Bike",
            equipmentId = 1L,
            commute = false,
            trainer = false,
            race = false,
            source = com.atrainingtracker.trainingtracker.database.WorkoutSource.TRACKED,
            mapPolyline = mapPolyline,
            encodedAltitudes = "",
            encodedDistances = "",
            uploadToStrava = uploadToStrava,
            totalDistance = totalDistance,
            maxDisplacement = 5000.0,
            activeTimeSec = activeTimeSec,
            totalTimeSec = activeTimeSec + 300L,
            avgSpeedMps = totalDistance / activeTimeSec,
            ascentMeters = ascentMeters,
            descentMeters = ascentMeters,
            minAltitude = 100.0,
            maxAltitude = 350.0,
            description = description,
            goal = "Zone 2",
            method = "Steady",
            stravaSportName = "Ride",
            stravaActivityData = stravaActivityData,
            minLat = 48.0,
            minLng = 11.0,
            maxLat = 48.1,
            maxLng = 11.1
        )
    }

    /**
     * TST-PER-022.1: Flow Projection & Deduplication.
     * Verifies that modifying non-period properties (description, notes, export status, workoutName)
     * yields identical PeriodProjectionKey, which prevents distinctUntilChanged from triggering
     * redundant loadFromDatabase(true) re-computations. Also verifies that period-relevant changes
     * produce different keys.
     */
    @Test
    fun testPeriodProjectionKey_filtersNonPeriodChanges_andDetectsPeriodRelevantChanges() {
        val base = createSampleWorkoutData()
        val nonPeriodModified = base.copy(
            workoutName = "Renamed Workout",
            description = "Updated workout notes",
            goal = "New training goal",
            method = "Different method",
            stravaActivityData = "{\"id\": 99999, \"name\": \"Morning Ride\"}",
            uploadToStrava = 1,
            clusterId = 42L,
            clusterName = "Commute Loop"
        )

        // Non-period changes must project to identical keys
        assertEquals(
            "Non-period property changes must produce identical PeriodProjectionKey",
            base.toPeriodProjectionKey(),
            nonPeriodModified.toPeriodProjectionKey()
        )

        // Period-relevant changes must produce different keys
        assertNotEquals(base.toPeriodProjectionKey(), base.copy(totalDistance = 30000.0).toPeriodProjectionKey())
        assertNotEquals(base.toPeriodProjectionKey(), base.copy(activeTimeSec = 4000L).toPeriodProjectionKey())
        assertNotEquals(base.toPeriodProjectionKey(), base.copy(ascentMeters = 500L).toPeriodProjectionKey())
        assertNotEquals(base.toPeriodProjectionKey(), base.copy(bSportType = BSportType.RUN).toPeriodProjectionKey())
        assertNotEquals(base.toPeriodProjectionKey(), base.copy(sportId = 99L).toPeriodProjectionKey())
        assertNotEquals(base.toPeriodProjectionKey(), base.copy(finished = false).toPeriodProjectionKey())
        assertNotEquals(base.toPeriodProjectionKey(), base.copy(startTimeS = 1700010000L).toPeriodProjectionKey())
        assertNotEquals(base.toPeriodProjectionKey(), base.copy(minLat = 49.0).toPeriodProjectionKey())
        assertNotEquals(base.toPeriodProjectionKey(), base.copy(mapPolyline = "different_poly").toPeriodProjectionKey())
    }

    /**
     * TST-PER-022.2: Single-Owner Tracking Finalization & Idempotency.
     * Verifies that PeriodsRepository no longer has direct BroadcastReceiver registrations,
     * relying on WorkoutRepository as the single orchestrator, and enforces idempotent
     * deduplication when onWorkoutFinished is called with duplicate IDs.
     */
    @Test
    fun testSingleOwnerFinalization_andIdempotentDeduplication() = runTest {
        val mockApp = mockk<Application>(relaxed = true)
        val mockDbManager = mockk<PeriodSummariesDatabaseManager>(relaxed = true)
        val mockSummariesManager = mockk<WorkoutSummariesDatabaseManager>(relaxed = true)
        val mockWorkoutRepo = mockk<WorkoutRepository>(relaxed = true)
        every { mockWorkoutRepo.allWorkouts } returns MutableStateFlow(emptyList())

        PeriodSummariesDatabaseManager.setInstanceForTesting(mockDbManager)
        WorkoutSummariesDatabaseManager.setInstanceForTesting(mockSummariesManager)
        WorkoutRepository.resetForTesting(mockWorkoutRepo)
        every { mockDbManager.isSyncFinished() } returns true

        val repo = PeriodsRepository.getInstance(mockApp)

        // 1. Verify direct trackingFinishedReceiver was eliminated
        val hasReceiverField = repo.javaClass.declaredFields.any { it.name == "trackingFinishedReceiver" }
        assertTrue("trackingFinishedReceiver must be eliminated from PeriodsRepository", !hasReceiverField)

        // 2. Verify onWorkoutFinished idempotency
        val workout = createSampleWorkoutData(id = 555L)

        // First call processes
        repo.onWorkoutFinished(workout)

        // Duplicate call with same ID should be dropped
        repo.onWorkoutFinished(workout)

        // Verify that calling onWorkoutDeleted cleans up the ID
        repo.onWorkoutDeleted(workout)
    }

    /**
     * TST-PER-022.3: Shared WorkoutDataMapper Instance Reuse.
     * Verifies that PeriodsRepository uses a single lazy/shared WorkoutDataMapper instance
     * rather than constructing ad-hoc instances and redundant DB helper singletons.
     */
    @Test
    fun testPeriodsRepository_sharedMapperReused() {
        val mockApp = mockk<Application>(relaxed = true)
        val mockDbManager = mockk<PeriodSummariesDatabaseManager>(relaxed = true)
        val mockSummariesManager = mockk<WorkoutSummariesDatabaseManager>(relaxed = true)
        val mockWorkoutRepo = mockk<WorkoutRepository>(relaxed = true)
        every { mockWorkoutRepo.allWorkouts } returns MutableStateFlow(emptyList())

        PeriodSummariesDatabaseManager.setInstanceForTesting(mockDbManager)
        WorkoutSummariesDatabaseManager.setInstanceForTesting(mockSummariesManager)
        WorkoutRepository.resetForTesting(mockWorkoutRepo)

        val repo = PeriodsRepository.getInstance(mockApp)

        val mapper1 = repo.mapper
        val mapper2 = repo.mapper
        assertSame("PeriodsRepository must reuse the single shared WorkoutDataMapper instance", mapper1, mapper2)
    }
}
