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

package com.atrainingtracker.trainingtracker.ui.athleteprofile

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.database.AthleteMetricEntry
import com.atrainingtracker.trainingtracker.database.AthleteMetricType
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Verification tests for Option 1 Fitness & Physiology separation (`ATT-9627`, `REQ-SET-136.4`, `TST-SET-136.4`):
 * - Body Weight remains directly editable (`+`).
 * - Threshold summary cards (`HRrest`, `FTP`, `CP`, `W'`, `HRmax`, `LTHR`) omit duplicate `+` creation triggers
 *   and route **"Edit in Zones"** directly to the matching Zones tab (`0` = HR Run, `1` = HR Bike, `2` = Power Bike).
 * - Sport-specific `HRmax`/`LTHR` trends plot cleanly via [AthleteMetricsPlotMath.filterAndConvertSeries].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AthleteProfileThresholdsSeparationTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun resolveZonesTabForPlotMetric_mapsEveryMetricToAuthoritativeZonesTab_TST_SET_136_4() {
        assertEquals(0, resolveZonesTabForPlotMetric(PlotMetricType.WEIGHT))
        assertEquals(0, resolveZonesTabForPlotMetric(PlotMetricType.HR_REST))
        assertEquals(0, resolveZonesTabForPlotMetric(PlotMetricType.HR_MAX_RUN))
        assertEquals(0, resolveZonesTabForPlotMetric(PlotMetricType.LTHR_RUN))
        assertEquals(1, resolveZonesTabForPlotMetric(PlotMetricType.HR_MAX_BIKE))
        assertEquals(1, resolveZonesTabForPlotMetric(PlotMetricType.LTHR_BIKE))
        assertEquals(2, resolveZonesTabForPlotMetric(PlotMetricType.FTP))
        assertEquals(2, resolveZonesTabForPlotMetric(PlotMetricType.CP))
        assertEquals(2, resolveZonesTabForPlotMetric(PlotMetricType.W_PRIME))
        assertEquals(2, resolveZonesTabForPlotMetric(PlotMetricType.POWER_TO_WEIGHT))
    }

    @Test
    fun plotMath_filtersSportSpecificHrMaxAndLthrSeriesChronologically_TST_SET_136_4() {
        val t1 = 1_750_000_000_000L
        val t2 = 1_755_000_000_000L
        val entries = listOf(
            AthleteMetricEntry(1L, t2, AthleteMetricType.HR_MAX_RUN_BPM, 194.0),
            AthleteMetricEntry(2L, t1, AthleteMetricType.HR_MAX_RUN_BPM, 190.0),
            AthleteMetricEntry(3L, t1, AthleteMetricType.HR_MAX_BIKE_BPM, 184.0),
            AthleteMetricEntry(4L, t1, AthleteMetricType.LTHR_RUN_BPM, 172.0),
            AthleteMetricEntry(5L, t1, AthleteMetricType.LTHR_BIKE_BPM, 166.0)
        )

        val runMax = AthleteMetricsPlotMath.filterAndConvertSeries(entries, PlotMetricType.HR_MAX_RUN, MetricsTimeRange.ALL, t2)
        assertEquals(listOf(190.0, 194.0), runMax.map { it.value })

        val bikeMax = AthleteMetricsPlotMath.filterAndConvertSeries(entries, PlotMetricType.HR_MAX_BIKE, MetricsTimeRange.ALL, t2)
        assertEquals(listOf(184.0), bikeMax.map { it.value })

        val runLthr = AthleteMetricsPlotMath.filterAndConvertSeries(entries, PlotMetricType.LTHR_RUN, MetricsTimeRange.ALL, t2)
        assertEquals(listOf(172.0), runLthr.map { it.value })

        val bikeLthr = AthleteMetricsPlotMath.filterAndConvertSeries(entries, PlotMetricType.LTHR_BIKE, MetricsTimeRange.ALL, t2)
        assertEquals(listOf(166.0), bikeLthr.map { it.value })
    }

    @Test
    fun athleteProfileContent_keepsWeightEditable_andRoutesEditInZonesToMatchingTab_TST_SET_136_4() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val addLabel = context.getString(R.string.athlete_profile_add_entry)
        val editInZonesLabel = context.getString(R.string.athlete_profile_edit_in_zones)
        val now = 1_760_000_000_000L

        val navigatedTabs = mutableListOf<Int>()
        val selectedPlot = mutableListOf<PlotMetricType>()

        val uiState = AthleteProfileUiState(
            latestWeightEntry = AthleteMetricEntry(1L, now, AthleteMetricType.WEIGHT_KG, 72.0),
            latestHrRestEntry = AthleteMetricEntry(2L, now, AthleteMetricType.HR_REST_BPM, 50.0),
            latestFtpEntry = AthleteMetricEntry(3L, now, AthleteMetricType.FTP_WATTS, 280.0),
            latestCpEntry = AthleteMetricEntry(4L, now, AthleteMetricType.CP_WATTS, 270.0),
            latestWPrimeEntry = AthleteMetricEntry(5L, now, AthleteMetricType.W_PRIME_KJ, 19.0),
            latestHrMaxRunEntry = AthleteMetricEntry(6L, now, AthleteMetricType.HR_MAX_RUN_BPM, 192.0),
            latestHrMaxBikeEntry = AthleteMetricEntry(7L, now, AthleteMetricType.HR_MAX_BIKE_BPM, 184.0),
            latestLthrRunEntry = AthleteMetricEntry(8L, now, AthleteMetricType.LTHR_RUN_BPM, 172.0),
            latestLthrBikeEntry = AthleteMetricEntry(9L, now, AthleteMetricType.LTHR_BIKE_BPM, 166.0),
            selectedPlotMetric = PlotMetricType.FTP,
            enableCriticalPowerModel = true,
            filteredHistory = listOf(
                AthleteMetricEntry(1L, now, AthleteMetricType.WEIGHT_KG, 72.0),
                AthleteMetricEntry(3L, now, AthleteMetricType.FTP_WATTS, 280.0)
            )
        )

        composeRule.setContent {
            ATrainingTrackerTheme {
                AthleteProfileContent(
                    uiState = uiState,
                    onSelectPlotMetric = { selectedPlot.add(it) },
                    onSelectTimeRange = {},
                    onSelectHistoryFilter = {},
                    onAddEntry = { _, _, _ -> },
                    onUpdateEntry = { _, _, _, _ -> },
                    onDeleteEntry = { _, _ -> },
                    onNavigateToZones = { tab -> navigatedTabs.add(tab) },
                    initialTab = 0
                )
            }
        }
        composeRule.waitForIdle()

        // Only 2 '+' buttons exist: top-bar Body Weight add + Weight summary card (ZERO '+' on threshold cards!)
        composeRule.onAllNodesWithContentDescription(addLabel).assertCountEquals(2)

        // Prominent "Edit in Zones" CTA is visible and routes FTP -> Power Bike (tab 2)
        composeRule.onNodeWithTag("athlete_profile_edit_in_zones_cta").performScrollTo().assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        assertEquals(listOf(2), navigatedTabs)

        // Tapping HRmax (Bike) card selects its trend for charting
        composeRule.onNodeWithText("184 bpm").performScrollTo().assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        assertEquals(listOf(PlotMetricType.HR_MAX_BIKE), selectedPlot)

        // Edit in Zones icons exist on threshold cards (HRrest, FTP, CP, W', HRmax Run/Bike, LTHR Run/Bike = 8)
        composeRule.onAllNodesWithContentDescription(editInZonesLabel).assertCountEquals(8)
    }
}
