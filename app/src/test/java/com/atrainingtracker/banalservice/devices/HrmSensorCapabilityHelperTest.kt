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

package com.atrainingtracker.banalservice.devices

import android.content.Context
import com.atrainingtracker.banalservice.Protocol
import com.atrainingtracker.banalservice.devices.bluetooth_le.polar.PolarOfflineCapabilityStore
import com.atrainingtracker.banalservice.sensor.SensorType
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests verifying hardware-grounded Polar Live-IMU HRM capability gating
 * in [HrmSensorCapabilityHelper] (`REQ-CON-057.2`, `TST-CON-057.2`, `ATT-8991`).
 */
// Linked test specs: TST-CON-057, TST-CON-057.2
class HrmSensorCapabilityHelperTest {

    private val cadenceMetrics = listOf(SensorType.CADENCE, SensorType.STRIDES)
    private val runningDynamicsMetrics = listOf(SensorType.VERTICAL_OSCILLATION, SensorType.GROUND_CONTACT_TIME)
    private val coreHrAndHrvMetrics = listOf(
        SensorType.HR,
        SensorType.RR_INTERVAL,
        SensorType.HRV_RMSSD,
        SensorType.HRV_DFA_A1,
        SensorType.HRV_ARTIFACT_RATE,
        SensorType.HRV_SDNN
    )

    @Before
    fun setUp() {
        PolarOfflineCapabilityStore.resetForTesting()
    }

    @After
    fun tearDown() {
        PolarOfflineCapabilityStore.resetForTesting()
    }

    // TST-CON-057.2: BLE Polar H10 (hardware-confirmed even when renamed, or legacy name) supports all 4 Live-IMU + HR/HRV metrics
    @Test
    fun testBlePolarH10_supportsCadenceStridesVoGctAndHrHrv_TST_CON_057_2() {
        val h10Mac = "A0:9E:1A:11:22:33"
        PolarOfflineCapabilityStore.recordSdkDeviceType(
            context = null,
            macAddress = h10Mac,
            deviceType = PolarOfflineCapabilityStore.PolarDeviceType.H10
        )

        for (sensor in cadenceMetrics + runningDynamicsMetrics + coreHrAndHrvMetrics) {
            assertTrue(
                "Renamed hardware-confirmed BLE Polar H10 must support $sensor",
                HrmSensorCapabilityHelper.isSensorSupported(
                    null, sensor, Protocol.BLUETOOTH_LE, "Brustgurt", "", h10Mac, polarLiveImuEnabled = true
                )
            )
            assertTrue(
                "Legacy-named un-reconnected BLE Polar H10 must support $sensor",
                HrmSensorCapabilityHelper.isSensorSupported(
                    null, sensor, Protocol.BLUETOOTH_LE, "Polar H10 1A2B3C4D", "Polar", "A0:9E:1A:00:00:01",
                    polarLiveImuEnabled = true
                )
            )
        }
    }

    // TST-CON-057.2: BLE Polar Verity Sense/OH1 supports CADENCE & STRIDES + HR/HRV but NOT VO/GCT
    @Test
    fun testBlePolarVeritySense_supportsCadenceAndStridesOnlyNotVoOrGct_TST_CON_057_2() {
        val verityMac = "A0:9E:1A:44:55:66"
        PolarOfflineCapabilityStore.recordSdkDeviceType(
            context = null,
            macAddress = verityMac,
            deviceType = PolarOfflineCapabilityStore.PolarDeviceType.VERITY_SENSE
        )
        val legacyOfflineMac = "A0:9E:1A:44:55:77"
        PolarOfflineCapabilityStore.markOfflineRecordingCapable(null, legacyOfflineMac)

        for (sensor in cadenceMetrics + coreHrAndHrvMetrics) {
            assertTrue(
                "Renamed hardware Verity Sense must support $sensor",
                HrmSensorCapabilityHelper.isSensorSupported(
                    null, sensor, Protocol.BLUETOOTH_LE, "Armband", "", verityMac, polarLiveImuEnabled = true
                )
            )
            assertTrue(
                "Legacy offline_rec_ Verity Sense must support $sensor",
                HrmSensorCapabilityHelper.isSensorSupported(
                    null, sensor, Protocol.BLUETOOTH_LE, "Optical", "", legacyOfflineMac, polarLiveImuEnabled = true
                )
            )
            assertTrue(
                "Legacy-named Verity Sense must support $sensor",
                HrmSensorCapabilityHelper.isSensorSupported(
                    null, sensor, Protocol.BLUETOOTH_LE, "Polar Sense 24825632", "Polar", "A0:9E:1A:00:00:02",
                    polarLiveImuEnabled = true
                )
            )
        }
        for (sensor in runningDynamicsMetrics) {
            assertFalse(
                "Hardware Verity Sense must NOT support $sensor",
                HrmSensorCapabilityHelper.isSensorSupported(
                    null, sensor, Protocol.BLUETOOTH_LE, "Armband", "", verityMac, polarLiveImuEnabled = true
                )
            )
            assertFalse(
                "Legacy offline_rec_ Verity Sense must NOT support $sensor",
                HrmSensorCapabilityHelper.isSensorSupported(
                    null, sensor, Protocol.BLUETOOTH_LE, "Optical", "", legacyOfflineMac, polarLiveImuEnabled = true
                )
            )
            assertFalse(
                "Legacy-named Verity Sense must NOT support $sensor",
                HrmSensorCapabilityHelper.isSensorSupported(
                    null, sensor, Protocol.BLUETOOTH_LE, "Polar Sense 24825632", "Polar", "A0:9E:1A:00:00:02",
                    polarLiveImuEnabled = true
                )
            )
        }
    }

