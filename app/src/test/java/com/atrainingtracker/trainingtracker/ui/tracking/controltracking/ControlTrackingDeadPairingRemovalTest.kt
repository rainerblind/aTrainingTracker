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

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Architectural contract test enforcing REQ-UI-393 / TST-UI-356 (ATT-3692):
 * Removal of dead sensor pairing code from ControlTrackingScreen, ControlTrackingViewModel,
 * TrackingTabsScreen, and MainActivityWithNavigation while preserving active sensor discovery
 * and My Sensors pairing.
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
        val screenFile = resolveFile("src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/ControlTrackingScreen.kt")
        assertTrue(screenFile.exists())
        val content = screenFile.readText()

        val forbiddenTokens = listOf(
            "isAntSupported",
            "isBluetoothSupported",
            "onPairingClicked",
            "selectingProtocol",
            "onDeviceTypeSelected",
            "onCancelDeviceTypeSelection",
            "DeviceTypeSelectionDialog"
        )
        for (token in forbiddenTokens) {
            assertFalse(
                "ControlTrackingScreen.kt must not contain dead pairing symbol '$token' (REQ-UI-393.2)",
                content.contains(token)
            )
        }
    }

    @Test
    fun testControlTrackingViewModel_omitsDeadPairingStateAndRetainsActiveSensorDiscovery() {
        val vmFile = resolveFile("src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/ControlTrackingViewModel.kt")
        assertTrue(vmFile.exists())
        val content = vmFile.readText()

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
                "ControlTrackingViewModel.kt must not contain dead pairing symbol '$token' (REQ-UI-393.3)",
                content.contains(token)
            )
        }

        val requiredTokens = listOf(
            "hasPairedRemoteDevices",
            "remoteDevices",
            "onSearchClicked",
            "ToEditDevice",
            "ShowRecoveryDialog"
        )
        for (token in requiredTokens) {
            assertTrue(
                "ControlTrackingViewModel.kt must retain active sensor/recovery symbol '$token'",
                content.contains(token)
            )
        }
    }

    @Test
    fun testTrackingTabsScreenAndMainActivity_omitDeadPairingWiring() {
        val tabsFile = resolveFile("src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/trackingtabs/TrackingTabsScreen.kt")
        assertTrue(tabsFile.exists())
        val tabsContent = tabsFile.readText()

        val forbiddenInTabs = listOf(
            "selectingProtocol",
            "ToPairing",
            "isAntSupported",
            "isBluetoothSupported",
            "onPairingClicked",
            "onDeviceTypeSelected",
            "onCancelDeviceTypeSelection"
        )
        for (token in forbiddenInTabs) {
            assertFalse(
                "TrackingTabsScreen.kt must not contain dead pairing symbol '$token' (REQ-UI-393.4)",
                tabsContent.contains(token)
            )
        }

        val mainActivityFile = resolveFile("src/main/java/com/atrainingtracker/trainingtracker/activities/MainActivityWithNavigation.kt")
        assertTrue(mainActivityFile.exists())
        val mainActivityContent = mainActivityFile.readText()
        assertFalse(
            "MainActivityWithNavigation.kt must not contain dead startPairing function (REQ-UI-393.4)",
            mainActivityContent.contains("fun startPairing(")
        )
    }

    @Test
    fun testMySensorsPairingFlowAnd9LocaleStrings_remainIntact() {
        val dialogFile = resolveFile("src/main/java/com/atrainingtracker/banalservice/ui/devices/DeviceTypeSelectionDialog.kt")
        val screenFile = resolveFile("src/main/java/com/atrainingtracker/banalservice/ui/devices/devicetabs/DevicesTabbedScreen.kt")
        assertTrue("DeviceTypeSelectionDialog.kt must remain intact for My Sensors", dialogFile.exists())
        assertTrue("DevicesTabbedScreen.kt must exist", screenFile.exists())
        assertTrue(
            "DevicesTabbedScreen.kt must reference DeviceTypeSelectionDialog",
            screenFile.readText().contains("DeviceTypeSelectionDialog")
        )

        val locales = listOf("values", "values-de", "values-es", "values-fr", "values-it", "values-ja", "values-nl", "values-pl", "values-pt")
        for (locale in locales) {
            val stringsFile = resolveFile("src/main/res/$locale/strings.xml")
            assertTrue("Missing $locale/strings.xml", stringsFile.exists())
            val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(stringsFile)
            val nodes = doc.getElementsByTagName("string")
            val map = mutableMapOf<String, String>()
            for (i in 0 until nodes.length) {
                val item = nodes.item(i)
                val name = item.attributes?.getNamedItem("name")?.nodeValue ?: continue
                map[name] = item.textContent
            }
            assertTrue("Missing devices_pair_protocol_title in $locale", !map["devices_pair_protocol_title"].isNullOrBlank())
        }
    }
}
