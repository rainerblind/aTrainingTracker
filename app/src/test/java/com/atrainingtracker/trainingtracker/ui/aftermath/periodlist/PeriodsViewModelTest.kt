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
import android.util.Log
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.MyPreferenceManager
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository
import com.google.android.gms.maps.model.LatLng
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for PeriodsViewModel sport type filtering and multi-sport projections.
 * Verifies REQ-PER-022 and TST-PER-028 (ATT-4770).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PeriodsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var mockApplication: Application
    private lateinit var mockWorkoutRepo: WorkoutRepository
    private lateinit var mockPeriodsRepo: PeriodsRepository
    private lateinit var mockPrefManager: MyPreferenceManager

    private val fakeGroupedPeriods = MutableStateFlow<List<List<PeriodSummary>>>(emptyList())

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.w(any(), any<String>(), any()) } returns 0
        every { Log.e(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>(), any()) } returns 0

        mockApplication = mockk(relaxed = true)
        mockWorkoutRepo = mockk(relaxed = true)
        mockPeriodsRepo = mockk(relaxed = true)
        mockPrefManager = mockk(relaxed = true)

        every { mockPeriodsRepo.scope } returns kotlinx.coroutines.CoroutineScope(Dispatchers.Unconfined)

        every { mockWorkoutRepo.allWorkouts } returns MutableStateFlow(emptyList())
        every { mockPeriodsRepo.groupedPeriods } returns fakeGroupedPeriods
        every { mockPeriodsRepo.migrationStatus } returns MutableStateFlow(null)
        every { mockPrefManager.enabledPeriodMarkerTypesFlow } returns MutableStateFlow(emptySet())

        WorkoutRepository.resetForTesting(mockWorkoutRepo)
        PeriodsRepository.setInstanceForTesting(mockPeriodsRepo)

        mockkConstructor(MyPreferenceManager::class)
        every { anyConstructed<MyPreferenceManager>().enabledPeriodMarkerTypesFlow } returns MutableStateFlow(emptySet())
    }

    @After
    fun tearDown() {
        WorkoutRepository.resetForTesting(null)
        PeriodsRepository.setInstanceForTesting(null)
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun createPeriodSummary(
        label: String = "Week 42, 2026",
        sportStats: Map<BSportType, SportStats>,
        workoutIdToSportMap: Map<Long, BSportType> = emptyMap(),
        workoutIdToAllPolylinesMap: Map<Long, String> = emptyMap(),
        workoutIdToPolylineMap: Map<Long, String> = emptyMap(),
        polylines: List<String> = emptyList(),
        extremaMarkers: List<PeriodPeakMarker> = emptyList()
    ): PeriodSummary {
        val totalWorkouts = sportStats.values.sumOf { it.count }
        val totalDurationSec = sportStats.values.sumOf { it.totalDurationSec }
        val totalDistance = sportStats.values.sumOf { it.totalDistanceMeters }

        return PeriodSummary(
            periodLabel = label,
            periodDateRange = "Oct 19 - Oct 25",
            periodType = PeriodType.WEEK,
            startTimestampS = 1792454400L,
            endTimestampS = 1793059199L,
            totalWorkouts = totalWorkouts,
            totalDurationSec = totalDurationSec,
            totalDistance = totalDistance,
            sportStats = sportStats,
            workoutIdToSportMap = workoutIdToSportMap,
            workoutIdToAllPolylinesMap = workoutIdToAllPolylinesMap,
            workoutIdToPolylineMap = workoutIdToPolylineMap,
            polylines = polylines,
            extremaMarkers = extremaMarkers,
            sortKey = label
        )
    }

    /**
     * TST-PER-028.1: Default Filter Set & Empty Guard Test.
     * Verifies initial selection [Bike, Run], toggling sports, and empty set prevention.
     */
    @Test
    fun testDefaultFilterSet_andEmptyGuard() {
        val viewModel = PeriodsViewModel(mockApplication)

        // 1. Initial default is [BIKE, RUN]
        assertEquals(setOf(BSportType.BIKE, BSportType.RUN), viewModel.selectedSports.value)

        // 2. Toggle RUN off -> becomes [BIKE]
        viewModel.toggleSportFilter(BSportType.RUN)
        assertEquals(setOf(BSportType.BIKE), viewModel.selectedSports.value)

        // 3. Toggle BIKE off when it's the last remaining sport -> Guard prevents empty set
        viewModel.toggleSportFilter(BSportType.BIKE)
        assertEquals(setOf(BSportType.BIKE), viewModel.selectedSports.value)

        // 4. Toggle UNKNOWN on -> becomes [BIKE, UNKNOWN]
        viewModel.toggleSportFilter(BSportType.UNKNOWN)
        assertEquals(setOf(BSportType.BIKE, BSportType.UNKNOWN), viewModel.selectedSports.value)

        // 5. Toggle BIKE off -> becomes [UNKNOWN]
        viewModel.toggleSportFilter(BSportType.BIKE)
        assertEquals(setOf(BSportType.UNKNOWN), viewModel.selectedSports.value)

        // 6. Toggle UNKNOWN off when it's the last remaining sport -> Guard prevents empty set
        viewModel.toggleSportFilter(BSportType.UNKNOWN)
        assertEquals(setOf(BSportType.UNKNOWN), viewModel.selectedSports.value)

        // 7. Toggle RUN and BIKE back on
        viewModel.toggleSportFilter(BSportType.RUN)
        viewModel.toggleSportFilter(BSportType.BIKE)
        assertEquals(setOf(BSportType.UNKNOWN, BSportType.RUN, BSportType.BIKE), viewModel.selectedSports.value)
    }

    /**
     * TST-PER-028.2: Reactive In-Memory Period Projection Test.
     * Verifies recalculation of totalWorkouts, duration, distance, and omission of 0-workout periods.
     */
    @Test
    fun testFilterPeriodSummary_recalculatesTotals_andOmitsZeroWorkouts() {
        val viewModel = PeriodsViewModel(mockApplication)

        val bikeStats = SportStats(count = 2, totalDurationSec = 3600L, totalDistanceMeters = 20000.0, totalAscentMeters = 200L, detailedSportStats = emptyMap(), longestWorkout = null)
        val runStats = SportStats(count = 1, totalDurationSec = 1800L, totalDistanceMeters = 5000.0, totalAscentMeters = 50L, detailedSportStats = emptyMap(), longestWorkout = null)
        val otherStats = SportStats(count = 1, totalDurationSec = 2700L, totalDistanceMeters = 0.0, totalAscentMeters = 0L, detailedSportStats = emptyMap(), longestWorkout = null)

        val mixedPeriod = createPeriodSummary(
            label = "Mixed Period",
            sportStats = mapOf(
                BSportType.BIKE to bikeStats,
                BSportType.RUN to runStats,
                BSportType.UNKNOWN to otherStats
            )
        )

        // Filter: [BIKE, RUN] (Default)
        val bikeAndRunFiltered = viewModel.filterPeriodSummary(mixedPeriod, setOf(BSportType.BIKE, BSportType.RUN))
        assertNotNull(bikeAndRunFiltered)
        assertEquals(3, bikeAndRunFiltered!!.totalWorkouts)
        assertEquals(5400L, bikeAndRunFiltered.totalDurationSec)
        assertEquals(25000.0, bikeAndRunFiltered.totalDistance, 0.001)
        assertEquals(setOf(BSportType.BIKE, BSportType.RUN), bikeAndRunFiltered.sportStats.keys)

        // Filter: [BIKE] only
        val bikeOnlyFiltered = viewModel.filterPeriodSummary(mixedPeriod, setOf(BSportType.BIKE))
        assertNotNull(bikeOnlyFiltered)
        assertEquals(2, bikeOnlyFiltered!!.totalWorkouts)
        assertEquals(3600L, bikeOnlyFiltered.totalDurationSec)
        assertEquals(20000.0, bikeOnlyFiltered.totalDistance, 0.001)
        assertEquals(setOf(BSportType.BIKE), bikeOnlyFiltered.sportStats.keys)

        // Period with only UNKNOWN workouts
        val otherOnlyPeriod = createPeriodSummary(
            label = "Other Period",
            sportStats = mapOf(BSportType.UNKNOWN to otherStats)
        )

        // Under [BIKE, RUN]: should be omitted (return null)
        val omittedPeriod = viewModel.filterPeriodSummary(otherOnlyPeriod, setOf(BSportType.BIKE, BSportType.RUN))
        assertNull("Periods with 0 filtered workouts must be omitted (null)", omittedPeriod)

        // Under [UNKNOWN]: should be retained
        val retainedPeriod = viewModel.filterPeriodSummary(otherOnlyPeriod, setOf(BSportType.UNKNOWN))
        assertNotNull(retainedPeriod)
        assertEquals(1, retainedPeriod!!.totalWorkouts)
        assertEquals(2700L, retainedPeriod.totalDurationSec)
    }

    /**
     * TST-PER-028.3: Map Preview & Polylines Filtering Test.
     * Verifies polylines, workoutIdToPolylineMap, and extremaMarkers filtering by sport type.
     */
    @Test
    fun testFilterPeriodSummary_filtersPolylinesAndExtremaMarkers() {
        val viewModel = PeriodsViewModel(mockApplication)

        val bikeStats = SportStats(count = 1, totalDurationSec = 3600L, totalDistanceMeters = 20000.0, totalAscentMeters = 200L, detailedSportStats = emptyMap(), longestWorkout = null)
        val runStats = SportStats(count = 1, totalDurationSec = 1800L, totalDistanceMeters = 5000.0, totalAscentMeters = 50L, detailedSportStats = emptyMap(), longestWorkout = null)
        val otherStats = SportStats(count = 1, totalDurationSec = 1200L, totalDistanceMeters = 0.0, totalAscentMeters = 0L, detailedSportStats = emptyMap(), longestWorkout = null)

        val markerBike = PeriodPeakMarker(workoutId = 101L, pos = LatLng(48.0, 11.0), iconResId = 0, title = "Bike Peak", markerType = PeriodMarkerType.ALTITUDE)
        val markerRun = PeriodPeakMarker(workoutId = 102L, pos = LatLng(48.1, 11.1), iconResId = 0, title = "Run Start", markerType = PeriodMarkerType.START)
        val markerOther = PeriodPeakMarker(workoutId = 103L, pos = LatLng(48.2, 11.2), iconResId = 0, title = "Other Apex", markerType = PeriodMarkerType.DISTANCE)

        val period = createPeriodSummary(
            sportStats = mapOf(
                BSportType.BIKE to bikeStats,
                BSportType.RUN to runStats,
                BSportType.UNKNOWN to otherStats
            ),
            workoutIdToSportMap = mapOf(
                101L to BSportType.BIKE,
                102L to BSportType.RUN,
                103L to BSportType.UNKNOWN
            ),
            workoutIdToAllPolylinesMap = mapOf(
                101L to "_p~iF~ps|U_ulLnnqC",
                102L to "_c~iF~ps|U_ulLnnqC",
                103L to "_q~iF~ps|U_ulLnnqC"
            ),
            extremaMarkers = listOf(markerBike, markerRun, markerOther)
        )

        // Filter: [BIKE]
        val bikeOnly = viewModel.filterPeriodSummary(period, setOf(BSportType.BIKE))!!
        assertEquals(listOf("_p~iF~ps|U_ulLnnqC"), bikeOnly.polylines)
        assertEquals(mapOf(101L to "_p~iF~ps|U_ulLnnqC"), bikeOnly.workoutIdToPolylineMap)
        assertEquals(listOf(markerBike), bikeOnly.extremaMarkers)

        // Filter: [RUN]
        val runOnly = viewModel.filterPeriodSummary(period, setOf(BSportType.RUN))!!
        assertEquals(listOf("_c~iF~ps|U_ulLnnqC"), runOnly.polylines)
        assertEquals(mapOf(102L to "_c~iF~ps|U_ulLnnqC"), runOnly.workoutIdToPolylineMap)
        assertEquals(listOf(markerRun), runOnly.extremaMarkers)

        // Filter: [BIKE, RUN]
        val bikeAndRun = viewModel.filterPeriodSummary(period, setOf(BSportType.BIKE, BSportType.RUN))!!
        assertEquals(2, bikeAndRun.polylines.size)
        assertTrue(bikeAndRun.polylines.contains("_p~iF~ps|U_ulLnnqC"))
        assertTrue(bikeAndRun.polylines.contains("_c~iF~ps|U_ulLnnqC"))
        assertEquals(setOf(101L, 102L), bikeAndRun.workoutIdToPolylineMap.keys)
        assertEquals(listOf(markerBike, markerRun), bikeAndRun.extremaMarkers)
    }

    /**
     * TST-PER-028.4: Filtered Grouped Periods Reactive Flow Test.
     * Verifies that filteredGroupedPeriods flow emits correctly transformed period hierarchies.
     */
    @Test
    fun testFilteredGroupedPeriods_emitsTransformedHierarchy() = runTest {
        val bikeStats = SportStats(count = 1, totalDurationSec = 3600L, totalDistanceMeters = 20000.0, totalAscentMeters = 200L, detailedSportStats = emptyMap(), longestWorkout = null)
        val otherStats = SportStats(count = 1, totalDurationSec = 1800L, totalDistanceMeters = 0.0, totalAscentMeters = 0L, detailedSportStats = emptyMap(), longestWorkout = null)

        val p1 = createPeriodSummary("Period 1", mapOf(BSportType.BIKE to bikeStats))
        val p2 = createPeriodSummary("Period 2", mapOf(BSportType.UNKNOWN to otherStats))

        fakeGroupedPeriods.value = listOf(
            listOf(p1, p2), // Days
            emptyList(),    // Weeks
            emptyList(),    // Months
            emptyList()     // Years
        )

        val viewModel = PeriodsViewModel(mockApplication)
        testScheduler.advanceUntilIdle()

        // Default [BIKE, RUN]: p1 retained, p2 omitted from Days
        val daysFiltered = viewModel.filteredGroupedPeriods.value[0]
        assertEquals(1, daysFiltered.size)
        assertEquals("Period 1", daysFiltered[0].periodLabel)

        // Toggle UNKNOWN on: both p1 and p2 retained
        viewModel.toggleSportFilter(BSportType.UNKNOWN)
        testScheduler.advanceUntilIdle()

        val daysWithUnknown = viewModel.filteredGroupedPeriods.value[0]
        assertEquals(2, daysWithUnknown.size)
    }

    /**
     * TST-PER-030.5: Region State Propagation Test (REQ-PER-024.2).
     */
    @Test
    fun testShowPeriodMap_withInitialRegionId_setsSelectedRegionId() = runTest {
        coEvery { mockPeriodsRepo.getWorkoutsForRange(any(), any()) } returns emptyList()

        val viewModel = PeriodsViewModel(mockApplication)
        val summary = createPeriodSummary("Test Period", emptyMap())

        viewModel.showPeriodMap(summary, initialRegionId = "region_custom_42")
        // Immediate loading state has selectedRegionId set
        assertEquals("region_custom_42", viewModel.mapState.value.selectedRegionId)

        testScheduler.advanceUntilIdle()
        // After background processing finishes
        assertEquals("region_custom_42", viewModel.mapState.value.selectedRegionId)
    }
}