    // TST-CON-057.2: Dual-protocol ANT+ Polar H10, ordinary BLE/ANT+ HRMs, Polar H9, confirmed non-Polar GATT spoofs & rename-suppressed HRMs are excluded from Live-IMU but kept for HR/HRV
    @Test
    fun testAntPlusAndNonPolarOrSpoofedHrms_excludedFromLiveImuButKeptForHrHrv_TST_CON_057_2() {
        val spoofedMac = "DE:AD:BE:EF:00:01"
        PolarOfflineCapabilityStore.recordGattServiceCapabilities(
            context = null,
            macAddress = spoofedMac,
            hasPmdService = false,
            hasPftpService = false
        )
        val suppressedMac = "DE:AD:BE:EF:00:02"
        PolarOfflineCapabilityStore.suppressLegacyNameHeuristicIfUnknown(null, suppressedMac)

        data class NonImuHrm(val label: String, val protocol: Protocol, val name: String, val mfg: String, val mac: String?)
        val nonImuHrms = listOf(
            NonImuHrm("ANT+ Polar H10", Protocol.ANT_PLUS, "Polar H10 12345", "Polar", null),
            NonImuHrm("ANT+ Garmin HRM-Pro", Protocol.ANT_PLUS, "Garmin HRM-Pro", "Garmin", null),
            NonImuHrm("BLE Wahoo TICKR", Protocol.BLUETOOTH_LE, "TICKR X", "Wahoo", "11:22:33:44:55:66"),
            NonImuHrm("BLE Polar H9", Protocol.BLUETOOTH_LE, "Polar H9 87654321", "Polar", "A0:9E:1A:99:99:99"),
            NonImuHrm("Confirmed non-Polar GATT spoof", Protocol.BLUETOOTH_LE, "Polar H10 Fake", "Polar", spoofedMac),
            NonImuHrm("Rename-suppressed BLE HRM", Protocol.BLUETOOTH_LE, "Polar H10 Renamed", "", suppressedMac)
        )

        for (hrm in nonImuHrms) {
            for (sensor in cadenceMetrics + runningDynamicsMetrics) {
                assertFalse(
                    "${hrm.label} must NOT support Live-IMU $sensor",
                    HrmSensorCapabilityHelper.isSensorSupported(null, sensor, hrm.protocol, hrm.name, hrm.mfg, hrm.mac)
                )
            }
            for (sensor in coreHrAndHrvMetrics) {
                assertTrue(
                    "${hrm.label} MUST remain selectable for $sensor (REQ-HRV-050.1)",
                    HrmSensorCapabilityHelper.isSensorSupported(null, sensor, hrm.protocol, hrm.name, hrm.mfg, hrm.mac)
                )
            }
        }
    }

    // TST-CON-057.2: Null sensorType returns false and throwing mocked Context falls back gracefully to null Context
    @Test
    fun testNullSensorTypeAndThrowingContext_resilientFallback_TST_CON_057_2() {
        assertFalse(
            "Null sensorType must return false",
            HrmSensorCapabilityHelper.isSensorSupported(null, null, Protocol.BLUETOOTH_LE, "Polar H10 1A2B3C4D", "Polar", null)
        )

        val throwingContext = mockk<Context>()
        every { throwingContext.applicationContext } throws RuntimeException("Unmocked JVM context")

        assertTrue(
            "Throwing context must fall back cleanly to null-context classification for Polar H10",
            HrmSensorCapabilityHelper.isSensorSupported(
                throwingContext,
                SensorType.VERTICAL_OSCILLATION,
                Protocol.BLUETOOTH_LE,
                "Polar H10 1A2B3C4D",
                "Polar",
                "A0:9E:1A:77:88:99",
                polarLiveImuEnabled = true
            )
        )
        assertTrue(
            "Throwing context must fall back cleanly to null-context classification for Verity Sense CADENCE",
            HrmSensorCapabilityHelper.isSensorSupported(
                throwingContext,
                SensorType.CADENCE,
                Protocol.BLUETOOTH_LE,
                "Polar Sense 24825632",
                "Polar",
                "A0:9E:1A:77:88:98",
                polarLiveImuEnabled = true
            )
        )
    }

