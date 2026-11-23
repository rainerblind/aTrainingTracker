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
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository
import com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist.WorkoutFilterCriteria
import com.atrainingtracker.trainingtracker.ui.map.TrackOnMapAftermathViewModel
import com.atrainingtracker.trainingtracker.ui.navigation.NavRoutes
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.atrainingtracker.trainingtracker.ui.util.MigrationStatus
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Behavioral Compose UI & NavHost navigation tests verifying pure Compose day-click routing in
 * [PeriodsScreen] without legacy Fragment transactions on `android.R.id.content`
 * (`REQ-UI-2799.1`, `TST-UI-2799.1`, `ATT-8125`).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xxhdpi")
class PeriodsScreenComposeNavigationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var app: Application
    private lateinit var previousLocale: Locale

    @Before
    fun setUp() {
        previousLocale = Locale.getDefault()
        Locale.setDefault(Locale.US)
        app = ApplicationProvider.getApplicationContext()
    }

    @After
    fun tearDown() {
        Locale.setDefault(previousLocale)
        PeriodsRepository.resetInstanceForTesting()
        WorkoutRepository.resetForTesting(null)
        unmockkAll()
    }

    @Test
    fun testBuildDayPeriodSummary_producesExactDayBoundsAndStats_tstUi2799_1() {
        val date = LocalDate.of(2026, 5, 13)
        val zone = ZoneId.of("Europe/Berlin")
        val activity = DailySportActivity(
            date = date,
            bSportType = BSportType.RUN,
            count = 2,
            durationSec = 3600L,
            distanceMeters = 12_500.0,
            ascentMeters = 180L,
            workoutIds = listOf(401L, 402L),
            workoutNames = listOf("Morning Run", "Track Session")
        )

        val daySummary = buildDayPeriodSummary(
            date = date,
            sport = BSportType.RUN,
            activity = activity,
            locale = Locale.GERMANY,
            zoneId = zone
        )

        assertEquals(PeriodType.DAY, daySummary.periodType)
        assertEquals(date.atStartOfDay(zone).toEpochSecond(), daySummary.startTimestampS)
        assertEquals(date.plusDays(1).atStartOfDay(zone).toEpochSecond() - 1L, daySummary.endTimestampS)
        assertEquals(2, daySummary.totalWorkouts)
        assertEquals(3600L, daySummary.totalDurationSec)
        assertEquals(12_500.0, daySummary.totalDistance, 1e-6)
        assertEquals(2, daySummary.sportStats[BSportType.RUN]?.count)
        assertEquals("2026-05-13", daySummary.sortKey)
    }

    @Test
    fun testPeriodsScreen_dayClickRoutesStrictlyViaComposeNavHostAndBackReturnsCleanly_tstUi2799_1() {
        val targetDate = LocalDate.of(2026, 5, 13) // Wednesday
        val weekStart = LocalDate.of(2026, 5, 11) // Monday
        val weekEnd = LocalDate.of(2026, 5, 17)   // Sunday
        val zone = ZoneId.systemDefault()

        val dayActivity = DailySportActivity(
            date = targetDate,
            bSportType = BSportType.RUN,
            count = 1,
            durationSec = 2400L,
            distanceMeters = 8_500.0,
            ascentMeters = 95L,
            workoutIds = listOf(909L),
            workoutNames = listOf("Tempo Run")
        )
        val weekSummary = PeriodSummary(
            periodLabel = "Week 20, 2026",
            periodDateRange = "May 11 - May 17, 2026",
            periodType = PeriodType.WEEK,
            startTimestampS = weekStart.atStartOfDay(zone).toEpochSecond(),
            endTimestampS = weekEnd.plusDays(1).atStartOfDay(zone).toEpochSecond() - 1L,
            totalWorkouts = 1,
            totalDurationSec = 2400L,
            totalDistance = 8_500.0,
            sportStats = mapOf(
                BSportType.RUN to SportStats(
                    count = 1,
                    totalDurationSec = 2400L,
                    totalDistanceMeters = 8_500.0,
                    totalAscentMeters = 95L,
                    detailedSportStats = emptyMap(),
                    longestWorkout = null
                )
            ),
            dailySportContributions = mapOf(
                BSportType.RUN to DailySportContribution(
                    sportType = BSportType.RUN,
                    dailyActivities = mapOf(targetDate to dayActivity),
                    maxDailyDurationSec = 2400L,
                    maxDailyDistanceMeters = 8_500.0,
                    maxDailyAscentMeters = 95L
                )
            ),
            sortKey = "2026-W20"
        )

        val mockWorkoutRepo = mockk<WorkoutRepository>(relaxed = true) {
            every { allWorkouts } returns MutableStateFlow(emptyList())
        }
        val mockPeriodsRepo = mockk<PeriodsRepository>(relaxed = true) {
            // Page index 1 is Weeks tab (initialPage = 1 in PeriodsScreen)
            every { groupedPeriods } returns MutableStateFlow(
                listOf(emptyList(), listOf(weekSummary), emptyList(), emptyList())
            )
            every { migrationStatus } returns MutableStateFlow<MigrationStatus?>(null)
        }
        WorkoutRepository.resetForTesting(mockWorkoutRepo)
        PeriodsRepository.setInstanceForTesting(mockPeriodsRepo)

        val periodsViewModel = PeriodsViewModel(app)
        val mockTrackOnMapVm = mockk<TrackOnMapAftermathViewModel>(relaxed = true)

        var capturedCriteria: WorkoutFilterCriteria? = null
        var capturedScrollWorkoutId: Long? = null
        var capturedPeriodSummary: PeriodSummary? = null

        composeTestRule.setContent {
            val navController = rememberNavController()
            ATrainingTrackerTheme(darkTheme = false) {
                Surface {
                    NavHost(
                        navController = navController,
                        startDestination = NavRoutes.PERIODS
                    ) {
                        composable(NavRoutes.PERIODS) {
                            PeriodsScreen(
                                viewModel = periodsViewModel,
                                trackOnMapViewModel = mockTrackOnMapVm,
                                onStartWorkoutList = { summary, sport, scrollId ->
                                    capturedPeriodSummary = summary
                                    capturedScrollWorkoutId = scrollId
                                    capturedCriteria = WorkoutFilterCriteria(
                                        startDateS = summary.startTimestampS,
                                        endDateS = summary.endTimestampS,
                                        bSportType = sport
                                    )
                                    navController.navigate(NavRoutes.workouts(pushed = true))
                                }
                            )
                        }
                        composable(NavRoutes.WORKOUTS_PATTERN) {
                            Button(onClick = { navController.popBackStack() }) {
                                Text("FilteredWorkoutsBack")
                            }
                        }
                    }
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Week 20, 2026").assertIsDisplayed()

        // Tap Wednesday column in the week's DailyActivityStrip to reveal the selected day detail banner
        val wednesdayShort = targetDate.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
            .take(2)
        composeTestRule.onNodeWithText(wednesdayShort).performClick()
        composeTestRule.waitForIdle()

        // Tap the selected day detail banner to trigger handleDayClick -> pure Compose NavHost route
        val expectedBannerDate = targetDate.format(DateTimeFormatter.ofPattern("EEEE, d. MMMM yyyy", Locale.getDefault()))
        composeTestRule.onNodeWithText(expectedBannerDate).assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()

        // Verify pure Compose navigation landed on Workouts route with exact day filter criteria
        composeTestRule.onNodeWithText("FilteredWorkoutsBack").assertIsDisplayed()
        assertNotNull("Expected day-scoped PeriodSummary via onStartWorkoutList", capturedPeriodSummary)
        assertEquals(PeriodType.DAY, capturedPeriodSummary?.periodType)
        assertEquals(BSportType.RUN, capturedCriteria?.bSportType)
        assertEquals(targetDate.atStartOfDay(zone).toEpochSecond(), capturedCriteria?.startDateS)
        assertEquals(targetDate.plusDays(1).atStartOfDay(zone).toEpochSecond() - 1L, capturedCriteria?.endDateS)
        assertEquals(909L, capturedScrollWorkoutId)

        // Verify returning via Compose back pops cleanly back to PeriodsScreen
        composeTestRule.onNodeWithText("FilteredWorkoutsBack").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Week 20, 2026").assertIsDisplayed()

        // Verify PeriodsScreenKt declares zero legacy Fragment/WorkoutSummariesListFragment references
        val periodsScreenClass = Class.forName("com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.PeriodsScreenKt")
        val paramNames = periodsScreenClass.declaredMethods.flatMap { it.parameterTypes.map { p -> p.name } }
        assertTrue(
            "PeriodsScreenKt must not reference FragmentActivity or WorkoutSummariesListFragment",
            paramNames.none { it.contains("Fragment") }
        )
    }
}
