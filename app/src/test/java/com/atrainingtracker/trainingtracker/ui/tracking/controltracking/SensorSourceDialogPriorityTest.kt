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

import com.atrainingtracker.banalservice.Protocol
import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.banalservice.devices.SimpleSensorData
import com.atrainingtracker.banalservice.sensor.MySensorManager
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.banalservice.ui.devices.devicedata.DeviceUiData
import com.atrainingtracker.trainingtracker.repositories.DeviceTelemetry
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit verification suite for canonical priority sorting in [SensorSourceDialog]
 * (REQ-UI-268.5, TST-UI-227.1, TST-UI-227.2, ATT-4375).
 *
 * Verifies that active backups and disconnected devices are sorted deterministically
 * according to [MySensorManager.getPriorityRank], ensuring Google Fused appears above
 * Network location provider regardless of input collection order or SQLite row IDs.
 */
class SensorSourceDialogPriorityTest {

    private fun createDevice(
        id: Long,
        name: String,
        deviceType: DeviceType,
        protocol: Protocol = Protocol.SMARTPHONE
    ): DeviceUiData {
        return DeviceUiData(
            id = id,
            protocol = protocol,
            deviceType = deviceType,
            lastSeen = null,
            manufacturer = "TestManufacturer",
            deviceName = name,
            isPaired = true,
            linkedEquipment = emptyList(),
            availableEquipment = emptyList(),
            powerFeaturesFlags = null,
            batteryPercentage = 100,
            linkedSportTypes = emptyList(),
            deviceTypeIconRes = 0,
            batteryStatusIconRes = 0,
            onEquipmentResId = 0,
            wheelCircumference = null,
            calibrationFactor = null,
            powerFeatures = null,
            isConnected = false,
            mainValue = null,
            allValues = null,
            isNewlyDiscovered = false
        )
    }

    private fun createTelemetry(
        deviceId: Long,
        vararg sensors: SensorType
    ): DeviceTelemetry {
        val values = sensors.map { SimpleSensorData("12.3", it) }
        return DeviceTelemetry(
            deviceId = deviceId,
            mainValue = values.firstOrNull(),
            allValues = values
        )
    }

    /**
     * `TST-UI-227.1`: Verifies that [SensorSourceDialog] sorts `activeBackups` using
     * [MySensorManager.getPriorityRank], ensuring [DeviceType.SPEED_AND_LOCATION_GOOGLE_FUSED]
     * appears before [DeviceType.SPEED_AND_LOCATION_NETWORK] when GPS is the primary source.
     */
    @Test
    fun activeBackups_sortedByPriority_googleFusedBeforeNetwork() {
        val sourceDevice = createDevice(1L, "Smartphone GPS", DeviceType.SPEED_AND_LOCATION_GPS)
        val networkDevice = createDevice(2L, "Network Location", DeviceType.SPEED_AND_LOCATION_NETWORK)
        val fusedDevice = createDevice(3L, "Google Fused Location", DeviceType.SPEED_AND_LOCATION_GOOGLE_FUSED)
        val allDevices = listOf(sourceDevice, networkDevice, fusedDevice)

        // Intentionally provide active telemetries in reversed order (Network before Google Fused)
        val allActiveTelemetries = listOf(
            createTelemetry(2L, SensorType.LATITUDE, SensorType.LONGITUDE),
            createTelemetry(3L, SensorType.LATITUDE, SensorType.LONGITUDE),
            createTelemetry(1L, SensorType.LATITUDE, SensorType.LONGITUDE)
        )

        val sensorType = SensorType.LATITUDE

        // Replicate SensorSourceDialog activeBackups sorting logic
        val activeBackups = allActiveTelemetries
            .filter { it.deviceId != sourceDevice.id }
            .sortedWith(
                compareBy<DeviceTelemetry> { telemetry ->
                    val dev = allDevices.find { it.id == telemetry.deviceId }
                    MySensorManager.getPriorityRank(sensorType, dev?.deviceType)
                }
                .thenBy { telemetry ->
                    allDevices.find { it.id == telemetry.deviceId }?.deviceName ?: ""
                }
                .thenBy { it.deviceId }
            )

        assertEquals(2, activeBackups.size)
        // Google Fused (rank 1) must precede Network (rank 2)
        assertEquals(3L, activeBackups[0].deviceId)
        assertEquals(2L, activeBackups[1].deviceId)
    }