    // TST-CON-064.1: polarLiveImuEnabled opt-in and Android 13+ (TIRAMISU) gating on Live-IMU metrics
    @Test
    fun testPolarLiveImuOptInAndAndroid13PlusGating_TST_CON_064_1() {
        val h10Mac = "A0:9E:1A:10:10:10"
        val verityMac = "A0:9E:1A:20:20:20"
        PolarOfflineCapabilityStore.recordSdkDeviceType(null, h10Mac, PolarOfflineCapabilityStore.PolarDeviceType.H10)
        PolarOfflineCapabilityStore.recordSdkDeviceType(null, verityMac, PolarOfflineCapabilityStore.PolarDeviceType.VERITY_SENSE)

        // Default polarLiveImuEnabled = false must reject all Live-IMU metrics even for H10 & Verity Sense
        for (sensor in cadenceMetrics + runningDynamicsMetrics) {
            assertTrue(HrmSensorCapabilityHelper.requiresPolarLiveImu(sensor))
            assertFalse(
                "H10 without opt-in must NOT support $sensor",
                HrmSensorCapabilityHelper.isSensorSupported(null, sensor, Protocol.BLUETOOTH_LE, "Polar H10", "Polar", h10Mac)
            )
            assertFalse(
                "Verity without opt-in must NOT support $sensor",
                HrmSensorCapabilityHelper.isSensorSupported(null, sensor, Protocol.BLUETOOTH_LE, "Verity Sense", "Polar", verityMac)
            )
            // Opted-in on Android 12L (API 32) or Android 8 (API 26) must also be rejected
            assertFalse(
                "Opted-in H10 on API 32 (< TIRAMISU) must NOT support $sensor",
                HrmSensorCapabilityHelper.isSensorSupported(
                    null, sensor, Protocol.BLUETOOTH_LE, "Polar H10", "Polar", h10Mac,
                    polarLiveImuEnabled = true, sdkInt = android.os.Build.VERSION_CODES.S_V2
                )
            )
            assertFalse(
                "Opted-in Verity on API 26 (< TIRAMISU) must NOT support $sensor",
                HrmSensorCapabilityHelper.isSensorSupported(
                    null, sensor, Protocol.BLUETOOTH_LE, "Verity Sense", "Polar", verityMac,
                    polarLiveImuEnabled = true, sdkInt = android.os.Build.VERSION_CODES.O
                )
            )
        }

        // Opted-in on Android 13+ (API 33 & API 35) allows H10 (all 4) & Verity (CADENCE/STRIDES only)
        for (api in listOf(android.os.Build.VERSION_CODES.TIRAMISU, 35)) {
            for (sensor in cadenceMetrics) {
                assertTrue(
                    "Opted-in H10 on API $api must support $sensor",
                    HrmSensorCapabilityHelper.isSensorSupported(
                        null, sensor, Protocol.BLUETOOTH_LE, "Polar H10", "Polar", h10Mac,
                        polarLiveImuEnabled = true, sdkInt = api
                    )
                )
                assertTrue(
                    "Opted-in Verity on API $api must support $sensor",
                    HrmSensorCapabilityHelper.isSensorSupported(
                        null, sensor, Protocol.BLUETOOTH_LE, "Verity Sense", "Polar", verityMac,
                        polarLiveImuEnabled = true, sdkInt = api
                    )
                )
            }
            for (sensor in runningDynamicsMetrics) {
                assertTrue(
                    "Opted-in H10 on API $api must support $sensor",
                    HrmSensorCapabilityHelper.isSensorSupported(
                        null, sensor, Protocol.BLUETOOTH_LE, "Polar H10", "Polar", h10Mac,
                        polarLiveImuEnabled = true, sdkInt = api
                    )
                )
                assertFalse(
                    "Opted-in Verity on API $api must NOT support $sensor",
                    HrmSensorCapabilityHelper.isSensorSupported(
                        null, sensor, Protocol.BLUETOOTH_LE, "Verity Sense", "Polar", verityMac,
                        polarLiveImuEnabled = true, sdkInt = api
                    )
                )
            }
        }

        // HR / RR_INTERVAL / HRV_* never require opt-in or Android 13+
        for (sensor in coreHrAndHrvMetrics) {
            assertFalse(HrmSensorCapabilityHelper.requiresPolarLiveImu(sensor))
            assertTrue(
                HrmSensorCapabilityHelper.isSensorSupported(
                    null, sensor, Protocol.BLUETOOTH_LE, "Polar H10", "Polar", h10Mac,
                    polarLiveImuEnabled = false, sdkInt = android.os.Build.VERSION_CODES.O
                )
            )
        }
    }
}
