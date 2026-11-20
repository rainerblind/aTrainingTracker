/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.atrainingtracker.trainingtracker.ui.settings.tuning

import android.app.Application
import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.MyUnits
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.database.WorkoutClusterRepository
import com.atrainingtracker.trainingtracker.ui.settings.tuning.categories.ClusterTuningSection
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Robolectric `createComposeRule()` behavioral UI tests for [ClusterTuningContent] and
 * [ClusterTuningSection] (`REQ-SET-010`, `REQ-SET-011`, `TST-SET-003`, `REQ-PRO-132`, `REQ-PRO-133`).
 */
// Linked test specs: TST-SET-003
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w412dp-h915dp-xxhdpi")
class ClusterTuningUiBehaviorTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var mockClusterRepo: WorkoutClusterRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        TrainingApplication.setUnitForTesting(MyUnits.METRIC)
        mockClusterRepo = mockk(relaxed = true)
        every { mockClusterRepo.migrationStatus } returns MutableStateFlow(null)
        WorkoutClusterRepository.resetForTesting(mockClusterRepo)
    }

    @After
    fun tearDown() {
        WorkoutClusterRepository.resetForTesting(null)
    }

    @Test
    fun clusterTuningSection_masterSliderDetailsToggleAndRecalculateConfirm_TST_SET_003() {
        // TST-SET-003
        var endpointTolChanged = false
        composeTestRule.setContent {
            ATrainingTrackerTheme {
                var endTol by remember { mutableFloatStateOf(200f) }
                var apexTol by remember { mutableFloatStateOf(400f) }
                var distTol by remember { mutableFloatStateOf(0.20f) }
                var altTol by remember { mutableFloatStateOf(400f) }
                var useSport by remember { mutableStateOf(true) }
                var useAlt by remember { mutableStateOf(true) }
                var scoreThreshold by remember { mutableFloatStateOf(1.2f) }

                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    ClusterTuningSection(
                        endpointTolerance = endTol,
                        onEndpointToleranceChange = { endTol = it; endpointTolChanged = true },
                        apexTolerance = apexTol,
                        onApexToleranceChange = { apexTol = it },
                        distanceTolerance = distTol,
                        onDistanceToleranceChange = { distTol = it },
                        altitudePositionTolerance = altTol,
                        onAltitudePositionToleranceChange = { altTol = it },
                        useSportTypeForClustering = useSport,
                        onUseSportTypeChange = { useSport = it },
                        useAltitudePosForClustering = useAlt,
                        onUseAltitudePosChange = { useAlt = it },
                        clusterScoreThreshold = scoreThreshold,
                        onClusterScoreThresholdChange = { scoreThreshold = it }
                    )
                }
            }
        }

        // 1. Master slider visible and adjustable
        composeTestRule.onNodeWithText(context.getString(R.string.cluster_tuning_master_label)).assertIsDisplayed()
        composeTestRule.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo(0f, 0f..1f).let {
            // match first slider (master slider has range 0f..1f)
            ProgressBarRangeInfo(
                current = (
                    ((200f - 10f) / 490f) +
                        ((400f - 10f) / 490f) +
                        ((0.20f - 0.01f) / 0.19f) +
                        ((1.2f - 0.5f) / 2.0f) +
                        ((400f - 10f) / 490f)
                    ) / 5f,
                range = 0f..1f
            )
        })).performSemanticsAction(SemanticsActions.SetProgress) { setProgress ->
            setProgress(0.8f)
        }
        assertTrue("Adjusting master slider must update endpoint tolerance", endpointTolChanged)

        // 2. Toggle detailed controls -> detailed sliders appear
        composeTestRule.onNode(isToggleable()).performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.cluster_tuning_endpoint_label)).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.cluster_tuning_apex_label)).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.cluster_tuning_distance_label)).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.cluster_tuning_score_threshold_label)).performScrollTo().assertIsDisplayed()

        // 3. Tap Recalculate -> confirmation dialog appears -> confirm triggers repository
        val recalcBtnText = context.getString(R.string.cluster_tuning_recalculate_button)
        composeTestRule.onNodeWithText(recalcBtnText).performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.cluster_tuning_warning)).assertIsDisplayed()
        composeTestRule.onAllNodes(hasText(recalcBtnText) and hasClickAction()).onLast()
            .performSemanticsAction(SemanticsActions.OnClick)

        verify(exactly = 1) {
            mockClusterRepo.recalculateClustersAsync(any(), any(), any(), any(), any(), any(), any())
        }
    }
}
