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
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.PolyUtil
import io.mockk.*
import kotlinx.coroutines.CoroutineDispatcher
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

    private fun createViewModel(
        app: Application = mockApplication,
        ioDispatcher: CoroutineDispatcher = testDispatcher,
        defaultDispatcher: CoroutineDispatcher = testDispatcher
    ): PeriodsViewModel = PeriodsViewModel(app, ioDispatcher, defaultDispatcher)

    private fun createPeriodSummary(
        label: String = "Week 42, 2026",
        sportStats: Map<BSportType, SportStats>,
        workoutIdToSportMap: Map<Long, BSportType> = emptyMap(),
        workoutIdToAllPolylinesMap: Map<Long, String> = emptyMap(),
        workoutIdToPolylineMap: Map<Long, String> = emptyMap(),
        workoutIdToPathMap: Map<Long, List<LatLng>> = emptyMap(),
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
            workoutIdToPathMap = workoutIdToPathMap,
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
        val viewModel = createViewModel()

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
        val viewModel = createViewModel()

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
        val viewModel = createViewModel()

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

        val viewModel = createViewModel()
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

        val viewModel = createViewModel()
        val summary = createPeriodSummary("Test Period", emptyMap())

        viewModel.showPeriodMap(summary, initialRegionId = "region_custom_42")
        // Immediate loading state has selectedRegionId set
        assertEquals("region_custom_42", viewModel.mapState.value.selectedRegionId)

        testScheduler.advanceUntilIdle()
        // After background processing finishes
        assertEquals("region_custom_42", viewModel.mapState.value.selectedRegionId)
    }

    /**
     * TST-PER-031.4: Master Metric Selector Hoisting & Reactivity Test (REQ-PER-025.2).
     */
    @Test
    fun testSelectedMetric_defaultAndHoisting() {
        val viewModel = createViewModel()
        assertEquals(PeriodBarMetric.DURATION, viewModel.selectedMetric.value)

        viewModel.selectMetric(PeriodBarMetric.DISTANCE)
        assertEquals(PeriodBarMetric.DISTANCE, viewModel.selectedMetric.value)

        viewModel.selectMetric(PeriodBarMetric.ASCENT)
        assertEquals(PeriodBarMetric.ASCENT, viewModel.selectedMetric.value)
    }

    /**
     * TST-PER-031.4: Filter Yearly Sport Contributions by Selected Sports Test (REQ-PER-025.2, REQ-PER-025.4).
     */
    @Test
    fun testFilterPeriodSummary_filtersYearlySportContributions() {
        val viewModel = createViewModel()

        val bikeContribution = YearlySportContribution(sportType = BSportType.BIKE, year = 2026)
        val runContribution = YearlySportContribution(sportType = BSportType.RUN, year = 2026)
        val otherContribution = YearlySportContribution(sportType = BSportType.UNKNOWN, year = 2026)

        val bikeStats = SportStats(count = 1, totalDurationSec = 3600L, totalDistanceMeters = 20000.0, totalAscentMeters = 200L, detailedSportStats = emptyMap(), longestWorkout = null)
        val runStats = SportStats(count = 1, totalDurationSec = 1800L, totalDistanceMeters = 5000.0, totalAscentMeters = 50L, detailedSportStats = emptyMap(), longestWorkout = null)
        val otherStats = SportStats(count = 1, totalDurationSec = 2700L, totalDistanceMeters = 0.0, totalAscentMeters = 0L, detailedSportStats = emptyMap(), longestWorkout = null)

        val base = createPeriodSummary(
            label = "Year 2026",
            sportStats = mapOf(
                BSportType.BIKE to bikeStats,
                BSportType.RUN to runStats,
                BSportType.UNKNOWN to otherStats
            )
        ).copy(
            periodType = PeriodType.YEAR,
            yearlySportContributions = mapOf(
                BSportType.BIKE to bikeContribution,
                BSportType.RUN to runContribution,
                BSportType.UNKNOWN to otherContribution
            )
        )

        // Filter for BIKE only
        val bikeOnly = viewModel.filterPeriodSummary(base, setOf(BSportType.BIKE))
        assertNotNull(bikeOnly)
        assertEquals(setOf(BSportType.BIKE), bikeOnly!!.yearlySportContributions.keys)

        // Filter for BIKE and RUN
        val bikeAndRun = viewModel.filterPeriodSummary(base, setOf(BSportType.BIKE, BSportType.RUN))
        assertNotNull(bikeAndRun)
        assertEquals(setOf(BSportType.BIKE, BSportType.RUN), bikeAndRun!!.yearlySportContributions.keys)

        // Filter for UNKNOWN only
        val otherOnly = viewModel.filterPeriodSummary(base, setOf(BSportType.UNKNOWN))
        assertNotNull(otherOnly)
        assertEquals(setOf(BSportType.UNKNOWN), otherOnly!!.yearlySportContributions.keys)
    }

    /**
     * TST-PER-035.4: Filter Daily Sport Contributions by Selected Sports Test (REQ-PER-027.2, ATT-5195).
     */
    @Test
    fun testFilterPeriodSummary_filtersDailySportContributions_TST_PER_035_4() {
        val viewModel = createViewModel()

        val bikeContribution = DailySportContribution(sportType = BSportType.BIKE)
        val runContribution = DailySportContribution(sportType = BSportType.RUN)
        val otherContribution = DailySportContribution(sportType = BSportType.UNKNOWN)

        val bikeStats = SportStats(count = 1, totalDurationSec = 3600L, totalDistanceMeters = 20000.0, totalAscentMeters = 200L, detailedSportStats = emptyMap(), longestWorkout = null)
        val runStats = SportStats(count = 1, totalDurationSec = 1800L, totalDistanceMeters = 5000.0, totalAscentMeters = 50L, detailedSportStats = emptyMap(), longestWorkout = null)
        val otherStats = SportStats(count = 1, totalDurationSec = 2700L, totalDistanceMeters = 0.0, totalAscentMeters = 0L, detailedSportStats = emptyMap(), longestWorkout = null)

        val base = createPeriodSummary(
            label = "May 2026",
            sportStats = mapOf(
                BSportType.BIKE to bikeStats,
                BSportType.RUN to runStats,
                BSportType.UNKNOWN to otherStats
            )
        ).copy(
            periodType = PeriodType.MONTH,
            dailySportContributions = mapOf(
                BSportType.BIKE to bikeContribution,
                BSportType.RUN to runContribution,
                BSportType.UNKNOWN to otherContribution
            )
        )

        // Filter for BIKE only
        val bikeOnly = viewModel.filterPeriodSummary(base, setOf(BSportType.BIKE))
        assertNotNull(bikeOnly)
        assertEquals(setOf(BSportType.BIKE), bikeOnly!!.dailySportContributions.keys)

        // Filter for BIKE and RUN
        val bikeAndRun = viewModel.filterPeriodSummary(base, setOf(BSportType.BIKE, BSportType.RUN))
        assertNotNull(bikeAndRun)
        assertEquals(setOf(BSportType.BIKE, BSportType.RUN), bikeAndRun!!.dailySportContributions.keys)

        // Filter for UNKNOWN only
        val otherOnly = viewModel.filterPeriodSummary(base, setOf(BSportType.UNKNOWN))
        assertNotNull(otherOnly)
        assertEquals(setOf(BSportType.UNKNOWN), otherOnly!!.dailySportContributions.keys)
    }

    /**
     * TST-PER-032.1: Symmetric DTO Polyline Sport Filtering Unit Test (ATT-4935 / REQ-PER-024.5b).
     * Asserts that filterPeriodSummary symmetrically filters workoutIdToAllPolylinesMap, workoutIdToPathMap,
     * workoutIdToPolylineMap, and polylines strictly according to selectedSports.
     */
    @Test
    fun testSymmetricDtoPolylineSportFiltering_TST_PER_032_1() {
        val viewModel = createViewModel()

        val bikeStats = SportStats(count = 1, totalDurationSec = 3600L, totalDistanceMeters = 25000.0, totalAscentMeters = 300L, detailedSportStats = emptyMap(), longestWorkout = null)
        val runStats = SportStats(count = 1, totalDurationSec = 1800L, totalDistanceMeters = 5000.0, totalAscentMeters = 50L, detailedSportStats = emptyMap(), longestWorkout = null)

        val bikePoly = PolyUtil.encode(listOf(LatLng(48.50, 9.00), LatLng(48.51, 9.01)))
        val runPoly = PolyUtil.encode(listOf(LatLng(48.52, 9.02), LatLng(48.53, 9.03)))

        val summary = createPeriodSummary(
            label = "Week 44, 2026",
            sportStats = mapOf(BSportType.BIKE to bikeStats, BSportType.RUN to runStats),
            workoutIdToSportMap = mapOf(1L to BSportType.BIKE, 2L to BSportType.RUN),
            workoutIdToAllPolylinesMap = mapOf(1L to bikePoly, 2L to runPoly),
            workoutIdToPolylineMap = mapOf(1L to bikePoly, 2L to runPoly),
            workoutIdToPathMap = mapOf(
                1L to listOf(LatLng(48.50, 9.00), LatLng(48.51, 9.01)),
                2L to listOf(LatLng(48.52, 9.02), LatLng(48.53, 9.03))
            ),
            polylines = listOf(bikePoly, runPoly)
        )

        // Filter for BIKE only
        val bikeSummary = viewModel.filterPeriodSummary(summary, setOf(BSportType.BIKE))
        assertNotNull(bikeSummary)
        assertEquals(1, bikeSummary!!.totalWorkouts)
        assertEquals(setOf(1L), bikeSummary.workoutIdToAllPolylinesMap.keys)
        assertEquals(setOf(1L), bikeSummary.workoutIdToPolylineMap.keys)
        assertEquals(setOf(1L), bikeSummary.workoutIdToPathMap.keys)
        assertEquals(setOf(1L), bikeSummary.workoutIdToSportMap.keys)
        assertEquals(listOf(bikePoly), bikeSummary.polylines)

        // Filter for RUN only
        val runSummary = viewModel.filterPeriodSummary(summary, setOf(BSportType.RUN))
        assertNotNull(runSummary)
        assertEquals(1, runSummary!!.totalWorkouts)
        assertEquals(setOf(2L), runSummary.workoutIdToAllPolylinesMap.keys)
        assertEquals(setOf(2L), runSummary.workoutIdToPolylineMap.keys)
        assertEquals(setOf(2L), runSummary.workoutIdToPathMap.keys)
        assertEquals(setOf(2L), runSummary.workoutIdToSportMap.keys)
        assertEquals(listOf(runPoly), runSummary.polylines)
    }

    /**
     * TST-PER-032.2: PeriodMapScreen Sport-Filtered Track & Heatmap Decoupling Test (ATT-4935 / REQ-PER-024.5c).
     * Asserts that showPeriodMap filters loaded workouts by selectedSports before populating heatmapPathMap and tracks.
     */
    @Test
    fun testShowPeriodMap_sportFilteredWorkoutsAndHeatmapPaths_TST_PER_032_2() = runTest {
        val bikeWorkout = mockk<WorkoutData>(relaxed = true) {
            every { id } returns 101L
            every { workoutName } returns "Bike Route"
            every { bSportType } returns BSportType.BIKE
            every { mapPolyline } returns PolyUtil.encode(listOf(LatLng(48.50, 9.00), LatLng(48.51, 9.01)))
            every { startLatLng } returns LatLng(48.50, 9.00)
            every { endLatLng } returns LatLng(48.51, 9.01)
            every { minLat } returns 48.50
            every { maxLat } returns 48.51
            every { minLng } returns 9.00
            every { maxLng } returns 9.01
        }
        val runWorkout = mockk<WorkoutData>(relaxed = true) {
            every { id } returns 102L
            every { workoutName } returns "Run Route"
            every { bSportType } returns BSportType.RUN
            every { mapPolyline } returns PolyUtil.encode(listOf(LatLng(48.52, 9.02), LatLng(48.53, 9.03)))
            every { startLatLng } returns LatLng(48.52, 9.02)
            every { endLatLng } returns LatLng(48.53, 9.03)
            every { minLat } returns 48.52
            every { maxLat } returns 48.53
            every { minLng } returns 9.02
            every { maxLng } returns 9.03
        }

        coEvery { mockPeriodsRepo.getWorkoutsForRange(any(), any()) } returns listOf(bikeWorkout, runWorkout)

        val viewModel = createViewModel()
        // Deselect RUN -> only BIKE is active
        viewModel.toggleSportFilter(BSportType.RUN)
        assertEquals(setOf(BSportType.BIKE), viewModel.selectedSports.value)

        val summary = createPeriodSummary(
            label = "Week 44, 2026",
            sportStats = mapOf(
                BSportType.BIKE to SportStats(1, 3600L, 20000.0, 200L, emptyMap(), null),
                BSportType.RUN to SportStats(1, 1800L, 5000.0, 50L, emptyMap(), null)
            ),
            workoutIdToSportMap = mapOf(101L to BSportType.BIKE, 102L to BSportType.RUN)
        )

        viewModel.showPeriodMap(summary)
        testScheduler.advanceUntilIdle()

        val state = viewModel.mapState.value
        assertFalse(state.isLoading)
        // Heatmap path map and tracks must contain ONLY the bike workout (101L)
        assertEquals(setOf(101L), state.workoutIdToHeatmapPathMap.keys)
        assertEquals(1, state.tracks.size)
        assertEquals(101L, state.tracks.first().id)
        assertEquals(BSportType.BIKE, state.tracks.first().bSportType)
    }

    /**
     * TST-PER-032.3: Spatial Region Clustering Sport Sensitivity & Workout Count Accuracy Test (ATT-4935 / REQ-PER-024.5d).
     * Asserts that region detection and region chips reflect only the sport-filtered workout distribution.
     */
    @Test
    fun testSpatialRegionClustering_sportSensitivityAndAccuracy_TST_PER_032_3() = runTest {
        // Stuttgart Bike Workout
        val stuttgartBike = mockk<WorkoutData>(relaxed = true) {
            every { id } returns 201L
            every { workoutName } returns "Stuttgart Ride"
            every { bSportType } returns BSportType.BIKE
            every { mapPolyline } returns PolyUtil.encode(listOf(LatLng(48.78, 9.18), LatLng(48.79, 9.19)))
            every { startLatLng } returns LatLng(48.78, 9.18)
            every { endLatLng } returns LatLng(48.79, 9.19)
            every { minLat } returns 48.78
            every { maxLat } returns 48.79
            every { minLng } returns 9.18
            every { maxLng } returns 9.19
        }
        // Berlin Run Workout (far away in distinct region)
        val berlinRun = mockk<WorkoutData>(relaxed = true) {
            every { id } returns 202L
            every { workoutName } returns "Berlin Run"
            every { bSportType } returns BSportType.RUN
            every { mapPolyline } returns PolyUtil.encode(listOf(LatLng(52.52, 13.40), LatLng(52.53, 13.41)))
            every { startLatLng } returns LatLng(52.52, 13.40)
            every { endLatLng } returns LatLng(52.53, 13.41)
            every { minLat } returns 52.52
            every { maxLat } returns 52.53
            every { minLng } returns 13.40
            every { maxLng } returns 13.41
        }

        coEvery { mockPeriodsRepo.getWorkoutsForRange(any(), any()) } returns listOf(stuttgartBike, berlinRun)

        val viewModel = createViewModel()

        // 1. When filtered to BIKE only: only 1 region (Stuttgart) detected, Berlin excluded
        viewModel.toggleSportFilter(BSportType.RUN)
        val summary = createPeriodSummary(
            label = "Month 11, 2026",
            sportStats = mapOf(
                BSportType.BIKE to SportStats(1, 3600L, 20000.0, 200L, emptyMap(), null),
                BSportType.RUN to SportStats(1, 1800L, 5000.0, 50L, emptyMap(), null)
            ),
            workoutIdToSportMap = mapOf(201L to BSportType.BIKE, 202L to BSportType.RUN)
        )

        viewModel.showPeriodMap(summary)
        testScheduler.advanceUntilIdle()

        val bikeState = viewModel.mapState.value
        assertEquals(1, bikeState.regions.size)
        assertEquals(1, bikeState.regions.first().workoutCount)
        assertEquals(48.78, bikeState.regions.first().bounds.southwest.latitude, 0.05)

        // 2. When both BIKE and RUN are active: 2 regions detected (>500km apart)
        viewModel.toggleSportFilter(BSportType.RUN)
        viewModel.showPeriodMap(summary)
        testScheduler.advanceUntilIdle()

        val bothState = viewModel.mapState.value
        assertEquals(2, bothState.regions.size)
    }
}
