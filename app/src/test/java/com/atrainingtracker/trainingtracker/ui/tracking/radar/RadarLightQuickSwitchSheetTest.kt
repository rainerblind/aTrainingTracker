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

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.atrainingtracker.banalservice.devices.radar.RadarAudioSnoozeMode
import com.atrainingtracker.banalservice.devices.radar.RadarAudioSnoozeState
import com.atrainingtracker.banalservice.devices.radar.RadarLightMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric Compose UI test suite for [RadarLightQuickSwitchSheet] and [RadarLightQuickSwitchSheetContent]
 * (REQ-CON-031.3, TST-CON-031.3, ATT-7991).
 *
 * Validates real UI node rendering, click interactions, and callback wiring without reading source files.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
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
                    onSnoozeSelected = { selectedSnooze = it }
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
                    onAutoSelected = { autoSelected = true }
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
                    onModeSelected = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("RadarLightMode_OFF").assertIsDisplayed()
        composeTestRule.onNodeWithTag("RadarLightMode_SOLID").assertIsDisplayed()
        composeTestRule.onNodeWithTag("RadarLightMode_DAY_FLASH").assertDoesNotExist()
        composeTestRule.onNodeWithTag("RadarLightMode_NIGHT_FLASH").assertDoesNotExist()
        composeTestRule.onNodeWithTag("RadarLightMode_PELOTON").assertDoesNotExist()
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
                    onModeSelected = {}
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
}
