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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BANALService
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.MyUnits
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.repositories.SimpleSportTypeInfo
import com.atrainingtracker.trainingtracker.repositories.SportTypesRepository
import com.atrainingtracker.trainingtracker.ui.settings.tuning.TuningSubtitleFormatter
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
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
import org.robolectric.annotation.GraphicsMode
import java.util.Locale

/**
 * Robolectric `createComposeRule()` verification for Imperial unit rendering in cluster tuning
 * and locale-aware comma decimal input (`"5,5"`) in [ManualClusterScreen]
 * (`REQ-SET-023`, `REQ-SET-039`, `TST-SET-015`, `TST-SET-030`, `REQ-PRO-132`, `REQ-PRO-133`).
 */
// Linked test specs: TST-SET-015, TST-SET-030
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w412dp-h915dp-xxhdpi")
class ClusterImperialAndLocaleUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var previousLocale: Locale

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        previousLocale = Locale.getDefault()
        val mockSportRepo = mockk<SportTypesRepository>(relaxed = true)
        every { mockSportRepo.sportTypesList } returns listOf(
            SimpleSportTypeInfo(1L, "Road Cycling", BSportType.BIKE)
        )
        SportTypesRepository.resetForTesting(mockSportRepo)
    }

    @After
    fun tearDown() {
        Locale.setDefault(previousLocale)
        TrainingApplication.setUnitForTesting(MyUnits.METRIC)
        SportTypesRepository.resetForTesting(null)
    }

    @Test
    fun clusterTuningContent_andSubtitleFormatter_renderImperialMiles_TST_SET_015() {
        // TST-SET-015
        TrainingApplication.setUnitForTesting(MyUnits.IMPERIAL)

        val subtitle = TuningSubtitleFormatter.formatClusterTuningSubtitle(
            endpointTolerance = 200f,
            apexTolerance = 400f,
            distanceTolerance = 0.20f,
            altitudePositionTolerance = 400f,
            useSportTypeForClustering = true,
            useAltitudePosForClustering = true,
            isImperial = true,
            context = context
        )
        val imperialUnit = context.getString(R.string.units_distance_imperial)
        assertTrue("Imperial subtitle must contain '$imperialUnit', was '$subtitle'", subtitle.contains(imperialUnit))
        assertEquals(1609.344, BANALService.METER_PER_MILE, 0.5)

        composeTestRule.setContent {
            ATrainingTrackerTheme {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    ClusterTuningContent(
                        endpointTolerance = 200f,
                        onEndpointToleranceChange = {},
                        apexTolerance = 400f,
                        onApexToleranceChange = {},
                        distanceTolerance = 0.20f,
                        onDistanceToleranceChange = {},
                        altitudePositionTolerance = 400f,
                        onAltitudePositionToleranceChange = {}
                    )
                }
            }
        }

        composeTestRule.onNode(isToggleable()).performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onAllNodesWithText(imperialUnit, substring = true).onFirst().performScrollTo().assertIsDisplayed()
    }

    @Test
    fun manualClusterScreen_acceptsGermanCommaDecimalDistanceInput_TST_SET_030() {
        // TST-SET-030
        Locale.setDefault(Locale.GERMAN)
        TrainingApplication.setUnitForTesting(MyUnits.METRIC)
        val mockViewModel = mockk<WorkoutClustersViewModel>(relaxed = true)
        every { mockViewModel.currentLocation } returns MutableStateFlow(null)

        composeTestRule.setContent {
            val germanConfig = android.content.res.Configuration(context.resources.configuration).apply {
                setLocale(Locale.GERMAN)
            }
            CompositionLocalProvider(LocalConfiguration provides germanConfig) {
                ATrainingTrackerTheme {
                    ManualClusterScreen(
                        viewModel = mockViewModel,
                        onBack = {}
                    )
                }
            }
        }

        val unitStr = context.getString(R.string.units_distance_metric)
        val distanceLabel = context.getString(R.string.cluster_manual_distance_hint, unitStr)
        composeTestRule.onNodeWithText(distanceLabel).performTextInput("5,5")
        composeTestRule.onNodeWithText("5,5").assertIsDisplayed()
    }
}
