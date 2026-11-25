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

import android.content.Context
import android.database.Cursor
import com.atrainingtracker.banalservice.Protocol
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager
import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.banalservice.devices.SimpleSensorData
import com.atrainingtracker.banalservice.devices.bluetooth_le.polar.PolarOfflineCapabilityStore
import com.atrainingtracker.banalservice.sensor.HrHealthTier
import com.atrainingtracker.banalservice.sensor.HrModality
import com.atrainingtracker.banalservice.sensor.MySensorManager
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.banalservice.ui.devices.devicedata.DeviceUiData
import com.atrainingtracker.banalservice.ui.devices.devicedata.RawDeviceDataProvider
import com.atrainingtracker.banalservice.ui.devices.devicedata.raw2UiDeviceData
import com.atrainingtracker.trainingtracker.database.EquipmentAndSportTypeDiscoveryManager
import com.atrainingtracker.trainingtracker.database.EquipmentDbHelper
import com.atrainingtracker.trainingtracker.repositories.DeviceTelemetry
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Unit verification suite for canonical priority sorting in [SensorSourceDialog]
 * (`REQ-UI-268.5`, `TST-UI-227.1`, `TST-UI-227.2`, `ATT-4375`, `REQ-UI-2804`, `TST-UI-2804`, `ATT-9610`).
 *
 * Verifies:
 * - Non-HR active backups and disconnected devices sort deterministically via [MySensorManager.getPriorityRank].
 * - `SensorType.HR` active backups sort by `(healthTier [HEALTHY < DEGRADED < STALE_OR_NULL],
 *   modalityTier [ECG < ANT+ < Optical PPG < Other], deviceName, deviceId)`.
 * - `SensorType.HR` disconnected devices sort by `(modalityTier [ECG < ANT+ < Optical PPG < Other],
 *   deviceName, deviceId)` using persisted protocol and capability metadata.
 */
class SensorSourceDialogPriorityTest {

    @Before
    fun setUp() {
        PolarOfflineCapabilityStore.resetForTesting()
    }

    @After
    fun tearDown() {
        PolarOfflineCapabilityStore.resetForTesting()
    }

    private fun createDevice(
        id: Long,
        name: String,
        deviceType: DeviceType,
        protocol: Protocol = Protocol.SMARTPHONE,
        manufacturer: String = "TestManufacturer",
        btAddress: String? = null,
        hrModality: HrModality? = null,
        isPaired: Boolean = true,
        polarLiveImuEnabled: Boolean = false
    ): DeviceUiData {
        return DeviceUiData(
            id = id,
            protocol = protocol,
            deviceType = deviceType,
            lastSeen = null,
            manufacturer = manufacturer,
            deviceName = name,
            isPaired = isPaired,
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
            isNewlyDiscovered = false,
            btAddress = btAddress,
            hrModality = hrModality,
            polarLiveImuEnabled = polarLiveImuEnabled
        )
    }

    private fun createTelemetry(
        deviceId: Long,
        vararg sensors: SensorType,
        hrHealthTier: HrHealthTier? = null,
        hrModality: HrModality? = null
    ): DeviceTelemetry {
        val values = sensors.map { SimpleSensorData("142", it) }
        return DeviceTelemetry(
            deviceId = deviceId,
            mainValue = values.firstOrNull(),
            allValues = values,
            hrHealthTier = hrHealthTier,
            hrModality = hrModality
        )
    }

    /**
     * `TST-UI-227.1`: Verifies that [sortActiveBackupTelemetries] keeps non-HR sorting on
     * [MySensorManager.getPriorityRank], ensuring Google Fused appears before GPS.
     */
    @Test
    fun activeBackups_sortedByPriority_googleFusedBeforeGps() {
        val sourceDevice = createDevice(100L, "External GNSS", DeviceType.SPEED_AND_LOCATION_GPS)
        val gpsDevice = createDevice(1L, "Smartphone GPS", DeviceType.SPEED_AND_LOCATION_GPS)
        val fusedDevice = createDevice(2L, "Google Fused Location", DeviceType.SPEED_AND_LOCATION_GOOGLE_FUSED)
        val allDevices = listOf(sourceDevice, gpsDevice, fusedDevice)

        val allActiveTelemetries = listOf(
            createTelemetry(1L, SensorType.LATITUDE, SensorType.LONGITUDE),
            createTelemetry(2L, SensorType.LATITUDE, SensorType.LONGITUDE),
            createTelemetry(100L, SensorType.LATITUDE, SensorType.LONGITUDE)
        )

        val activeBackups = sortActiveBackupTelemetries(
            telemetries = allActiveTelemetries,
            sensorType = SensorType.LATITUDE,
            sourceDeviceId = sourceDevice.id,
            allDevices = allDevices
        )

        assertEquals(2, activeBackups.size)
        assertEquals(2L, activeBackups[0].deviceId)
        assertEquals(1L, activeBackups[1].deviceId)
    }

