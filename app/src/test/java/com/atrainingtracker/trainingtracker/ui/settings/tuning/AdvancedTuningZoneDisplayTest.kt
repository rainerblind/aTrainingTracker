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

package com.atrainingtracker.trainingtracker.ui.settings.tuning

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.settings.SettingsDataStore
import com.atrainingtracker.trainingtracker.settings.ZoneDisplayOptions
import com.atrainingtracker.trainingtracker.ui.settings.ZoneDisplayOptionsDraft
import com.atrainingtracker.trainingtracker.ui.settings.ZoneProfileTarget
import com.atrainingtracker.trainingtracker.ui.settings.tuning.categories.CockpitTypographySection
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.atrainingtracker.trainingtracker.ui.tracking.typography.CockpitFontFamily
import com.atrainingtracker.trainingtracker.ui.tracking.typography.CockpitFontWeight
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Unit, Compose UI, and DataStore Save/Cancel/Reset parity tests for [ZoneDisplayOptions]
 * integration in Cockpit & Tiles (`REQ-UI-2783`, `TST-UI-2783.1`, `TST-UI-2783.2`, `TST-UI-2783.3`).
 */
// Traces to: REQ-UI-2783, TST-UI-2783.1, TST-UI-2783.2, TST-UI-2783.3
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w420dp-h1400dp-xhdpi")
class AdvancedTuningZoneDisplayTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private fun clickTextColorSwitch(scroll: Boolean = true) {
        val switches = composeTestRule.onAllNodes(isToggleable())
        assertEquals(4, switches.fetchSemanticsNodes().size)
        val node = switches[3]
        if (scroll) node.performScrollTo()
        node.performClick()
    }

    private fun waitForSnapshotCondition(
        dataStore: SettingsDataStore,
        predicate: (com.atrainingtracker.trainingtracker.settings.SettingsSnapshot) -> Boolean
    ) {
        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            runBlocking { predicate(dataStore.snapshotFlow.first()) }
        }
    }

    @Test
    fun testZoneDisplayOptionsDraft_applyToAll_copiesFourFlagsAndPreservesHrvGauge() {
        val initial = ZoneDisplayOptionsDraft(
            hrRun = ZoneDisplayOptions(
                showBackground = false,
                showLeftBar = false,
                showRightBar = true,
                showTextColor = true,
                showGauge = false
            ),
            hrBike = ZoneDisplayOptions(),
            pwrBike = ZoneDisplayOptions(),
            hrv = ZoneDisplayOptions(
                showBackground = true,
                showLeftBar = true,
                showRightBar = true,
                showTextColor = false,
                showGauge = true
            )
        )

        val applied = initial.applyToAllFrom(ZoneProfileTarget.HR_RUN)

        val expectedShared = ZoneDisplayOptions(
            showBackground = false,
            showLeftBar = false,
            showRightBar = true,
            showTextColor = true,
            showGauge = false
        )
        assertEquals(expectedShared, applied.hrRun)
        assertEquals(expectedShared, applied.hrBike)
        assertEquals(expectedShared, applied.pwrBike)
        assertEquals(expectedShared.copy(showGauge = true), applied.hrv)
    }

    @Test
    fun testZoneDisplayOptionsDraft_persistAndReset_preservesZoneThresholds() = runBlocking {
        val dataStore = SettingsDataStore.getInstance(context)
        val customThresholds = listOf(115, 130, 145, 160)
        dataStore.applyLatestZoneBoundaries(SettingsDataStore.ZoneType.HR_RUN, customThresholds)

        val customDraft = ZoneDisplayOptionsDraft(
            hrRun = ZoneDisplayOptions(showBackground = false, showTextColor = true),
            hrv = ZoneDisplayOptions(showGauge = true)
        )
        customDraft.persistTo(dataStore)

        val savedSnapshot = dataStore.snapshotFlow.first()
        assertFalse(savedSnapshot.hrRunDisplayOptions.showBackground)
        assertTrue(savedSnapshot.hrRunDisplayOptions.showTextColor)
        assertTrue(savedSnapshot.hrvDisplayOptions.showGauge)

        // Resetting ZoneDisplayOptions to defaults must never clobber custom zone thresholds
        ZoneDisplayOptionsDraft().persistTo(dataStore)
        val resetSnapshot = dataStore.snapshotFlow.first()
        assertEquals(ZoneDisplayOptions(), resetSnapshot.hrRunDisplayOptions)
        assertEquals(ZoneDisplayOptions(), resetSnapshot.hrvDisplayOptions)
        assertEquals(customThresholds, resetSnapshot.hrRunZoneMax)
    }

    @Test
    fun testCockpitTypographySection_profileSwitchTogglesAndCombinedPreview() {
        var draft by mutableStateOf(
            ZoneDisplayOptionsDraft(
                hrv = ZoneDisplayOptions(showGauge = true)
            )
        )

        composeTestRule.setContent {
            ATrainingTrackerTheme {
                CockpitTypographySection(
                    cockpitFontFamily = CockpitFontFamily.SYSTEM_DEFAULT,
                    onFontFamilyChange = {},
                    cockpitFontWeight = CockpitFontWeight.BOLD,
                    onFontWeightChange = {},
                    zoneDisplayDraft = draft,
                    onZoneDisplayDraftChange = { draft = it }
                )
            }
        }
        composeTestRule.waitForIdle()

        // Default profile is HR_RUN -> preview shows 148 bpm and no gauge toggle
        composeTestRule.onNodeWithText("148").assertIsDisplayed()
        composeTestRule.onNodeWithText("bpm").assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.zone_display_gauge)).assertDoesNotExist()

        // Toggle Text Color on HR_RUN
        clickTextColorSwitch(scroll = false)
        composeTestRule.waitForIdle()
        assertTrue(draft.hrRun.showTextColor)

        // Apply to all from HR_RUN preserves HRV.showGauge == true
        composeTestRule.onNodeWithText(context.getString(R.string.zone_display_apply_all)).performClick()
        assertTrue(draft.hrBike.showTextColor)
        assertTrue(draft.pwrBike.showTextColor)
        assertTrue(draft.hrv.showTextColor)
        assertTrue(draft.hrv.showGauge)

        // Switch to PWR_BIKE -> preview shows 245 W
        composeTestRule.onNodeWithText(context.getString(R.string.tab_pwr_bike)).performClick()
        composeTestRule.onNodeWithText("245").assertIsDisplayed()
        composeTestRule.onNodeWithText("W").assertIsDisplayed()

        // Switch to HRV_DFA_A1 -> preview shows 0.82 α1 and gauge toggle appears
        composeTestRule.onNodeWithText(context.getString(R.string.tab_hrv_dfa_a1)).performClick()
        composeTestRule.onNodeWithText("0.82").assertIsDisplayed()
        composeTestRule.onNodeWithText("α1").assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.zone_display_gauge)).assertIsDisplayed()
    }

    @Test
    fun testAdvancedTuningScreen_saveCancelResetParity() {
        val dataStore = SettingsDataStore.getInstance(context)
        val customThresholds = listOf(118, 132, 146, 162)
        runBlocking {
            dataStore.applyLatestZoneBoundaries(SettingsDataStore.ZoneType.HR_RUN, customThresholds)
            ZoneDisplayOptionsDraft().persistTo(dataStore)
        }

        var showScreen by mutableStateOf(true)
        var screenInstanceKey by mutableStateOf(0)
        composeTestRule.setContent {
            ATrainingTrackerTheme {
                if (showScreen) {
                    androidx.compose.runtime.key(screenInstanceKey) {
                        AdvancedTuningScreen(
                            onNavigateBack = { showScreen = false }
                        )
                    }
                }
            }
        }
        composeTestRule.waitForIdle()

        // 1. Expand Cockpit & Tiles, toggle Text Color, and Cancel -> change must be discarded
        composeTestRule.onNodeWithText(context.getString(R.string.tuning_cat_cockpit_typography)).performClick()
        composeTestRule.waitForIdle()
        clickTextColorSwitch()
        composeTestRule.onNodeWithText(context.getString(R.string.cancel)).performClick()
        composeTestRule.waitForIdle()
        assertFalse(runBlocking { dataStore.snapshotFlow.first().hrRunDisplayOptions.showTextColor })

        // 2. Reopen, toggle Text Color, and Save -> must persist
        screenInstanceKey++
        showScreen = true
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.tuning_cat_cockpit_typography)).performClick()
        composeTestRule.waitForIdle()
        clickTextColorSwitch()
        composeTestRule.onNodeWithText(context.getString(R.string.save)).performClick()
        waitForSnapshotCondition(dataStore) { it.hrRunDisplayOptions.showTextColor }

        // 3. Reopen and Reset to Defaults -> resets display options while preserving thresholds
        screenInstanceKey++
        showScreen = true
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription(context.getString(R.string.reset_to_defaults)).performClick()
        waitForSnapshotCondition(dataStore) { !it.hrRunDisplayOptions.showTextColor }
        val afterReset = runBlocking { dataStore.snapshotFlow.first() }
        assertFalse(afterReset.hrRunDisplayOptions.showTextColor)
        assertEquals(customThresholds, afterReset.hrRunZoneMax)
    }

    @Test
    @Suppress("DEPRECATION")
    fun testAdvancedTuningDialog_saveCancelResetParity() {
        val dataStore = SettingsDataStore.getInstance(context)
        val customThresholds = listOf(120, 134, 148, 164)
        runBlocking {
            dataStore.applyLatestZoneBoundaries(SettingsDataStore.ZoneType.HR_RUN, customThresholds)
            ZoneDisplayOptionsDraft().persistTo(dataStore)
        }

        var showDialog by mutableStateOf(true)
        var dialogInstanceKey by mutableStateOf(0)
        composeTestRule.setContent {
            ATrainingTrackerTheme {
                if (showDialog) {
                    androidx.compose.runtime.key(dialogInstanceKey) {
                        AdvancedTuningDialog(
                            onDismiss = { showDialog = false }
                        )
                    }
                }
            }
        }
        composeTestRule.waitForIdle()

        // 1. Expand Cockpit & Tiles, toggle Text Color, and Cancel -> discarded
        composeTestRule.onNodeWithText(context.getString(R.string.tuning_cat_cockpit_typography)).performClick()
        composeTestRule.waitForIdle()
        clickTextColorSwitch()
        composeTestRule.onNodeWithText(context.getString(R.string.cancel)).performClick()
        composeTestRule.waitForIdle()
        assertFalse(runBlocking { dataStore.snapshotFlow.first().hrRunDisplayOptions.showTextColor })

        // 2. Reopen, toggle Text Color, and Save -> persisted
        dialogInstanceKey++
        showDialog = true
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.tuning_cat_cockpit_typography)).performClick()
        composeTestRule.waitForIdle()
        clickTextColorSwitch()
        composeTestRule.onNodeWithText(context.getString(R.string.save)).performClick()
        waitForSnapshotCondition(dataStore) { it.hrRunDisplayOptions.showTextColor }

        // 3. Reopen and Reset to Defaults -> resets display options, preserves thresholds
        dialogInstanceKey++
        showDialog = true
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.reset_to_defaults)).performScrollTo().performClick()
        waitForSnapshotCondition(dataStore) { !it.hrRunDisplayOptions.showTextColor }
        val afterReset = runBlocking { dataStore.snapshotFlow.first() }
        assertFalse(afterReset.hrRunDisplayOptions.showTextColor)
        assertEquals(customThresholds, afterReset.hrRunZoneMax)
    }

    @Test
    fun testCockpitTypographySection_remainsUnder400Lines() {
        val currentDir = File(System.getProperty("user.dir") ?: ".")
        val rel = "app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/categories/CockpitTypographySection.kt"
        val file = if (File(currentDir, rel).exists()) File(currentDir, rel) else File(currentDir.parentFile, rel)
        assertTrue("CockpitTypographySection.kt must exist", file.exists())
        val lineCount = file.readLines().size
        assertTrue("CockpitTypographySection.kt must remain < 400 LOC (REQ-UI-277.3), got $lineCount", lineCount < 400)
    }
}
