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

import android.app.Application
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Targeted Compose UI & layout-geometry tests for the refactored ATT Fused input-source toggle
 * cards in [AttFusedDeviceSections] (`ATT-8112`, `REQ-SEN-028.4`, `REQ-FIL-016.5`).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-xxhdpi")
class EditDeviceFusedSourcesUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var app: Application

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun testSplitToggleTitleAndSubtitle_handlesAsciiFullWidthAndPlainLabels() {
        assertEquals(
            "GPS-Höhe" to "Langzeit-Driftkorrektur",
            splitToggleTitleAndSubtitle("GPS-Höhe (Langzeit-Driftkorrektur)")
        )
        assertEquals(
            "Geschwindigkeits-Kopplung" to "Stillstands-Fix & dh = g * ds",
            splitToggleTitleAndSubtitle("Geschwindigkeits-Kopplung (Stillstands-Fix & dh = g * ds)")
        )
        assertEquals(
            "気圧高度" to "気圧計",
            splitToggleTitleAndSubtitle("気圧高度（気圧計）")
        )
        val (plainTitle, plainSub) = splitToggleTitleAndSubtitle("GNSS-Positionen nutzen")
        assertEquals("GNSS-Positionen nutzen", plainTitle)
        assertNull(plainSub)
    }

    @Test
    @Config(sdk = [34], qualifiers = "de-rDE-w360dp-h800dp-xxhdpi")
    fun testAttFusedAltitudeSourcesCard_german130FontScale_zeroSwitchOverlapOrCutoff() {
        var gpsToggledTo: Boolean? = null

        composeTestRule.setContent {
            val baseDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = baseDensity.density, fontScale = 1.3f)
            ) {
                ATrainingTrackerTheme(darkTheme = false) {
                    Surface(modifier = Modifier.padding(16.dp)) {
                        AttFusedAltitudeSourcesCard(
                            draft = AttFusedAltitudeDraftConfig(
                                barometerEnabled = true,
                                demEnabled = false,
                                gpsEnabled = false,
                                speedEnabled = true
                            ),
                            baroAvailable = true,
                            demAvailable = false,
                            gpsAvailable = true,
                            speedAvailable = true,
                            onBaroChanged = {},
                            onDemChanged = {},
                            onGpsChanged = { gpsToggledTo = it },
                            onSpeedChanged = {}
                        )
                    }
                }
            }
        }

        composeTestRule.waitForIdle()

        // Unified accurate header ("Datenquellen") — never duplicate "Sensordaten"
        composeTestRule.onNodeWithText("Datenquellen").assertIsDisplayed()
        composeTestRule.onNodeWithText(app.getString(R.string.devices_live_sensor_data_header))
            .assertDoesNotExist()

        // Clean title + subtitle separation for all 4 German altitude sources
        val expectedPairs = listOf(
            "Höhe aus Luftdruck" to "att_fused_alt_switch_baro",
            "Topografische Höhe" to "att_fused_alt_switch_dem",
            "GPS-Höhe" to "att_fused_alt_switch_gps",
            "Geschwindigkeits-Kopplung" to "att_fused_alt_switch_speed"
        )
        val cardBounds = composeTestRule.onNodeWithTag("att_fused_altitude_sources_card")
            .assertIsDisplayed()
            .getUnclippedBoundsInRoot()

        for ((title, switchTag) in expectedPairs) {
            val titleBounds = composeTestRule.onNodeWithText(title)
                .assertIsDisplayed()
                .getUnclippedBoundsInRoot()
            val switchBounds = composeTestRule.onNodeWithTag(switchTag)
                .assertIsDisplayed()
                .getUnclippedBoundsInRoot()

            assertTrue(
                "Title '$title' right (${titleBounds.right}) must stay left of switch left (${switchBounds.left})",
                titleBounds.right < switchBounds.left
            )
            assertTrue(
                "Switch '$switchTag' right (${switchBounds.right}) must stay inside card right (${cardBounds.right})",
                switchBounds.right <= cardBounds.right
            )
        }

        // Subtitles & disabled prerequisite hint rendered cleanly
        composeTestRule.onNodeWithText("Langzeit-Driftkorrektur").assertIsDisplayed()
        composeTestRule.onNodeWithText("Stillstands-Fix & dh = g * ds").assertIsDisplayed()
        composeTestRule.onNodeWithText("Topografische Höhe (DEM) nicht gekoppelt").assertIsDisplayed()

        // Enabled/disabled states & live toggle interaction
        composeTestRule.onNodeWithTag("att_fused_alt_switch_baro").assertIsEnabled().assertIsOn()
        composeTestRule.onNodeWithTag("att_fused_alt_switch_dem").assertIsNotEnabled().assertIsOff()
        composeTestRule.onNodeWithTag("att_fused_alt_switch_gps").assertIsEnabled().assertIsOff().performClick()
        composeTestRule.waitForIdle()
        assertEquals(true, gpsToggledTo)
    }

    @Test
    fun testAttFusedOdometrySourcesCard_unifiedHeaderGuardsAndToggleInteraction() {
        var gyroToggledTo: Boolean? = null

        composeTestRule.setContent {
            ATrainingTrackerTheme(darkTheme = true) {
                Surface(modifier = Modifier.padding(16.dp)) {
                    AttFusedOdometrySourcesCard(
                        draft = AttFusedDraftConfig(
                            gnssEnabled = true,
                            wheelEnabled = true,
                            gyroEnabled = true,
                            autoCalibEnabled = false
                        ),
                        gnssAvailable = true,
                        wheelAvailable = false,
                        gyroAvailable = true,
                        autoCalibAvailable = false,
                        onGnssChanged = {},
                        onWheelChanged = {},
                        onGyroChanged = { gyroToggledTo = it },
                        onAutoCalibChanged = {}
                    )
                }
            }
        }

        composeTestRule.waitForIdle()

        val cardBounds = composeTestRule.onNodeWithTag("att_fused_odometry_sources_card")
            .assertIsDisplayed()
            .getUnclippedBoundsInRoot()
        composeTestRule.onNodeWithText(app.getString(R.string.device_att_fused_sources_header))
            .assertIsDisplayed()

        val rows = listOf(
            app.getString(R.string.device_att_fused_toggle_gnss) to "att_fused_odo_switch_gnss",
            app.getString(R.string.device_att_fused_toggle_wheel) to "att_fused_odo_switch_wheel",
            app.getString(R.string.device_att_fused_toggle_gyro) to "att_fused_odo_switch_gyro",
            app.getString(R.string.device_att_fused_toggle_autocalib) to "att_fused_odo_switch_autocalib"
        )
        for ((title, switchTag) in rows) {
            val titleBounds = composeTestRule.onNodeWithText(title)
                .assertIsDisplayed()
                .getUnclippedBoundsInRoot()
            val switchBounds = composeTestRule.onNodeWithTag(switchTag)
                .assertIsDisplayed()
                .getUnclippedBoundsInRoot()
            assertTrue(titleBounds.right < switchBounds.left)
            assertTrue(switchBounds.right <= cardBounds.right)
        }

        // Guarded disabled hints rendered for unpaired wheel & auto-calib
        composeTestRule.onNodeWithText(app.getString(R.string.device_att_fused_toggle_wheel_hint_disabled))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(app.getString(R.string.device_att_fused_toggle_autocalib_hint_disabled))
            .assertIsDisplayed()

        composeTestRule.onNodeWithTag("att_fused_odo_switch_wheel").assertIsNotEnabled().assertIsOff()
        composeTestRule.onNodeWithTag("att_fused_odo_switch_gyro").assertIsEnabled().assertIsOn().performClick()
        composeTestRule.waitForIdle()
        assertEquals(false, gyroToggledTo)
    }
}