    /**
     * `TST-UI-227.1`: Verifies that non-HR `activeBackups` sorting works across speed/distance sensors,
     * prioritizing BIKE_SPEED > GOOGLE_FUSED > GPS (`REQ-TRK-024.1`).
     */
    @Test
    fun activeBackups_speedPriority_ordersWheelSpeedBeforeFusedAndFusedBeforeGps() {
        val sourceDevice = createDevice(10L, "Stryd Footpod", DeviceType.RUN_SPEED, Protocol.BLUETOOTH_LE)
        val bikeSpeedDevice = createDevice(20L, "Wahoo SPEED", DeviceType.BIKE_SPEED, Protocol.BLUETOOTH_LE)
        val gpsDevice = createDevice(30L, "Smartphone GPS", DeviceType.SPEED_AND_LOCATION_GPS)
        val fusedDevice = createDevice(40L, "Google Fused", DeviceType.SPEED_AND_LOCATION_GOOGLE_FUSED)
        val allDevices = listOf(sourceDevice, bikeSpeedDevice, gpsDevice, fusedDevice)

        val allActiveTelemetries = listOf(
            createTelemetry(30L, SensorType.SPEED_mps),
            createTelemetry(40L, SensorType.SPEED_mps),
            createTelemetry(20L, SensorType.SPEED_mps),
            createTelemetry(10L, SensorType.SPEED_mps)
        )

        val activeBackups = sortActiveBackupTelemetries(
            telemetries = allActiveTelemetries,
            sensorType = SensorType.SPEED_mps,
            sourceDeviceId = sourceDevice.id,
            allDevices = allDevices
        )

        assertEquals(3, activeBackups.size)
        assertEquals(20L, activeBackups[0].deviceId)
        assertEquals(40L, activeBackups[1].deviceId)
        assertEquals(30L, activeBackups[2].deviceId)
    }

    /**
     * `TST-UI-227.2`: Verifies that [sortNotConnectedDevices] keeps non-HR sorting on
     * [MySensorManager.getPriorityRank], ensuring Google Fused precedes GPS.
     */
    @Test
    fun notConnected_sortedByPriority_googleFusedBeforeGps() {
        val gpsDevice = createDevice(1L, "Smartphone GPS", DeviceType.SPEED_AND_LOCATION_GPS)
        val fusedDevice = createDevice(2L, "Google Fused Location", DeviceType.SPEED_AND_LOCATION_GOOGLE_FUSED)
        val allDevices = listOf(gpsDevice, fusedDevice)
        val potentialTypes = DeviceType.getDeviceTypeList(SensorType.LATITUDE) ?: emptySet()

        val notConnected = sortNotConnectedDevices(
            allDevices = allDevices,
            activeDeviceIds = emptySet(),
            potentialTypes = potentialTypes,
            sensorType = SensorType.LATITUDE
        )

        assertEquals(2, notConnected.size)
        assertEquals(2L, notConnected[0].id)
        assertEquals(1L, notConnected[1].id)
    }

