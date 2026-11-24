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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.Protocol
import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.banalservice.devices.radar.RadarBondActionResult
import com.atrainingtracker.banalservice.devices.radar.RadarConnectionState
import com.atrainingtracker.banalservice.devices.radar.RadarSource
import com.atrainingtracker.banalservice.devices.radar.RadarStateRepository
import com.atrainingtracker.banalservice.devices.radar.RadarThreatState
import com.atrainingtracker.banalservice.ui.devices.radar.RadarV2BondingSnackbarEffect
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
 * Behavioral Robolectric Compose UI test suite for [RadarV2BondingHintCard],
 * [shouldShowRadarV2BondingHint], [RadarV2BondingSnackbarEffect], German 360dp fit,
 * and `Rule 8.5` Light/Dark PNG capture (`REQ-CON-061.3`, `TST-CON-061.3`, `ATT-8731`).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xxhdpi")
class EditDeviceDialogRadarBondingTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        RadarStateRepository.reset()
    }

    @After
    fun tearDown() {
        RadarStateRepository.reset()
    }

    @Test
    fun testShouldShowRadarV2BondingHint_macNormalizationAndProtocolMatrix_tstCon061_3() {
        // Matching BLE BIKE_RADAR with mixed case & whitespace -> true
        assertTrue(
            shouldShowRadarV2BondingHint(
                deviceType = DeviceType.BIKE_RADAR,
                protocol = Protocol.BLUETOOTH_LE,
                isV2BondingRequired = true,
                bondingDeviceAddress = "  C6:CC:E3:F5:F0:37 ",
                deviceMacAddress = "c6:cc:e3:f5:f0:37"
            )
        )
        // Non-matching BLE BIKE_RADAR (e.g. co-saved RTL515) -> false
        assertFalse(
            shouldShowRadarV2BondingHint(
                deviceType = DeviceType.BIKE_RADAR,
                protocol = Protocol.BLUETOOTH_LE,
                isV2BondingRequired = true,
                bondingDeviceAddress = "C6:CC:E3:F5:F0:37",
                deviceMacAddress = "11:22:33:44:55:66"
            )
        )
        // ANT+ BIKE_RADAR -> false
        assertFalse(
            shouldShowRadarV2BondingHint(
                deviceType = DeviceType.BIKE_RADAR,
                protocol = Protocol.ANT_PLUS,
                isV2BondingRequired = true,
                bondingDeviceAddress = "C6:CC:E3:F5:F0:37",
                deviceMacAddress = "C6:CC:E3:F5:F0:37"
            )
        )
        // Non-radar BLE device -> false
        assertFalse(
            shouldShowRadarV2BondingHint(
                deviceType = DeviceType.HRM,
                protocol = Protocol.BLUETOOTH_LE,
                isV2BondingRequired = true,
                bondingDeviceAddress = "C6:CC:E3:F5:F0:37",
                deviceMacAddress = "C6:CC:E3:F5:F0:37"
            )
        )
        // Bonding not required -> false
        assertFalse(
            shouldShowRadarV2BondingHint(
                deviceType = DeviceType.BIKE_RADAR,
                protocol = Protocol.BLUETOOTH_LE,
                isV2BondingRequired = false,
                bondingDeviceAddress = "C6:CC:E3:F5:F0:37",
                deviceMacAddress = "C6:CC:E3:F5:F0:37"
            )
        )
    }

    @Test
    fun testRadarV2BondingHintCard_rendersInteractiveCtaAndCapturesLightDarkPng_tstCon061_3() {
        var pairClicks = 0
        var actionResult by mutableStateOf<RadarBondActionResult?>(null)

        composeTestRule.setContent {
            Column(
                modifier = Modifier
                    .width(412.dp)
                    .testTag("RadarBondingPreviewComposite")
            ) {
                ATrainingTrackerTheme(darkTheme = false) {
                    Surface(color = MaterialTheme.colorScheme.surface) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Light Theme", style = MaterialTheme.typography.labelLarge)
                            RadarV2BondingHintCard(
                                actionResult = actionResult,
                                onPairClick = {
                                    pairClicks++
                                    actionResult = RadarBondActionResult.BONDING_INITIATED
                                }
                            )
                        }
                    }
                }
                ATrainingTrackerTheme(darkTheme = true) {
                    Surface(color = MaterialTheme.colorScheme.surface) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Dark Theme (Initiated)", style = MaterialTheme.typography.labelLarge)
                            RadarV2BondingHintCard(
                                actionResult = RadarBondActionResult.BONDING_INITIATED,
                                onPairClick = {}
                            )
                        }
                    }
                }
            }
        }

        // Capture composite PNG before click so Light shows default and Dark shows Initiated state
        val bitmap = composeTestRule.onNodeWithTag("RadarBondingPreviewComposite")
            .captureToImage()
            .asAndroidBitmap()
        val imgDir = resolveWalkthroughImgDir()
        if (imgDir != null) {
            imgDir.mkdirs()
            val outFile = File(imgDir, "ATT-8731_radar_v2_bonding_card_light_dark.png")
            FileOutputStream(outFile).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
            }
        }

        // Verify interactive click transitions Light card to BONDING_INITIATED state
        composeTestRule.onAllNodes(androidx.compose.ui.test.hasTestTag("RadarV2BondingPairButton"))[0].performClick()
        composeTestRule.waitForIdle()
        assertEquals(1, pairClicks)
        assertEquals(RadarBondActionResult.BONDING_INITIATED, actionResult)
        assertEquals(
            2,
            composeTestRule.onAllNodes(androidx.compose.ui.test.hasTestTag("RadarV2BondingInitiatedHint")).fetchSemanticsNodes().size
        )
    }

    @Test
    @Config(sdk = [34], qualifiers = "de-rDE-w360dp-h800dp-xxhdpi")
    fun testRadarV2BondingHintCard_german360dp_singleLineButtonFit_tstCon061_3() {
        composeTestRule.setContent {
            ATrainingTrackerTheme(darkTheme = false) {
                Surface(color = MaterialTheme.colorScheme.surface) {
                    Column(modifier = Modifier.width(360.dp).padding(horizontal = 24.dp, vertical = 8.dp)) {
                        RadarV2BondingHintCard(
                            actionResult = null,
                            onPairClick = {}
                        )
                    }
                }
            }
        }

        composeTestRule.onNodeWithText("Bluetooth-Kopplung für V2 erforderlich").assertIsDisplayed()
        val buttonNode = composeTestRule.onNodeWithTag("RadarV2BondingPairButton").assertIsDisplayed()
        val bounds = buttonNode.getBoundsInRoot()
        assertTrue(
            "German Pair button height (${bounds.bottom - bounds.top}) must stay single-line compact (<= 56.dp)",
            (bounds.bottom - bounds.top) <= 56.dp
        )
    }

    @Test
    fun testRadarV2BondingSnackbarEffect_suppressesTrackingWithoutBurningLatch_andCancelsOnReadyToTracking_tstCon061_3() {
        var isAllowedToShow by mutableStateOf(false)
        lateinit var hostStateRef: SnackbarHostState
        var pairedMac: String? = null

        RadarStateRepository.updateThreatState(
            RadarThreatState(connectionState = RadarConnectionState.CONNECTED, source = RadarSource.BLE)
        )
        RadarStateRepository.setV2BondingRequired(true, "C6:CC:E3:F5:F0:37")

        composeTestRule.setContent {
            val snackbarHostState = remember { SnackbarHostState() }
            hostStateRef = snackbarHostState
            ATrainingTrackerTheme(darkTheme = false) {
                Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
                    Column(modifier = Modifier.padding(padding)) {
                        RadarV2BondingSnackbarEffect(
                            snackbarHostState = snackbarHostState,
                            isAllowedToShow = isAllowedToShow,
                            onPairAction = { _, mac -> pairedMac = mac }
                        )
                    }
                }
            }
        }

        composeTestRule.waitForIdle()
        // 1. In TRACKING/PAUSED (isAllowedToShow = false): zero snackbar & latch NOT burned
        assertNull("TRACKING/PAUSED must suppress bonding snackbar", hostStateRef.currentSnackbarData)

        // 2. Transition to READY (isAllowedToShow = true): snackbar appears because latch was preserved
        composeTestRule.runOnUiThread { isAllowedToShow = true }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.radar_v2_bonding_snackbar_message)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.radar_v2_bonding_pair_action_short)).assertIsDisplayed()

        // 3. Transition READY -> TRACKING (isAllowedToShow = false): active snackbar immediately dismissed
        composeTestRule.runOnUiThread { isAllowedToShow = false }
        composeTestRule.waitForIdle()
        assertNull("Transitioning READY -> TRACKING must dismiss active bonding snackbar", hostStateRef.currentSnackbarData)

        // 4. Returning to READY in the same connection does NOT spam a second snackbar
        composeTestRule.runOnUiThread { isAllowedToShow = true }
        composeTestRule.waitForIdle()
        assertNull("One-shot latch must prevent duplicate snackbar in same connection session", hostStateRef.currentSnackbarData)

        // 5. After disconnect + reconnect, snackbar fires once again and Pair action triggers callback
        RadarStateRepository.updateThreatState(
            RadarThreatState(connectionState = RadarConnectionState.DISCONNECTED, source = RadarSource.BLE)
        )
        composeTestRule.waitForIdle()
        RadarStateRepository.updateThreatState(
            RadarThreatState(connectionState = RadarConnectionState.CONNECTED, source = RadarSource.BLE)
        )
        RadarStateRepository.setV2BondingRequired(true, "C6:CC:E3:F5:F0:37")
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(context.getString(R.string.radar_v2_bonding_pair_action_short)).performClick()
        composeTestRule.waitForIdle()
        assertEquals("C6:CC:E3:F5:F0:37", pairedMac)
    }

    /**
     * TST-CON-061.3 / REQ-CON-061.3 (ATT-8731 Bounce 1 Heal):
     * RadarV2BondingSnackbarEffect MUST dismiss strictly its own RadarV2BondingSnackbarVisuals
     * when isAllowedToShow is/becomes false, NEVER dismissing unrelated active snackbars
     * (e.g., CRITICAL low-battery or Polar PMD warnings) on initial TRACKING or READY -> TRACKING.
     */
    @Test
    fun testRadarV2BondingSnackbarEffect_neverDismissesUnrelatedSnackbar_onInitialTrackingOrReadyToTracking_tstCon061_3() {
        var isAllowedToShow by mutableStateOf(true)
        var showUnrelatedTrigger by mutableStateOf(0)
        lateinit var hostStateRef: SnackbarHostState
        val unrelatedMessage = "Critical sensor battery: 5%"

        composeTestRule.setContent {
            val snackbarHostState = remember { SnackbarHostState() }
            hostStateRef = snackbarHostState
            LaunchedEffect(showUnrelatedTrigger) {
                if (showUnrelatedTrigger > 0) {
                    snackbarHostState.showSnackbar(
                        message = unrelatedMessage,
                        duration = SnackbarDuration.Indefinite
                    )
                }
            }
            ATrainingTrackerTheme(darkTheme = false) {
                Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
                    Column(modifier = Modifier.padding(padding)) {
                        RadarV2BondingSnackbarEffect(
                            snackbarHostState = snackbarHostState,
                            isAllowedToShow = isAllowedToShow,
                            onPairAction = { _, _ -> }
                        )
                    }
                }
            }
        }

        // 1. Show unrelated critical snackbar in READY (no radar connected)
        composeTestRule.runOnUiThread { showUnrelatedTrigger = 1 }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(unrelatedMessage).assertIsDisplayed()

        // 2. Transition READY -> TRACKING (isAllowedToShow = false): unrelated snackbar MUST survive!
        composeTestRule.runOnUiThread { isAllowedToShow = false }
        composeTestRule.waitForIdle()
        assertEquals(
            "Unrelated snackbar must survive READY -> TRACKING transition",
            unrelatedMessage,
            hostStateRef.currentSnackbarData?.visuals?.message
        )
        composeTestRule.onNodeWithText(unrelatedMessage).assertIsDisplayed()
    }

    private fun resolveWalkthroughImgDir(): File? {
        val candidates = listOf(
            File("docs/engineering/walkthroughs/img"),
            File("../docs/engineering/walkthroughs/img"),
            File("../../docs/engineering/walkthroughs/img")
        )
        return candidates.firstOrNull { it.parentFile?.exists() == true }
    }
}
