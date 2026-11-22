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

package com.atrainingtracker.banalservice.devices.bluetooth_le.polar

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Offline unit tests for [PolarOfflineCapabilityStore] persistence and
 * [PolarOfflineGattCoordinator] hardware-grounded classification & fallback precedence
 * (`REQ-CON-049.1`, `.2`, `.4`, `TST-CON-049.1`, `REQ-PRO-049.1`).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PolarOfflineCapabilityStoreTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        PolarOfflineCapabilityStore.resetForTesting()
        PolarOfflineGattCoordinator.resetForTesting()
    }

    @After
    fun tearDown() {
        PolarOfflineCapabilityStore.resetForTesting()
        PolarOfflineGattCoordinator.resetForTesting()
    }

    @Test
    fun testRenamedH10_withPmdAndPftpAndLegacyOfflineRec_neverMisclassifiesAsVeritySense() {
        val h10Mac = "AA:BB:CC:DD:EE:01"
        // Simulate legacy app version having marked PFTP on H10 as offline_rec_ = true
        PolarOfflineCapabilityStore.markOfflineRecordingCapable(context, h10Mac)
        PolarOfflineCapabilityStore.recordGattServiceCapabilities(
            context = context,
            macAddress = h10Mac,
            hasPmdService = true,
            hasPftpService = true
        )
        PolarOfflineCapabilityStore.recordDisMetadata(
            context = context,
            macAddress = h10Mac,
            modelNumber = "H10",
            serialNumber = "0a1b2c3d"
        )

        // Simulate process restart by clearing in-memory caches and reading back from SharedPreferences
        PolarOfflineCapabilityStore.resetForTesting()
        PolarOfflineGattCoordinator.resetForTesting()

        assertTrue(PolarOfflineCapabilityStore.hasPmdService(context, h10Mac))
        assertTrue(PolarOfflineCapabilityStore.hasPftpService(context, h10Mac))
        assertEquals("H10", PolarOfflineCapabilityStore.getModelNumber(context, h10Mac))
        assertEquals("0A1B2C3D", PolarOfflineCapabilityStore.getPolarDeviceId(context, h10Mac))
        assertEquals(
            PolarOfflineCapabilityStore.PolarDeviceType.H10,
            PolarOfflineCapabilityStore.getDeviceType(context, h10Mac)
        )

        assertTrue(
            PolarOfflineGattCoordinator.isPolarH10Device(
                context = context,
                name = "Chest Strap",
                manufacturer = "Polar Electro",
                macAddress = h10Mac
            )
        )
        assertFalse(
            PolarOfflineGattCoordinator.isPolarVeritySenseDevice(
                context = context,
                name = "Chest Strap",
                manufacturer = "Polar Electro",
                macAddress = h10Mac
            )
        )
        assertEquals("0A1B2C3D", PolarOfflineGattCoordinator.extractPolarId(context, "Chest Strap", h10Mac))
        assertTrue(PolarOfflineGattCoordinator.matchesDeviceIdentifier("0A1B2C3D", h10Mac))
    }

    @Test
    fun testRenamedVeritySenseAndOh1_classifyDeterministicallyViaDisModel() {
        val verityMac = "AA:BB:CC:DD:EE:02"
        val oh1Mac = "AA:BB:CC:DD:EE:03"

        PolarOfflineCapabilityStore.recordDisMetadata(context, verityMac, modelNumber = "Verity Sense", serialNumber = "9988AABB")
        PolarOfflineCapabilityStore.recordDisMetadata(context, oh1Mac, modelNumber = "OH1", serialNumber = "1122CCDD")

        assertTrue(PolarOfflineGattCoordinator.isPolarVeritySenseDevice(context, "Armband", "", verityMac))
        assertFalse(PolarOfflineGattCoordinator.isPolarH10Device(context, "Armband", "", verityMac))
        assertEquals("9988AABB", PolarOfflineGattCoordinator.extractPolarId(context, "Armband", verityMac))

        assertTrue(PolarOfflineGattCoordinator.isPolarVeritySenseDevice(context, "Swim Strap", "", oh1Mac))
        assertFalse(PolarOfflineGattCoordinator.isPolarH10Device(context, "Swim Strap", "", oh1Mac))
        assertEquals("1122CCDD", PolarOfflineGattCoordinator.extractPolarId(context, "Swim Strap", oh1Mac))
    }

    @Test
    fun testLegacyPreReconnectRowFallback_worksUntilHardwareMetadataHydrated() {
        val unReconnectedH10Mac = "AA:BB:CC:DD:EE:04"
        val unReconnectedVerityMac = "AA:BB:CC:DD:EE:05"
        val nonPolarMac = "AA:BB:CC:DD:EE:06"

        assertEquals(
            PolarOfflineCapabilityStore.PolarDeviceType.UNKNOWN,
            PolarOfflineCapabilityStore.getDeviceType(context, unReconnectedH10Mac)
        )
        assertNull(PolarOfflineCapabilityStore.getPolarDeviceId(context, unReconnectedH10Mac))

        assertTrue(PolarOfflineGattCoordinator.isPolarH10Device(context, "Polar H10 1C3B2421", "Polar Electro", unReconnectedH10Mac))
        assertEquals("1C3B2421", PolarOfflineGattCoordinator.extractPolarId(context, "Polar H10 1C3B2421", unReconnectedH10Mac))

        assertTrue(PolarOfflineGattCoordinator.isPolarVeritySenseDevice(context, "Polar Sense 9876ABCD", "Polar Electro", unReconnectedVerityMac))
        assertEquals("9876ABCD", PolarOfflineGattCoordinator.extractPolarId(context, "Polar Sense 9876ABCD", unReconnectedVerityMac))

        assertFalse(PolarOfflineGattCoordinator.isPolarH10Device(context, "Garmin HRM-Pro", "Garmin", nonPolarMac))
        assertFalse(PolarOfflineGattCoordinator.isPolarVeritySenseDevice(context, "Garmin HRM-Pro", "Garmin", nonPolarMac))
    }

    @Test
    fun testClassifyModelNumber_rejectsBareSenseLikeCardioSenseAndPreservesCanonical8HexId() {
        assertEquals(
            PolarOfflineCapabilityStore.PolarDeviceType.UNKNOWN,
            PolarOfflineCapabilityStore.classifyModelNumber("CardioSense")
        )
        assertEquals(
            PolarOfflineCapabilityStore.PolarDeviceType.UNKNOWN,
            PolarOfflineCapabilityStore.classifyModelNumber("Sense")
        )
        assertEquals(
            PolarOfflineCapabilityStore.PolarDeviceType.VERITY_SENSE,
            PolarOfflineCapabilityStore.classifyModelNumber("Polar Sense")
        )
        assertEquals(
            PolarOfflineCapabilityStore.PolarDeviceType.VERITY_SENSE,
            PolarOfflineCapabilityStore.classifyModelNumber("Verity Sense")
        )
        assertEquals(
            PolarOfflineCapabilityStore.PolarDeviceType.H10,
            PolarOfflineCapabilityStore.classifyModelNumber("H10")
        )

        val mac = "AA:BB:CC:DD:EE:07"
        PolarOfflineCapabilityStore.recordDisMetadata(context, mac, modelNumber = "CardioSense", serialNumber = "1234567890123")
        assertEquals(PolarOfflineCapabilityStore.PolarDeviceType.UNKNOWN, PolarOfflineCapabilityStore.getDeviceType(context, mac))
        assertNull(PolarOfflineCapabilityStore.getPolarDeviceId(context, mac))
        assertFalse(PolarOfflineGattCoordinator.isPolarVeritySenseDevice(context, "CardioSense", "", mac))
        assertFalse(PolarOfflineGattCoordinator.isPolarOfflineCapableDevice(context, "CardioSense", "", mac))

        // Valid 8-hex Polar ID is accepted and must not be overwritten by a 13-digit factory serial
        PolarOfflineCapabilityStore.recordDisMetadata(context, mac, modelNumber = "H10", serialNumber = "0A1B2C3D")
        PolarOfflineCapabilityStore.recordDisMetadata(context, mac, serialNumber = "1234567890123")
        assertEquals("0A1B2C3D", PolarOfflineCapabilityStore.getPolarDeviceId(context, mac))
    }

    @Test
    fun testConfirmedNonPolarGatt_blocksNameHeuristicsEvenWhenRenamedPolarH10OrVeritySense_TST_CON_049_1() {
        // REQ-CON-049.1 / REQ-CON-049.4 / TST-CON-049.1
        val mac = "AA:BB:CC:DD:EE:08"
        PolarOfflineCapabilityStore.seedLegacyDeviceTypeIfUnknown(
            context,
            mac,
            PolarOfflineCapabilityStore.PolarDeviceType.H10,
            "11223344"
        )
        PolarOfflineCapabilityStore.recordGattServiceCapabilities(
            context = context,
            macAddress = mac,
            hasPmdService = false,
            hasPftpService = false
        )

        // Simulate process restart
        PolarOfflineCapabilityStore.resetForTesting()
        assertTrue(PolarOfflineCapabilityStore.isConfirmedNonPolarGatt(context, mac))
        assertEquals(PolarOfflineCapabilityStore.PolarDeviceType.UNKNOWN, PolarOfflineCapabilityStore.getDeviceType(context, mac))
        assertFalse(PolarOfflineGattCoordinator.isPolarH10Device(context, "Polar H10", "", mac))
        assertFalse(PolarOfflineGattCoordinator.isPolarH10Device(context, "Polar H10 1234ABCD", "Polar Electro", mac))
        assertFalse(PolarOfflineGattCoordinator.isPolarVeritySenseDevice(context, "Polar Verity Sense", "", mac))
        assertFalse(PolarOfflineGattCoordinator.isPolarOfflineCapableDevice(context, "Polar H10", "", mac))
    }

    @Test
    fun testPolarGatedBareSenseDisAndEmptyNameResilience_TST_CON_049_1() {
        // REQ-CON-049.2 / REQ-CON-049.4 / TST-CON-049.1
        assertEquals(
            PolarOfflineCapabilityStore.PolarDeviceType.UNKNOWN,
            PolarOfflineCapabilityStore.classifyModelNumber("Sense", hasPolarServices = false)
        )
        assertEquals(
            PolarOfflineCapabilityStore.PolarDeviceType.VERITY_SENSE,
            PolarOfflineCapabilityStore.classifyModelNumber("Sense", hasPolarServices = true)
        )
        assertEquals(
            PolarOfflineCapabilityStore.PolarDeviceType.UNKNOWN,
            PolarOfflineCapabilityStore.classifyModelNumber("CardioSense", hasPolarServices = true)
        )

        val senseMac = "AA:BB:CC:DD:EE:09"
        // DIS "Sense" arriving before PMD/PFTP is promoted and persisted to SharedPreferences once Polar services are confirmed
        PolarOfflineCapabilityStore.recordDisMetadata(context, senseMac, modelNumber = "Sense", serialNumber = "AABB1122")
        assertEquals(PolarOfflineCapabilityStore.PolarDeviceType.UNKNOWN, PolarOfflineCapabilityStore.getDeviceType(context, senseMac))
        PolarOfflineCapabilityStore.recordGattServiceCapabilities(context, senseMac, hasPmdService = true, hasPftpService = true)
        // Simulate process restart to verify KEY_TYPE_PREFIX persistence
        PolarOfflineCapabilityStore.resetForTesting()
        assertEquals(PolarOfflineCapabilityStore.PolarDeviceType.VERITY_SENSE, PolarOfflineCapabilityStore.getDeviceType(context, senseMac))
        assertTrue(PolarOfflineGattCoordinator.isPolarVeritySenseDevice(context, "", "", senseMac))

        // Legacy offline_rec_ Verity Sense without polar_type_ also survives empty name ""
        val legacyVerityMac = "AA:BB:CC:DD:EE:0A"
        PolarOfflineCapabilityStore.markOfflineRecordingCapable(context, legacyVerityMac)
        assertTrue(PolarOfflineGattCoordinator.isPolarVeritySenseDevice(context, "", "", legacyVerityMac))
        assertFalse(PolarOfflineGattCoordinator.isPolarH10Device(context, "", "", legacyVerityMac))
    }

    @Test
    fun testLegacySeedSuppressionAndExplicitNonPolarManufacturerGuard_TST_CON_049_1() {
        // REQ-CON-049.3 / REQ-CON-049.4 / TST-CON-049.1
        val garminMac = "AA:BB:CC:DD:EE:0B"
        assertFalse(PolarOfflineGattCoordinator.isPolarH10Device(context, "Polar H10", "Garmin", garminMac))
        assertFalse(PolarOfflineGattCoordinator.isPolarVeritySenseDevice(context, "Polar Verity Sense", "Wahoo", garminMac))

        // Un-reconnected non-Polar rename suppression blocks name fallback, but real Polar GATT/DIS overrides it
        val renamedMac = "AA:BB:CC:DD:EE:0C"
        PolarOfflineCapabilityStore.suppressLegacyNameHeuristicIfUnknown(context, renamedMac)
        assertTrue(PolarOfflineCapabilityStore.isLegacyNameHeuristicSuppressed(context, renamedMac))
        assertFalse(PolarOfflineGattCoordinator.isPolarH10Device(context, "Polar H10", "", renamedMac))

        PolarOfflineCapabilityStore.recordGattServiceCapabilities(context, renamedMac, hasPmdService = true, hasPftpService = true)
        PolarOfflineCapabilityStore.recordDisMetadata(context, renamedMac, modelNumber = "H10", serialNumber = "DEADBEEF")
        assertFalse(PolarOfflineCapabilityStore.isLegacyNameHeuristicSuppressed(context, renamedMac))
        assertTrue(PolarOfflineGattCoordinator.isPolarH10Device(context, "", "", renamedMac))
    }
}
