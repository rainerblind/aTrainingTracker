/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.atrainingtracker.trainingtracker.ui.components.workoutextrema

import android.app.Application
import androidx.compose.material3.Surface
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * Behavioral Robolectric Compose UI verification for [WorkoutExtrema] (`REQ-UI-011`, `REQ-UI-054`):
 * - `TST-UI-011`: Units formatted inside square brackets (`[bpm]`, `[km/h]`, `[W]`).
 * - `TST-UI-012`: Sensor label & numeric values share 16.sp/bodyLarge visual scale vs micro unit.
 * - `TST-UI-013`: Data row elements (`label`, `[unit]`, `min`, `avg`, `max`) align to bottom edge.
 * - `TST-UI-014`: Units render with micro fontSize (`< 16.sp`) and lighter reduced alpha (`< 1f`).
 * - `TST-UI-015`: Average column header displays `"Ø"` rather than `"average"`.
 * - `TST-UI-019`: Context-aware bolding (HR/Power: Avg+Max; Speed/Cadence: Avg; Alt/Temp: Min+Max).
 * - `TST-UI-020` / `TST-UI-059`: Placeholder `"-"` uses disabled alpha (`< unit alpha < value alpha`).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w412dp-h915dp-xxhdpi")
class WorkoutExtremaUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun readTextLayout(interaction: SemanticsNodeInteraction): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        val action = interaction.fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult]
        assertTrue("GetTextLayoutResult action succeeded", action.action?.invoke(results) == true)
        assertTrue("At least one TextLayoutResult present", results.isNotEmpty())
        return results.first()
    }

    private fun sampleExtremaState(): ExtremaUiState = ExtremaUiState(
        workoutId = 42L,
        rows = listOf(
            ExtremaRowUiState("Power", "-", "215", "640", "W", R.drawable.ic_power, boldAvg = true, boldMax = true),
            ExtremaRowUiState("HR", "65", "142", "185", "bpm", R.drawable.ic_heart_rate, boldAvg = true, boldMax = true),
            ExtremaRowUiState("Speed", "-", "24.5", "52.1", "km/h", R.drawable.ic_speed, boldAvg = true),
            ExtremaRowUiState("Cadence", "-", "85", "112", "rpm", R.drawable.ic_cadence, boldAvg = true),
            ExtremaRowUiState("Alt", "150", "210", "410", "m", R.drawable.ic_altitude, boldMin = true, boldMax = true),
            ExtremaRowUiState("Temp", "18", "22", "25", "°C", R.drawable.ic_temp_max, boldMin = true, boldMax = true)
        )
    )

    @Test
    fun workoutExtrema_verifiesBracketsNotationTypographyHierarchyAlignmentAndAlphas_TST_UI_011_015_019_020_059() {
        // TST-UI-011, TST-UI-012, TST-UI-013, TST-UI-014, TST-UI-015, TST-UI-019, TST-UI-020, TST-UI-059
        composeTestRule.setContent {
            ATrainingTrackerTheme {
                Surface {
                    WorkoutExtrema(uiState = sampleExtremaState())
                }
            }
        }
        composeTestRule.waitForIdle()

        // TST-UI-015: Header uses mathematical Ø symbol and not the word "average"
        composeTestRule.onNodeWithText("Ø").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("average", ignoreCase = true).fetchSemanticsNodes().also {
            assertTrue("Header must not spell 'average'", it.isEmpty())
        }

        // TST-UI-011: Units surrounded by square brackets
        composeTestRule.onNodeWithText("[W]").assertIsDisplayed()
        composeTestRule.onNodeWithText("[bpm]").assertIsDisplayed()
        composeTestRule.onNodeWithText("[km/h]").assertIsDisplayed()
        composeTestRule.onNodeWithText("[rpm]").assertIsDisplayed()
        composeTestRule.onNodeWithText("[m]").assertIsDisplayed()
        composeTestRule.onNodeWithText("[°C]").assertIsDisplayed()

        // TST-UI-012 & TST-UI-014: Label and numeric values share bodyLarge (16.sp); unit uses smaller labelSmall + reduced alpha
        val hrLabelStyle = readTextLayout(composeTestRule.onNodeWithText("HR")).layoutInput.style
        val hrUnitStyle = readTextLayout(composeTestRule.onNodeWithText("[bpm]")).layoutInput.style
        val hrAvgStyle = readTextLayout(composeTestRule.onNodeWithText("142")).layoutInput.style
        assertEquals(hrLabelStyle.fontSize.value, hrAvgStyle.fontSize.value, 0.5f)
        assertTrue("Unit fontSize (${hrUnitStyle.fontSize}) must be smaller than label (${hrLabelStyle.fontSize})", hrUnitStyle.fontSize.value < hrLabelStyle.fontSize.value)
        assertTrue("Unit alpha (${hrUnitStyle.color.alpha}) must be lighter than value (${hrAvgStyle.color.alpha})", hrUnitStyle.color.alpha < hrAvgStyle.color.alpha)

        // TST-UI-013: Label, unit, min/avg/max on HR row share aligned bottom edge
        val hrLabelBounds = composeTestRule.onNodeWithText("HR").getBoundsInRoot()
        val hrUnitBounds = composeTestRule.onNodeWithText("[bpm]").getBoundsInRoot()
        val hrAvgBounds = composeTestRule.onNodeWithText("142").getBoundsInRoot()
        val hrMaxBounds = composeTestRule.onNodeWithText("185").getBoundsInRoot()
        assertTrue("Unit bottom aligns with label bottom", abs((hrUnitBounds.bottom - hrLabelBounds.bottom).value) <= 3f)
        assertTrue("Avg bottom aligns with label bottom", abs((hrAvgBounds.bottom - hrLabelBounds.bottom).value) <= 3f)
        assertTrue("Max bottom aligns with label bottom", abs((hrMaxBounds.bottom - hrLabelBounds.bottom).value) <= 3f)

        // TST-UI-019: Context-aware bolding
        assertEquals(FontWeight.Bold, readTextLayout(composeTestRule.onNodeWithText("142")).layoutInput.style.fontWeight) // HR Avg
        assertEquals(FontWeight.Bold, readTextLayout(composeTestRule.onNodeWithText("185")).layoutInput.style.fontWeight) // HR Max
        assertTrue(readTextLayout(composeTestRule.onNodeWithText("65")).layoutInput.style.fontWeight != FontWeight.Bold) // HR Min not bold

        assertEquals(FontWeight.Bold, readTextLayout(composeTestRule.onNodeWithText("24.5")).layoutInput.style.fontWeight) // Speed Avg
        assertTrue(readTextLayout(composeTestRule.onNodeWithText("52.1")).layoutInput.style.fontWeight != FontWeight.Bold) // Speed Max not bold

        assertEquals(FontWeight.Bold, readTextLayout(composeTestRule.onNodeWithText("150")).layoutInput.style.fontWeight) // Alt Min
        assertEquals(FontWeight.Bold, readTextLayout(composeTestRule.onNodeWithText("410")).layoutInput.style.fontWeight) // Alt Max
        assertTrue(readTextLayout(composeTestRule.onNodeWithText("210")).layoutInput.style.fontWeight != FontWeight.Bold) // Alt Avg not bold

        // TST-UI-020 / TST-UI-059: Placeholder "-" has disabled alpha < unit medium alpha < full numeric alpha
        val dashAlpha = readTextLayout(composeTestRule.onAllNodesWithText("-").onFirst()).layoutInput.style.color.alpha
        assertTrue("Placeholder '-' alpha ($dashAlpha) must be lighter than unit alpha (${hrUnitStyle.color.alpha})", dashAlpha < hrUnitStyle.color.alpha)
        assertTrue("Placeholder '-' alpha ($dashAlpha) must be <= 0.45", dashAlpha <= 0.45f)
    }
}
