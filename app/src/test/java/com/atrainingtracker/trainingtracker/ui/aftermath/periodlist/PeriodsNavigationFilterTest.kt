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

import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavGraph
import androidx.navigation.NavGraphNavigator
import androidx.navigation.NavHostController
import androidx.navigation.NavigatorProvider
import androidx.navigation.NavDestination
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.activities.MainActivityWithNavigation
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository
import com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist.WorkoutSummariesViewModel
import com.atrainingtracker.trainingtracker.ui.navigation.NavRoutes
import com.atrainingtracker.trainingtracker.ui.util.MigrationStatus
import com.google.android.gms.common.GooglePlayServicesUtil
import com.google.android.gms.maps.MapsInitializer
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Pure Compose navigation verification for [MainActivityWithNavigation.startWorkoutSummaryListFromPeriod]
 * ensuring sport & period boundary arguments propagate cleanly into [WorkoutSummariesViewModel]
 * after removal of legacy `PeriodsFragment` & `WorkoutSummariesListFragment`
 * (REQ-PER-008, TST-PER-009, REQ-UI-2801 / ATT-8324, ATT-8129).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PeriodsNavigationFilterTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        MainActivityWithNavigation.defaultIoDispatcher = testDispatcher
        val mockWorkoutRepo = mockk<WorkoutRepository>(relaxed = true) {
            every { allWorkouts } returns MutableStateFlow(emptyList())
        }
        val mockPeriodsRepo = mockk<PeriodsRepository>(relaxed = true) {
            every { groupedPeriods } returns MutableStateFlow(listOf(emptyList(), emptyList(), emptyList(), emptyList()))
            every { migrationStatus } returns MutableStateFlow<MigrationStatus?>(null)
            coEvery { checkIntegrityAndSync() } returns false
        }
        WorkoutRepository.resetForTesting(mockWorkoutRepo)
        PeriodsRepository.setInstanceForTesting(mockPeriodsRepo)

        mockkStatic(MapsInitializer::class)
        every { MapsInitializer.initialize(any(), any(), any()) } returns 0
        every { MapsInitializer.initialize(any()) } returns 0

        @Suppress("DEPRECATION")
        run {
            mockkStatic(GooglePlayServicesUtil::class)
            every { GooglePlayServicesUtil.isGooglePlayServicesAvailable(any()) } returns 0
            every { GooglePlayServicesUtil.getErrorDialog(any(), any(), any()) } returns null
        }
    }

    @After
    fun tearDown() {
        PeriodsRepository.resetInstanceForTesting()
        WorkoutRepository.resetForTesting(null)
        unmockkAll()
        MainActivityWithNavigation.defaultIoDispatcher = Dispatchers.IO
        Dispatchers.resetMain()
    }

    /**
     * TST-PER-009 (REQ-PER-008, REQ-UI-2801.1): Clicking a sport row on a PeriodSummary routes
     * through MainActivityWithNavigation.startWorkoutSummaryListFromPeriod into Compose NavHost
     * with matching bSportType, startDateS, and endDateS filter criteria, while legacy
     * PeriodsFragment and WorkoutSummariesListFragment remain excised.
     */
    @Test
    fun startWorkoutSummaryList_propagatesSportAndTemporalBoundsArguments_TST_PER_009() {
        for (retired in listOf(
            "com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.PeriodsFragment",
            "com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist.WorkoutSummariesListFragment"
        )) {
            try {
                Class.forName(retired)
                fail("Retired legacy fragment $retired must be eliminated")
            } catch (_: ClassNotFoundException) {
                // Expected
            }
        }

        val activity = Robolectric.buildActivity(MainActivityWithNavigation::class.java).get()

        val readyController = mockk<NavHostController>(relaxed = true)
        val navigatorProvider = NavigatorProvider()
        val graphNavigator = NavGraphNavigator(navigatorProvider)
        val realGraph = NavGraph(graphNavigator)
        val destination = NavDestination("test").apply { id = 123 }
        realGraph.addDestination(destination)
        realGraph.setStartDestination(123)
        every { readyController.graph } returns realGraph
        activity.navController = readyController

        val summary = PeriodSummary(
            periodLabel = "March 2026",
            periodDateRange = "Mar 1 - Mar 31, 2026",
            periodType = PeriodType.MONTH,
            startTimestampS = 1_772_323_200L,
            endTimestampS = 1_774_915_200L,
            totalWorkouts = 7,
            totalDurationSec = 14_400L,
            totalDistance = 80_000.0,
            sportStats = mapOf(
                BSportType.BIKE to SportStats(4, 9_600L, 60_000.0, 500L, emptyMap(), null),
                BSportType.RUN to SportStats(3, 4_800L, 20_000.0, 150L, emptyMap(), null)
            ),
            sortKey = "2026-03"
        )

        activity.startWorkoutSummaryListFromPeriod(
            periodSummary = summary,
            bSportType = BSportType.RUN,
            scrollToWorkoutId = null
        )

        val summariesViewModel = ViewModelProvider(activity)[WorkoutSummariesViewModel::class.java]
        val criteria = summariesViewModel.filterCriteria.value
        assertEquals(BSportType.RUN, criteria.bSportType)
        assertEquals(summary.startTimestampS, criteria.startDateS)
        assertEquals(summary.endTimestampS, criteria.endDateS)
        verify { readyController.navigate(NavRoutes.workouts(pushed = true)) }
    }
}
