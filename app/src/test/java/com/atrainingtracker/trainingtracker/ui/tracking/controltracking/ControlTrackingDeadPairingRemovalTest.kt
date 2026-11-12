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

package com.atrainingtracker.trainingtracker.ui.tracking.controltracking

import com.atrainingtracker.testing.LocalizationTestCache
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.lang.reflect.Modifier

/**
 * Architectural contract test enforcing REQ-UI-393 / TST-UI-356 (ATT-3692):
 * Removal of dead sensor pairing code from ControlTrackingScreen, ControlTrackingViewModel,
 * TrackingTabsScreen, and MainActivityWithNavigation while preserving active sensor discovery
 * and My Sensors pairing.
 * Refactored to reflection and LocalizationTestCache per docs/coding_standards.md §5.4 and REQ-PRO-049.
 */
class ControlTrackingDeadPairingRemovalTest {

    private fun resolveFile(relativePath: String): File {
        val projectDir = File(System.getProperty("user.dir") ?: ".")
        val candidates = listOf(
            File(projectDir, relativePath),
            File(projectDir, "app/$relativePath"),
            File(projectDir, "../app/$relativePath")
        )
        return candidates.firstOrNull { it.exists() } ?: File(projectDir, "app/$relativePath")
    }

    @Test
    fun testPairingButtonsFile_isDeleted() {
        val pairingButtonsFile = resolveFile("src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/PairingButtons.kt")
        assertFalse(
            "Dead PairingButtons.kt file must be deleted (REQ-UI-393.1)",
            pairingButtonsFile.exists()
        )
    }

    @Test
    fun testControlTrackingScreen_omitsDeadPairingParametersAndDialog() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.tracking.controltracking.ControlTrackingScreenKt")
        val methods = clazz.declaredMethods
        val screenMethod = methods.find { it.name.startsWith("ControlTrackingScreen") && Modifier.isPublic(it.modifiers) }
        assertNotNull("ControlTrackingScreen composable must exist", screenMethod)

        val forbiddenMethodNames = listOf("DeviceTypeSelectionDialog", "PairingButtons")
        for (token in forbiddenMethodNames) {
            assertFalse(
                "ControlTrackingScreenKt must not define dead composable '$token' (REQ-UI-393.2)",
                methods.any { it.name.startsWith(token) }
            )
        }
    }

    @Test
    fun testControlTrackingViewModel_omitsDeadPairingStateAndRetainsActiveSensorDiscovery() {
        val vmClazz = ControlTrackingViewModel::class.java
        val methods = vmClazz.declaredMethods.map { it.name }
        val fields = vmClazz.declaredFields.map { it.name }

        val forbiddenTokens = listOf(
            "ToPairing",
            "selectingProtocol",
            "_selectingProtocol",
            "isAntProperlyInstalled",
            "isBluetoothSupported",
            "onPairingClicked",
            "onDeviceTypeSelected",
            "onCancelDeviceTypeSelection"
        )
        for (token in forbiddenTokens) {
            assertFalse(
                "ControlTrackingViewModel must not contain dead pairing method '$token' (REQ-UI-393.3)",
                methods.contains(token)
            )
            assertFalse(
                "ControlTrackingViewModel must not contain dead pairing field '$token' (REQ-UI-393.3)",
                fields.contains(token)
            )
        }

        val requiredMethods = listOf(
            "onSearchClicked"
        )
        for (token in requiredMethods) {
            assertTrue(
                "ControlTrackingViewModel must retain active sensor method '$token'",
                methods.contains(token)
            )
        }
    }

    @Test
    fun testTrackingTabsScreenAndMainActivity_omitDeadPairingWiring() {
        val tabsClazz = Class.forName("com.atrainingtracker.trainingtracker.ui.tracking.trackingtabs.TrackingTabsScreenKt")
        assertNotNull("TrackingTabsScreenKt must exist", tabsClazz)

        val mainActivityClazz = Class.forName("com.atrainingtracker.trainingtracker.activities.MainActivityWithNavigation")
        val mainMethods = mainActivityClazz.declaredMethods.map { it.name }
        assertFalse(
            "MainActivityWithNavigation must not contain dead startPairing function (REQ-UI-393.4)",
            mainMethods.contains("startPairing")
        )
    }

    @Test
    fun testMySensorsPairingFlowAnd9LocaleStrings_remainIntact() {
        val dialogClazz = Class.forName("com.atrainingtracker.banalservice.ui.devices.DeviceTypeSelectionDialogKt")
        val screenClazz = Class.forName("com.atrainingtracker.banalservice.ui.devices.devicetabs.DevicesTabbedScreenKt")
        assertNotNull("DeviceTypeSelectionDialogKt must exist for My Sensors", dialogClazz)
        assertNotNull("DevicesTabbedScreenKt must exist", screenClazz)

        val locales = listOf("values", "values-de", "values-es", "values-fr", "values-it", "values-ja", "values-nl", "values-pl", "values-pt")
        for (locale in locales) {
            val strings = LocalizationTestCache.getStrings(locale)
            val value = strings["devices_pair_protocol_title"]
            assertTrue("Missing devices_pair_protocol_title in $locale", !value.isNullOrBlank())
        }
    }
}
