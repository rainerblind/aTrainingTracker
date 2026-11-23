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
import android.util.Log
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries
import com.atrainingtracker.trainingtracker.tracker.TrackerService
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests verifying REQ-PER-014 / REQ-PER-016 / REQ-PER-056 / REQ-STB-020.3:
 * 1. WorkoutSummariesDatabaseManager.getWorkoutsInRangeCursor enforces FINISHED = 1 gating.
 * 2. TrackerService intent contracts for tracking finalization.
 * 3. PeriodsViewModel.loadPeriods triggers WorkoutRepository.loadAllWorkouts() for reactive projection.
 * 4. PeriodProjectionKey suppresses non-period edits while detecting period-relevant edits.
 * 5. PeriodsRepository holds zero BroadcastReceiver / SQLite DB manager fields.
 * 6. 3,600 1-Hz unfinished workout ticks alongside 2,200 finished workouts trigger 0 period recomputations,
 *    followed by immediate inclusion when finished flips to true.
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

        mockContext = mockk(relaxed = true)
        mockDb = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        WorkoutRepository.resetForTesting(null)
        WorkoutSummariesDatabaseManager.setInstanceForTesting(null)
        PeriodsRepository.resetInstanceForTesting()
        unmockkAll()
    }

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
            override val database: SQLiteDatabase get() = mockDb
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

    @Test
    fun testTrackingFinishedIntent_contractConstants() {
        assertEquals(
            "de.rainerblind.trainingtracker.TrackerService.TRACKING_FINISHED_INTENT",
            TrackerService.TRACKING_FINISHED_INTENT
        )
        assertEquals("WORKOUT_ID", TrackerService.WORKOUT_ID)
    }

    @Test
    fun testPeriodsViewModel_loadPeriods_triggersWorkoutLoad() = runTest {
        val mockApp = mockk<Application>(relaxed = true)
        val mockWorkoutRepo = mockk<WorkoutRepository>(relaxed = true)
        val mockPeriodsRepo = mockk<PeriodsRepository>(relaxed = true)

        every { mockWorkoutRepo.allWorkouts } returns MutableStateFlow(emptyList())
        every { mockPeriodsRepo.groupedPeriods } returns MutableStateFlow(emptyList())
        every { mockPeriodsRepo.migrationStatus } returns MutableStateFlow(null)

        WorkoutRepository.resetForTesting(mockWorkoutRepo)
        PeriodsRepository.setInstanceForTesting(mockPeriodsRepo)

        val testDispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = PeriodsViewModel(mockApp, testDispatcher, testDispatcher)
        viewModel.loadPeriods()

        coVerify(timeout = 2000) { mockWorkoutRepo.loadAllWorkouts() }
    }

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

        assertEquals(
            "Non-period property changes must produce identical PeriodProjectionKey",
            base.toPeriodProjectionKey(),
            nonPeriodModified.toPeriodProjectionKey()
        )

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
     * TST-PER-022.2 & TST-PER-056.1: Zero BroadcastReceiver and zero SQLite/DatabaseManager/Mapper fields.
     */
    @Test
    fun testPeriodsRepository_hasZeroReceiverOrDatabaseManagerFields() {
        val mockApp = mockk<Application>(relaxed = true)
        val mockWorkoutRepo = mockk<WorkoutRepository>(relaxed = true)
        every { mockWorkoutRepo.allWorkouts } returns MutableStateFlow(emptyList())
        WorkoutRepository.resetForTesting(mockWorkoutRepo)

        val repo = PeriodsRepository(mockApp, Dispatchers.Unconfined, mockWorkoutRepo)
        val fieldNames = repo.javaClass.declaredFields.map { it.name }
        assertFalse("trackingFinishedReceiver must be absent", fieldNames.contains("trackingFinishedReceiver"))
        assertFalse("dbManager must be absent after PeriodSummaries.db removal", fieldNames.contains("dbManager"))
        assertFalse("workoutSummariesManager must be absent", fieldNames.contains("workoutSummariesManager"))
        assertFalse("mapper must be absent", fieldNames.contains("mapper"))
        assertFalse("batchMetadataLoader must be absent", fieldNames.contains("batchMetadataLoader"))
    }

    /**
     * TST-STB-020.2 & TST-STB-020.4 & TST-PER-056.3 / REQ-STB-020.3:
     * Replays 3,600 1-Hz unfinished workout updates alongside 2,200 historical finished workouts,
     * asserting 0 period recomputations and exclusion from groupedPeriods, followed by immediate
     * inclusion once the active workout flips finished = true.
     */
    @Test
    fun testPeriodsRepository_unfinishedLiveWorkout3600Ticks_triggersZeroRebuilds_TST_STB_020_2_and_4() {
        val mockApp = mockk<Application>(relaxed = true)
        val mockWorkoutRepo = mockk<WorkoutRepository>(relaxed = true)

        val finishedWorkouts = (1L..2200L).map { id ->
            createSampleWorkoutData(
                id = id,
                finished = true,
                activeTimeSec = if (id == 1L) 5000L else 3600L
            )
        }
        val activeWorkoutId = 99_999L
        val activeUnfinishedHuge = createSampleWorkoutData(
            id = activeWorkoutId,
            finished = false,
            activeTimeSec = 999_999L,
            totalDistance = 500.0
        )

        val workoutsFlow = MutableStateFlow(finishedWorkouts + activeUnfinishedHuge)
        every { mockWorkoutRepo.allWorkouts } returns workoutsFlow

        val repo = spyk(
            PeriodsRepository(
                application = mockApp,
                defaultDispatcher = Dispatchers.Unconfined,
                workoutRepo = mockWorkoutRepo
            )
        )

        // Initial projection completed with strictly the 2,200 finished workouts
        val settledMonth = repo.groupedPeriods.value[2].firstOrNull()
        assertTrue("Initial monthly PeriodSummary must be projected", settledMonth != null)
        assertFalse(
            "Unfinished live workout must be excluded from period projection",
            settledMonth!!.workoutIdToSportMap.containsKey(activeWorkoutId)
        )
        assertEquals(2200, settledMonth.totalWorkouts)
        assertEquals(2200, settledMonth.workoutIdToSportMap.size)

        // Capture baseline reference identity to verify zero rebuild emissions across 3,600 live ticks
        val baselineRef = repo.groupedPeriods.value

        for (sec in 1..3600) {
            workoutsFlow.value = finishedWorkouts + activeUnfinishedHuge.copy(
                activeTimeSec = sec.toLong(),
                totalDistance = sec * 8.0,
                ascentMeters = sec / 10L,
                mapPolyline = "poly_$sec"
            )
        }

        // Exact same reference emitted -> zero recomputations occurred during all 3,600 1-Hz ticks
        assertTrue("3,600 unfinished ticks must trigger zero groupedPeriods recomputations", repo.groupedPeriods.value === baselineRef)
        verify(exactly = 0) { repo.buildGroupedPeriods(any(), any(), any()) }

        // Finishing the active workout (finished: false -> true) immediately projects the 2,201st workout
        workoutsFlow.value = finishedWorkouts + activeUnfinishedHuge.copy(
            finished = true,
            activeTimeSec = 999_999L,
            totalDistance = 42_000.0
        )
        val updatedMonth = repo.groupedPeriods.value[2].firstOrNull()
        assertTrue(updatedMonth!!.workoutIdToSportMap.containsKey(activeWorkoutId))
        assertEquals(2201, updatedMonth.totalWorkouts)
        assertEquals(2201, updatedMonth.workoutIdToSportMap.size)
        assertEquals(activeWorkoutId, updatedMonth.longestId)
    }
}