    /**
     * `TST-UI-2804.2`: Verifies that HR `activeBackups` orders strictly by
     * `(healthTier [HEALTHY < DEGRADED < STALE_OR_NULL], modalityTier [ECG < ANT+ < Optical PPG < Other], deviceName, deviceId)`
     * even when alphabetical names would invert the order (e.g. `"Armband Optical"` vs. `"Polar H10"`).
     */
    @Test
    fun activeBackups_hr_ordersByHealthTierThenModalityThenNameThenId() {
        val primary = createDevice(1L, "Primary H10", DeviceType.HRM, Protocol.BLUETOOTH_LE, "Polar")
        val healthyEcgB = createDevice(20L, "Polar H10", DeviceType.HRM, Protocol.BLUETOOTH_LE, "Polar")
        val healthyEcgA1 = createDevice(12L, "Alpha ECG", DeviceType.HRM, Protocol.BLUETOOTH_LE, "Garmin")
        val healthyEcgA2 = createDevice(11L, "Alpha ECG", DeviceType.HRM, Protocol.BLUETOOTH_LE, "Garmin")
        val healthyAnt = createDevice(30L, "Garmin ANT+ HRM", DeviceType.HRM, Protocol.ANT_PLUS, "Garmin")
        val healthyOptical = createDevice(
            40L,
            "Armband Optical",
            DeviceType.HRM,
            Protocol.BLUETOOTH_LE,
            "Polar",
            hrModality = HrModality.OPTICAL_PPG
        )
        val healthyOther = createDevice(50L, "000 Generic HR", DeviceType.HRM, Protocol.ALL, "Generic")
        val degradedEcg = createDevice(60L, "AAA Degraded ECG", DeviceType.HRM, Protocol.BLUETOOTH_LE, "Polar")
        val degradedOptical = createDevice(
            70L,
            "BBB Degraded Optical",
            DeviceType.HRM,
            Protocol.BLUETOOTH_LE,
            "Polar",
            hrModality = HrModality.OPTICAL_PPG
        )
        val staleEcg = createDevice(80L, "000 Stale ECG", DeviceType.HRM, Protocol.BLUETOOTH_LE, "Polar")
        val nullHealthOptical = createDevice(
            90L,
            "AAA Null Health Optical",
            DeviceType.HRM,
            Protocol.BLUETOOTH_LE,
            "Polar",
            hrModality = HrModality.OPTICAL_PPG
        )

        val allDevices = listOf(
            primary,
            healthyEcgB,
            healthyEcgA1,
            healthyEcgA2,
            healthyAnt,
            healthyOptical,
            healthyOther,
            degradedEcg,
            degradedOptical,
            staleEcg,
            nullHealthOptical
        )

        val telemetries = listOf(
            createTelemetry(90L, SensorType.HR, hrHealthTier = null, hrModality = HrModality.OPTICAL_PPG),
            createTelemetry(80L, SensorType.HR, hrHealthTier = HrHealthTier.STALE_OR_NULL, hrModality = HrModality.ECG),
            createTelemetry(70L, SensorType.HR, hrHealthTier = HrHealthTier.DEGRADED, hrModality = HrModality.OPTICAL_PPG),
            createTelemetry(60L, SensorType.HR, hrHealthTier = HrHealthTier.DEGRADED, hrModality = HrModality.ECG),
            createTelemetry(50L, SensorType.HR, hrHealthTier = HrHealthTier.HEALTHY, hrModality = HrModality.OTHER),
            createTelemetry(40L, SensorType.HR, hrHealthTier = HrHealthTier.HEALTHY, hrModality = HrModality.OPTICAL_PPG),
            createTelemetry(30L, SensorType.HR, hrHealthTier = HrHealthTier.HEALTHY, hrModality = HrModality.ANT_PLUS),
            createTelemetry(20L, SensorType.HR, hrHealthTier = HrHealthTier.HEALTHY, hrModality = HrModality.ECG),
            createTelemetry(12L, SensorType.HR, hrHealthTier = HrHealthTier.HEALTHY, hrModality = HrModality.ECG),
            createTelemetry(11L, SensorType.HR, hrHealthTier = HrHealthTier.HEALTHY, hrModality = HrModality.ECG),
            createTelemetry(1L, SensorType.HR, hrHealthTier = HrHealthTier.HEALTHY, hrModality = HrModality.ECG)
        )

        val sorted = sortActiveBackupTelemetries(
            telemetries = telemetries,
            sensorType = SensorType.HR,
            sourceDeviceId = primary.id,
            allDevices = allDevices
        )

        assertEquals(
            listOf(
                11L, // HEALTHY + ECG + "Alpha ECG" + id 11
                12L, // HEALTHY + ECG + "Alpha ECG" + id 12
                20L, // HEALTHY + ECG + "Polar H10"
                30L, // HEALTHY + ANT_PLUS + "Garmin ANT+ HRM"
                40L, // HEALTHY + OPTICAL_PPG + "Armband Optical"
                50L, // HEALTHY + OTHER + "000 Generic HR"
                60L, // DEGRADED + ECG + "AAA Degraded ECG"
                70L, // DEGRADED + OPTICAL_PPG + "BBB Degraded Optical"
                80L, // STALE_OR_NULL + ECG + "000 Stale ECG"
                90L  // null (-> STALE_OR_NULL) + OPTICAL_PPG + "AAA Null Health Optical"
            ),
            sorted.map { it.deviceId }
        )
    }

