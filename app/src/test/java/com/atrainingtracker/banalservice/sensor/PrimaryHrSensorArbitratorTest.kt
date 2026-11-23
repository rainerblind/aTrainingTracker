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

package com.atrainingtracker.banalservice.sensor

import com.atrainingtracker.banalservice.Protocol
import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.banalservice.devices.MyDevice
import com.atrainingtracker.banalservice.devices.bluetooth_le.polar.PolarOfflineCapabilityStore
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

/**
 * Pure-JVM unit tests for [PrimaryHrSensorArbitrator] (`REQ-SET-126.3`, `TST-SET-128`, `ATT-9345`).
 */
class PrimaryHrSensorArbitratorTest {

    @Before
    fun setUp() {
        PolarOfflineCapabilityStore.resetForTesting()
    }

    @After
    fun tearDown() {
        PolarOfflineCapabilityStore.resetForTesting()
    }

    private abstract class MockHealthDevice : MyDevice(null, null, null), HrSensorHealthProvider

    private fun stubHrSensor(
        modality: HrModality,
        healthTier: HrHealthTier,
        activated: Boolean = true
    ): MySensor<Int> {
        val device = mockk<MockHealthDevice>(relaxed = true)
        every { device.getHrModality() } returns modality
        every { device.getHrHealthTier(any()) } returns healthTier
        val sensor = mockk<MySensor<Int>>(relaxed = true)
        every { sensor.device } returns device
        every { sensor.isActivated } returns activated
        return sensor
    }

    @Test
    fun selectBestHrSensor_prefersHealthyEcgOverHealthyOpticalRegardlessOfRegistrationOrder() {
        // Verifies REQ-SET-126 / TST-SET-128
        val verityOptical = stubHrSensor(HrModality.OPTICAL_PPG, HrHealthTier.HEALTHY)
        val h10Ecg = stubHrSensor(HrModality.ECG, HrHealthTier.HEALTHY)

        assertSame(h10Ecg, PrimaryHrSensorArbitrator.selectBestHrSensor(listOf(verityOptical, h10Ecg), nowMs = 5_000L))
        assertSame(h10Ecg, PrimaryHrSensorArbitrator.selectBestHrSensor(listOf(h10Ecg, verityOptical), nowMs = 5_000L))
    }

    @Test
    fun selectBestHrSensor_failsOverToHealthyVerityWhenH10IsStaleOrDegraded() {
        // Verifies REQ-SET-126 / TST-SET-128
        val verityHealthy = stubHrSensor(HrModality.OPTICAL_PPG, HrHealthTier.HEALTHY)
        val h10Stale = stubHrSensor(HrModality.ECG, HrHealthTier.STALE_OR_NULL)
        val h10Degraded = stubHrSensor(HrModality.ECG, HrHealthTier.DEGRADED)

        assertSame(verityHealthy, PrimaryHrSensorArbitrator.selectBestHrSensor(listOf(h10Stale, verityHealthy), nowMs = 5_000L))
        assertSame(verityHealthy, PrimaryHrSensorArbitrator.selectBestHrSensor(listOf(h10Degraded, verityHealthy), nowMs = 5_000L))
    }

    @Test
    fun selectBestHrSensor_prefersHealthyH10OverDegradedOrFlatlinedVerity() {
        // Verifies REQ-SET-126 / TST-SET-128
        val verityFrozen = stubHrSensor(HrModality.OPTICAL_PPG, HrHealthTier.DEGRADED)
        val h10Healthy = stubHrSensor(HrModality.ECG, HrHealthTier.HEALTHY)

        assertSame(h10Healthy, PrimaryHrSensorArbitrator.selectBestHrSensor(listOf(verityFrozen, h10Healthy), nowMs = 5_000L))
    }

    @Test
    fun selectBestHrSensor_enforcesFullModalityOrderEcgAntOpticalOther() {
        // Verifies REQ-SET-126 / TST-SET-128
        val other = stubHrSensor(HrModality.OTHER, HrHealthTier.HEALTHY)
        val optical = stubHrSensor(HrModality.OPTICAL_PPG, HrHealthTier.HEALTHY)
        val antPlus = stubHrSensor(HrModality.ANT_PLUS, HrHealthTier.HEALTHY)
        val ecg = stubHrSensor(HrModality.ECG, HrHealthTier.HEALTHY)

        assertSame(ecg, PrimaryHrSensorArbitrator.selectBestHrSensor(listOf(other, optical, antPlus, ecg), nowMs = 5_000L))
        assertSame(antPlus, PrimaryHrSensorArbitrator.selectBestHrSensor(listOf(other, optical, antPlus), nowMs = 5_000L))
        assertSame(optical, PrimaryHrSensorArbitrator.selectBestHrSensor(listOf(other, optical), nowMs = 5_000L))
    }

