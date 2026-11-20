/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.atrainingtracker.trainingtracker.ui.clusters

import android.app.Application
import android.content.Context
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.MyUnits
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.database.WorkoutCluster
import com.atrainingtracker.trainingtracker.database.WorkoutClusterStats
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.google.android.gms.maps.model.LatLng
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime

/**
 * Robolectric `createComposeRule()` behavioral UI tests for [WorkoutClustersTabsScreen],
 * [WorkoutClusterDetails], [ClusterItem], and [UnclusteredWorkoutsList]
 * (`REQ-PRO-132`, `REQ-PRO-133`: zero `src/main/` reads).
 */
// Linked test specs: TST-SET-016, TST-SET-017, TST-SET-018, TST-SET-019, TST-SET-020, TST-SET-029, TST-SET-040, TST-SET-041
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w412dp-h915dp-xhdpi")
class WorkoutClustersTabsUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        TrainingApplication.setUnitForTesting(MyUnits.METRIC)
    }

    @After
    fun tearDown() {
        TrainingApplication.setUnitForTesting(MyUnits.METRIC)
    }

    private fun cluster(id: Long, name: String, sport: BSportType, hitCount: Int = 5) = WorkoutCluster(
        id = id,
        name = name,
        probableSportId = if (sport == BSportType.BIKE) 1L else if (sport == BSportType.RUN) 2L else 0L,
        startLat = 48.1,
        startLng = 11.5,
        endLat = 48.1,
        endLng = 11.5,
        maxDispLat = 48.12,
        maxDispLng = 11.52,
        refDistance = 10000.0,
        hitCount = hitCount,
        bSportType = sport
    )

    private fun unclusteredWorkout(id: Long = 90L, name: String = "Standalone Gravel") = WorkoutData(
        id = id,
        finished = true,
        fileBaseName = "workout_$id",
        workoutName = name,
        sportId = 1L,
        sportName = "Cycling",
        bSportType = BSportType.BIKE,
        startTimeS = 1700000000L,
        formattedDate = "2026-11-20",
        formattedTime = "09:00",
        localDateTime = LocalDateTime.now(),
        equipmentName = "Canyon Aeroad",
        equipmentId = 1L,
        commute = false,
        trainer = false,
        mapPolyline = "_p~iF~ps|U_ulLnnqC_mqNvxq`@",
        encodedAltitudes = "",
        encodedDistances = "",
        uploadToStrava = 0,
        totalDistance = 12000.0,
        maxDisplacement = 4000.0,
        activeTimeSec = 2400L,
        totalTimeSec = 2450L,
        avgSpeedMps = 5.0,
        ascentMeters = 100L,
        descentMeters = 100L,
        minAltitude = 400.0,
        maxAltitude = 500.0,
        startLatLng = LatLng(48.1, 11.5),
        endLatLng = LatLng(48.1, 11.5),
        maxDisplacementLatLng = LatLng(48.12, 11.52),
        description = null,
        goal = null,
        method = null,
        stravaSportName = "Ride",
        clusterId = -1L
    )

    private fun mockViewModel(
        clusters: List<WorkoutCluster>,
        unclustered: List<WorkoutData> = emptyList()
    ): WorkoutClustersViewModel {
        val statsMap = clusters.associate {
            it.id to WorkoutClusterStats(
                clusterId = it.id,
                workoutCount = it.hitCount,
                bestDurationSec = 1800L,
                totalDistanceMeters = it.refDistance * it.hitCount
            )
        }
        return mockk<WorkoutClustersViewModel>(relaxed = true).apply {
            every { allClusters } returns MutableStateFlow(clusters)
            every { unclusteredWorkouts } returns MutableStateFlow(unclustered)
            every { filterCriteria } returns MutableStateFlow(ClusterFilterCriteria())
            every { availableEquipment } returns MutableStateFlow(listOf("Canyon Aeroad"))
            every { sortOrder } returns MutableStateFlow(ClusterSortOrder.RECORDINGS)
            every { currentLocation } returns MutableStateFlow(null)
            every { isLocationAvailable } returns MutableStateFlow(false)
            every { knownLocations } returns MutableStateFlow(emptyList())
            every { clusterStats } returns MutableStateFlow(statsMap)
            every { linkedEquipmentBySport } returns MutableStateFlow(mapOf(1L to setOf("Canyon Aeroad")))
            every { getBSportType(1L) } returns BSportType.BIKE
            every { getBSportType(2L) } returns BSportType.RUN
            every { getBSportType(0L) } returns BSportType.UNKNOWN
            every { getSportName(1L) } returns "Road Bike"
            every { getSportName(2L) } returns "Running"
            every { getSportName(0L) } returns "Other"
            every { getLinkedEquipment(1L) } returns listOf("Canyon Aeroad")
            every { getLinkedEquipment(2L) } returns emptyList()
            every { getLinkedEquipment(0L) } returns emptyList()
            every { sortClusters(any(), any(), any()) } answers { firstArg() }
        }
    }

    @Test
    fun workoutClusterDetails_displaysEquipmentArrowNotation_andTriggersHitCountClick_TST_SET_016_TST_SET_041() {
        // TST-SET-016, TST-SET-041
        val c = cluster(1L, "Isar Bike Loop", BSportType.BIKE, hitCount = 5)
        val vm = mockViewModel(listOf(c))
        var hitCountClickedCluster: WorkoutCluster? = null

        composeTestRule.setContent {
            ATrainingTrackerTheme {
                CompositionLocalProvider(LocalInspectionMode provides true) {
                    ClusterItem(
                        cluster = c,
                        viewModel = vm,
                        onClick = {},
                        onDeleteRequest = {},
                        onHitCountClick = { hitCountClickedCluster = c }
                    )
                }
            }
        }

        val expectedEquip = context.getString(R.string.cluster_equipment_mapping_format, "Canyon Aeroad")
        composeTestRule.onNodeWithText(expectedEquip).assertIsDisplayed()

        val recordingsText = context.resources.getQuantityString(R.plurals.cluster_recordings, 5, 5)
        composeTestRule.onNodeWithText(recordingsText).performClick()
        assertEquals(1L, hitCountClickedCluster?.id)
    }

    @Test
    fun workoutClustersTabsScreen_rendersTabsFabFiltersAndUnclusteredNavigation_TST_SET_017_018_020_029_040() {
        // TST-SET-017, TST-SET-018, TST-SET-020, TST-SET-029, TST-SET-040
        val bikeCluster = cluster(1L, "Bike Route", BSportType.BIKE)
        val runCluster = cluster(2L, "Run Route", BSportType.RUN)
        val unclustered = unclusteredWorkout(99L, "Unclustered Morning Spin")
        val vm = mockViewModel(listOf(bikeCluster, runCluster), listOf(unclustered))

        var addClicked = false
        var clickedUnclustered: WorkoutData? = null

        composeTestRule.setContent {
            ATrainingTrackerTheme {
                CompositionLocalProvider(LocalInspectionMode provides true) {
                    val pagerState = rememberPagerState { 5 }
                    WorkoutClustersTabsScreen(
                        viewModel = vm,
                        pagerState = pagerState,
                        allListState = rememberLazyListState(),
                        bikeListState = rememberLazyListState(),
                        runListState = rememberLazyListState(),
                        otherListState = rememberLazyListState(),
                        unclusteredListState = rememberLazyListState(),
                        onClusterClick = {},
                        onWorkoutClick = { clickedUnclustered = it },
                        onHitCountClick = {},
                        onAddClick = { addClicked = true },
                        onDeleteRequest = {}
                    )
                }
            }
        }

        // TST-SET-018: All 5 tabs exist and All displays both clusters
        composeTestRule.onNodeWithText(context.getString(R.string.sport_type_tab_all)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.sport_type_tab_bike)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.sport_type_tab_run)).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.sport_type_tab_unknown)).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.unclustered)).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.sport_type_tab_all)).performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Bike Route").assertIsDisplayed()

        // TST-SET-020: Unified FAB present and clickable
        composeTestRule.onNodeWithContentDescription(context.getString(R.string.text_new))
            .assertIsDisplayed()
            .performClick()
        assertTrue("FAB click must invoke onAddClick", addClicked)

        // TST-SET-018: Filter to Run tab
        composeTestRule.onNodeWithText(context.getString(R.string.sport_type_tab_run)).performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Run Route").assertIsDisplayed()

        // TST-SET-029 & TST-SET-040: Switch to Unclustered tab, verify mini-map parity & click workout
        composeTestRule.onNodeWithText(context.getString(R.string.unclustered)).performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Unclustered Morning Spin").assertIsDisplayed()
        composeTestRule.onNodeWithTag(WORKOUT_THUMBNAIL_TEST_TAG, useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Unclustered Morning Spin").onFirst().performClick()
        assertEquals(99L, clickedUnclustered?.id)
    }

    @Test
    fun workoutClustersTabsScreen_preservesHoistedScrollStateAcrossDetailToggle_TST_SET_019() {
        // TST-SET-019
        val clusters = (1L..15L).map { idx -> cluster(idx, "Cluster $idx", BSportType.BIKE) }
        val vm = mockViewModel(clusters)
        val hoistedAllState = LazyListState(firstVisibleItemIndex = 6, firstVisibleItemScrollOffset = 24)
        var showDetail by mutableStateOf(false)

        composeTestRule.setContent {
            ATrainingTrackerTheme {
                CompositionLocalProvider(LocalInspectionMode provides true) {
                    val pagerState = rememberPagerState { 5 }
                    if (!showDetail) {
                        WorkoutClustersTabsScreen(
                            viewModel = vm,
                            pagerState = pagerState,
                            allListState = hoistedAllState,
                            bikeListState = rememberLazyListState(),
                            runListState = rememberLazyListState(),
                            otherListState = rememberLazyListState(),
                            unclusteredListState = rememberLazyListState(),
                            onClusterClick = { showDetail = true },
                            onWorkoutClick = {},
                            onHitCountClick = {},
                            onAddClick = {},
                            onDeleteRequest = {}
                        )
                    }
                }
            }
        }

        assertEquals(6, hoistedAllState.firstVisibleItemIndex)
        showDetail = true
        composeTestRule.waitForIdle()
        showDetail = false
        composeTestRule.waitForIdle()
        assertEquals("Hoisted LazyListState must preserve scroll index on return", 6, hoistedAllState.firstVisibleItemIndex)
    }
}
