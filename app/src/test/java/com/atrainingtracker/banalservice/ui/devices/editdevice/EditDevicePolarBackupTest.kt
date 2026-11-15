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
    fun testPolarBackupRecordingCard_andStringsContractExist() {
        val clazz = Class.forName("com.atrainingtracker.banalservice.ui.devices.editdevice.EditDeviceDialogKt")
        val cardMethod = clazz.declaredMethods.firstOrNull { it.name.startsWith("PolarBackupRecordingCard") }
        assertNotNull("PolarBackupRecordingCard composable must exist", cardMethod)
        assertTrue(R.string.device_polar_verity_backup_recording_title != 0)
        assertTrue(R.string.device_polar_verity_backup_recording_desc != 0)
        assertTrue(R.string.device_polar_h10_backup_recording_requires_android_13 != 0)
    }
}