    /**
     * `TST-UI-2804.2`: Verifies that HR `notConnected` orders strictly by
     * `(modalityTier [ECG < ANT+ < Optical PPG < Other], deviceName, deviceId)`, placing `"Polar H10"` (ECG)
     * and ANT+ ahead of `"Armband Optical"` (Optical PPG) despite alphabetical inversion.
     */
    @Test
    fun notConnected_hr_ordersByModalityThenNameThenId_includingAntiAlphabeticalNames() {
        val potentialTypes = DeviceType.getDeviceTypeList(SensorType.HR) ?: emptySet()

        val verityAddress = "CC:DD:EE:FF:00:11"
        PolarOfflineCapabilityStore.recordDisMetadata(
            context = null,
            macAddress = verityAddress,
            modelNumber = "Sense",
            confirmedPolarService = true
        )

        val armbandOptical = createDevice(
            id = 10L,
            name = "Armband Optical",
            deviceType = DeviceType.HRM,
            protocol = Protocol.BLUETOOTH_LE,
            manufacturer = "",
            btAddress = verityAddress,
            hrModality = HrModality.OPTICAL_PPG
        )
        val verityNamed = createDevice(
            id = 11L,
            name = "Polar Verity Sense",
            deviceType = DeviceType.HRM,
            protocol = Protocol.BLUETOOTH_LE,
            manufacturer = "Polar"
        )
        val antPlusB = createDevice(21L, "Beta ANT+ HRM", DeviceType.HRM, Protocol.ANT_PLUS, "Garmin")
        val antPlusA = createDevice(20L, "Alpha ANT+ HRM", DeviceType.HRM, Protocol.ANT_PLUS, "Garmin")
        val ecgPolarH10 = createDevice(30L, "Polar H10", DeviceType.HRM, Protocol.BLUETOOTH_LE, "Polar")
        val ecgAlpha2 = createDevice(32L, "Alpha ECG", DeviceType.HRM, Protocol.BLUETOOTH_LE, "Wahoo")
        val ecgAlpha1 = createDevice(31L, "Alpha ECG", DeviceType.HRM, Protocol.BLUETOOTH_LE, "Wahoo")
        val genericOther = createDevice(40L, "000 Other HRM", DeviceType.HRM, Protocol.ALL, "")

        val notConnected = sortNotConnectedDevices(
            allDevices = listOf(
                genericOther,
                verityNamed,
                armbandOptical,
                antPlusB,
                ecgAlpha2,
                ecgPolarH10,
                antPlusA,
                ecgAlpha1
            ),
            activeDeviceIds = emptySet(),
            potentialTypes = potentialTypes,
            sensorType = SensorType.HR
        )

        assertEquals(
            listOf(
                31L, // ECG + "Alpha ECG" + id 31
                32L, // ECG + "Alpha ECG" + id 32
                30L, // ECG + "Polar H10"
                20L, // ANT_PLUS + "Alpha ANT+ HRM"
                21L, // ANT_PLUS + "Beta ANT+ HRM"
                10L, // OPTICAL_PPG + "Armband Optical" (persisted capability)
                11L, // OPTICAL_PPG + "Polar Verity Sense"
                40L  // OTHER + "000 Other HRM"
            ),
            notConnected.map { it.id }
        )
    }

