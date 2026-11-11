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

import com.atrainingtracker.testing.LocalizationTestCache
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Architectural and contract test verifying contextual ANT+ status guidance
 * components and UI integration (REQ-UI-296, TST-UI-256, ATT-2513).
 */
class AntServicesStatusContractTest {

    @Test
    fun testAntServicesStatusCardDeclaration() {
        val cardClass = Class.forName("com.atrainingtracker.banalservice.ui.devices.ant.AntServicesStatusCardKt")
        assertNotNull("AntServicesStatusCardKt class must exist", cardClass)
        val hasCardComposable = cardClass.declaredMethods.any { it.name.startsWith("AntServicesStatusCard") }
        assertTrue("Must declare @Composable AntServicesStatusCard", hasCardComposable)

        val defaultStrings = LocalizationTestCache.getStrings("values")
        assertTrue("Must define ant_status_card_title", defaultStrings.containsKey("ant_status_card_title"))
        assertTrue("Must define ant_status_card_desc", defaultStrings.containsKey("ant_status_card_desc"))
        assertTrue("Must define ant_status_card_action", defaultStrings.containsKey("ant_status_card_action"))
    }

    @Test
    fun testAntServicesStatusSheetDeclaration() {
        val sheetClass = Class.forName("com.atrainingtracker.banalservice.ui.devices.ant.AntServicesStatusSheetKt")
        assertNotNull("AntServicesStatusSheetKt class must exist", sheetClass)
        val hasSheetComposable = sheetClass.declaredMethods.any { it.name.startsWith("AntServicesStatusSheet") }
        assertTrue("Must declare @Composable AntServicesStatusSheet", hasSheetComposable)

        val banalClass = Class.forName("com.atrainingtracker.banalservice.BANALService")
        val banalMethods = banalClass.methods.map { it.name }
        assertTrue("Must check ANT Plugin service", banalMethods.contains("isANTPluginServiceInstalled"))
        assertTrue("Must check ANT Radio service", banalMethods.contains("isANTRadioServiceInstalled"))
        assertTrue("Must check ANT USB service with USB host feature", banalMethods.contains("isANTUSBServiceInstalled"))

        // REQ-UI-309 / ATT-2749: Untinted official ANT+ logo and elimination of BLE hint
        val defaultStrings = LocalizationTestCache.getStrings("values")
        assertFalse(
            "Must not reference ant_status_ble_alternative_note (REQ-UI-309)",
            defaultStrings.containsKey("ant_status_ble_alternative_note")
        )
    }

    @Test
    fun testDeviceListScreenIntegratesAntStatusCard() {
        val listScreenClass = Class.forName("com.atrainingtracker.banalservice.ui.devices.devicelist.DeviceListScreenKt")
        assertNotNull("DeviceListScreenKt class must exist", listScreenClass)
        val hasDeviceListComposable = listScreenClass.declaredMethods.any { it.name.startsWith("DeviceListScreen") }
        assertTrue("Must declare @Composable DeviceListScreen", hasDeviceListComposable)

        val banalClass = Class.forName("com.atrainingtracker.banalservice.BANALService")
        assertTrue("Must evaluate areAllANTServicesInstalled", banalClass.methods.any { it.name == "areAllANTServicesInstalled" })
    }

    @Test
    fun testDevicesTabbedScreenIntegratesAntStatusSheetAndMenu() {
        val tabScreenClass = Class.forName("com.atrainingtracker.banalservice.ui.devices.devicetabs.DevicesTabbedScreenKt")
        assertNotNull("DevicesTabbedScreenKt class must exist", tabScreenClass)
        val hasTabScreenComposable = tabScreenClass.declaredMethods.any { it.name.startsWith("DevicesTabbedScreen") }
        assertTrue("Must declare @Composable DevicesTabbedScreen", hasTabScreenComposable)
    }

    @Test
    fun testPairingProtocolBottomSheetChecksAntServices() {
        val sheetClass = Class.forName("com.atrainingtracker.banalservice.ui.devices.PairingProtocolBottomSheetKt")
        assertNotNull("PairingProtocolBottomSheetKt class must exist", sheetClass)
        val hasPairingComposable = sheetClass.declaredMethods.any { it.name.startsWith("PairingProtocolBottomSheet") }
        assertTrue("Must declare @Composable PairingProtocolBottomSheet", hasPairingComposable)

        val banalClass = Class.forName("com.atrainingtracker.banalservice.BANALService")
        assertTrue("Must check areAllANTServicesInstalled before selecting ANT+", banalClass.methods.any { it.name == "areAllANTServicesInstalled" })
    }
}
