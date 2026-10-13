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

package com.atrainingtracker.banalservice.ui.devices.ant

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Architectural and contract test verifying contextual ANT+ status guidance
 * components and UI integration (REQ-UI-296, TST-UI-256, ATT-2513).
 */
class AntServicesStatusContractTest {

    private fun findFile(relativePath: String): File {
        val candidates = listOf(
            File(relativePath),
            File("app/$relativePath"),
            File("../$relativePath"),
            File("../../$relativePath")
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("File not found in candidates: $relativePath")
    }

    @Test
    fun testAntServicesStatusCardDeclaration() {
        val cardFile = findFile("src/main/java/com/atrainingtracker/banalservice/ui/devices/ant/AntServicesStatusCard.kt")
        assertTrue("AntServicesStatusCard.kt must exist", cardFile.exists())
        val content = cardFile.readText()

        assertTrue("Must declare @Composable AntServicesStatusCard", content.contains("fun AntServicesStatusCard("))
        assertTrue("Must bind onCheckStatusClick", content.contains("onCheckStatusClick: () -> Unit"))
        assertTrue("Must reference ant_status_card_title", content.contains("R.string.ant_status_card_title"))
        assertTrue("Must reference ant_status_card_desc", content.contains("R.string.ant_status_card_desc"))
        assertTrue("Must reference ant_status_card_action", content.contains("R.string.ant_status_card_action"))
    }

    @Test
    fun testAntServicesStatusSheetDeclaration() {
        val sheetFile = findFile("src/main/java/com/atrainingtracker/banalservice/ui/devices/ant/AntServicesStatusSheet.kt")
        assertTrue("AntServicesStatusSheet.kt must exist", sheetFile.exists())
        val content = sheetFile.readText()

        assertTrue("Must declare @Composable AntServicesStatusSheet", content.contains("fun AntServicesStatusSheet("))
        assertTrue("Must bind onDismiss", content.contains("onDismiss: () -> Unit"))
        assertTrue("Must reference AppModalBottomSheet", content.contains("AppModalBottomSheet("))
        assertTrue("Must check ANT Plugin service", content.contains("isANTPluginServiceInstalled"))
        assertTrue("Must check ANT Radio service", content.contains("isANTRadioServiceInstalled"))
        assertTrue("Must check ANT USB service with USB host feature", content.contains("isANTUSBServiceInstalled"))
        assertTrue("Must reference market install URI", content.contains("market://details?id="))
        assertTrue("Must provide fallback play store URI", content.contains("https://play.google.com/store/apps/details?id="))

        // REQ-UI-309 / ATT-2749: Untinted official ANT+ logo and elimination of BLE hint
        assertTrue(
            "Must pass iconTint = Color.Unspecified to AppModalBottomSheet (REQ-UI-309)",
            content.contains("iconTint = Color.Unspecified")
        )
        org.junit.Assert.assertFalse(
            "Must not reference ant_status_ble_alternative_note (REQ-UI-309)",
            content.contains("ant_status_ble_alternative_note")
        )
        org.junit.Assert.assertFalse(
            "Must not reference logo_protocol_bluetooth (REQ-UI-309)",
            content.contains("logo_protocol_bluetooth")
        )
    }

    @Test
    fun testDeviceListScreenIntegratesAntStatusCard() {
        val listScreenFile = findFile("src/main/java/com/atrainingtracker/banalservice/ui/devices/devicelist/DeviceListScreen.kt")
        assertTrue("DeviceListScreen.kt must exist", listScreenFile.exists())
        val content = listScreenFile.readText()

        assertTrue("Must accept onCheckAntInstallation parameter", content.contains("onCheckAntInstallation: (() -> Unit)? = null"))
        assertTrue("Must evaluate areAllANTServicesInstalled", content.contains("areAllANTServicesInstalled"))
        assertTrue("Must conditionally render AntServicesStatusCard", content.contains("AntServicesStatusCard("))
    }

    @Test
    fun testDevicesTabbedScreenIntegratesAntStatusSheetAndMenu() {
        val tabScreenFile = findFile("src/main/java/com/atrainingtracker/banalservice/ui/devices/devicetabs/DevicesTabbedScreen.kt")
        assertTrue("DevicesTabbedScreen.kt must exist", tabScreenFile.exists())
        val content = tabScreenFile.readText()

        assertTrue("Must declare showAntStatusSheet state", content.contains("var showAntStatusSheet by remember"))
        assertTrue("Must pass onCheckAntInstallation to DeviceListScreen", content.contains("onCheckAntInstallation = { showAntStatusSheet = true }"))
        assertTrue("Must render AntServicesStatusSheet", content.contains("AntServicesStatusSheet("))
    }

    @Test
    fun testPairingProtocolBottomSheetChecksAntServices() {
        val sheetFile = findFile("src/main/java/com/atrainingtracker/banalservice/ui/devices/PairingProtocolBottomSheet.kt")
        assertTrue("PairingProtocolBottomSheet.kt must exist", sheetFile.exists())
        val content = sheetFile.readText()

        assertTrue("Must accept onAntServicesMissing", content.contains("onAntServicesMissing: (() -> Unit)? = null"))
        assertTrue("Must check areAllANTServicesInstalled before selecting ANT+", content.contains("BANALService.areAllANTServicesInstalled"))
    }
}
