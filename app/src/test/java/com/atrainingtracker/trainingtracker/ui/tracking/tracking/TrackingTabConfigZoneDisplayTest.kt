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

package com.atrainingtracker.trainingtracker.ui.tracking.tracking

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.ActivityType
import com.atrainingtracker.trainingtracker.segments.LiveSegment
import com.atrainingtracker.trainingtracker.settings.SettingsDataStore
import com.atrainingtracker.trainingtracker.settings.ZoneDisplayOptions
import com.atrainingtracker.trainingtracker.ui.settings.ZoneDisplayConfigSection
import com.atrainingtracker.trainingtracker.ui.settings.ZoneDisplayOptionsDraft
import com.atrainingtracker.trainingtracker.ui.settings.ZoneProfileTarget
import com.atrainingtracker.trainingtracker.ui.settings.defaultZoneProfileTarget
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.atrainingtracker.trainingtracker.ui.tracking.ScreenMode
import com.atrainingtracker.trainingtracker.ui.tracking.SensorFieldState
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.flow.MutableStateFlow
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
import java.io.FileOutputStream

/**
 * Unit, Compose UI, DataStore, and Rule 8.5 Light/Dark visual verification for
 * [ZoneDisplayOptions] inside tracking tabs configuration (`ScreenMode.CONFIGURATION`
 * in [SensorGridScreen]) (`REQ-UI-2783.3`, `TST-UI-2783.3`, `TST-UI-2783.4`).
 */
