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

package com.atrainingtracker.trainingtracker.ui.components.radar

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.devices.radar.RadarAudioSnoozeMode
import com.atrainingtracker.banalservice.devices.radar.RadarAudioSnoozeState
import com.atrainingtracker.banalservice.devices.radar.RadarLightMode
import com.atrainingtracker.banalservice.ui.devices.editdevice.RadarLightControlSectionContent
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.atrainingtracker.trainingtracker.ui.tracking.radar.RadarLightQuickSwitchSheetContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/**
 * Behavioral Robolectric Compose UI test suite for shared [RadarLightModeGrid] and dual-surface
 * parity between [RadarLightQuickSwitchSheetContent] and [RadarLightControlSectionContent]
 * (`REQ-CON-047.2`, `REQ-CON-047.3`, `REQ-CON-048.3`, `TST-CON-047`, `TST-CON-048`, `ATT-8678`).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xxhdpi")
class RadarLightModeGridTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun testSharedGrid_nonStvzoRowMajorOrderAndCallbacks() {
        var autoClicked = false
        var selectedMode: RadarLightMode? = null

        composeTestRule.setContent {
            ATrainingTrackerTheme(darkTheme = false) {
                RadarLightModeGrid(
                    currentMode = RadarLightMode.PELOTON,
                    availableModes = RadarLightMode.getAvailableModes(isStvzo = false),
                    isStvzo = false,
                    isAutoMode = false,
                    showActiveModeSubtitle = true,
                    onModeSelected = { selectedMode = it },
                    onAutoSelected = { autoClicked = true }
                )
            }
        }

        composeTestRule.onNodeWithTag("RadarLightStvzoNotice").assertDoesNotExist()
        composeTestRule.onNodeWithTag("RadarLightMode_PELOTON").assertIsSelected()
        composeTestRule.onNodeWithTag("RadarLightMode_AUTO").assertIsNotSelected()

        val bAuto = composeTestRule.onNodeWithTag("RadarLightMode_AUTO").getBoundsInRoot()
        val bOff = composeTestRule.onNodeWithTag("RadarLightMode_OFF").getBoundsInRoot()
        val bPeloton = composeTestRule.onNodeWithTag("RadarLightMode_PELOTON").getBoundsInRoot()
        val bSolid = composeTestRule.onNodeWithTag("RadarLightMode_SOLID").getBoundsInRoot()
        val bNight = composeTestRule.onNodeWithTag("RadarLightMode_NIGHT_FLASH").getBoundsInRoot()
        val bDay = composeTestRule.onNodeWithTag("RadarLightMode_DAY_FLASH").getBoundsInRoot()

        assertEquals(bAuto.top, bOff.top)
        assertTrue(bAuto.left < bOff.left)
        assertEquals(bPeloton.top, bSolid.top)
        assertTrue(bPeloton.top > bAuto.top && bPeloton.left < bSolid.left)
        assertEquals(bNight.top, bDay.top)
        assertTrue(bNight.top > bPeloton.top && bNight.left < bDay.left)

        composeTestRule.onNodeWithTag("RadarLightMode_NIGHT_FLASH").performClick()
        assertEquals(RadarLightMode.NIGHT_FLASH, selectedMode)

        composeTestRule.onNodeWithTag("RadarLightMode_AUTO").performClick()
        assertTrue(autoClicked)
    }

    @Test
    fun testSharedGrid_showActiveModeSubtitleGate_andStvzoBanner() {
        val activeFormatDayFlash = context.getString(
            R.string.radar_light_active_mode_format,
            context.getString(RadarLightMode.DAY_FLASH.titleResId)
        )

        composeTestRule.setContent {
            ATrainingTrackerTheme(darkTheme = true) {
                RadarLightModeGrid(
                    currentMode = RadarLightMode.DAY_FLASH,
                    availableModes = RadarLightMode.getAvailableModes(isStvzo = true),
                    isStvzo = true,
                    isAutoMode = true,
                    showActiveModeSubtitle = false,
                    onModeSelected = {},
                    onAutoSelected = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("RadarLightStvzoNotice").assertIsDisplayed()
        composeTestRule.onNodeWithTag("RadarLightMode_AUTO").assertIsSelected()
        composeTestRule.onNodeWithText(activeFormatDayFlash).assertDoesNotExist()
        composeTestRule.onNodeWithTag("RadarLightMode_OFF").assertIsDisplayed()
        composeTestRule.onNodeWithTag("RadarLightMode_SOLID").assertIsDisplayed()
        composeTestRule.onNodeWithTag("RadarLightMode_DAY_FLASH").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "w920dp-h1400dp-xxhdpi")
    fun testCaptureUnifiedRadarLightGridLightDarkStvzoPreview_Rule8_5() {
        composeTestRule.setContent {
            Row(
                modifier = Modifier
                    .width(880.dp)
                    .background(Color(0xFF78909C))
                    .padding(12.dp)
                    .testTag("UnifiedRadarLightGridPreviewRoot"),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ATrainingTrackerTheme(darkTheme = false) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Light - EditDevice (Offline Multi-Mode)",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            RadarLightControlSectionContent(
                                isOffline = true,
                                isAutoMode = false,
                                effectiveMode = RadarLightMode.SOLID,
                                isStvzo = false,
                                onSelectAuto = {},
                                onSelectMode = {}
                            )
                            HorizontalDivider()
                            Text(
                                text = "Light - EditDevice (Online StVZO Auto)",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            RadarLightControlSectionContent(
                                isOffline = false,
                                isAutoMode = true,
                                effectiveMode = RadarLightMode.SOLID,
                                isStvzo = true,
                                onSelectAuto = {},
                                onSelectMode = {}
                            )
                            HorizontalDivider()
                            Text(
                                text = "Light - QuickSwitchSheet (Multi-Mode)",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            RadarLightQuickSwitchSheetContent(
                                currentMode = RadarLightMode.DAY_FLASH,
                                availableModes = RadarLightMode.getAvailableModes(false),
                                isStvzo = false,
                                isAutoMode = true,
                                isLightControlSupported = true,
                                snoozeState = RadarAudioSnoozeState(isSnoozed = false),
                                onModeSelected = {}
                            )
                        }
                    }
                }
                ATrainingTrackerTheme(darkTheme = true) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Dark - EditDevice (Offline Auto)",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            RadarLightControlSectionContent(
                                isOffline = true,
                                isAutoMode = true,
                                effectiveMode = RadarLightMode.DAY_FLASH,
                                isStvzo = false,
                                onSelectAuto = {},
                                onSelectMode = {}
                            )
                            HorizontalDivider()
                            Text(
                                text = "Dark - EditDevice (Offline StVZO)",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            RadarLightControlSectionContent(
                                isOffline = true,
                                isAutoMode = false,
                                effectiveMode = RadarLightMode.OFF,
                                isStvzo = true,
                                onSelectAuto = {},
                                onSelectMode = {}
                            )
                            HorizontalDivider()
                            Text(
                                text = "Dark - QuickSwitchSheet (StVZO)",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            RadarLightQuickSwitchSheetContent(
                                currentMode = RadarLightMode.SOLID,
                                availableModes = RadarLightMode.getAvailableModes(true),
                                isStvzo = true,
                                isAutoMode = false,
                                isLightControlSupported = true,
                                snoozeState = RadarAudioSnoozeState(
                                    isSnoozed = true,
                                    mode = RadarAudioSnoozeMode.SNOOZE_15M
                                ),
                                onModeSelected = {}
                            )
                        }
                    }
                }
            }
        }

        val bmp = composeTestRule.onNodeWithTag("UnifiedRadarLightGridPreviewRoot")
            .captureToImage()
            .asAndroidBitmap()

        val repoRoot = if (File("..", "docs/engineering/walkthroughs/img").exists()) File("..") else File(".")
        val outFile = File(repoRoot, "docs/engineering/walkthroughs/img/ATT-8678_radar_light_grid_preview.png")
        outFile.parentFile?.mkdirs()
        FileOutputStream(outFile).use { out ->
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        assertTrue("Screenshot PNG must exist and be non-empty", outFile.exists() && outFile.length() > 0L)
    }
}