    /**
     * `TST-UI-2804.2`: Verifies end-to-end persistence hydration from `BT_ADDRESS` column in
     * [RawDeviceDataProvider] + [raw2UiDeviceData] + [PolarOfflineCapabilityStore] into [sortNotConnectedDevices].
     */
    @Test
    fun notConnected_hr_persistedCapabilityMetadataResolvesRenamedOpticalViaRawDeviceDataProvider() {
        val mockDevicesDb = mockk<DevicesDatabaseManager>(relaxed = true)
        val mockEqDb = mockk<EquipmentDbHelper>(relaxed = true)
        val mockDiscovery = mockk<EquipmentAndSportTypeDiscoveryManager>(relaxed = true)
        val mockContext = mockk<Context>(relaxed = true)

        val verityMac = "AA:11:22:33:44:55"
        val h10Mac = "BB:11:22:33:44:55"
        PolarOfflineCapabilityStore.recordDisMetadata(
            context = null,
            macAddress = verityMac,
            modelNumber = "Sense",
            confirmedPolarService = true
        )
        PolarOfflineCapabilityStore.recordDisMetadata(
            context = null,
            macAddress = h10Mac,
            modelNumber = "Polar H10",
            confirmedPolarService = true
        )

        val cols = listOf(
            DevicesDatabaseManager.DevicesDbHelper.C_ID,
            DevicesDatabaseManager.DevicesDbHelper.DEVICE_TYPE,
            DevicesDatabaseManager.DevicesDbHelper.PROTOCOL,
            DevicesDatabaseManager.DevicesDbHelper.LAST_ACTIVE,
            DevicesDatabaseManager.DevicesDbHelper.LAST_BATTERY_PERCENTAGE,
            DevicesDatabaseManager.DevicesDbHelper.MANUFACTURER_NAME,
            DevicesDatabaseManager.DevicesDbHelper.NAME,
            DevicesDatabaseManager.DevicesDbHelper.PAIRED,
            DevicesDatabaseManager.DevicesDbHelper.BT_ADDRESS
        )
        val rows = listOf(
            // Row 1: Renamed Verity Sense ("Armband Optical") appears first in DB cursor
            mapOf<String, Any?>(
                DevicesDatabaseManager.DevicesDbHelper.C_ID to 1L,
                DevicesDatabaseManager.DevicesDbHelper.DEVICE_TYPE to DeviceType.HRM.name,
                DevicesDatabaseManager.DevicesDbHelper.PROTOCOL to Protocol.BLUETOOTH_LE.name,
                DevicesDatabaseManager.DevicesDbHelper.LAST_ACTIVE to "Yesterday",
                DevicesDatabaseManager.DevicesDbHelper.LAST_BATTERY_PERCENTAGE to 80,
                DevicesDatabaseManager.DevicesDbHelper.MANUFACTURER_NAME to "",
                DevicesDatabaseManager.DevicesDbHelper.NAME to "Armband Optical",
                DevicesDatabaseManager.DevicesDbHelper.PAIRED to 1,
                DevicesDatabaseManager.DevicesDbHelper.BT_ADDRESS to verityMac
            ),
            // Row 2: ANT+ HRM
            mapOf<String, Any?>(
                DevicesDatabaseManager.DevicesDbHelper.C_ID to 2L,
                DevicesDatabaseManager.DevicesDbHelper.DEVICE_TYPE to DeviceType.HRM.name,
                DevicesDatabaseManager.DevicesDbHelper.PROTOCOL to Protocol.ANT_PLUS.name,
                DevicesDatabaseManager.DevicesDbHelper.LAST_ACTIVE to "Yesterday",
                DevicesDatabaseManager.DevicesDbHelper.LAST_BATTERY_PERCENTAGE to 70,
                DevicesDatabaseManager.DevicesDbHelper.MANUFACTURER_NAME to "Garmin",
                DevicesDatabaseManager.DevicesDbHelper.NAME to "Garmin Dual ANT+",
                DevicesDatabaseManager.DevicesDbHelper.PAIRED to 1,
                DevicesDatabaseManager.DevicesDbHelper.BT_ADDRESS to null
            ),
            // Row 3: Polar H10 ECG
            mapOf<String, Any?>(
                DevicesDatabaseManager.DevicesDbHelper.C_ID to 3L,
                DevicesDatabaseManager.DevicesDbHelper.DEVICE_TYPE to DeviceType.HRM.name,
                DevicesDatabaseManager.DevicesDbHelper.PROTOCOL to Protocol.BLUETOOTH_LE.name,
                DevicesDatabaseManager.DevicesDbHelper.LAST_ACTIVE to "Yesterday",
                DevicesDatabaseManager.DevicesDbHelper.LAST_BATTERY_PERCENTAGE to 90,
                DevicesDatabaseManager.DevicesDbHelper.MANUFACTURER_NAME to "Polar",
                DevicesDatabaseManager.DevicesDbHelper.NAME to "Polar H10",
                DevicesDatabaseManager.DevicesDbHelper.PAIRED to 1,
                DevicesDatabaseManager.DevicesDbHelper.BT_ADDRESS to h10Mac
            )
        )
        var rowIndex = -1
        val cursor = mockk<Cursor>(relaxed = true)
        every { cursor.moveToNext() } answers {
            rowIndex++
            rowIndex < rows.size
        }
        every { cursor.getColumnIndexOrThrow(any()) } answers {
            val name = firstArg<String>()
            cols.indexOf(name).also { if (it < 0) throw IllegalArgumentException(name) }
        }
        every { cursor.getColumnIndex(any()) } answers { cols.indexOf(firstArg<String>()) }
        every { cursor.isNull(any()) } answers { rows[rowIndex][cols[firstArg()]] == null }
        every { cursor.getLong(any()) } answers { (rows[rowIndex][cols[firstArg()]] as Number).toLong() }
        every { cursor.getInt(any()) } answers { (rows[rowIndex][cols[firstArg()]] as Number).toInt() }
        every { cursor.getString(any()) } answers { rows[rowIndex][cols[firstArg()]] as? String }

        every { mockEqDb.getLinkedEquipmentFromDeviceId(any()) } returns emptyList()
        every { mockEqDb.getEquipment(any<com.atrainingtracker.banalservice.BSportType>()) } returns emptyList()
        every { mockDiscovery.getLinkedSportTypeNames(any<Set<Long>>()) } returns emptySet<String>()

        val provider = RawDeviceDataProvider(mockDevicesDb, mockEqDb, mockDiscovery)
        val devices = buildList<DeviceUiData> {
            while (cursor.moveToNext()) {
                val raw = provider.getDeviceData(cursor)
                add(raw2UiDeviceData(raw, mockContext))
            }
        }
        cursor.close()

        val potentialTypes = DeviceType.getDeviceTypeList(SensorType.HR) ?: emptySet()
        val sorted = sortNotConnectedDevices(
            allDevices = devices,
            activeDeviceIds = emptySet(),
            potentialTypes = potentialTypes,
            sensorType = SensorType.HR,
            context = mockContext
        )

        assertEquals(listOf(3L, 2L, 1L), sorted.map { it.id })
        assertEquals(HrModality.ECG, sorted[0].hrModality)
        assertEquals(HrModality.ANT_PLUS, sorted[1].hrModality)
        assertEquals(HrModality.OPTICAL_PPG, sorted[2].hrModality)
    }