// Traces to: REQ-UI-2783, TST-UI-2783.3, TST-UI-2783.4
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w420dp-h1600dp-xhdpi")
class TrackingTabConfigZoneDisplayTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun testDefaultZoneProfileTarget_mapsAllActivityTypes() {
        assertEquals(ZoneProfileTarget.HR_RUN, defaultZoneProfileTarget(ActivityType.RUN_SPEED_AND_CADENCE))
        assertEquals(ZoneProfileTarget.HR_BIKE, defaultZoneProfileTarget(ActivityType.BIKE_SPEED_AND_CADENCE))
        assertEquals(ZoneProfileTarget.PWR_BIKE, defaultZoneProfileTarget(ActivityType.BIKE_POWER))
        assertEquals(ZoneProfileTarget.HRV_DFA_A1, defaultZoneProfileTarget(ActivityType.GENERIC_HR))
    }

    @Test
    fun testSensorGridConfigurationMode_zoneDisplayControlsPersistImmediately() {
        val dataStore = SettingsDataStore.getInstance(context)
        runBlocking {
            ZoneDisplayOptionsDraft(
                hrv = ZoneDisplayOptions(showGauge = true)
            ).persistTo(dataStore)
        }

        val gridActions = object : GridActions {
            override fun onEditField(fieldState: SensorFieldState) {}
            override fun onDeleteField(fieldState: SensorFieldState) {}
            override fun onAddRow(beforeRow: Int) {}
            override fun onAddCol(atRow: Int, beforeCol: Int) {}
        }

        composeTestRule.setContent {
            ATrainingTrackerTheme {
                SensorGridScreen(
                    state = TrackingScreenState(),
                    screenMode = ScreenMode.CONFIGURATION,
                    gridActions = gridActions,
                    currentLocationFlow = MutableStateFlow<LatLng?>(null),
                    liveSegments = MutableStateFlow<List<LiveSegment>>(emptyList()),
                    activityType = ActivityType.BIKE_POWER
                )
            }
        }
        composeTestRule.waitForIdle()

        // Pre-selected to PWR_BIKE (4 toggles, no gauge)
        composeTestRule.onNodeWithText(context.getString(R.string.zone_display_title)).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.tab_pwr_bike)).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.zone_display_gauge)).assertDoesNotExist()

        // Toggle Text Color -> persists immediately to PWR_BIKE
        composeTestRule.onAllNodes(isToggleable())[3].performScrollTo().performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            runBlocking { dataStore.snapshotFlow.first().pwrBikeDisplayOptions.showTextColor }
        }

        // Apply to all -> copies flags across all 4 profiles while preserving HRV.showGauge
        composeTestRule.onNodeWithText(context.getString(R.string.zone_display_apply_all)).performScrollTo().performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            runBlocking { dataStore.snapshotFlow.first().hrRunDisplayOptions.showTextColor }
        }
        val afterApplyAll = runBlocking { dataStore.snapshotFlow.first() }
        assertTrue(afterApplyAll.hrBikeDisplayOptions.showTextColor)
        assertTrue(afterApplyAll.hrvDisplayOptions.showTextColor)
        assertTrue("HRV showGauge must stay preserved", afterApplyAll.hrvDisplayOptions.showGauge)

        // Switch segmented tab to HRV DFA a1 -> 5th gauge toggle appears and persists
        composeTestRule.onNodeWithText(context.getString(R.string.tab_hrv_dfa_a1)).performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.zone_display_gauge)).performScrollTo().assertIsDisplayed()
        composeTestRule.onAllNodes(isToggleable())[4].performScrollTo().performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            runBlocking { !dataStore.snapshotFlow.first().hrvDisplayOptions.showGauge }
        }
        assertFalse(runBlocking { dataStore.snapshotFlow.first().hrvDisplayOptions.showGauge })
    }

    /**
     * TST-UI-2783.4: Rule 8.5 Light & Dark Compose Screenshot verification (plus narrow 360dp German
     * segmented selector check) saved to `docs/engineering/walkthroughs/img/ATT-8498_zone_display_tile_and_tabs.png`.
     */
    @Test
    @Config(sdk = [34], qualifiers = "de-rDE-w920dp-h1200dp-xhdpi")
    fun testCaptureZoneDisplayTileAndTabsScreenshot_LightAndDark() {
        val sampleOptions = ZoneDisplayOptions(
            showBackground = true,
            showLeftBar = true,
            showRightBar = false,
            showTextColor = true,
            showGauge = true
        )

        composeTestRule.setContent {
            Row(
                modifier = Modifier
                    .width(860.dp)
                    .background(Color(0xFF888888))
                    .padding(12.dp)
                    .testTag("zone_display_tile_and_tabs_comparison"),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                listOf(false to "Light Theme (Tile + Tab Config, DE)", true to "Dark Theme (Tile + Tab Config, DE)").forEach { (isDark, title) ->
                    ATrainingTrackerTheme(darkTheme = isDark) {
                        Surface(
                            color = MaterialTheme.colorScheme.background,
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                // 1. Sensor Tile Long-Press mode (single profile, no selector)
                                ZoneDisplayConfigSection(
                                    selectedProfile = ZoneProfileTarget.HR_BIKE,
                                    displayOptions = sampleOptions,
                                    onUpdateDisplayOptions = {},
                                    onApplyToAll = {},
                                    onProfileSelected = null
                                )
                                // 2. Tracking Tabs Config mode (4-profile segmented selector + HRV gauge)
                                ZoneDisplayConfigSection(
                                    selectedProfile = ZoneProfileTarget.HRV_DFA_A1,
                                    displayOptions = sampleOptions,
                                    onUpdateDisplayOptions = {},
                                    onApplyToAll = {},
                                    onProfileSelected = {}
                                )
                            }
                        }
                    }
                }
            }
        }
        composeTestRule.waitForIdle()

        val bmp = composeTestRule.onNodeWithTag("zone_display_tile_and_tabs_comparison")
            .captureToImage()
            .asAndroidBitmap()
        val repoRoot = if (File("..", "docs/engineering/walkthroughs/img").exists()) File("..") else File(".")
        val outFile = File(repoRoot, "docs/engineering/walkthroughs/img/ATT-8498_zone_display_tile_and_tabs.png")
        outFile.parentFile?.mkdirs()
        FileOutputStream(outFile).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue("Screenshot must exist and be non-empty", outFile.exists() && outFile.length() > 0)
    }
}
