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

package com.atrainingtracker.trainingtracker.zones

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Architectural and UI contract test for the Contextual Guidance Banner and Zone Navigation Integration
 * under REQ-UI-374.3 and REQ-UI-374.4.
 *
 * Covers [TST-UI-337.4].
 */
class ZoneOnboardingBannerContractTest {

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
    fun testZoneOnboardingBanner_componentContract() {
        val file = findFile("src/main/java/com/atrainingtracker/trainingtracker/ui/settings/ZoneOnboardingBanner.kt")
        val content = file.readText()

        // 1. Must define ZoneOnboardingBanner composable
        assertTrue(
            "ZoneOnboardingBanner must define composable",
            content.contains("fun ZoneOnboardingBanner(")
        )

        // 2. Must accept promptReason, onDismiss, and modifier
        assertTrue(
            "ZoneOnboardingBanner must accept promptReason: String",
            content.contains("promptReason: String")
        )
        assertTrue(
            "ZoneOnboardingBanner must accept onDismiss: () -> Unit",
            content.contains("onDismiss: () -> Unit")
        )

        // 3. Must use Material 3 primaryContainer color tokens
        assertTrue(
            "ZoneOnboardingBanner must use primaryContainer token",
            content.contains("MaterialTheme.colorScheme.primaryContainer")
        )
        assertTrue(
            "ZoneOnboardingBanner must use onPrimaryContainer token",
            content.contains("MaterialTheme.colorScheme.onPrimaryContainer")
        )

        // 4. Must discriminate hr_paired and pwr_paired reasons
        assertTrue(
            "ZoneOnboardingBanner must handle PROMPT_REASON_HR_PAIRED",
            content.contains("PROMPT_REASON_HR_PAIRED")
        )
        assertTrue(
            "ZoneOnboardingBanner must handle PROMPT_REASON_PWR_PAIRED",
            content.contains("PROMPT_REASON_PWR_PAIRED")
        )

        // 5. Must reference localization strings
        assertTrue(
            "ZoneOnboardingBanner must reference R.string.zone_onboarding_hr_banner",
            content.contains("R.string.zone_onboarding_hr_banner")
        )
        assertTrue(
            "ZoneOnboardingBanner must reference R.string.zone_onboarding_pwr_banner",
            content.contains("R.string.zone_onboarding_pwr_banner")
        )
        assertTrue(
            "ZoneOnboardingBanner must reference R.string.zone_onboarding_dismiss",
            content.contains("R.string.zone_onboarding_dismiss")
        )

        // 6. Must define semantic test tags
        assertTrue(
            "ZoneOnboardingBanner must define TEST_TAG_ZONE_ONBOARDING_BANNER",
            content.contains("TEST_TAG_ZONE_ONBOARDING_BANNER")
        )
        assertTrue(
            "ZoneOnboardingBanner must define TEST_TAG_ZONE_ONBOARDING_DISMISS",
            content.contains("TEST_TAG_ZONE_ONBOARDING_DISMISS")
        )
    }

    @Test
    fun testDevicesTabbedScreen_zoneNavigationIntegrationContract() {
        val file = findFile("src/main/java/com/atrainingtracker/banalservice/ui/devices/devicetabs/DevicesTabbedScreen.kt")
        val content = file.readText()

        // 1. Must accept onNavigateToZones callback
        assertTrue(
            "DevicesTabbedScreen must declare onNavigateToZones parameter",
            content.contains("onNavigateToZones: ((initialTab: Int, promptReason: String?) -> Unit)? = null")
        )

        // 2. Must collect zoneNavigationEvent from listViewModel
        assertTrue(
            "DevicesTabbedScreen must collect zoneNavigationEvent",
            content.contains("listViewModel.zoneNavigationEvent.collect")
        )
        assertTrue(
            "DevicesTabbedScreen must invoke onNavigateToZones callback on event",
            content.contains("onNavigateToZones?.invoke(event.initialTab, event.promptReason)")
        )
    }

    @Test
    fun testATrainingTrackerApp_zoneDeepLinkNavigationContract() {
        val file = findFile("src/main/java/com/atrainingtracker/trainingtracker/ui/navigation/ATrainingTrackerApp.kt")
        val content = file.readText()

        // 1. Must pass onNavigateToZones in SENSORS composable
        assertTrue(
            "ATrainingTrackerApp must wire onNavigateToZones in SENSORS route",
            content.contains("onNavigateToZones = { initialTab, promptReason ->") &&
            content.contains("navController.navigate(NavRoutes.trainingZones(initialTab, promptReason))")
        )

        // 2. Must register TRAINING_ZONES_PATTERN route with navigation arguments
        assertTrue(
            "ATrainingTrackerApp must register TRAINING_ZONES_PATTERN route",
            content.contains("route = NavRoutes.TRAINING_ZONES_PATTERN")
        )
        assertTrue(
            "ATrainingTrackerApp must configure ARG_INITIAL_TAB argument",
            content.contains("navArgument(NavRoutes.ARG_INITIAL_TAB)")
        )
        assertTrue(
            "ATrainingTrackerApp must configure ARG_PROMPT_REASON argument",
            content.contains("navArgument(NavRoutes.ARG_PROMPT_REASON)")
        )

        // 3. Must pass initialTab and promptReason to ZoneSettingsScreen
        assertTrue(
            "ATrainingTrackerApp must forward arguments to ZoneSettingsScreen",
            content.contains("ZoneSettingsScreen(") &&
            content.contains("initialTab = initialTab") &&
            content.contains("promptReason = promptReason")
        )
    }

    @Test
    fun testZonesSettingsActivity_bannerMountAndDismissContract() {
        val file = findFile("src/main/java/com/atrainingtracker/trainingtracker/activities/ZonesSettingsActivity.kt")
        val content = file.readText()

        // 1. Must accept promptReason in ZoneSettingsScreen
        assertTrue(
            "ZoneSettingsScreen must accept promptReason parameter",
            content.contains("promptReason: String? = null")
        )

        // 2. Must manage bannerVisible state with rememberSaveable
        assertTrue(
            "ZoneSettingsScreen must use rememberSaveable for bannerVisible",
            content.contains("var bannerVisible by rememberSaveable { mutableStateOf(promptReason != null) }")
        )

        // 3. Must mount ZoneOnboardingBanner
        assertTrue(
            "ZoneSettingsScreen must render ZoneOnboardingBanner",
            content.contains("ZoneOnboardingBanner(")
        )

        // 4. Must update SettingsDataStore configured flags on dismiss
        assertTrue(
            "ZoneSettingsScreen must set configured flags on dismiss",
            content.contains("dataStore.setHrZonesConfigured(true)") &&
            content.contains("dataStore.setPwrZonesConfigured(true)")
        )
    }
}
