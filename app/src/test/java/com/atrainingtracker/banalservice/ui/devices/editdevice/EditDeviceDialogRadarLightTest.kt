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

package com.atrainingtracker.banalservice.ui.devices.editdevice

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
import com.atrainingtracker.banalservice.devices.radar.RadarLightMode
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
 * Robolectric Compose UI test suite for offline [RadarLightControlSectionContent], visibility guards,
 * StVZO vs Multi-Mode rendering, Light/Dark previews, and Rule 8.5 PNG verification
 * (`REQ-CON-048.1`..`REQ-CON-048.4`, `TST-CON-048.1`, `TST-CON-048.3`, `ATT-8450`).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xxhdpi")
class EditDeviceDialogRadarLightTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun testVisibilityPredicate_offlineRearVueShown_rvr315AndConnectedUnsupportedHidden() {
        assertTrue(isKnownRadarWithoutLight("RVR315"))
        assertTrue(isKnownRadarWithoutLight("Garmin RVR 315"))
        assertFalse(isKnownRadarWithoutLight("RearVue8"))
        assertFalse(isKnownRadarWithoutLight("RTL515"))
        assertFalse(isKnownRadarWithoutLight(null))

        // Offline controllable / ambiguous radar models must be visible
        assertTrue(
            shouldShowRadarLightControlSection(
                isConnected = false,
                isSimulating = false,
                isLightControlAvailable = false,
                deviceName = "RearVue8"
            )
        )
        // Offline explicit radar-only RVR315 must be hidden
        assertFalse(
            shouldShowRadarLightControlSection(
                isConnected = false,
                isSimulating = false,
                isLightControlAvailable = false,
                deviceName = "Garmin RVR315"
            )
        )
        // Live connected without light capability must be hidden unless simulating
        assertFalse(
            shouldShowRadarLightControlSection(
                isConnected = true,
                isSimulating = false,
                isLightControlAvailable = false,
                deviceName = "RearVue8"
            )
        )
        assertTrue(
            shouldShowRadarLightControlSection(
                isConnected = true,
                isSimulating = false,
                isLightControlAvailable = true,
                deviceName = "RearVue8"
            )
        )
        assertTrue(
            shouldShowRadarLightControlSection(
                isConnected = false,
                isSimulating = true,
                isLightControlAvailable = false,
                deviceName = "Garmin RVR315"
            )
        )
    }

    @Test
    fun testOfflineMultiMode_displaysAscendingModesAndHint_withoutStvzoNotice() {
        var autoClicked = false
        var selectedMode: RadarLightMode? = null

        composeTestRule.setContent {
            ATrainingTrackerTheme(darkTheme = false) {
                RadarLightControlSectionContent(
                    isOffline = true,
                    isAutoMode = false,
                    effectiveMode = RadarLightMode.SOLID,
                    isStvzo = false,
                    onSelectAuto = { autoClicked = true },
                    onSelectMode = { selectedMode = it }
                )
            }
        }

        composeTestRule.onNodeWithTag("RadarLightControlSection").assertIsDisplayed()
        composeTestRule.onNodeWithTag("RadarLightOfflineHint").assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.radar_light_offline_hint)).assertIsDisplayed()
        composeTestRule.onNodeWithTag("RadarLightStvzoNotice").assertDoesNotExist()

        val expectedTags = listOf(
            "RadarLightMode_AUTO",
            "RadarLightMode_OFF",
            "RadarLightMode_PELOTON",
            "RadarLightMode_SOLID",
            "RadarLightMode_NIGHT_FLASH",
            "RadarLightMode_DAY_FLASH"
        )
        expectedTags.forEach { tag ->
            composeTestRule.onNodeWithTag(tag).assertIsDisplayed()
        }

        // Verify SOLID is highlighted (never OFF) when effectiveMode == SOLID
        composeTestRule.onNodeWithTag("RadarLightMode_SOLID").assertIsSelected()
        composeTestRule.onNodeWithTag("RadarLightMode_OFF").assertIsNotSelected()
        composeTestRule.onNodeWithTag("RadarLightMode_AUTO").assertIsNotSelected()

        // Verify ascending order top-to-bottom
        val bounds = expectedTags.map { composeTestRule.onNodeWithTag(it).getBoundsInRoot().top }
        for (i in 0 until bounds.lastIndex) {
            assertTrue("Expected ${expectedTags[i]} above ${expectedTags[i + 1]}", bounds[i] < bounds[i + 1])
        }

        composeTestRule.onNodeWithTag("RadarLightMode_DAY_FLASH").performClick()
        assertEquals(RadarLightMode.DAY_FLASH, selectedMode)

        composeTestRule.onNodeWithTag("RadarLightMode_AUTO").performClick()
        assertTrue(autoClicked)
    }

    @Test
    fun testOfflineStvzo_displaysNoticeAndOfflineHint_prunesFlashingModes() {
        var autoClicked = false
        var selectedMode: RadarLightMode? = null

        composeTestRule.setContent {
            ATrainingTrackerTheme(darkTheme = false) {
                RadarLightControlSectionContent(
                    isOffline = true,
                    isAutoMode = false,
                    effectiveMode = RadarLightMode.SOLID,
                    isStvzo = true,
                    onSelectAuto = { autoClicked = true },
                    onSelectMode = { selectedMode = it }
                )
            }
        }

        composeTestRule.onNodeWithTag("RadarLightControlSection").assertIsDisplayed()
        composeTestRule.onNodeWithTag("RadarLightOfflineHint").assertIsDisplayed()
        composeTestRule.onNodeWithTag("RadarLightStvzoNotice").assertIsDisplayed()

        composeTestRule.onNodeWithTag("RadarLightMode_AUTO").assertIsDisplayed().assertIsNotSelected()
        composeTestRule.onNodeWithTag("RadarLightMode_OFF").assertIsDisplayed().assertIsNotSelected()
        composeTestRule.onNodeWithTag("RadarLightMode_SOLID").assertIsDisplayed().assertIsSelected()

        composeTestRule.onNodeWithTag("RadarLightMode_PELOTON").assertDoesNotExist()
        composeTestRule.onNodeWithTag("RadarLightMode_NIGHT_FLASH").assertDoesNotExist()
        composeTestRule.onNodeWithTag("RadarLightMode_DAY_FLASH").assertDoesNotExist()

        composeTestRule.onNodeWithTag("RadarLightMode_OFF").performClick()
        assertEquals(RadarLightMode.OFF, selectedMode)

        composeTestRule.onNodeWithTag("RadarLightMode_AUTO").performClick()
        assertTrue(autoClicked)
    }

    @Test
    @Config(qualifiers = "w920dp-h1200dp-xxhdpi")
    fun testCaptureOfflineLightAndDarkPreviewScreenshot_Rule8_5() {
        composeTestRule.setContent {
            Row(
                modifier = Modifier
                    .width(860.dp)
                    .background(Color(0xFF78909C))
                    .padding(12.dp)
                    .testTag("OfflineRadarEditDevicePreviewRoot"),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ATrainingTrackerTheme(darkTheme = false) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = "Light — Offline Multi-Mode",
                                style = MaterialTheme.typography.titleMedium,
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
                            Text(
                                text = "Light — Offline StVZO",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            RadarLightControlSectionContent(
                                isOffline = true,
                                isAutoMode = false,
                                effectiveMode = RadarLightMode.SOLID,
                                isStvzo = true,
                                onSelectAuto = {},
                                onSelectMode = {}
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
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = "Dark — Offline Multi-Mode",
                                style = MaterialTheme.typography.titleMedium,
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
                            Text(
                                text = "Dark — Offline StVZO",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            RadarLightControlSectionContent(
                                isOffline = true,
                                isAutoMode = true,
                                effectiveMode = RadarLightMode.OFF,
                                isStvzo = true,
                                onSelectAuto = {},
                                onSelectMode = {}
                            )
                        }
                    }
                }
            }
        }

        val bmp = composeTestRule.onNodeWithTag("OfflineRadarEditDevicePreviewRoot")
            .captureToImage()
            .asAndroidBitmap()

        val repoRoot = if (File("..", "docs/engineering/walkthroughs/img").exists()) File("..") else File(".")
        val outFile = File(repoRoot, "docs/engineering/walkthroughs/img/ATT-8450_radar_offline_edit_device.png")
        outFile.parentFile?.mkdirs()
        FileOutputStream(outFile).use { out ->
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        assertTrue("Screenshot PNG must exist and be non-empty", outFile.exists() && outFile.length() > 0L)
        assertTrue(context.getString(R.string.radar_light_offline_hint).isNotBlank())
    }
}
