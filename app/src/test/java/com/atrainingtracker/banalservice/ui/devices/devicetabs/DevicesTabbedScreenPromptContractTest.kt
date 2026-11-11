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

import com.atrainingtracker.testing.LocalizationTestCache
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Architectural and UI contract test verifying DevicesTabbedScreen auto-pairing zone onboarding
 * confirmation dialog integration (REQ-UI-374.6, TST-UI-337.2, ATT-4452).
 */
class DevicesTabbedScreenPromptContractTest {

    @Test
    fun testDevicesTabbedScreen_zoneOnboardingPromptContract() {
        val screenClass = Class.forName("com.atrainingtracker.banalservice.ui.devices.devicetabs.DevicesTabbedScreenKt")
        assertNotNull("DevicesTabbedScreenKt class must exist", screenClass)

        val methods = screenClass.declaredMethods.map { it.name }
        assertTrue(
            "Must define ZoneOnboardingPromptDialog composable",
            methods.any { it.startsWith("ZoneOnboardingPromptDialog") }
        )

        // Verify string resources in localization cache
        val strings = LocalizationTestCache.getStrings("values")
        assertTrue("Must define zone_onboarding_prompt_hr_title", strings.containsKey("zone_onboarding_prompt_hr_title"))
        assertTrue("Must define zone_onboarding_prompt_hr_message", strings.containsKey("zone_onboarding_prompt_hr_message"))
        assertTrue("Must define zone_onboarding_prompt_pwr_title", strings.containsKey("zone_onboarding_prompt_pwr_title"))
        assertTrue("Must define zone_onboarding_prompt_pwr_message", strings.containsKey("zone_onboarding_prompt_pwr_message"))
        assertTrue("Must define zone_onboarding_action_configure", strings.containsKey("zone_onboarding_action_configure"))
        assertTrue("Must define zone_onboarding_action_later", strings.containsKey("zone_onboarding_action_later"))
    }
}
