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

package com.atrainingtracker.trainingtracker.ui.aftermath

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.WorkoutCardSectionPreferences
import com.atrainingtracker.trainingtracker.WorkoutDetailPreferences
import com.atrainingtracker.trainingtracker.settings.TuningConfig
import com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist.WorkoutSummary
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime

/**
 * Behavioral Compose UI tests verifying that Workout Details keeps Coggan Power,
 * Aerobic Efficiency, and Critical Power / W'bal visible when `show*InWorkoutSummary`
 * toggles are `false` (`enable* = true`), while [WorkoutSummary] hides them
 * (`ATT-8079` / `REQ-UI-2787.2` / `TST-UI-2787.2`).
 *
 * Strictly performs zero `src/main` file reads (`REQ-PRO-049.1`).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class WorkoutDetailSummaryDecouplingTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun sampleWorkoutWithPhysiologyModels(): WorkoutData = WorkoutData(
        id = 777L,
        finished = true,
        fileBaseName = "ride_777.tcx",
        workoutName = "Threshold Interval Ride",
        sportId = 1L,
        sportName = "Road Bike",
        bSportType = BSportType.BIKE,
        startTimeS = 1_760_000_000L,
        formattedDate = "18.11.2026",
        formattedTime = "08:30",
        localDateTime = LocalDateTime.of(2026, 11, 18, 8, 30),
        equipmentName = "Road Bike",
        equipmentId = 1L,
        commute = false,
        trainer = false,
        mapPolyline = "",
        encodedAltitudes = "",
        encodedDistances = "",
        uploadToStrava = 0,
        totalDistance = 42_000.0,
        maxDisplacement = 15_000.0,
        activeTimeSec = 3600L,
        totalTimeSec = 3660L,
        avgSpeedMps = 11.6,
        ascentMeters = 450L,
        descentMeters = 450L,
        minAltitude = 500.0,
        maxAltitude = 680.0,
        description = null,
        goal = null,
        method = null,
        stravaSportName = null,
        normalizedPowerWatts = 248.0,
        intensityFactor = 0.88,
        tss = 77.4,
        variabilityIndex = 1.06,
        efficiencyFactor = 1.62,
        aerobicDecouplingPct = 3.4,
        isPowerBasedEfficiency = true,
        minWPrimeBalKj = 8.5,
        minWPrimeBalPct = 42.0
    )

    private fun summaryHiddenTuningConfig(): TuningConfig = TuningConfig(
        enableCogganPowerMetrics = true,
        showCogganPowerInWorkoutSummary = false,
        enableAerobicEfficiencyModel = true,
        showAerobicEfficiencyInWorkoutSummary = false,
        enableCriticalPowerModel = true,
        showCriticalPowerInWorkoutSummary = false
    )

    @Test
    fun `TST-UI-2787_2 workoutDetailsShowsPhysiologyModelsWhenSummaryFlagsFalse`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val workoutData = sampleWorkoutWithPhysiologyModels()
        val tabs = listOf(
            WorkoutDetailTabConfig(
                id = "tab_overview",
                preset = WorkoutDetailTabPreset.OVERVIEW,
                sections = listOf(WorkoutDetailSectionType.DETAILS)
            )
        )

        composeTestRule.setContent {
            ATrainingTrackerTheme(darkTheme = false) {
                Box(modifier = Modifier.fillMaxSize()) {
                    WorkoutDetailTabbedContent(
                        workoutData = workoutData,
                        tabs = tabs,
                        availability = WorkoutDetailSectionAvailability(hasDetails = true),
                        detailPreferences = WorkoutDetailPreferences(),
                        tuningConfig = summaryHiddenTuningConfig(),
                        activeScrubPath = null,
                        hasAltitudeData = false,
                        splitChartData = null,
                        selectedLapNr = null,
                        onSelectLapNr = {},
                        hrZoneDistribution = null,
                        powerZoneDistribution = null,
                        effectiveHrZoneDisplayMode = com.atrainingtracker.trainingtracker.ui.aftermath.zones.ZoneCardDisplayMode.HISTOGRAM,
                        effectivePowerZoneDisplayMode = com.atrainingtracker.trainingtracker.ui.aftermath.zones.ZoneCardDisplayMode.HISTOGRAM,
                        onHrZoneDisplayModeChange = {},
                        onPowerZoneDisplayModeChange = {},
                        useStatusBarsPadding = false,
                        onClusterClick = null,
                        onEditWorkout = null,
                        headerActions = {},
                        onOpenTabsConfig = {},
                        mapContent = {},
                        mapOverlay = {}
                    )
                }
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.normalized_power_short)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.intensity_factor_short)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.tss_short)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.workout_ef_short)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.w_prime_bal_short)).assertIsDisplayed()
    }

    @Test
    fun `TST-UI-2787_2 workoutSummaryStillHidesPhysiologyModelsWhenSummaryFlagsFalse`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val workoutData = sampleWorkoutWithPhysiologyModels()

        composeTestRule.setContent {
            ATrainingTrackerTheme(darkTheme = false) {
                WorkoutSummary(
                    workoutData = workoutData,
                    isPlayServiceAvailable = false,
                    onExport = {},
                    onSaveAsRoute = {},
                    onDeleteRequest = {},
                    onEditWorkout = {},
                    onMapClick = {},
                    preferences = WorkoutCardSectionPreferences(
                        showDetails = true,
                        showDescription = false,
                        showExtrema = false,
                        showLaps = false,
                        showStrava = false,
                        showMapPreview = false,
                        showElevationProfile = false,
                        showTelemetryCharts = false,
                        showZoneAnalysis = false,
                        showGradeDistribution = false,
                        showHrv = false,
                        showExportStatus = false,
                        showPhotos = false,
                        showRpeAndFeel = false
                    ),
                    tuningConfig = summaryHiddenTuningConfig()
                )
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.normalized_power_short)).assertDoesNotExist()
        composeTestRule.onNodeWithText(context.getString(R.string.workout_ef_short)).assertDoesNotExist()
        composeTestRule.onNodeWithText(context.getString(R.string.w_prime_bal_short)).assertDoesNotExist()
    }
}
