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

import com.atrainingtracker.R
import com.atrainingtracker.banalservice.Protocol
import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.banalservice.devices.bluetooth_le.polar.PolarOfflineCapabilityStore
import com.atrainingtracker.banalservice.ui.devices.devicedata.DeviceUiData
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for Polar H10 vs. Polar Verity Sense detection and backup toggle UI contract
 * in [EditDeviceDialog] (`ATT-7923`, `REQ-SET-108.1`, `TST-SET-110.5`).
 */
class EditDevicePolarBackupTest {

    @After
    fun tearDown() {
        PolarOfflineCapabilityStore.resetForTesting()
    }

    private fun createHrmUiData(
        name: String,
        manufacturer: String = "Polar Electro",
        deviceType: DeviceType = DeviceType.HRM
    ): DeviceUiData {
        return DeviceUiData(
            id = 42L,
            protocol = Protocol.BLUETOOTH_LE,
            deviceType = deviceType,
            lastSeen = "now",
            manufacturer = manufacturer,
            deviceName = name,
            isPaired = true,
            linkedEquipment = emptyList(),
            availableEquipment = emptyList(),
            powerFeaturesFlags = null,
            batteryPercentage = 95,
            linkedSportTypes = emptyList(),
            deviceTypeIconRes = 0,
            batteryStatusIconRes = 0,
            onEquipmentResId = 0,
            wheelCircumference = null,
            calibrationFactor = null,
            powerFeatures = null,
            baselineCircumference = null,
            polarH10OfflineRecording = true,
            isConnected = true,
            mainValue = "142 bpm",
            allValues = null
        )
    }

    @Test
    fun testVeritySenseDetection_matchesOpticalModelsAndExcludesH10AndGenericHrm() {
        val polarSense = createHrmUiData("Polar Sense A1B2C3D4")
        val veritySense = createHrmUiData("Polar Verity Sense 9876ABCD")
        val oh1 = createHrmUiData("Polar OH1 11223344")
        val h10 = createHrmUiData("Polar H10 1C3B2421")
        val garminHrm = createHrmUiData("HRM-Pro Plus", manufacturer = "Garmin")

        assertTrue(isPolarVeritySenseDevice(polarSense))
        assertTrue(isPolarVeritySenseDevice(veritySense))
        assertTrue(isPolarVeritySenseDevice(oh1))

        assertFalse(isPolarH10Device(polarSense))
        assertFalse(isPolarH10Device(veritySense))
        assertFalse(isPolarVeritySenseDevice(h10))
        assertTrue(isPolarH10Device(h10))
        assertFalse(isPolarVeritySenseDevice(garminHrm))
    }

    @Test
    fun testVeritySenseDetection_matchesRenamedDeviceWithStoredPftpCapability() {
        val mac = "AA:BB:CC:DD:EE:01"
        val renamed = createHrmUiData("Pool Optical Band", manufacturer = "Polar Electro")
        assertFalse(isPolarVeritySenseDevice(renamed, macAddress = mac))

        PolarOfflineCapabilityStore.markOfflineRecordingCapable(null, mac)
        assertTrue(isPolarVeritySenseDevice(renamed, macAddress = mac))
    }

    @Test
    fun testRenamedH10WithDisMetadata_routesToH10NotVeritySenseEvenWithLegacyOfflineRec() {
        val mac = "AA:BB:CC:DD:EE:02"
        val renamedH10 = createHrmUiData("Chest Strap", manufacturer = "")
        PolarOfflineCapabilityStore.markOfflineRecordingCapable(null, mac)
        PolarOfflineCapabilityStore.recordDisMetadata(null, mac, modelNumber = "H10", serialNumber = "1C3B2421")

        assertTrue(isPolarH10Device(renamedH10, macAddress = mac))
        assertFalse(isPolarVeritySenseDevice(renamedH10, macAddress = mac))
    }

