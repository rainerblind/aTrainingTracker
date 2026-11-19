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

package com.atrainingtracker.trainingtracker.ui.aftermath.periodlist

import android.app.Application
import android.content.Context
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.MyUnits
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.atrainingtracker.trainingtracker.ui.util.MigrationStatus
import com.atrainingtracker.trainingtracker.ui.util.ProgressPhase
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Behavioral Compose UI verification for [PeriodsTabsScreen] and [PeriodList] under
 * Robolectric (`isPlayServiceAvailable = false` to isolate from Play Services `GoogleMap`):
 * - `TST-PERF-006` (`REQ-PER-005`): Multi-phase `MigrationStatus` card visibility & dismissal.
 * - `TST-PER-010` (`REQ-UI-120`): Absence of interactive `Whatshot`/heatmap toggle in Periods UI.
 * - `TST-PER-017` (`REQ-UI-125`): Empty state `EmptyStatePlaceholder` rendering vs populated list.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w412dp-h915dp-xhdpi")
class PeriodsProgressAndEmptyStateUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        TrainingApplication.setUnitForTesting(MyUnits.METRIC)
    }

    /**
     * TST-PERF-006 (REQ-PER-005): Multi-Phase Progress Feedback.
     * Verifies MigrationStatus card renders title and formatted Phase 1/2 labels when non-null
     * and disappears cleanly when cleared to null.
     */
    @Test
    fun periodsTabsScreen_rendersMultiPhaseMigrationCardAndDismissesWhenNull_TST_PERF_006() {
        var migrationState by mutableStateOf<MigrationStatus?>(
            MigrationStatus(
                title = "Rebuilding Period Summaries",
                phases = listOf(
                    ProgressPhase(1, "Reading workouts (50/50)", 1.0f),
                    ProgressPhase(2, "Syncing February 2026", 0.45f)
                )
            )
        )

        composeTestRule.setContent {
            ATrainingTrackerTheme {
                val tabs = listOf("Day", "Week", "Month", "Year")
                val pagerState = rememberPagerState(initialPage = 2) { tabs.size }
                val listStates = List(tabs.size) { rememberLazyListState() }
                PeriodsTabsScreen(
                    groupedPeriods = List(tabs.size) { emptyList() },
                    tabs = tabs,
                    pagerState = pagerState,
                    listStates = listStates,
                    isPlayServiceAvailable = false,
                    onHeaderClick = {},
                    onMapClick = { _, _ -> },
                    onSportClick = { _, _ -> },
                    onLongestWorkoutClick = { _, _, _ -> },
                    migrationStatus = migrationState
                )
            }
        }

        val phase1Text = context.getString(R.string.migration_phase_label, 1, "Reading workouts (50/50)")
        val phase2Text = context.getString(R.string.migration_phase_label, 2, "Syncing February 2026")

        composeTestRule.onNodeWithText("Rebuilding Period Summaries").assertIsDisplayed()
        composeTestRule.onNodeWithText(phase1Text).assertIsDisplayed()
        composeTestRule.onNodeWithText(phase2Text).assertIsDisplayed()
        composeTestRule.onAllNodes(hasProgressBarRangeInfo(ProgressBarRangeInfo(1.0f, 0f..1f))).assertCountEquals(2)
        composeTestRule.onAllNodes(hasProgressBarRangeInfo(ProgressBarRangeInfo(0.45f, 0f..1f))).assertCountEquals(2)

        composeTestRule.runOnIdle {
            migrationState = null
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Rebuilding Period Summaries").assertDoesNotExist()
        composeTestRule.onNodeWithText(phase2Text).assertDoesNotExist()
    }

    /**
     * TST-PER-010 (REQ-UI-120): Heatmap Toggle Removal.
     * Verifies PeriodsTabsScreen renders canonical sport filter controls (Bike/Run/Other)
     * with zero heatmap/Whatshot toggle button anywhere in the UI hierarchy.
     */
    @Test
    fun periodsTabsScreen_exposesOnlySportFiltersWithNoHeatmapToggle_TST_PER_010() {
        composeTestRule.setContent {
            ATrainingTrackerTheme {
                val tabs = listOf("Day", "Week", "Month", "Year")
                val pagerState = rememberPagerState(initialPage = 2) { tabs.size }
                val listStates = List(tabs.size) { rememberLazyListState() }
                PeriodsTabsScreen(
                    groupedPeriods = List(tabs.size) { emptyList() },
                    tabs = tabs,
                    pagerState = pagerState,
                    listStates = listStates,
                    isPlayServiceAvailable = false,
                    onHeaderClick = {},
                    onMapClick = { _, _ -> },
                    onSportClick = { _, _ -> },
                    onLongestWorkoutClick = { _, _, _ -> }
                )
            }
        }

        // Canonical sport filter icons are present
        composeTestRule.onNodeWithContentDescription(context.getString(BSportType.BIKE.stringResId)).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(context.getString(BSportType.RUN.stringResId)).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(context.getString(BSportType.UNKNOWN.stringResId)).assertIsDisplayed()

        // No heatmap or Whatshot toggle button exists
        composeTestRule.onNode(hasContentDescription("heatmap", substring = true, ignoreCase = true)).assertDoesNotExist()
        composeTestRule.onNode(hasText("heatmap", substring = true, ignoreCase = true)).assertDoesNotExist()
        composeTestRule.onNode(hasContentDescription("whatshot", substring = true, ignoreCase = true)).assertDoesNotExist()
    }

    /**
     * TST-PER-017 (REQ-UI-125): Period Empty State Placeholder Verification.
     * Verifies EmptyStatePlaceholder renders R.string.no_periods_available and
     * R.string.no_periods_available_hint when periods is empty, and hides once populated.
     */
    @Test
    fun periodList_emptyVsPopulated_rendersAndHidesEmptyStatePlaceholder_TST_PER_017() {
        var periodsState by mutableStateOf<List<PeriodSummary>>(emptyList())

        composeTestRule.setContent {
            ATrainingTrackerTheme {
                val scrollState = rememberLazyListState()
                PeriodList(
                    scrollState = scrollState,
                    periods = periodsState,
                    isPlayServiceAvailable = false,
                    onHeaderClick = {},
                    onMapClick = { _, _ -> },
                    onSportClick = { _, _ -> },
                    onLongestWorkoutClick = { _, _, _ -> }
                )
            }
        }

        val emptyTitle = context.getString(R.string.no_periods_available)
        val emptyHint = context.getString(R.string.no_periods_available_hint)

        composeTestRule.onNodeWithText(emptyTitle).assertIsDisplayed()
        composeTestRule.onNodeWithText(emptyHint).assertIsDisplayed()

        val populatedSummary = PeriodSummary(
            periodLabel = "April 2026",
            periodDateRange = "Apr 1 - Apr 30, 2026",
            periodType = PeriodType.MONTH,
            startTimestampS = 1_775_001_600L,
            endTimestampS = 1_777_593_600L,
            totalWorkouts = 3,
            totalDurationSec = 7_200L,
            totalDistance = 42_000.0,
            sportStats = mapOf(
                BSportType.RUN to SportStats(3, 7_200L, 42_000.0, 300L, emptyMap(), null)
            ),
            sortKey = "2026-04"
        )

        composeTestRule.runOnIdle {
            periodsState = listOf(populatedSummary)
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(emptyTitle).assertDoesNotExist()
        composeTestRule.onNodeWithText("April 2026").assertIsDisplayed()
    }
}