    @Test
    fun selectBestHrSensor_retainsHighestModalityWhenAllCandidatesAreStaleOrNull() {
        // Verifies REQ-SET-126 / TST-SET-128
        val verityStale = stubHrSensor(HrModality.OPTICAL_PPG, HrHealthTier.STALE_OR_NULL)
        val h10Stale = stubHrSensor(HrModality.ECG, HrHealthTier.STALE_OR_NULL)

        assertSame(h10Stale, PrimaryHrSensorArbitrator.selectBestHrSensor(listOf(verityStale, h10Stale), nowMs = 5_000L))
        assertNull(PrimaryHrSensorArbitrator.selectBestHrSensor(emptyList(), nowMs = 5_000L))
    }

    @Test
    fun selectBestHrSensor_handlesNonRelaxedMockStubsWithoutThrowing() {
        // Verifies REQ-SET-126 / TST-SET-128
        val nonRelaxedA = mockk<MySensor<Int>>()
        val nonRelaxedB = mockk<MySensor<Int>>()
        every { nonRelaxedA.device } returns null
        every { nonRelaxedB.device } returns null

        // Neither isActivated, value, nor lastUpdateTimestamp is stubbed -> runCatching guards prevent MockKException.
        assertSame(nonRelaxedA, PrimaryHrSensorArbitrator.selectBestHrSensor(listOf(nonRelaxedA, nonRelaxedB), nowMs = 5_000L))
    }

    @Test
    fun resolveModality_fromPersistedMetadata_classifiesEcgAntOpticalAndOther() {
        // Verifies REQ-UI-2804.1 / TST-UI-2804.1
        assertEquals(
            HrModality.ECG,
            PrimaryHrSensorArbitrator.resolveModality(Protocol.BLUETOOTH_LE, DeviceType.HRM, "Polar H10", "Polar")
        )
        assertEquals(
            HrModality.ANT_PLUS,
            PrimaryHrSensorArbitrator.resolveModality(Protocol.ANT_PLUS, DeviceType.HRM, "Garmin HRM-Pro", "Garmin")
        )
        assertEquals(
            HrModality.OPTICAL_PPG,
            PrimaryHrSensorArbitrator.resolveModality(Protocol.BLUETOOTH_LE, DeviceType.HRM, "Polar Verity Sense", "Polar")
        )
        assertEquals(
            HrModality.OTHER,
            PrimaryHrSensorArbitrator.resolveModality(Protocol.ALL, DeviceType.HRM, "Generic", "")
        )
        assertEquals(
            HrModality.OTHER,
            PrimaryHrSensorArbitrator.resolveModality(Protocol.BLUETOOTH_LE, DeviceType.BIKE_POWER, "Polar H10", "Polar")
        )
    }

    @Test
    fun resolveModality_fromPersistedCapabilityStore_detectsRenamedOpticalAndRespectsConfirmedNonPolarGatt() {
        // Verifies REQ-UI-2804.1 / REQ-CON-049.4 / TST-UI-2804.1
        val verityMac = "AA:BB:CC:DD:EE:01"
        PolarOfflineCapabilityStore.recordDisMetadata(
            context = null,
            macAddress = verityMac,
            modelNumber = "Sense",
            confirmedPolarService = true
        )
        assertEquals(
            HrModality.OPTICAL_PPG,
            PrimaryHrSensorArbitrator.resolveModality(
                protocol = Protocol.BLUETOOTH_LE,
                deviceType = DeviceType.HRM,
                deviceName = "Armband Optical",
                manufacturer = "",
                macAddress = verityMac
            )
        )

        val nonPolarMac = "AA:BB:CC:DD:EE:02"
        PolarOfflineCapabilityStore.recordGattServiceCapabilities(
            context = null,
            macAddress = nonPolarMac,
            hasPmdService = false,
            hasPftpService = false
        )
        assertEquals(
            HrModality.ECG,
            PrimaryHrSensorArbitrator.resolveModality(
                protocol = Protocol.BLUETOOTH_LE,
                deviceType = DeviceType.HRM,
                deviceName = "Polar Verity Clone",
                manufacturer = "Wahoo",
                macAddress = nonPolarMac
            )
        )
    }

    @Test
    fun resolveHealthTier_returnsHealthyDegradedAndStaleOrNullDeterministically() {
        // Verifies REQ-UI-2804.1 / TST-UI-2804.1
        val healthy = stubHrSensor(HrModality.ECG, HrHealthTier.HEALTHY)
        val degraded = stubHrSensor(HrModality.OPTICAL_PPG, HrHealthTier.DEGRADED)
        val stale = stubHrSensor(HrModality.ANT_PLUS, HrHealthTier.STALE_OR_NULL)

        assertEquals(HrHealthTier.HEALTHY, PrimaryHrSensorArbitrator.resolveHealthTier(healthy, nowMs = 5_000L))
        assertEquals(HrHealthTier.DEGRADED, PrimaryHrSensorArbitrator.resolveHealthTier(degraded, nowMs = 5_000L))
        assertEquals(HrHealthTier.STALE_OR_NULL, PrimaryHrSensorArbitrator.resolveHealthTier(stale, nowMs = 5_000L))
    }
}