    // TST-CON-057.3: SensorSourceDialog.sortNotConnectedDevices filters HRM Polar Live-IMU candidates by capability,
    // normalizes localized R.string.devices_unknown_manufacturer placeholder, and excludes BIKE_POWER from SLOPE
    @Test
    fun testNotConnectedDevices_capabilityGatesPolarLiveImuAndNormalizesUnknownManufacturer_TST_CON_057_3() {
        val h10Mac = "A0:9E:1A:51:51:51"
        val verityMac = "A0:9E:1A:52:52:52"
        PolarOfflineCapabilityStore.recordSdkDeviceType(null, h10Mac, PolarOfflineCapabilityStore.PolarDeviceType.H10)
        PolarOfflineCapabilityStore.recordSdkDeviceType(null, verityMac, PolarOfflineCapabilityStore.PolarDeviceType.VERITY_SENSE)

        val mockContext = mockk<Context>(relaxed = true)
        every { mockContext.getString(com.atrainingtracker.R.string.devices_unknown_manufacturer) } returns "Unbekannter Hersteller"

        val bleH10Renamed = createDevice(
            1L, "Brustgurt", DeviceType.HRM, Protocol.BLUETOOTH_LE, "",
            btAddress = h10Mac, polarLiveImuEnabled = true
        )
        val bleH10LegacyUnknownMfg = createDevice(
            2L,
            "Polar H10 99887766",
            DeviceType.HRM,
            Protocol.BLUETOOTH_LE,
            "Unbekannter Hersteller",
            btAddress = "A0:9E:1A:53:53:53",
            polarLiveImuEnabled = true
        )
        val bleVerityRenamed = createDevice(
            3L, "Armband", DeviceType.HRM, Protocol.BLUETOOTH_LE, "",
            btAddress = verityMac, polarLiveImuEnabled = true
        )
        val antH10 = createDevice(4L, "Polar H10 ANT+", DeviceType.HRM, Protocol.ANT_PLUS, "Polar")
        val bleGenericHrm = createDevice(5L, "Wahoo TICKR", DeviceType.HRM, Protocol.BLUETOOTH_LE, "Wahoo", btAddress = "11:22:33:44:55:66")
        val bikePower = createDevice(6L, "Assioma Duo", DeviceType.BIKE_POWER, Protocol.ANT_PLUS, "Favero")
        val fusedAlt = createDevice(7L, "ATT Fused Alt", DeviceType.ALTITUDE_AND_SLOPE_ATT_FUSED, Protocol.SMARTPHONE, "Google")
        val allDevices = listOf(bleH10Renamed, bleH10LegacyUnknownMfg, bleVerityRenamed, antH10, bleGenericHrm, bikePower, fusedAlt)

        val cadenceNotConnected = sortNotConnectedDevices(
            allDevices = allDevices,
            activeDeviceIds = emptySet(),
            potentialTypes = DeviceType.getDeviceTypeList(SensorType.CADENCE) ?: emptySet(),
            sensorType = SensorType.CADENCE,
            context = mockContext
        )
        assertEquals(listOf(6L, 3L, 1L, 2L), cadenceNotConnected.map { it.id })

        val voNotConnected = sortNotConnectedDevices(
            allDevices = allDevices,
            activeDeviceIds = emptySet(),
            potentialTypes = DeviceType.getDeviceTypeList(SensorType.VERTICAL_OSCILLATION) ?: emptySet(),
            sensorType = SensorType.VERTICAL_OSCILLATION,
            context = mockContext
        )
        assertEquals(listOf(1L, 2L), voNotConnected.map { it.id })

        val slopeNotConnected = sortNotConnectedDevices(
            allDevices = allDevices,
            activeDeviceIds = emptySet(),
            potentialTypes = DeviceType.getDeviceTypeList(SensorType.SLOPE) ?: emptySet(),
            sensorType = SensorType.SLOPE,
            context = mockContext
        )
        assertEquals(listOf(7L), slopeNotConnected.map { it.id })

        val hrNotConnected = sortNotConnectedDevices(
            allDevices = allDevices,
            activeDeviceIds = emptySet(),
            potentialTypes = DeviceType.getDeviceTypeList(SensorType.HR) ?: emptySet(),
            sensorType = SensorType.HR,
            context = mockContext
        )
        assertEquals(5, hrNotConnected.size)
    }