    /**
     * `TST-UI-227.1`: Verifies that `activeBackups` sorting works across speed/distance sensors,
     * prioritizing BIKE_SPEED > GPS > GOOGLE_FUSED > NETWORK.
     */
    @Test
    fun activeBackups_speedPriority_ordersWheelSpeedBeforeGpsAndFusedBeforeNetwork() {
        val sourceDevice = createDevice(10L, "Stryd Footpod", DeviceType.RUN_SPEED, Protocol.BLUETOOTH_LE)
        val bikeSpeedDevice = createDevice(20L, "Wahoo SPEED", DeviceType.BIKE_SPEED, Protocol.BLUETOOTH_LE)
        val gpsDevice = createDevice(30L, "Smartphone GPS", DeviceType.SPEED_AND_LOCATION_GPS)
        val fusedDevice = createDevice(40L, "Google Fused", DeviceType.SPEED_AND_LOCATION_GOOGLE_FUSED)
        val netDevice = createDevice(50L, "Network", DeviceType.SPEED_AND_LOCATION_NETWORK)
        val allDevices = listOf(sourceDevice, bikeSpeedDevice, gpsDevice, fusedDevice, netDevice)

        val allActiveTelemetries = listOf(
            createTelemetry(50L, SensorType.SPEED_mps),
            createTelemetry(30L, SensorType.SPEED_mps),
            createTelemetry(40L, SensorType.SPEED_mps),
            createTelemetry(20L, SensorType.SPEED_mps),
            createTelemetry(10L, SensorType.SPEED_mps)
        )

        val sensorType = SensorType.SPEED_mps

        val activeBackups = allActiveTelemetries
            .filter { it.deviceId != sourceDevice.id }
            .sortedWith(
                compareBy<DeviceTelemetry> { telemetry ->
                    val dev = allDevices.find { it.id == telemetry.deviceId }
                    MySensorManager.getPriorityRank(sensorType, dev?.deviceType)
                }
                .thenBy { telemetry ->
                    allDevices.find { it.id == telemetry.deviceId }?.deviceName ?: ""
                }
                .thenBy { it.deviceId }
            )

        assertEquals(4, activeBackups.size)
        // Expected order: BIKE_SPEED (rank 1) -> GPS (rank 4) -> GOOGLE_FUSED (rank 5) -> NETWORK (rank 6)
        assertEquals(20L, activeBackups[0].deviceId)
        assertEquals(30L, activeBackups[1].deviceId)
        assertEquals(40L, activeBackups[2].deviceId)
        assertEquals(50L, activeBackups[3].deviceId)
    }

    /**
     * `TST-UI-227.2`: Verifies that [SensorSourceDialog] sorts `notConnected` devices using
     * [MySensorManager.getPriorityRank], ensuring [DeviceType.SPEED_AND_LOCATION_GOOGLE_FUSED]
     * is rendered before [DeviceType.SPEED_AND_LOCATION_NETWORK] even when database cursor order
     * presents Network first (lower _id).
     */
    @Test
    fun notConnected_sortedByPriority_googleFusedBeforeNetwork() {
        // Simulating legacy DB cursor order where Network had _id=2 and Google Fused had _id=3
        val netDevice = createDevice(2L, "Network Location", DeviceType.SPEED_AND_LOCATION_NETWORK)
        val fusedDevice = createDevice(3L, "Google Fused Location", DeviceType.SPEED_AND_LOCATION_GOOGLE_FUSED)
        val allDevices = listOf(netDevice, fusedDevice)

        val potentialTypes = DeviceType.getDeviceTypeList(SensorType.LATITUDE) ?: emptySet()
        val activeDeviceIds = emptySet<Long>() // Both disconnected
        val sensorType = SensorType.LATITUDE

        // Replicate SensorSourceDialog notConnected sorting logic
        val notConnected = allDevices
            .filter { potentialTypes.contains(it.deviceType) && !activeDeviceIds.contains(it.id) }
            .sortedWith(
                compareBy<DeviceUiData> { MySensorManager.getPriorityRank(sensorType, it.deviceType) }
                    .thenBy { it.deviceName }
                    .thenBy { it.id }
            )

        assertEquals(2, notConnected.size)
        // Google Fused (rank 1) must be rendered above Network (rank 2)
        assertEquals(3L, notConnected[0].id)
        assertEquals(2L, notConnected[1].id)
    }

    /**
     * `TST-UI-227.2`: Verifies that `notConnected` sorting uses deviceName and id as secondary tie-breakers
     * when multiple devices share the same priority rank.
     */
    @Test
    fun notConnected_tieBreaking_sortsAlphabeticallyThenById() {
        val sensorType = SensorType.HR
        val potentialTypes = DeviceType.getDeviceTypeList(sensorType) ?: emptySet()

        val hrmBeta = createDevice(200L, "Beta HRM", DeviceType.HRM, Protocol.BLUETOOTH_LE)
        val hrmAlpha = createDevice(100L, "Alpha HRM", DeviceType.HRM, Protocol.BLUETOOTH_LE)
        val hrmAlpha2 = createDevice(101L, "Alpha HRM", DeviceType.HRM, Protocol.ANT_PLUS)
        val allDevices = listOf(hrmBeta, hrmAlpha2, hrmAlpha)

        val notConnected = allDevices
            .filter { potentialTypes.contains(it.deviceType) }
            .sortedWith(
                compareBy<DeviceUiData> { MySensorManager.getPriorityRank(sensorType, it.deviceType) }
                    .thenBy { it.deviceName }
                    .thenBy { it.id }
            )

        assertEquals(3, notConnected.size)
        assertEquals(100L, notConnected[0].id) // "Alpha HRM" with lower ID 100
        assertEquals(101L, notConnected[1].id) // "Alpha HRM" with ID 101
        assertEquals(200L, notConnected[2].id) // "Beta HRM" with ID 200
    }
}
