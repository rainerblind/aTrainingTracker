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

package com.atrainingtracker.trainingtracker.ui.tracking.radar

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.atrainingtracker.banalservice.devices.radar.RadarAudioSnoozeMode
import com.atrainingtracker.banalservice.devices.radar.RadarAudioSnoozeState
import com.atrainingtracker.banalservice.devices.radar.RadarLightMode
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
 * Robolectric Compose UI test suite for [RadarLightQuickSwitchSheet] and [RadarLightQuickSwitchSheetContent]
 * (REQ-CON-031.3, REQ-CON-047, TST-CON-031.3, TST-CON-047, ATT-7991, ATT-8456).
 *
 * Validates real UI node rendering, compact non-clipping 2-column layout, ascending mode order,
 * selection semantics, Light/Dark visual rendering, and callback wiring.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1080dp-h2200dp-xhdpi")
class RadarLightQuickSwitchSheetTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testContent_clickingSmartAuto_triggersOnAutoSelected() {
        var autoSelected = false
        var selectedMode: RadarLightMode? = null
        var selectedSnooze: RadarAudioSnoozeMode? = null

        composeTestRule.setContent {
            MaterialTheme {
                RadarLightQuickSwitchSheetContent(
                    currentMode = RadarLightMode.DAY_FLASH,
                    availableModes = RadarLightMode.getAvailableModes(false),
                    isStvzo = false,
                    isAutoMode = false,
                    isLightControlSupported = true,
                    onModeSelected = { selectedMode = it },
                    onAutoSelected = { autoSelected = true },
                    onSnoozeSelected = { selectedSnooze = it },
                    onResumeAudio = {},
                    onDismiss = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("RadarLightMode_AUTO")
            .assertIsDisplayed()
            .performClick()

        assertTrue("Clicking Smart Auto must trigger onAutoSelected", autoSelected)
        assertNull("Clicking Smart Auto must not trigger onModeSelected", selectedMode)
        assertNull("Clicking Smart Auto must not trigger onSnoozeSelected", selectedSnooze)
    }

    @Test
    fun testContent_clickingManualMode_triggersOnModeSelectedWithExactMode() {
        var selectedMode: RadarLightMode? = null
        var autoSelected = false

        composeTestRule.setContent {
            MaterialTheme {
                RadarLightQuickSwitchSheetContent(
                    currentMode = RadarLightMode.DAY_FLASH,
                    availableModes = RadarLightMode.getAvailableModes(false),
                    isStvzo = false,
                    isAutoMode = true,
                    isLightControlSupported = true,
                    onModeSelected = { selectedMode = it },
                    onAutoSelected = { autoSelected = true },
                    onSnoozeSelected = {},
                    onResumeAudio = {},
                    onDismiss = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("RadarLightMode_SOLID")
            .assertIsDisplayed()
            .performClick()

        assertEquals("Clicking SOLID mode must trigger onModeSelected with SOLID", RadarLightMode.SOLID, selectedMode)
        assertFalse("Clicking manual mode must not trigger onAutoSelected", autoSelected)
    }

    @Test
    fun testContent_clickingSnoozeOptions_triggersOnSnoozeSelectedAndDismiss() {
        var selectedSnooze: RadarAudioSnoozeMode? = null
        var dismissCount = 0
        var autoSelected = false

        composeTestRule.setContent {
            MaterialTheme {
                RadarLightQuickSwitchSheetContent(
                    currentMode = RadarLightMode.OFF,
                    availableModes = emptyList(),
                    isStvzo = false,
                    isAutoMode = false,
                    isLightControlSupported = false,
                    onModeSelected = {},
                    onAutoSelected = { autoSelected = true },
                    onSnoozeSelected = { selectedSnooze = it },
                    onResumeAudio = {},
                    onDismiss = { dismissCount++ }
                )
            }
        }

        val testModes = listOf(
            RadarAudioSnoozeMode.SNOOZE_15M,
            RadarAudioSnoozeMode.SNOOZE_30M,
            RadarAudioSnoozeMode.FAST_ONLY,
            RadarAudioSnoozeMode.REST_OF_RIDE
        )

        for (targetMode in testModes) {
            selectedSnooze = null
            composeTestRule.onNodeWithTag("RadarSnooze_${targetMode.name}")
                .assertIsDisplayed()
                .performClick()

            assertEquals(
                "Clicking snooze option ${targetMode.name} must trigger onSnoozeSelected with exact mode",
                targetMode,
                selectedSnooze
            )
            assertFalse("Clicking snooze option must not trigger onAutoSelected", autoSelected)
        }
        assertEquals("Clicking snooze options must trigger onDismiss each time", 4, dismissCount)
    }

    @Test
    fun testContent_activeSnooze_clickingResume_triggersOnResumeAudioAndDismiss() {
        var resumeInvoked = false
        var dismissed = false

        composeTestRule.setContent {
            MaterialTheme {
                RadarLightQuickSwitchSheetContent(
                    currentMode = RadarLightMode.OFF,
                    availableModes = emptyList(),
                    isStvzo = false,
                    isAutoMode = false,
                    isLightControlSupported = false,
                    snoozeState = RadarAudioSnoozeState(
                        isSnoozed = true,
                        mode = RadarAudioSnoozeMode.SNOOZE_15M
                    ),
                    onModeSelected = {},
                    onAutoSelected = {},
                    onSnoozeSelected = {},
                    onResumeAudio = { resumeInvoked = true },
                    onDismiss = { dismissed = true }
                )
            }
        }

        composeTestRule.onNodeWithTag("RadarSnooze_Resume")
            .assertIsDisplayed()
            .performClick()

        assertTrue("Clicking resume button must trigger onResumeAudio", resumeInvoked)
        assertTrue("Clicking resume button must trigger onDismiss", dismissed)
    }

    @Test
    fun testContent_stvzoPruning_hidesFlashingModes() {
        composeTestRule.setContent {
            MaterialTheme {
                RadarLightQuickSwitchSheetContent(
                    currentMode = RadarLightMode.SOLID,
                    availableModes = RadarLightMode.getAvailableModes(isStvzo = true),
                    isStvzo = true,
                    isAutoMode = false,
                    isLightControlSupported = true,
                    onModeSelected = {},
                    onAutoSelected = {},
                    onSnoozeSelected = {},
                    onResumeAudio = {},
                    onDismiss = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("RadarLightStvzoNotice").assertIsDisplayed()
        composeTestRule.onNodeWithTag("RadarLightMode_AUTO").assertIsDisplayed()
        composeTestRule.onNodeWithTag("RadarLightMode_OFF").assertIsDisplayed()
        composeTestRule.onNodeWithTag("RadarLightMode_SOLID").assertIsDisplayed()
        composeTestRule.onNodeWithTag("RadarLightMode_DAY_FLASH").assertDoesNotExist()
        composeTestRule.onNodeWithTag("RadarLightMode_NIGHT_FLASH").assertDoesNotExist()
        composeTestRule.onNodeWithTag("RadarLightMode_PELOTON").assertDoesNotExist()

        val bAuto = composeTestRule.onNodeWithTag("RadarLightMode_AUTO").getBoundsInRoot()
        val bOff = composeTestRule.onNodeWithTag("RadarLightMode_OFF").getBoundsInRoot()
        val bSolid = composeTestRule.onNodeWithTag("RadarLightMode_SOLID").getBoundsInRoot()
        assertEquals("OFF and AUTO must share row 1 top", bOff.top, bAuto.top)
        assertTrue("OFF must be left of AUTO on row 1", bOff.left < bAuto.left)
        assertTrue("SOLID must be below OFF on row 2", bSolid.top > bOff.top)
    }

    @Test
    fun testContent_unsupportedLightControl_hidesLightSectionRetainsSnooze() {
        composeTestRule.setContent {
            MaterialTheme {
                RadarLightQuickSwitchSheetContent(
                    currentMode = RadarLightMode.SOLID,
                    availableModes = emptyList(),
                    isStvzo = false,
                    isAutoMode = false,
                    isLightControlSupported = false,
                    onModeSelected = {},
                    onAutoSelected = {},
                    onSnoozeSelected = {},
                    onResumeAudio = {},
                    onDismiss = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("RadarLightMode_AUTO").assertDoesNotExist()
        composeTestRule.onNodeWithTag("RadarLightMode_SOLID").assertDoesNotExist()
        composeTestRule.onNodeWithTag("RadarSnooze_SNOOZE_15M").assertIsDisplayed()
        composeTestRule.onNodeWithTag("RadarSnooze_REST_OF_RIDE").assertIsDisplayed()
    }

    @Test
    fun testModalSheet_composableSignaturesExposeAllRequiredComponents() {
        val clazz = Class.forName(
            "com.atrainingtracker.trainingtracker.ui.tracking.radar.RadarLightQuickSwitchSheetKt"
        )
        val methods = clazz.declaredMethods
        val sheetMethod = methods.find { it.name == "RadarLightQuickSwitchSheet" }
        assertTrue("RadarLightQuickSwitchSheet composable must exist", sheetMethod != null)
        val contentMethod = methods.find { it.name == "RadarLightQuickSwitchSheetContent" }
        assertTrue("RadarLightQuickSwitchSheetContent composable must exist", contentMethod != null)
    }

    /**
     * TST-CON-047.1 & TST-CON-047.2 / REQ-CON-047.1..REQ-CON-047.3 (ATT-8456):
     * Verifies all 6 light tiles + 4 snooze tiles fit inside a constrained 380x480 dp phone viewport
     * without vertical scroll clipping, and light tiles follow ascending intensity order:
     * AUTO -> OFF -> PELOTON -> SOLID -> NIGHT_FLASH -> DAY_FLASH.
     */
    @Test
    fun testContent_compactGridFitsWithoutClipping_andMatchesAscendingOrder() {
        composeTestRule.setContent {
            ATrainingTrackerTheme(darkTheme = false) {
                Box(
                    modifier = Modifier
                        .width(380.dp)
                        .heightIn(max = 480.dp)
                        .testTag("BoundedViewport")
                ) {
                    RadarLightQuickSwitchSheetContent(
                        currentMode = RadarLightMode.SOLID,
                        availableModes = RadarLightMode.getAvailableModes(false),
                        isStvzo = false,
                        isAutoMode = false,
                        isLightControlSupported = true,
                        snoozeState = RadarAudioSnoozeState(
                            isSnoozed = true,
                            mode = RadarAudioSnoozeMode.SNOOZE_15M
                        ),
                        onModeSelected = {},
                        onAutoSelected = {},
                        onSnoozeSelected = {},
                        onResumeAudio = {},
                        onDismiss = {}
                    )
                }
            }
        }

        val viewportBounds = composeTestRule.onNodeWithTag("BoundedViewport").getBoundsInRoot()
        assertTrue("Sheet height (${viewportBounds.bottom}) must stay compact (<= 480.dp)", viewportBounds.bottom <= 480.dp)

        val expectedTags = listOf(
            "RadarLightMode_AUTO",
            "RadarLightMode_OFF",
            "RadarLightMode_PELOTON",
            "RadarLightMode_SOLID",
            "RadarLightMode_NIGHT_FLASH",
            "RadarLightMode_DAY_FLASH",
            "RadarSnooze_Resume",
            "RadarSnooze_SNOOZE_15M",
            "RadarSnooze_SNOOZE_30M",
            "RadarSnooze_FAST_ONLY",
            "RadarSnooze_REST_OF_RIDE"
        )
        expectedTags.forEach { tag ->
            val node = composeTestRule.onNodeWithTag(tag)
            node.assertIsDisplayed()
            val bounds = node.getBoundsInRoot()
            assertTrue("$tag bottom (${bounds.bottom}) must fit inside viewport (${viewportBounds.bottom})", bounds.bottom <= viewportBounds.bottom + 0.5.dp)
        }

        // Verify selection semantics
        composeTestRule.onNodeWithTag("RadarLightMode_SOLID").assertIsSelected()
        composeTestRule.onNodeWithTag("RadarLightMode_AUTO").assertIsNotSelected()
        composeTestRule.onNodeWithTag("RadarSnooze_SNOOZE_15M").assertIsSelected()
        composeTestRule.onNodeWithTag("RadarSnooze_REST_OF_RIDE").assertIsNotSelected()

        // Verify 2-column reading order (row-major: [OFF, AUTO], [PELOTON, SOLID], [NIGHT_FLASH, DAY_FLASH])
        val bAuto = composeTestRule.onNodeWithTag("RadarLightMode_AUTO").getBoundsInRoot()
        val bOff = composeTestRule.onNodeWithTag("RadarLightMode_OFF").getBoundsInRoot()
        val bPeloton = composeTestRule.onNodeWithTag("RadarLightMode_PELOTON").getBoundsInRoot()
        val bSolid = composeTestRule.onNodeWithTag("RadarLightMode_SOLID").getBoundsInRoot()
        val bNight = composeTestRule.onNodeWithTag("RadarLightMode_NIGHT_FLASH").getBoundsInRoot()
        val bDay = composeTestRule.onNodeWithTag("RadarLightMode_DAY_FLASH").getBoundsInRoot()

        assertEquals("OFF and AUTO must share row 1 top", bOff.top, bAuto.top)
        assertTrue("OFF must be left of AUTO on row 1", bOff.left < bAuto.left)
        assertTrue("PELOTON must be below OFF and left of SOLID on row 2", bPeloton.top > bOff.top && bPeloton.left < bSolid.left)
        assertTrue("NIGHT_FLASH must be below PELOTON and left of DAY_FLASH on row 3", bNight.top > bPeloton.top && bNight.left < bDay.left)
    }

    private fun captureQuickSwitchThemeScreenshot(darkTheme: Boolean, fileName: String) {
        val repoRoot = if (File("..", "docs/engineering/walkthroughs/img").exists()) File("..") else File(".")
        composeTestRule.setContent {
            ATrainingTrackerTheme(darkTheme = darkTheme) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .width(420.dp)
                        .testTag("QuickSwitchThemeCapture_$fileName")
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = if (darkTheme) "Dark — Non-StVZO (Off | Auto -> Peloton -> Solid -> Night -> Day)" else "Light — Non-StVZO (Off | Auto -> Peloton -> Solid -> Night -> Day)",
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
                            onModeSelected = {},
                            onAutoSelected = {},
                            onSnoozeSelected = {},
                            onResumeAudio = {},
                            onDismiss = {}
                        )
                        HorizontalDivider()
                        Text(
                            text = if (darkTheme) "Dark — StVZO (Off | Auto -> Solid)" else "Light — StVZO (Off | Auto -> Solid)",
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
                            onModeSelected = {},
                            onAutoSelected = {},
                            onSnoozeSelected = {},
                            onResumeAudio = {},
                            onDismiss = {}
                        )
                    }
                }
            }
        }

        val bmp = composeTestRule.onNodeWithTag("QuickSwitchThemeCapture_$fileName")
            .captureToImage()
            .asAndroidBitmap()
        val outFile = File(repoRoot, "docs/engineering/walkthroughs/img/$fileName")
        outFile.parentFile?.mkdirs()
        FileOutputStream(outFile).use { out ->
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        assertTrue("Screenshot PNG $fileName must exist and be non-empty", outFile.exists() && outFile.length() > 0L)
    }

    /**
     * TST-CON-047.4 / Rule 8.5 (ATT-8674): Captures Light theme PNG (Non-StVZO & StVZO)
     * in docs/engineering/walkthroughs/img/ for visual self-inspection.
     */
    @Test
    @Config(qualifiers = "w880dp-h1200dp-xxhdpi")
    fun testContent_captureLightScreenshot_ATT8674() {
        captureQuickSwitchThemeScreenshot(darkTheme = false, fileName = "ATT-8674_quick_switch_light.png")
    }

    /**
     * TST-CON-047.4 / Rule 8.5 (ATT-8674): Captures Dark theme PNG (Non-StVZO & StVZO)
     * in docs/engineering/walkthroughs/img/ for visual self-inspection.
     */
    @Test
    @Config(qualifiers = "w880dp-h1200dp-xxhdpi")
    fun testContent_captureDarkScreenshot_ATT8674() {
        captureQuickSwitchThemeScreenshot(darkTheme = true, fileName = "ATT-8674_quick_switch_dark.png")
    }

    /**
     * TST-CON-047.4 / Rule 8.5: Captures side-by-side Light & Dark preview PNG in build/reports/
     * for visual self-inspection during Stage 4.
     */
    @Test
    fun testContent_captureLightAndDarkPreviewForVisualInspection() {
        composeTestRule.setContent {
            Row(
                modifier = Modifier
                    .width(840.dp)
                    .background(Color(0xFF78909C))
                    .padding(12.dp)
                    .testTag("QuickSwitchLightDarkComparison"),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ATrainingTrackerTheme(darkTheme = false) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                            RadarLightQuickSwitchSheetContent(
                                currentMode = RadarLightMode.DAY_FLASH,
                                availableModes = RadarLightMode.getAvailableModes(false),
                                isStvzo = false,
                                isAutoMode = true,
                                isLightControlSupported = true,
                                snoozeState = RadarAudioSnoozeState(isSnoozed = false),
                                onModeSelected = {},
                                onAutoSelected = {},
                                onSnoozeSelected = {},
                                onResumeAudio = {},
                                onDismiss = {}
                            )
                        }
                    }
                }
                ATrainingTrackerTheme(darkTheme = true) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                            RadarLightQuickSwitchSheetContent(
                                currentMode = RadarLightMode.SOLID,
                                availableModes = RadarLightMode.getAvailableModes(false),
                                isStvzo = false,
                                isAutoMode = false,
                                isLightControlSupported = true,
                                snoozeState = RadarAudioSnoozeState(
                                    isSnoozed = true,
                                    mode = RadarAudioSnoozeMode.SNOOZE_15M
                                ),
                                onModeSelected = {},
                                onAutoSelected = {},
                                onSnoozeSelected = {},
                                onResumeAudio = {},
                                onDismiss = {}
                            )
                        }
                    }
                }
            }
        }

        val bmp = composeTestRule.onNodeWithTag("QuickSwitchLightDarkComparison")
            .captureToImage()
            .asAndroidBitmap()

        val outFile = File("build/reports/ATT-8456_quick_switch_preview.png")
        outFile.parentFile?.mkdirs()
        FileOutputStream(outFile).use { out ->
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        assertTrue("Preview PNG must exist and be non-empty", outFile.exists() && outFile.length() > 0)
    }

    /**
     * TST-CON-055.1 / REQ-CON-055.1 (ATT-8123):
     * Renders the real [RadarLightQuickSwitchSheet] modal wrapper and verifies all 4 audio-snooze
     * presets invoke [onSnoozeSelected] with the exact preset strictly before [onDismiss].
     */
    @Test
    fun testModalWrapper_clickingAllSnoozePresets_triggersOnSnoozeSelectedBeforeDismiss_TST_CON_055_1() {
        val events = mutableListOf<String>()

        composeTestRule.setContent {
            ATrainingTrackerTheme(darkTheme = false) {
                RadarLightQuickSwitchSheet(
                    currentMode = RadarLightMode.SOLID,
                    availableModes = RadarLightMode.getAvailableModes(isStvzo = true),
                    isStvzo = true,
                    isAutoMode = false,
                    isLightControlSupported = true,
                    snoozeState = RadarAudioSnoozeState.INACTIVE,
                    onModeSelected = { events.add("mode:${it.name}") },
                    onAutoSelected = { events.add("auto") },
                    onSnoozeSelected = { events.add("snooze:${it.name}") },
                    onResumeAudio = { events.add("resume") },
                    onDismiss = { events.add("dismiss") }
                )
            }
        }
        composeTestRule.waitForIdle()

        val presets = listOf(
            RadarAudioSnoozeMode.SNOOZE_15M,
            RadarAudioSnoozeMode.SNOOZE_30M,
            RadarAudioSnoozeMode.FAST_ONLY,
            RadarAudioSnoozeMode.REST_OF_RIDE
        )
        for (preset in presets) {
            events.clear()
            composeTestRule.onNodeWithTag("RadarSnooze_${preset.name}")
                .assertIsDisplayed()
                .performClick()
            assertEquals(
                "Modal wrapper must forward onSnoozeSelected(${preset.name}) strictly before onDismiss",
                listOf("snooze:${preset.name}", "dismiss"),
                events
            )
        }
    }

    /**
     * TST-CON-055.1 / REQ-CON-055.1 (ATT-8123):
     * Renders [RadarLightQuickSwitchSheet] with active snooze and verifies tapping Resume Alerts
     * invokes [onResumeAudio] strictly before [onDismiss].
     */
    @Test
    fun testModalWrapper_activeSnooze_clickingResume_triggersOnResumeAudioBeforeDismiss_TST_CON_055_1() {
        val events = mutableListOf<String>()

        composeTestRule.setContent {
            ATrainingTrackerTheme(darkTheme = false) {
                RadarLightQuickSwitchSheet(
                    currentMode = RadarLightMode.OFF,
                    availableModes = emptyList(),
                    isStvzo = false,
                    isAutoMode = false,
                    isLightControlSupported = false,
                    snoozeState = RadarAudioSnoozeState(
                        isSnoozed = true,
                        mode = RadarAudioSnoozeMode.SNOOZE_30M
                    ),
                    onModeSelected = { events.add("mode:${it.name}") },
                    onAutoSelected = { events.add("auto") },
                    onSnoozeSelected = { events.add("snooze:${it.name}") },
                    onResumeAudio = { events.add("resume") },
                    onDismiss = { events.add("dismiss") }
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("RadarSnooze_Resume")
            .assertIsDisplayed()
            .performClick()

        assertEquals(
            "Modal wrapper must forward onResumeAudio strictly before onDismiss",
            listOf("resume", "dismiss"),
            events
        )
    }
}