    // TST-CON-064.2: SensorSourceDialog.sortNotConnectedDevices enforces paired + polarLiveImuEnabled + TIRAMISU
    // and preserves CADENCE_PRIORITY / STRIDES_PRIORITY ordering
    @Test
    fun testNotConnectedDevices_gatesPolarLiveImuOptInPairedAndCadencePriority_TST_CON_064_2() {
        val h10MacOptedIn = "A0:9E:1A:71:71:71"
        val h10MacDisabled = "A0:9E:1A:72:72:72"
        val h10MacUnpaired = "A0:9E:1A:73:73:73"
        val verityMacOptedIn = "A0:9E:1A:74:74:74"
        val verityMacDisabled = "A0:9E:1A:75:75:75"
        PolarOfflineCapabilityStore.recordSdkDeviceType(null, h10MacOptedIn, PolarOfflineCapabilityStore.PolarDeviceType.H10)
        PolarOfflineCapabilityStore.recordSdkDeviceType(null, h10MacDisabled, PolarOfflineCapabilityStore.PolarDeviceType.H10)
        PolarOfflineCapabilityStore.recordSdkDeviceType(null, h10MacUnpaired, PolarOfflineCapabilityStore.PolarDeviceType.H10)
        PolarOfflineCapabilityStore.recordSdkDeviceType(null, verityMacOptedIn, PolarOfflineCapabilityStore.PolarDeviceType.VERITY_SENSE)
        PolarOfflineCapabilityStore.recordSdkDeviceType(null, verityMacDisabled, PolarOfflineCapabilityStore.PolarDeviceType.VERITY_SENSE)

        val strydFootpod = createDevice(10L, "Stryd Pod", DeviceType.RUN_SPEED, Protocol.BLUETOOTH_LE, "Stryd")
        val assiomaPower = createDevice(11L, "Assioma Duo", DeviceType.BIKE_POWER, Protocol.ANT_PLUS, "Favero")
        val h10OptedIn = createDevice(
            12L, "Polar H10 OptIn", DeviceType.HRM, Protocol.BLUETOOTH_LE, "Polar",
            btAddress = h10MacOptedIn, isPaired = true, polarLiveImuEnabled = true
        )
        val verityOptedIn = createDevice(
            13L, "Verity OptIn", DeviceType.HRM, Protocol.BLUETOOTH_LE, "Polar",
            btAddress = verityMacOptedIn, isPaired = true, polarLiveImuEnabled = true
        )
        val h10Disabled = createDevice(
            14L, "Polar H10 Disabled", DeviceType.HRM, Protocol.BLUETOOTH_LE, "Polar",
            btAddress = h10MacDisabled, isPaired = true, polarLiveImuEnabled = false
        )
        val verityDisabled = createDevice(
            15L, "Verity Disabled", DeviceType.HRM, Protocol.BLUETOOTH_LE, "Polar",
            btAddress = verityMacDisabled, isPaired = true, polarLiveImuEnabled = false
        )
        val h10Unpaired = createDevice(
            16L, "Polar H10 Unpaired", DeviceType.HRM, Protocol.BLUETOOTH_LE, "Polar",
            btAddress = h10MacUnpaired, isPaired = false, polarLiveImuEnabled = true
        )
        val antH10 = createDevice(
            17L, "Polar H10 ANT+", DeviceType.HRM, Protocol.ANT_PLUS, "Polar",
            isPaired = true, polarLiveImuEnabled = true
        )
        val attFused = createDevice(
            18L, "ATT Fused", DeviceType.SPEED_AND_LOCATION_ATT_FUSED, Protocol.SMARTPHONE, "Google"
        )

        val allDevices = listOf(
            attFused, h10Disabled, verityDisabled, h10Unpaired, antH10, verityOptedIn, h10OptedIn, assiomaPower, strydFootpod
        )

        // API 33+: CADENCE_PRIORITY = RUN_SPEED (10) < BIKE_POWER (11) < HRM [Polar H10 OptIn (12) < Verity OptIn (13)] < ATT_FUSED (18)
        val cadenceApi34 = sortNotConnectedDevices(
            allDevices = allDevices,
            activeDeviceIds = emptySet(),
            potentialTypes = DeviceType.getDeviceTypeList(SensorType.CADENCE) ?: emptySet(),
            sensorType = SensorType.CADENCE,
            sdkInt = android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE
        )
        assertEquals(listOf(10L, 11L, 12L, 13L, 18L), cadenceApi34.map { it.id })

        // STRIDES_PRIORITY = RUN_SPEED (10) < HRM (12, 13) < ATT_FUSED (18)
        val stridesApi34 = sortNotConnectedDevices(
            allDevices = allDevices,
            activeDeviceIds = emptySet(),
            potentialTypes = DeviceType.getDeviceTypeList(SensorType.STRIDES) ?: emptySet(),
            sensorType = SensorType.STRIDES,
            sdkInt = android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE
        )
        assertEquals(listOf(10L, 12L, 13L, 18L), stridesApi34.map { it.id })

        // API 32 (< TIRAMISU): ALL Live-IMU HRMs excluded even if paired & opted-in
        val cadenceApi32 = sortNotConnectedDevices(
            allDevices = allDevices,
            activeDeviceIds = emptySet(),
            potentialTypes = DeviceType.getDeviceTypeList(SensorType.CADENCE) ?: emptySet(),
            sensorType = SensorType.CADENCE,
            sdkInt = android.os.Build.VERSION_CODES.S_V2
        )
        assertEquals(listOf(10L, 11L, 18L), cadenceApi32.map { it.id })

        // HR selection remains inclusive across paired/opt-in/API levels
        val hrApi32 = sortNotConnectedDevices(
            allDevices = allDevices,
            activeDeviceIds = emptySet(),
            potentialTypes = DeviceType.getDeviceTypeList(SensorType.HR) ?: emptySet(),
            sensorType = SensorType.HR,
            sdkInt = android.os.Build.VERSION_CODES.S_V2
        )
        assertEquals(6, hrApi32.size)
    }
}
