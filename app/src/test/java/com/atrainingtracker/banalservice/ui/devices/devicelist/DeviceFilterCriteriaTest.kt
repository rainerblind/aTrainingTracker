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

package com.atrainingtracker.banalservice.ui.devices.devicelist

import com.atrainingtracker.banalservice.Protocol
import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.banalservice.ui.devices.devicedata.DeviceUiData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceFilterCriteriaTest {

    private fun createDevice(
        id: Long = 1L,
        protocol: Protocol = Protocol.ANT_PLUS,
        deviceType: DeviceType = DeviceType.HRM,
        batteryPercentage: Int = 85,
        isConnected: Boolean = false,
        isPaired: Boolean = true
    ): DeviceUiData {
        return DeviceUiData(
            id = id,
            protocol = protocol,
            deviceType = deviceType,
            lastSeen = "2026-10-11",
            manufacturer = "Garmin",
            deviceName = "Test Sensor",
            isPaired = isPaired,
            linkedEquipment = emptyList(),
            availableEquipment = emptyList(),
            powerFeaturesFlags = null,
            batteryPercentage = batteryPercentage,
            linkedSportTypes = emptyList(),
            deviceTypeIconRes = 0,
            batteryStatusIconRes = 0,
            onEquipmentResId = 0,
            wheelCircumference = null,
            calibrationFactor = null,
            powerFeatures = null,
            isConnected = isConnected,
            mainValue = null,
            allValues = null,
            isNewlyDiscovered = false
        )
    }

    @Test
    fun testMatches_protocolFilter() {
        val antDevice = createDevice(protocol = Protocol.ANT_PLUS)
        val bleDevice = createDevice(protocol = Protocol.BLUETOOTH_LE)

        val antCriteria = DeviceFilterCriteria(protocol = Protocol.ANT_PLUS)
        assertTrue(antCriteria.matches(antDevice))
        assertFalse(antCriteria.matches(bleDevice))

        val bleCriteria = DeviceFilterCriteria(protocol = Protocol.BLUETOOTH_LE)
        assertFalse(bleCriteria.matches(antDevice))
        assertTrue(bleCriteria.matches(bleDevice))

        val allCriteria = DeviceFilterCriteria(protocol = Protocol.ALL)
        assertTrue(allCriteria.matches(antDevice))
        assertTrue(allCriteria.matches(bleDevice))

        val nullCriteria = DeviceFilterCriteria(protocol = null)
        assertTrue(nullCriteria.matches(antDevice))
        assertTrue(nullCriteria.matches(bleDevice))
    }

    @Test
    fun testMatches_deviceTypeFilter() {
        val hrm = createDevice(deviceType = DeviceType.HRM)
        val power = createDevice(deviceType = DeviceType.BIKE_POWER)
        val cadence = createDevice(deviceType = DeviceType.BIKE_CADENCE)

        val hrmOnly = DeviceFilterCriteria(deviceTypes = setOf(DeviceType.HRM))
        assertTrue(hrmOnly.matches(hrm))
        assertFalse(hrmOnly.matches(power))
        assertFalse(hrmOnly.matches(cadence))

        val powerAndCadence = DeviceFilterCriteria(deviceTypes = setOf(DeviceType.BIKE_POWER, DeviceType.BIKE_CADENCE))
        assertFalse(powerAndCadence.matches(hrm))
        assertTrue(powerAndCadence.matches(power))
        assertTrue(powerAndCadence.matches(cadence))

        val emptyTypes = DeviceFilterCriteria(deviceTypes = emptySet())
        assertTrue(emptyTypes.matches(hrm))
        assertTrue(emptyTypes.matches(power))
        assertTrue(emptyTypes.matches(cadence))
    }

    @Test
    fun testMatches_batteryFilter_critical() {
        val battery0 = createDevice(batteryPercentage = 0)
        val battery15 = createDevice(batteryPercentage = 15)
        val battery20 = createDevice(batteryPercentage = 20)
        val battery21 = createDevice(batteryPercentage = 21)
        val battery80 = createDevice(batteryPercentage = 80)
        val batteryUnknown = createDevice(batteryPercentage = -1)

        val criticalCriteria = DeviceFilterCriteria(batteryFilter = BatteryFilter.CRITICAL_ONLY)
        assertTrue(criticalCriteria.matches(battery0))
        assertTrue(criticalCriteria.matches(battery15))
        assertTrue(criticalCriteria.matches(battery20))
        assertFalse(criticalCriteria.matches(battery21))
        assertFalse(criticalCriteria.matches(battery80))
        assertFalse(criticalCriteria.matches(batteryUnknown))
    }

    @Test
    fun testMatches_batteryFilter_normal() {
        val battery0 = createDevice(batteryPercentage = 0)
        val battery20 = createDevice(batteryPercentage = 20)
        val battery21 = createDevice(batteryPercentage = 21)
        val battery80 = createDevice(batteryPercentage = 80)
        val batteryUnknown = createDevice(batteryPercentage = -1)

        val normalCriteria = DeviceFilterCriteria(batteryFilter = BatteryFilter.NORMAL)
        assertFalse(normalCriteria.matches(battery0))
        assertFalse(normalCriteria.matches(battery20))
        assertTrue(normalCriteria.matches(battery21))
        assertTrue(normalCriteria.matches(battery80))
        assertFalse(normalCriteria.matches(batteryUnknown))
    }

    @Test
    fun testActiveFilterCount_and_isEmpty() {
        val empty = DeviceFilterCriteria()
        assertEquals(0, empty.activeFilterCount)
        assertTrue(empty.isEmpty)
        assertFalse(empty.isNotEmpty)

        val withProto = DeviceFilterCriteria(protocol = Protocol.ANT_PLUS)
        assertEquals(1, withProto.activeFilterCount)
        assertFalse(withProto.isEmpty)
        assertTrue(withProto.isNotEmpty)

        val withProtoAndTypes = DeviceFilterCriteria(
            protocol = Protocol.BLUETOOTH_LE,
            deviceTypes = setOf(DeviceType.HRM, DeviceType.BIKE_POWER)
        )
        assertEquals(2, withProtoAndTypes.activeFilterCount)

        val withAllThree = DeviceFilterCriteria(
            protocol = Protocol.BLUETOOTH_LE,
            deviceTypes = setOf(DeviceType.HRM),
            batteryFilter = BatteryFilter.CRITICAL_ONLY
        )
        assertEquals(3, withAllThree.activeFilterCount)
    }

    @Test
    fun testSerialization_toJson_fromJson() {
        val original = DeviceFilterCriteria(
            protocol = Protocol.ANT_PLUS,
            deviceTypes = setOf(DeviceType.HRM, DeviceType.BIKE_POWER),
            batteryFilter = BatteryFilter.CRITICAL_ONLY
        )
        val json = original.toJson()
        val restored = DeviceFilterCriteria.fromJson(json)

        assertEquals(original.protocol, restored.protocol)
        assertEquals(original.deviceTypes, restored.deviceTypes)
        assertEquals(original.batteryFilter, restored.batteryFilter)
        assertEquals(original.activeFilterCount, restored.activeFilterCount)

        val emptyRestored = DeviceFilterCriteria.fromJson("")
        assertTrue(emptyRestored.isEmpty)

        val nullRestored = DeviceFilterCriteria.fromJson(null)
        assertTrue(nullRestored.isEmpty)
    }
}