    @Test
    fun testProtocolGateNonPolarMfgAndEmptyNameResilience_TST_CON_049_4() {
        // REQ-CON-049.4 / TST-CON-049.4: Non-BLE (ANT+) HRM renamed "Polar H10" must never expose Polar options
        val antHrmNamedPolar = createHrmUiData("Polar H10", manufacturer = "").copy(protocol = Protocol.ANT_PLUS)
        assertFalse(isPolarH10Device(antHrmNamedPolar))
        assertFalse(isPolarVeritySenseDevice(antHrmNamedPolar))

        // Explicitly non-Polar manufacturer blocks legacy name fallback
        val garminNamedPolar = createHrmUiData("Polar H10", manufacturer = "Garmin")
        assertFalse(isPolarH10Device(garminNamedPolar))
        assertFalse(isPolarVeritySenseDevice(garminNamedPolar))

        // Empty-name Verity Sense (legacy offline_rec_ or hardware VERITY_SENSE) & H10 stay detected
        val verityMac = "AA:BB:CC:DD:EE:03"
        PolarOfflineCapabilityStore.markOfflineRecordingCapable(null, verityMac)
        val emptyVerity = createHrmUiData("", manufacturer = "")
        assertTrue(isPolarVeritySenseDevice(emptyVerity, macAddress = verityMac))
        assertFalse(isPolarH10Device(emptyVerity, macAddress = verityMac))

        val h10Mac = "AA:BB:CC:DD:EE:04"
        PolarOfflineCapabilityStore.recordDisMetadata(null, h10Mac, modelNumber = "H10", serialNumber = "1C3B2421")
        val emptyH10 = createHrmUiData("", manufacturer = "")
        assertTrue(isPolarH10Device(emptyH10, macAddress = h10Mac))
        assertFalse(isPolarVeritySenseDevice(emptyH10, macAddress = h10Mac))
    }

    @Test
    fun testProtocolGate_bleOnlyForPolarH10AndVeritySenseEvenWithManufacturerAndDisMetadata_ATT_8442() {
        val h10Mac = "AA:BB:CC:DD:EE:61"
        val verityMac = "AA:BB:CC:DD:EE:62"
        PolarOfflineCapabilityStore.recordDisMetadata(null, h10Mac, modelNumber = "H10", serialNumber = "1C3B2421")
        PolarOfflineCapabilityStore.markOfflineRecordingCapable(null, verityMac)
        PolarOfflineCapabilityStore.recordDisMetadata(null, verityMac, modelNumber = "Verity Sense", serialNumber = "9876ABCD")

        val bleH10 = createHrmUiData("Polar H10 1C3B2421", manufacturer = "Polar Electro")
        val bleVerity = createHrmUiData("Polar Verity Sense 9876ABCD", manufacturer = "Polar Electro")
        assertTrue(isPolarH10Device(bleH10, macAddress = h10Mac))
        assertTrue(isPolarVeritySenseDevice(bleVerity, macAddress = verityMac))

        for (nonBleProtocol in Protocol.entries.filter { it != Protocol.BLUETOOTH_LE }) {
            val nonBleH10 = bleH10.copy(protocol = nonBleProtocol)
            val nonBleVerity = bleVerity.copy(protocol = nonBleProtocol)
            assertFalse(
                "isPolarH10Device must reject $nonBleProtocol",
                isPolarH10Device(nonBleH10, macAddress = h10Mac)
            )
            assertFalse(
                "isPolarVeritySenseDevice must reject $nonBleProtocol",
                isPolarVeritySenseDevice(nonBleVerity, macAddress = verityMac)
            )
        }
    }

    @Test
    fun testPolarBackupRecordingCard_andStringsContractExist() {
        val clazz = Class.forName("com.atrainingtracker.banalservice.ui.devices.editdevice.EditDeviceDialogKt")
        val cardMethod = clazz.declaredMethods.firstOrNull { it.name.startsWith("PolarBackupRecordingCard") }
        assertNotNull("PolarBackupRecordingCard composable must exist", cardMethod)
        assertTrue(R.string.device_polar_verity_backup_recording_title != 0)
        assertTrue(R.string.device_polar_verity_backup_recording_desc != 0)
        assertTrue(R.string.device_polar_h10_backup_recording_requires_android_13 != 0)
    }
}
