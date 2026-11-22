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

package com.atrainingtracker.trainingtracker.ui.aftermath.periodlist

import android.app.Application
import android.graphics.Color
import android.util.Log
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.MyPreferenceManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.location.RegionNameResolver
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.PolyUtil
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import java.time.LocalDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Behavioral unit tests for authoritative full-period primary region selection, sport-aware
 * preview curation, indoor isolation, cache/geocoding parity, and `REQ-PER-039` memory bounds
 * (REQ-PER-046, TST-PER-054, ATT-8664).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PeriodPrimaryRegionSelectionTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var mockApp: Application
    private lateinit var repo: PeriodsRepository
    private lateinit var viewModel: PeriodsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        mockkStatic(Color::class)
        every { Color.argb(any<Int>(), any<Int>(), any<Int>(), any<Int>()) } returns 0

        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.w(any(), any<String>(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any<String>(), any()) } returns 0

        val mockDbManager = mockk<PeriodSummariesDatabaseManager>(relaxed = true)
        val mockSummariesManager = mockk<WorkoutSummariesDatabaseManager>(relaxed = true)
        val mockWorkoutRepo = mockk<WorkoutRepository>(relaxed = true)
        every { mockWorkoutRepo.allWorkouts } returns MutableStateFlow(emptyList())

        PeriodSummariesDatabaseManager.setInstanceForTesting(mockDbManager)
        WorkoutSummariesDatabaseManager.setInstanceForTesting(mockSummariesManager)
        WorkoutRepository.resetForTesting(mockWorkoutRepo)

        val mockPrefManager = mockk<MyPreferenceManager>(relaxed = true)
        every { mockPrefManager.enabledPeriodMarkerTypesFlow } returns MutableStateFlow(emptySet())
        MyPreferenceManager.setInstanceForTesting(mockPrefManager)

        mockApp = mockk(relaxed = true)
        repo = PeriodsRepository(mockApp)
        PeriodsRepository.setInstanceForTesting(repo)
        viewModel = PeriodsViewModel(mockApp, testDispatcher, testDispatcher)
        clearPeriodCardMapStateCache()
    }

    @After
    fun tearDown() {
        repo.scope.cancel()
        clearPeriodCardMapStateCache()
        PeriodSummariesDatabaseManager.setInstanceForTesting(null)
        WorkoutSummariesDatabaseManager.setInstanceForTesting(null)
        MyPreferenceManager.setInstanceForTesting(null)
        WorkoutRepository.resetForTesting(null)
        PeriodsRepository.setInstanceForTesting(null)
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun createGpsWorkout(
        id: Long,
        sport: BSportType,
        lat: Double,
        lng: Double,
        startTimeS: Long,
        distanceMeters: Double = 25_000.0
    ): WorkoutData {
        val points = listOf(
            LatLng(lat, lng),
            LatLng(lat + 0.01, lng + 0.01),
            LatLng(lat + 0.02, lng + 0.02)
        )
        val now = LocalDateTime.of(2026, 5, 10, 10, 0)
        return WorkoutData(
            id = id,
            finished = true,
            fileBaseName = "w_$id",
            workoutName = "Workout $id",
            sportId = 1L,
            sportName = sport.name,
            bSportType = sport,
            startTimeS = startTimeS,
            formattedDate = now.toLocalDate().toString(),
            formattedTime = now.toLocalTime().toString(),
            localDateTime = now,
            equipmentName = null,
            equipmentId = 0L,
            commute = false,
            trainer = false,
            mapPolyline = PolyUtil.encode(points),
            encodedAltitudes = "",
            encodedDistances = "",
            uploadToStrava = 0,
            totalDistance = distanceMeters,
            maxDisplacement = 3000.0,
            activeTimeSec = 3600L,
            totalTimeSec = 3700L,
            avgSpeedMps = 7.0,
            ascentMeters = 200L,
            descentMeters = 200L,
            minAltitude = 250.0,
            maxAltitude = 450.0,
            maxAltitudeLatLng = points[1],
            maxDisplacementLatLng = points[2],
            startLatLng = points.first(),
            endLatLng = points.last(),
            minLat = lat,
            maxLat = lat + 0.02,
            minLng = lng,
            maxLng = lng + 0.02,
            description = null,
            goal = null,
            method = null,
            stravaSportName = null,
            stravaActivityData = null,
            clusterId = 0L,
            clusterName = null
        )
    }

    private fun createIndoorWorkout(id: Long, sport: BSportType, startTimeS: Long): WorkoutData {
        val now = LocalDateTime.of(2026, 5, 10, 18, 0)
        return WorkoutData(
            id = id,
            finished = true,
            fileBaseName = "indoor_$id",
            workoutName = "Indoor $id",
            sportId = 3L,
            sportName = sport.name,
            bSportType = sport,
            startTimeS = startTimeS,
            formattedDate = now.toLocalDate().toString(),
            formattedTime = now.toLocalTime().toString(),
            localDateTime = now,
            equipmentName = null,
            equipmentId = 0L,
            commute = false,
            trainer = true,
            mapPolyline = "",
            encodedAltitudes = "",
            encodedDistances = "",
            uploadToStrava = 0,
            totalDistance = 0.0,
            maxDisplacement = 0.0,
            activeTimeSec = 2700L,
            totalTimeSec = 2700L,
            avgSpeedMps = 0.0,
            ascentMeters = 0L,
            descentMeters = 0L,
            minAltitude = 0.0,
            maxAltitude = 0.0,
            maxAltitudeLatLng = null,
            maxDisplacementLatLng = null,
            startLatLng = null,
            endLatLng = null,
            minLat = null,
            maxLat = null,
            minLng = null,
            maxLng = null,
            description = null,
            goal = null,
            method = null,
            stravaSportName = null,
            stravaActivityData = null,
            clusterId = 0L,
            clusterName = null
        )
    }

    private fun buildSportStats(workouts: List<WorkoutData>): Map<BSportType, SportStats> =
        workouts.groupBy { it.bSportType }.mapValues { (_, list) ->
            SportStats(
                count = list.size,
                totalDurationSec = list.sumOf { it.activeTimeSec },
                totalDistanceMeters = list.sumOf { it.totalDistance },
                totalAscentMeters = list.sumOf { it.ascentMeters },
                detailedSportStats = emptyMap(),
                longestWorkout = null
            )
        }

    // REQ-PER-046.1 / TST-PER-054.1
    @Test
    fun testMultiRegionExtremaOutliers_selectsTrueHomePrimaryRegionWithTrueCount_tstPer054_1() {
        // 159 home workouts in Stuttgart (~48.78 N, 9.18 E)
        val homeWorkouts = (1L..159L).map { idx ->
            createGpsWorkout(
                id = idx,
                sport = if (idx <= 150L) BSportType.BIKE else BSportType.RUN,
                lat = 48.78 + (idx % 5) * 0.002,
                lng = 9.18 + (idx % 5) * 0.002,
                startTimeS = 1_770_000_000L + idx * 3600L
            )
        }
        // 2 outlier vacation workouts in Highland Council / Isle of Skye (~57.41 N, -6.20 W) defining northId & westId
        val scotlandNorth = createGpsWorkout(
            id = 501L,
            sport = BSportType.BIKE,
            lat = 57.50,
            lng = -6.10,
            startTimeS = 1_780_000_000L
        )
        val scotlandWest = createGpsWorkout(
            id = 502L,
            sport = BSportType.BIKE,
            lat = 57.35,
            lng = -6.30,
            startTimeS = 1_780_003_600L
        )
        val allWorkouts = homeWorkouts + scotlandNorth + scotlandWest

        val baseSummary = PeriodSummary(
            periodLabel = "2026",
            periodDateRange = "",
            periodType = PeriodType.YEAR,
            startTimestampS = 1_767_225_600L,
            endTimestampS = 1_798_761_599L,
            totalWorkouts = allWorkouts.size,
            totalDurationSec = allWorkouts.sumOf { it.activeTimeSec },
            totalDistance = allWorkouts.sumOf { it.totalDistance },
            sportStats = buildSportStats(allWorkouts),
            minLat = 48.78,
            maxLat = 57.52,
            minLng = -6.30,
            maxLng = 9.21,
            longestId = 1L,
            northId = scotlandNorth.id,
            southId = 1L,
            eastId = 1L,
            westId = scotlandWest.id,
            sortKey = "2026"
        )

        val enriched = repo.enrich(baseSummary, allWorkouts)
        val filtered = viewModel.filterPeriodSummary(enriched, setOf(BSportType.BIKE, BSportType.RUN))
        assertNotNull(filtered)

        // Strict REQ-PER-039 & REQ-PER-046.1 invariants
        assertTrue(filtered!!.workoutIdToAllPolylinesMap.isEmpty())
        assertTrue(filtered.workoutIdToPathMap.isEmpty())
        assertEquals(MAX_SUMMARY_THUMBNAIL_TRACKS, filtered.polylines.size)

        val state = prepareCardMapPreviewSync(filtered)
        assertTrue(state.isReady)
        assertEquals("Multi-region badge must stay visible (2 regions)", 2, state.regionCount)
        assertEquals("Primary region badge must display true period count (159)", 159, state.primaryRegionPathsCount)
        assertEquals("region_0", state.activeRegionId)
        assertEquals(MAX_SUMMARY_THUMBNAIL_TRACKS, state.primaryPaths.size)
        val stateBounds = requireNotNull(state.bounds)
        assertTrue(
            "Primary region bounds must frame Stuttgart (~48.78), NOT Scotland (~57.4)",
            stateBounds.southwest.latitude in 48.0..49.5 && stateBounds.northeast.latitude in 48.0..49.5
        )
        assertTrue(
            "All curated preview paths must belong to Stuttgart home region",
            state.primaryPaths.all { path -> path.all { it.latitude in 48.0..49.5 } }
        )

        // Verify 1:1 parity with full-period SpatialRegionEngine.detectRegions()
        val fullRegions = SpatialRegionEngine.detectRegions(allWorkouts)
        assertEquals(fullRegions.size, state.regionCount)
        assertEquals(fullRegions.first { it.isPrimary }.workoutCount, state.primaryRegionPathsCount)
    }

    // REQ-PER-046.2 / TST-PER-054.2
    @Test
    fun testSportToggleRerankingAndIndoorIsolation_updatesPrimaryRegionAndPolylines_tstPer054_2() {
        // Region A (Stuttgart): 150 BIKE + 5 RUN
        val stuttgartBikes = (1L..150L).map { idx ->
            createGpsWorkout(idx, BSportType.BIKE, 48.78, 9.18, startTimeS = 10_000L + idx)
        }
        val stuttgartRuns = (201L..205L).map { idx ->
            createGpsWorkout(idx, BSportType.RUN, 48.79, 9.19, startTimeS = 15_000L + idx)
        }
        // Region B (Isle of Skye): 2 BIKE (north/west outliers) + 10 RUN (dominates RUN!)
        val skyeBikes = listOf(
            createGpsWorkout(301L, BSportType.BIKE, 57.50, -6.10, startTimeS = 18_001L),
            createGpsWorkout(302L, BSportType.BIKE, 57.35, -6.30, startTimeS = 18_002L)
        )
        val skyeRuns = (401L..410L).map { idx ->
            createGpsWorkout(
                id = idx,
                sport = BSportType.RUN,
                lat = 57.41 + (idx - 401L) * 0.001,
                lng = -6.20,
                startTimeS = 20_000L + (idx - 400L) // 20_001 .. 20_010
            )
        }
        // 100%-indoor UNKNOWN workouts (zero GPS)
        val indoorOther = (501L..512L).map { idx ->
            createIndoorWorkout(idx, BSportType.UNKNOWN, startTimeS = 30_000L + idx)
        }

        val allWorkouts = stuttgartBikes + stuttgartRuns + skyeBikes + skyeRuns + indoorOther
        val baseSummary = PeriodSummary(
            periodLabel = "2026",
            periodDateRange = "",
            periodType = PeriodType.YEAR,
            startTimestampS = 1_767_225_600L,
            endTimestampS = 1_798_761_599L,
            totalWorkouts = allWorkouts.size,
            totalDurationSec = allWorkouts.sumOf { it.activeTimeSec },
            totalDistance = allWorkouts.sumOf { it.totalDistance },
            sportStats = buildSportStats(allWorkouts),
            minLat = 48.78,
            maxLat = 57.52,
            minLng = -6.30,
            maxLng = 9.21,
            longestId = 1L,
            northId = 301L,
            southId = 1L,
            eastId = 1L,
            westId = 302L,
            sortKey = "2026_mixed"
        )
        val enriched = repo.enrich(baseSummary, allWorkouts)

        // 1. Default {BIKE, RUN}: Stuttgart dominates (155 vs 12)
        val bikeAndRun = requireNotNull(viewModel.filterPeriodSummary(enriched, setOf(BSportType.BIKE, BSportType.RUN)))
        val bikeAndRunPrimary = requireNotNull(bikeAndRun.primaryRegionSummary)
        assertEquals(2, bikeAndRunPrimary.detectedRegionCount)
        assertEquals(155, bikeAndRunPrimary.primaryRegionWorkoutCount)
        assertTrue(bikeAndRunPrimary.center.latitude in 48.0..49.5)
        assertTrue(bikeAndRun.workoutIdToAllPolylinesMap.isEmpty())

        // 2. Toggle {BIKE}: Stuttgart dominates (150 vs 2)
        val bikeOnly = requireNotNull(viewModel.filterPeriodSummary(enriched, setOf(BSportType.BIKE)))
        val bikeOnlyPrimary = requireNotNull(bikeOnly.primaryRegionSummary)
        assertEquals(2, bikeOnlyPrimary.detectedRegionCount)
        assertEquals(150, bikeOnlyPrimary.primaryRegionWorkoutCount)
        assertTrue(bikeOnlyPrimary.center.latitude in 48.0..49.5)
        assertTrue(bikeOnly.workoutIdToAllPolylinesMap.isEmpty())

        // 3. Toggle {RUN}: Isle of Skye dominates (10 vs 5), curating 8 newest RUN polylines (410 down to 403)
        val runOnly = requireNotNull(viewModel.filterPeriodSummary(enriched, setOf(BSportType.RUN)))
        val runOnlyPrimary = requireNotNull(runOnly.primaryRegionSummary)
        assertEquals(2, runOnlyPrimary.detectedRegionCount)
        assertEquals(10, runOnlyPrimary.primaryRegionWorkoutCount)
        assertTrue(runOnlyPrimary.center.latitude in 57.0..58.0)
        assertEquals(MAX_SUMMARY_THUMBNAIL_TRACKS, runOnly.polylines.size)
        val expectedNewestRunPolylines = skyeRuns.sortedByDescending { it.startTimeS }
            .take(MAX_SUMMARY_THUMBNAIL_TRACKS)
            .map { it.mapPolyline }
        assertEquals(expectedNewestRunPolylines, runOnly.polylines)
        val runPreview = prepareCardMapPreviewSync(runOnly)
        assertEquals(10, runPreview.primaryRegionPathsCount)
        assertTrue(runPreview.bounds!!.southwest.latitude in 57.0..58.0)

        // 4. Toggle {UNKNOWN} (100% indoor): zero leaked GPS tracks, null primaryRegionSummary, empty anchors
        val indoorOnly = viewModel.filterPeriodSummary(enriched, setOf(BSportType.UNKNOWN))!!
        assertEquals(12, indoorOnly.totalWorkouts)
        assertNull(indoorOnly.primaryRegionSummary)
        assertTrue(indoorOnly.polylines.isEmpty())
        assertTrue(indoorOnly.workoutIdToPolylineMap.isEmpty())
        assertTrue(indoorOnly.workoutIdToAllPolylinesMap.isEmpty())
        val indoorPreview = prepareCardMapPreviewSync(indoorOnly)
        assertTrue(indoorPreview.isReady)
        assertTrue(indoorPreview.primaryPaths.isEmpty())
        assertEquals(0, indoorPreview.regionCount)
    }

    // REQ-PER-046.3 / TST-PER-054.3
    @Test
    fun testCacheInvalidationOnRegionCountChangeAndCenterGeocodingParity_tstPer054_3() = runTest(testDispatcher) {
        val stuttgartCenter = LatLng(48.7758, 9.1829)
        val bounds = LatLngBounds(LatLng(48.75, 9.15), LatLng(48.80, 9.21))
        val sharedPolylines = listOf(PolyUtil.encode(listOf(LatLng(48.76, 9.16), LatLng(48.79, 9.19))))

        val summaryTwoRegions159 = PeriodSummary(
            periodLabel = "2026",
            periodDateRange = "",
            periodType = PeriodType.YEAR,
            startTimestampS = 1000L,
            endTimestampS = 5000L,
            totalWorkouts = 161,
            totalDurationSec = 36000L,
            totalDistance = 500_000.0,
            sportStats = emptyMap(),
            polylines = sharedPolylines,
            primaryRegionSummary = PrimaryRegionSummary(2, 159, stuttgartCenter, bounds),
            sortKey = "2026_cache"
        )
        val summaryOneRegion150 = summaryTwoRegions159.copy(
            totalWorkouts = 150,
            primaryRegionSummary = PrimaryRegionSummary(1, 150, stuttgartCenter, bounds)
        )

        val state1 = prepareCardMapPreviewSync(summaryTwoRegions159)
        assertEquals(2, state1.regionCount)
        assertEquals(159, state1.primaryRegionPathsCount)

        // Identical polylines & sortKey, different primaryRegionSummary -> MUST NOT return stale cache
        val state2 = prepareCardMapPreviewSync(summaryOneRegion150)
        assertEquals(1, state2.regionCount)
        assertEquals(150, state2.primaryRegionPathsCount)
        assertEquals(2, periodCardMapStateCache.size())

        // Verify BOTH sync findNearestKnownLocationName and async resolvePlaceName receive primaryRegionSummary.center
        clearPeriodCardMapStateCache()
        mockkObject(RegionNameResolver)
        every { RegionNameResolver.findNearestKnownLocationName(mockApp, stuttgartCenter) } returns null
        coEvery { RegionNameResolver.resolvePlaceName(mockApp, stuttgartCenter) } returns "Stuttgart"

        val resolvedState = prepareCardMapPreview(summaryTwoRegions159, mockApp, testDispatcher)
        assertEquals("Stuttgart", resolvedState.primaryRegionName)
        verify(exactly = 1) { RegionNameResolver.findNearestKnownLocationName(mockApp, stuttgartCenter) }
        coVerify(exactly = 1) { RegionNameResolver.resolvePlaceName(mockApp, stuttgartCenter) }
    }

    // REQ-PER-046.4 / TST-PER-054.4
    @Test
    fun testHighVolume520Workouts_preservesReqPer039MemoryBoundsAndLegacyFallback_tstPer054_4() {
        val homeWorkouts = (1L..515L).map { idx ->
            createGpsWorkout(idx, BSportType.BIKE, 48.78, 9.18, startTimeS = 100_000L + idx)
        }
        val remoteWorkouts = (516L..520L).map { idx ->
            createGpsWorkout(idx, BSportType.BIKE, 57.41, -6.20, startTimeS = 200_000L + idx)
        }
        val allWorkouts = homeWorkouts + remoteWorkouts
        val baseSummary = PeriodSummary(
            periodLabel = "2026",
            periodDateRange = "",
            periodType = PeriodType.YEAR,
            startTimestampS = 100_000L,
            endTimestampS = 300_000L,
            totalWorkouts = 520,
            totalDurationSec = 1_800_000L,
            totalDistance = 13_000_000.0,
            sportStats = buildSportStats(allWorkouts),
            longestId = 1L,
            northId = 516L,
            southId = 1L,
            eastId = 1L,
            westId = 520L,
            sortKey = "2026_520"
        )

        val enriched = repo.enrich(baseSummary, allWorkouts)
        assertTrue("REQ-PER-039.1: workoutIdToPathMap must stay empty", enriched.workoutIdToPathMap.isEmpty())
        assertTrue("REQ-PER-039.2: workoutIdToAllPolylinesMap must stay empty", enriched.workoutIdToAllPolylinesMap.isEmpty())
        assertTrue("REQ-PER-021.1: workoutIdToPolylineMap <= 5 anchors", enriched.workoutIdToPolylineMap.size <= 5)
        assertEquals(MAX_SUMMARY_THUMBNAIL_TRACKS, enriched.polylines.size)
        assertEquals(2, enriched.regionSummaries.size)
        assertTrue(
            "Every per-region per-sport polyline list must remain <= 8",
            enriched.regionSummaries.all { r -> r.sportPreviewPolylines.values.all { it.size <= MAX_SUMMARY_THUMBNAIL_TRACKS } }
        )
        val enrichedPrimary = requireNotNull(enriched.primaryRegionSummary)
        assertEquals(515, enrichedPrimary.primaryRegionWorkoutCount)
        assertEquals(2, enrichedPrimary.detectedRegionCount)

        // Legacy synthetic fixture fallback (regionSummaries = emptyList(), primaryRegionSummary = null)
        val stuttgartTracks = (1L..5L).map { PolyUtil.encode(listOf(LatLng(48.78, 9.18), LatLng(48.79, 9.19))) }
        val munichTracks = (1L..2L).map { PolyUtil.encode(listOf(LatLng(48.13, 11.58), LatLng(48.14, 11.59))) }
        val legacySummary = PeriodSummary(
            periodLabel = "Legacy",
            periodDateRange = "",
            periodType = PeriodType.MONTH,
            startTimestampS = 1000L,
            endTimestampS = 5000L,
            totalWorkouts = 7,
            totalDurationSec = 7000L,
            totalDistance = 70_000.0,
            sportStats = emptyMap(),
            minLat = 48.13,
            maxLat = 48.79,
            minLng = 9.18,
            maxLng = 11.59,
            polylines = stuttgartTracks + munichTracks,
            sortKey = "legacy_fallback"
        )
        val legacyState = prepareCardMapPreviewSync(legacySummary)
        assertEquals(2, legacyState.regionCount)
        assertEquals(5, legacyState.primaryRegionPathsCount)
    }
}
