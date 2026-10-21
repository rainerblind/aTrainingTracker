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

package com.atrainingtracker.banalservice.ui.devices.devicetabs

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Architectural and UI contract test verifying DevicesTabbedScreen auto-pairing zone onboarding
 * confirmation dialog integration (REQ-UI-374.6, TST-UI-337.2, ATT-4452).
 */
class DevicesTabbedScreenPromptContractTest {

    private fun findFile(relativePath: String): File {
        val candidates = listOf(
            File(relativePath),
            File("app/$relativePath"),
            File("../$relativePath")
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("File not found in candidates: $relativePath")
    }

    @Test
    fun testDevicesTabbedScreen_zoneOnboardingPromptContract() {
        val file = findFile("src/main/java/com/atrainingtracker/banalservice/ui/devices/devicetabs/DevicesTabbedScreen.kt")
        val content = file.readText()

        // 1. Must collect zonePromptState from tabViewModel
        assertTrue(
            "DevicesTabbedScreen must collect zonePromptState",
            content.contains("tabViewModel.zonePromptState.collectAsState()")
        )

        // 2. Must handle HRM and Bike Power strings
        assertTrue(
            "DevicesTabbedScreen must reference zone_onboarding_prompt_hr_title",
            content.contains("R.string.zone_onboarding_prompt_hr_title")
        )
        assertTrue(
            "DevicesTabbedScreen must reference zone_onboarding_prompt_hr_message",
            content.contains("R.string.zone_onboarding_prompt_hr_message")
        )
        assertTrue(
            "DevicesTabbedScreen must reference zone_onboarding_prompt_pwr_title",
            content.contains("R.string.zone_onboarding_prompt_pwr_title")
        )
        assertTrue(
            "DevicesTabbedScreen must reference zone_onboarding_prompt_pwr_message",
            content.contains("R.string.zone_onboarding_prompt_pwr_message")
        )
        assertTrue(
            "DevicesTabbedScreen must reference zone_onboarding_action_configure",
            content.contains("R.string.zone_onboarding_action_configure")
        )
        assertTrue(
            "DevicesTabbedScreen must reference zone_onboarding_action_later",
            content.contains("R.string.zone_onboarding_action_later")
        )

        // 3. Must invoke onNavigateToZones and dismissZonePrompt
        assertTrue(
            "DevicesTabbedScreen must invoke onNavigateToZones on configure",
            content.contains("onNavigateToZones?.invoke(")
        )
        assertTrue(
            "DevicesTabbedScreen must call dismissZonePrompt()",
            content.contains("tabViewModel.dismissZonePrompt()")
        )

        // 4. Dialog styling tokens: surface container color and 0.dp elevation
        assertTrue(
            "ZoneOnboardingPromptDialog must use MaterialTheme.colorScheme.surface",
            content.contains("containerColor = MaterialTheme.colorScheme.surface")
        )
        assertTrue(
            "ZoneOnboardingPromptDialog must use 0.dp tonal elevation",
            content.contains("tonalElevation = 0.dp")
        )

        // 5. Must define composable ZoneOnboardingPromptDialog
        assertTrue(
            "Must define ZoneOnboardingPromptDialog composable",
            content.contains("fun ZoneOnboardingPromptDialog(")
        )

        // 6. Must define multi-state @Preview for ZoneOnboardingPromptDialog
        assertTrue(
            "Must define ZoneOnboardingPromptDialogPreview",
            content.contains("ZoneOnboardingPromptDialogPreview()")
        )
    }
}
