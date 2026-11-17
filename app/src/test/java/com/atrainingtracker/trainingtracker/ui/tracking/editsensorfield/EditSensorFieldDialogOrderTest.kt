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

package com.atrainingtracker.trainingtracker.ui.tracking.editsensorfield

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Compose UI verification for 3-group logical section ordering, conditional guards,
 * and callback preservation in [EditSensorFieldDialog] (REQ-UI-2782, TST-UI-2782.1, TST-UI-2782.2).
 */
// Traces to: REQ-UI-2782, TST-UI-2782.1, TST-UI-2782.2
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w420dp-h1600dp-xhdpi")
class EditSensorFieldDialogOrderTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private fun createMockViewModel(initialState: EditDialogUiState): Pair<EditSensorFieldViewModel, MutableStateFlow<EditDialogUiState>> {
        val stateFlow = MutableStateFlow(initialState)
        val viewModel = mockk<EditSensorFieldViewModel>(relaxed = true)
        every { viewModel.uiState } returns stateFlow
        return viewModel to stateFlow
    }

    private fun topOf(text: String, substring: Boolean = false): Float {
        return composeTestRule
            .onNodeWithText(text, substring = substring)
            .fetchSemanticsNode()
            .boundsInRoot
            .top
    }

    /**
     * TST-UI-2782.1: Verifies strict top-to-bottom vertical bounds across all 3 logical groups
     * (Messdaten -> Kachel-Geometrie -> Visuelle Darstellung).
     */
    @Test
    fun testLogicalThreeGroupVerticalOrdering_allGroupsActive() {
        val state = EditDialogUiState(
            selectedSensorType = SensorType.POWER,
            sportType = BSportType.BIKE,
            availableSensorTypesForCurrentActivityType = listOf(SensorType.POWER, SensorType.HR, SensorType.SPEED_mps),
            selectedDeviceId = 42L,
            selectedDeviceName = "Garmin Rally",
            availableDevices = listOf(-1L to "Best Sensor", 42L to "Garmin Rally"),
            filterSummary = "3s Ø",
            showValue = true,
            showBar = true,
            showGraph = true,
            graphHorizonSeconds = 300,
            graphColor = "VIOLET",
            colWeight = 2f,
            isMultiFieldRow = true
        )
        val (viewModel, _) = createMockViewModel(state)

        composeTestRule.setContent {
            ATrainingTrackerTheme {
                EditSensorFieldDialog(
                    title = context.getString(R.string.edit_field),
                    viewModel = viewModel,
                    onDismissRequest = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        val sensorTop = topOf(context.getString(SensorType.POWER.fullNameId))
        val sourceTop = topOf(context.getString(R.string.source))
        val filterTop = topOf("${context.getString(R.string.filter)}: ", substring = true)
        val textSizeTop = topOf(context.getString(R.string.text_size))
        val relativeWeightTop = topOf(context.getString(R.string.sensor_field_relative_weight))
        val displayElementsTop = topOf(context.getString(R.string.sensor_field_display_elements_title))
        val graphHorizonTop = topOf(context.getString(R.string.sensor_field_graph_horizon))
        val graphColorTop = topOf(context.getString(R.string.cockpit_graph_color_title))
        val previewTop = topOf(context.getString(R.string.Preview).replaceFirstChar { it.uppercase() })

        assertTrue("Sensortyp ($sensorTop) must precede Quelle ($sourceTop)", sensorTop < sourceTop)
        assertTrue("Quelle ($sourceTop) must precede Filter ($filterTop)", sourceTop < filterTop)
        assertTrue("Filter ($filterTop) must precede Textgröße ($textSizeTop)", filterTop < textSizeTop)
        assertTrue("Textgröße ($textSizeTop) must precede Relative Breite ($relativeWeightTop)", textSizeTop < relativeWeightTop)
        assertTrue("Relative Breite ($relativeWeightTop) must precede Anzeigeelemente ($displayElementsTop)", relativeWeightTop < displayElementsTop)
        assertTrue("Anzeigeelemente ($displayElementsTop) must precede Zeitfenster ($graphHorizonTop)", displayElementsTop < graphHorizonTop)
        assertTrue("Zeitfenster ($graphHorizonTop) must precede Graph-Farbe ($graphColorTop)", graphHorizonTop < graphColorTop)
        assertTrue("Graph-Farbe ($graphColorTop) must precede Live-Vorschau ($previewTop)", graphColorTop < previewTop)
    }

    /**
     * TST-UI-2782.1: Verifies metric-specific guidance blocks (POWER_TO_WEIGHT, INTENSITY_FACTOR, HRV_DFA_A1)
     * render strictly below the Visual Representation section.
     */
    @Test
    fun testMetricGuidanceBlocksRenderBelowVisualRepresentation() {
        val state = EditDialogUiState(
            selectedSensorType = SensorType.HRV_DFA_A1,
            sportType = BSportType.BIKE,
            availableSensorTypesForCurrentActivityType = listOf(SensorType.HRV_DFA_A1, SensorType.POWER_TO_WEIGHT, SensorType.INTENSITY_FACTOR),
            availableDevices = listOf(-1L to "Best Sensor"),
            showValue = true,
            showGraph = true,
            isMultiFieldRow = false
        )
        val (viewModel, stateFlow) = createMockViewModel(state)

        composeTestRule.setContent {
            ATrainingTrackerTheme {
                EditSensorFieldDialog(
                    title = context.getString(R.string.edit_field),
                    viewModel = viewModel,
                    onDismissRequest = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        val displayElementsTopHrv = topOf(context.getString(R.string.sensor_field_display_elements_title))
        val previewTopHrv = topOf(context.getString(R.string.Preview).replaceFirstChar { it.uppercase() })
        val hrvInfoTop = topOf(context.getString(R.string.hrv_info_title))
        assertTrue("Display elements must precede HRV guidance", displayElementsTopHrv < hrvInfoTop)
        assertTrue("Live preview ($previewTopHrv) must precede HRV guidance ($hrvInfoTop)", previewTopHrv < hrvInfoTop)

        stateFlow.value = state.copy(selectedSensorType = SensorType.POWER_TO_WEIGHT, showGraph = false)
        composeTestRule.waitForIdle()
        val displayElementsTopPtw = topOf(context.getString(R.string.sensor_field_display_elements_title))
        val weightBtnTop = topOf(context.getString(R.string.athlete_weight_title), substring = true)
        assertTrue("Display elements ($displayElementsTopPtw) must precede Athlete Weight ($weightBtnTop)", displayElementsTopPtw < weightBtnTop)

        stateFlow.value = state.copy(selectedSensorType = SensorType.INTENSITY_FACTOR, showGraph = false)
        composeTestRule.waitForIdle()
        val textSizeTopIf = topOf(context.getString(R.string.text_size))
        val ftpTop = topOf("FTP:", substring = true)
        assertTrue("Textgröße ($textSizeTopIf) must precede FTP guidance ($ftpTop)", textSizeTopIf < ftpTop)
    }

    /**
     * TST-UI-2782.2: Verifies conditional omission when filteringPossible=false, isMultiFieldRow=false,
     * single -1L source device, and showGraph=false.
     */
    @Test
    fun testConditionalGuards_omissionsPreserveLogicalOrder() {
        val state = EditDialogUiState(
            selectedSensorType = SensorType.TIME_ACTIVE, // filteringPossible == false, isGraphSupported == false
            sportType = BSportType.BIKE,
            availableSensorTypesForCurrentActivityType = listOf(SensorType.TIME_ACTIVE, SensorType.POWER),
            availableDevices = listOf(-1L to "Best Sensor"),
            isMultiFieldRow = false,
            showValue = true,
            showGraph = false
        )
        val (viewModel, stateFlow) = createMockViewModel(state)

        composeTestRule.setContent {
            ATrainingTrackerTheme {
                EditSensorFieldDialog(
                    title = context.getString(R.string.edit_field),
                    viewModel = viewModel,
                    onDismissRequest = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(context.getString(R.string.source)).assertDoesNotExist()
        composeTestRule.onNodeWithText("${context.getString(R.string.filter)}:", substring = true).assertDoesNotExist()
        composeTestRule.onNodeWithText(context.getString(R.string.sensor_field_relative_weight)).assertDoesNotExist()
        composeTestRule.onNodeWithText(context.getString(R.string.sensor_field_graph_horizon)).assertDoesNotExist()
        composeTestRule.onNodeWithText(context.getString(R.string.cockpit_graph_color_title)).assertDoesNotExist()

        val sensorTop = topOf(context.getString(SensorType.TIME_ACTIVE.fullNameId))
        val textSizeTop = topOf(context.getString(R.string.text_size))
        assertTrue("Sensortyp ($sensorTop) must precede Textgröße ($textSizeTop)", sensorTop < textSizeTop)

        // When graph IS supported (POWER) but showGraph == false, Display Elements appears without Graph sub-options
        stateFlow.value = state.copy(selectedSensorType = SensorType.POWER, showGraph = false)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.sensor_field_display_elements_title)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.sensor_field_graph_horizon)).assertDoesNotExist()
    }

    /**
     * TST-UI-2782.2: Verifies interactive callbacks across all 3 groups remain wired to EditSensorFieldViewModel.
     */
    @Test
    fun testCallbacksPreserved_acrossAllThreeGroups() {
        val state = EditDialogUiState(
            selectedSensorType = SensorType.POWER,
            sportType = BSportType.BIKE,
            availableSensorTypesForCurrentActivityType = listOf(SensorType.POWER),
            availableDevices = listOf(-1L to "Best Sensor", 42L to "Garmin Rally"),
            selectedDeviceId = 42L,
            filterSummary = "1s",
            isMultiFieldRow = true,
            colWeight = 1f,
            showValue = true,
            showGraph = false
        )
        val (viewModel, _) = createMockViewModel(state)
        var dismissed = false

        composeTestRule.setContent {
            ATrainingTrackerTheme {
                EditSensorFieldDialog(
                    title = context.getString(R.string.edit_field),
                    viewModel = viewModel,
                    onDismissRequest = { dismissed = true }
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("${context.getString(R.string.filter)}: ", substring = true).performClick()
        verify(exactly = 1) { viewModel.onConfigureFilterClicked() }

        composeTestRule.onNodeWithText("2x").performClick()
        verify(exactly = 1) { viewModel.onColWeightChanged(2f) }

        composeTestRule.onNodeWithText(context.getString(R.string.sensor_field_show_graph)).performClick()
        verify(exactly = 1) { viewModel.onShowGraphChanged(true) }

        composeTestRule.onNodeWithText(context.getString(R.string.save)).performClick()
        verify(exactly = 1) { viewModel.saveChanges() }
        assertTrue("Save action must invoke onDismissRequest", dismissed)
    }
}
