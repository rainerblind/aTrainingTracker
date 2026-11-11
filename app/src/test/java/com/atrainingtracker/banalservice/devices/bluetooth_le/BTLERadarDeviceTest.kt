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

package com.atrainingtracker.banalservice.devices.bluetooth_le

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager
import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.banalservice.devices.radar.LaneState
import com.atrainingtracker.banalservice.devices.radar.RadarConnectionState
import com.atrainingtracker.banalservice.devices.radar.RadarLightController
import com.atrainingtracker.banalservice.devices.radar.RadarLightMode
import com.atrainingtracker.banalservice.devices.radar.RadarScenario
import com.atrainingtracker.banalservice.devices.radar.RadarStateRepository
import com.atrainingtracker.banalservice.devices.radar.RadarTarget
import com.atrainingtracker.banalservice.devices.radar.RadarTrafficSimulator
import com.atrainingtracker.banalservice.devices.radar.RadarWindowAggregator
import com.atrainingtracker.banalservice.devices.radar.ThreatLevel
import com.atrainingtracker.banalservice.devices.radar.VehicleSizeCategory
import com.atrainingtracker.banalservice.sensor.MySensorManager
import com.atrainingtracker.banalservice.sensor.SensorType
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit test suite verifying BTLERadarDevice V1 packet decoding, threat level computation,
 * and fail-safe disconnect watchdog behavior (TST-CON-028.2, REQ-CON-028.4).
 */
class BTLERadarDeviceTest {

    private lateinit var mockContext: Context
    private lateinit var mockSensorManager: MySensorManager
    private lateinit var radarDevice: BTLERadarDevice

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>(), any()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any()) } returns 0

        mockkStatic(android.os.SystemClock::class)
        every { android.os.SystemClock.elapsedRealtime() } returns 1000L

        mockkStatic(android.text.TextUtils::class)
        every { android.text.TextUtils.equals(any(), any()) } answers {
            java.util.Objects.equals(firstArg<CharSequence?>(), secondArg<CharSequence?>())
        }
        every { android.text.TextUtils.isEmpty(any()) } answers {
            val s = firstArg<CharSequence?>()
            s == null || s.isEmpty()
        }

        mockkStatic(ContextCompat::class)
        every {
            ContextCompat.checkSelfPermission(any(), any())
        } returns PackageManager.PERMISSION_GRANTED
        every { ContextCompat.registerReceiver(any(), any(), any(), any()) } returns null

        mockkStatic(ActivityCompat::class)
        every {
            ActivityCompat.checkSelfPermission(any(), any())
        } returns PackageManager.PERMISSION_GRANTED

        mockkStatic(DevicesDatabaseManager::class)
        val mockDevicesDbManager = mockk<DevicesDatabaseManager>(relaxed = true)
        every { DevicesDatabaseManager.getInstance(any()) } returns mockDevicesDbManager

        mockContext = mockk(relaxed = true)
        val mockLooper = mockk<Looper>(relaxed = true)
        every { mockContext.mainLooper } returns mockLooper
        mockSensorManager = mockk(relaxed = true)

        mockkConstructor(Handler::class)
        every { anyConstructed<Handler>().post(any()) } answers { true }
        every { anyConstructed<Handler>().postDelayed(any(), any()) } answers { true }
        every { anyConstructed<Handler>().removeCallbacks(any<Runnable>()) } answers { }
        every { anyConstructed<Handler>().removeCallbacksAndMessages(any()) } answers { }

        RadarStateRepository.reset()
        radarDevice = BTLERadarDevice(mockContext, mockSensorManager, 101L, "DC:2C:26:01:02:03")
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testParseV1ThreatPacket_demuxesNonThreatPackets() {
        // Null or empty payload
        assertNull(BTLERadarDevice.parseV1ThreatPacket(null))
        assertNull(BTLERadarDevice.parseV1ThreatPacket(byteArrayOf()))

        // Heartbeat / status packet (header 0x08)
        val heartbeat = byteArrayOf(0x08, 0x01, 0x02, 0x03)
        assertNull(BTLERadarDevice.parseV1ThreatPacket(heartbeat))

        // Sector amplitude packet (header 0x02)
        val sector = byteArrayOf(0x02, 0x10, 0x20)
        assertNull(BTLERadarDevice.parseV1ThreatPacket(sector))

        // Malformed threat packet (not 1 + 3*N bytes)
        val malformed = byteArrayOf(0x00, 0x01, 0x02)
        assertNull(BTLERadarDevice.parseV1ThreatPacket(malformed))
    }

    @Test
    fun testParseV1ThreatPacket_decodesMultiTargetFrame() {
        // Clear frame (0 targets): header 0x00
        val clearFrame = byteArrayOf(0x00)
        val clearTargets = BTLERadarDevice.parseV1ThreatPacket(clearFrame)
        assertNotNull(clearTargets)
        assertTrue(clearTargets!!.isEmpty())

        // 2 targets:
        // Target 0: ID=1, Range=60m, SpeedAndThreat=0x15 (threatBits=1 -> APPROACHING, speed=5 mps)
        // Target 1: ID=2, Range=25m, SpeedAndThreat=0x29 (threatBits=2 -> FAST_APPROACHING, speed=9 mps)
        val payload = byteArrayOf(
            0x00,
            0x01, 60.toByte(), 0x15.toByte(),
            0x02, 25.toByte(), 0x29.toByte()
        )

        val targets = BTLERadarDevice.parseV1ThreatPacket(payload)
        assertNotNull(targets)
        assertEquals(2, targets!!.size)

        val t0 = targets[0]
        assertEquals(1, t0.targetId)
        assertEquals(60.0f, t0.rangeMeters, 0.001f)
        assertEquals(5.0f, t0.approachSpeedMps, 0.001f)
        assertEquals(ThreatLevel.APPROACHING, t0.threatLevel)

        val t1 = targets[1]
        assertEquals(2, t1.targetId)
        assertEquals(25.0f, t1.rangeMeters, 0.001f)
        assertEquals(9.0f, t1.approachSpeedMps, 0.001f)
        assertEquals(ThreatLevel.FAST_APPROACHING, t1.threatLevel)
    }

    @Test
    fun testComputeOverallThreatLevel_prioritizesHighestThreat() {
        assertEquals(ThreatLevel.NONE, BTLERadarDevice.computeOverallThreatLevel(emptyList()))

        val amber = listOf(RadarTarget(1, 50f, 4f, ThreatLevel.APPROACHING))
        assertEquals(ThreatLevel.APPROACHING, BTLERadarDevice.computeOverallThreatLevel(amber))

        val mixed = listOf(
            RadarTarget(1, 50f, 4f, ThreatLevel.APPROACHING),
            RadarTarget(2, 20f, 10f, ThreatLevel.FAST_APPROACHING)
        )
        assertEquals(ThreatLevel.FAST_APPROACHING, BTLERadarDevice.computeOverallThreatLevel(mixed))
    }

    @Test
    fun testWatchdog_transitionsToDisconnectedErrorOnStarvation() {
        // Trigger the watchdog runnable directly
        radarDevice.watchdogRunnableForTesting.run()

        val state = RadarStateRepository.getState()
        assertEquals(RadarConnectionState.DISCONNECTED_ERROR, state.connectionState)
        assertEquals(ThreatLevel.NONE, state.overallThreatLevel)
        assertTrue(state.targets.isEmpty())
    }

    @Test
    fun testParseV2ThreatPacket_demuxesNonThreatPackets() {
        // Null or empty payload
        assertNull(BTLERadarDevice.parseV2ThreatPacket(null))
        assertNull(BTLERadarDevice.parseV2ThreatPacket(byteArrayOf()))

        // Wrong header (0x00 is V1, not V2)
        val v1Header = byteArrayOf(0x00, 0x01, 0x02)
        assertNull(BTLERadarDevice.parseV2ThreatPacket(v1Header))

        // Heartbeat / status packet (header 0x08)
        val heartbeat = byteArrayOf(0x08, 0x01, 0x02, 0x03)
        assertNull(BTLERadarDevice.parseV2ThreatPacket(heartbeat))

        // Malformed threat packet (not 1 + 8*N bytes)
        val malformed = byteArrayOf(0x01, 0x01, 0x02, 0x03)
        assertNull(BTLERadarDevice.parseV2ThreatPacket(malformed))
    }

    @Test
    fun testParseV2ThreatPacket_decodesMultiTargetFrameWithLateralOffsetAndSize() {
        // Clear frame (0 targets): header 0x01
        val clearFrame = byteArrayOf(0x01)
        val clearTargets = BTLERadarDevice.parseV2ThreatPacket(clearFrame)
        assertNotNull(clearTargets)
        assertTrue(clearTargets!!.isEmpty())

        // 2 targets:
        // Target 0: ID=1, Range=165.4m (1654 -> low=0x76, high=0x06), Speed=15.0 m/s (150 -> low=0x96, high=0x00),
        //           Lateral=-2.1m (-21 -> low=0xEB, high=0xFF), sizeAndThreat=0x0A (threat=2 FAST_APPROACHING, size=2 TRUCK_OR_BUS, lane=0)
        // Target 1: ID=2, Range=35.0m (350 -> low=0x5E, high=0x01), Speed=10.0 m/s (100 -> low=0x64, high=0x00),
        //           Lateral=-1.8m (-18 -> low=0xEE, high=0xFF), sizeAndThreat=0x35 (threat=1 APPROACHING, size=1 CAR, lane=3 PASSING_LANE)
        val payload = byteArrayOf(
            0x01,
            // Target 0
            0x01, 0x76.toByte(), 0x06.toByte(), 0x96.toByte(), 0x00.toByte(), 0xEB.toByte(), 0xFF.toByte(), 0x0A.toByte(),
            // Target 1
            0x02, 0x5E.toByte(), 0x01.toByte(), 0x64.toByte(), 0x00.toByte(), 0xEE.toByte(), 0xFF.toByte(), 0x35.toByte()
        )

        val targets = BTLERadarDevice.parseV2ThreatPacket(payload)
        assertNotNull(targets)
        assertEquals(2, targets!!.size)

        val t0 = targets[0]
        assertEquals(1, t0.targetId)
        assertEquals(165.4f, t0.rangeMeters, 0.001f)
        assertEquals(15.0f, t0.approachSpeedMps, 0.001f)
        assertEquals(ThreatLevel.FAST_APPROACHING, t0.threatLevel)
        assertEquals(-2.1f, t0.lateralOffsetMeters!!, 0.001f)
        assertEquals(VehicleSizeCategory.TRUCK_OR_BUS, t0.vehicleSizeCategory)
        assertEquals(LaneState.UNKNOWN, t0.laneState)

        val t1 = targets[1]
        assertEquals(2, t1.targetId)
        assertEquals(35.0f, t1.rangeMeters, 0.001f)
        assertEquals(10.0f, t1.approachSpeedMps, 0.001f)
        assertEquals(ThreatLevel.APPROACHING, t1.threatLevel)
        assertEquals(-1.8f, t1.lateralOffsetMeters!!, 0.001f)
        assertEquals(VehicleSizeCategory.CAR, t1.vehicleSizeCategory)
        assertEquals(LaneState.PASSING_LANE, t1.laneState)
    }

    @Test
    fun testFallbackToV1_subscribesToV1WhenV2CharacteristicMissing() {
        val mockGatt = mockk<BluetoothGatt>(relaxed = true)
        val mockService = mockk<BluetoothGattService>(relaxed = true)
        val mockV1Char = mockk<BluetoothGattCharacteristic>(relaxed = true)
        val mockDescriptor = mockk<BluetoothGattDescriptor>(relaxed = true)

        every { mockService.getCharacteristic(BluetoothConstants.UUID_CHARACTERISTIC_RADAR_V1) } returns mockV1Char
        every { mockV1Char.getDescriptor(BluetoothConstants.UUID_CHARACTERISTIC_CLIENT_CONFIG) } returns mockDescriptor

        radarDevice.fallbackToV1(mockGatt, mockService)

        assertFalse(radarDevice.isV2Stream)
        io.mockk.verify { mockGatt.setCharacteristicNotification(mockV1Char, true) }
    }

    @Test
    fun testSetLightMode_enqueuesWriteCharacteristicWhenSupported() {
        val mockGatt = mockk<BluetoothGatt>(relaxed = true)
        val mockService = mockk<BluetoothGattService>(relaxed = true)
        val mockLightChar = mockk<BluetoothGattCharacteristic>(relaxed = true)
        val mockV2Char = mockk<BluetoothGattCharacteristic>(relaxed = true)
        val mockDescriptor = mockk<BluetoothGattDescriptor>(relaxed = true)

        every { mockService.getCharacteristic(BluetoothConstants.UUID_CHARACTERISTIC_RADAR_V2) } returns mockV2Char
        every { mockV2Char.getDescriptor(BluetoothConstants.UUID_CHARACTERISTIC_CLIENT_CONFIG) } returns mockDescriptor
        every { mockService.getCharacteristic(BluetoothConstants.UUID_CHARACTERISTIC_RADAR_LIGHT_CONTROL) } returns mockLightChar
        every { mockGatt.getService(BluetoothConstants.UUID_SERVICE_RADAR) } returns mockService

        radarDevice.configureServices(mockGatt)

        assertTrue(radarDevice.isLightControlSupported)
        val result = radarDevice.setLightMode(RadarLightMode.DAY_FLASH)
        assertTrue(result)
    }

    @Test
    fun testSetLightMode_returnsFalseWhenLightControlNotDiscovered() {
        assertFalse(radarDevice.isLightControlSupported)
        val result = radarDevice.setLightMode(RadarLightMode.SOLID)
        assertFalse(result)
    }

    @Test
    fun testProcessTargets_propagatesNullDistanceWhenRoadClears() {
        var currentTimeMs = 1000L
        radarDevice.setWindowAggregatorForTesting(RadarWindowAggregator(clock = { currentTimeMs }))

        // Approaching target at 15m
        radarDevice.processTargetsForTesting(listOf(RadarTarget(1, 15f, 5f, ThreatLevel.APPROACHING)))
        assertEquals(15.0, radarDevice.getSensor(SensorType.RADAR_CLOSEST_DISTANCE_m).value)
        assertEquals(1, radarDevice.getSensor(SensorType.RADAR_THREAT_COUNT).value)
        assertEquals(5.0, radarDevice.getSensor(SensorType.RADAR_APPROACH_SPEED_mps).value)

        // Road clears and 1,000 ms rolling window expires
        currentTimeMs = 2001L
        radarDevice.processTargetsForTesting(emptyList())

        assertNull("Closest distance sensor must transition to null on clear road",
            radarDevice.getSensor(SensorType.RADAR_CLOSEST_DISTANCE_m).value)
        assertEquals(0, radarDevice.getSensor(SensorType.RADAR_THREAT_COUNT).value)
        assertEquals(0.0, radarDevice.getSensor(SensorType.RADAR_APPROACH_SPEED_mps).value)
    }

    @Test
    fun testOnAccumulatorsReset_clearsVehicleCountSensorsAndSession() {
        // Vehicle approaches and passes (closing to <= 18m and disappearing)
        radarDevice.processTargetsForTesting(listOf(RadarTarget(1, 12f, 8f, ThreatLevel.APPROACHING)))
        radarDevice.processTargetsForTesting(emptyList())
        assertEquals(1, radarDevice.getSensor(SensorType.RADAR_VEHICLE_COUNT).value)

        // Broadcast RESET_ACCUMULATORS_INTENT triggers onAccumulatorsReset
        radarDevice.onAccumulatorsResetForTesting()

        assertEquals("Vehicle count sensor must reset to 0 on accumulator reset",
            0, radarDevice.getSensor(SensorType.RADAR_VEHICLE_COUNT).value)
        assertEquals("Large vehicle count sensor must reset to 0 on accumulator reset",
            0, radarDevice.getSensor(SensorType.RADAR_LARGE_VEHICLE_COUNT).value)
        assertEquals(0, radarDevice.windowAggregatorForTesting.getVehicleCount())
    }

    @Test
    fun testIsStvzoModel_patternMatching() {
        assertTrue(BTLERadarDevice.isStvzoModel("010-03180-10"))
        assertTrue(BTLERadarDevice.isStvzoModel("Garmin Varia RTL516"))
        assertTrue(BTLERadarDevice.isStvzoModel("Varia Radar StVZO"))
        assertTrue(BTLERadarDevice.isStvzoModel("RVR316"))

        assertFalse(BTLERadarDevice.isStvzoModel("010-03180-00"))
        assertFalse(BTLERadarDevice.isStvzoModel("Garmin Varia RTL515"))
        assertFalse(BTLERadarDevice.isStvzoModel("RVR315"))
        assertFalse(BTLERadarDevice.isStvzoModel(null))
        assertFalse(BTLERadarDevice.isStvzoModel(""))
    }

    @Test
    fun testOnOtherCharacteristicUpdate_modelNumberDetectsStvzo() {
        val mockGatt = mockk<BluetoothGatt>(relaxed = true)
        val mockModelChar = mockk<BluetoothGattCharacteristic>(relaxed = true)
        every { mockModelChar.uuid } returns BluetoothConstants.UUID_CHARACTERISTIC_MODEL_NUMBER
        every { mockModelChar.getStringValue(0) } returns "010-03180-10"

        radarDevice.onOtherCharacteristicUpdateForTesting(mockGatt, mockModelChar)

        assertTrue(radarDevice.isStvzo)
        assertEquals("010-03180-10", radarDevice.modelNumber)
        assertTrue(RadarLightController.isStvzo.value)
    }

    @Test
    fun testOnOtherCharacteristicUpdate_lightControlUpdatesController() {
        val mockGatt = mockk<BluetoothGatt>(relaxed = true)
        val mockLightChar = mockk<BluetoothGattCharacteristic>(relaxed = true)
        every { mockLightChar.uuid } returns BluetoothConstants.UUID_CHARACTERISTIC_RADAR_LIGHT_CONTROL
        every { mockLightChar.value } returns byteArrayOf(RadarLightMode.SOLID.commandByte)

        radarDevice.onOtherCharacteristicUpdateForTesting(mockGatt, mockLightChar)

        assertEquals(RadarLightMode.SOLID, RadarLightController.currentMode.value)
        assertEquals(RadarLightMode.SOLID, RadarStateRepository.getState().lightMode)
    }

    @Test
    fun testWatchdog_resetsSensorsToClearOnStarvation() {
        radarDevice.processTargetsForTesting(listOf(RadarTarget(1, 10f, 6f, ThreatLevel.APPROACHING)))
        assertEquals(10.0, radarDevice.getSensor(SensorType.RADAR_CLOSEST_DISTANCE_m).value)
        assertEquals(1, radarDevice.getSensor(SensorType.RADAR_THREAT_COUNT).value)

        radarDevice.watchdogRunnableForTesting.run()

        assertNull(radarDevice.getSensor(SensorType.RADAR_CLOSEST_DISTANCE_m).value)
        assertEquals(0, radarDevice.getSensor(SensorType.RADAR_THREAT_COUNT).value)
        assertEquals(0.0, radarDevice.getSensor(SensorType.RADAR_APPROACH_SPEED_mps).value)
    }

    @Test
    fun testIsMeasurementCharacteristic_acceptsBothV1AndV2() {
        assertTrue(radarDevice.isMeasurementCharacteristicForTesting(
            BluetoothConstants.UUID_CHARACTERISTIC_RADAR_V1
        ))
        assertTrue(radarDevice.isMeasurementCharacteristicForTesting(
            BluetoothConstants.UUID_CHARACTERISTIC_RADAR_V2
        ))
        assertFalse(radarDevice.isMeasurementCharacteristicForTesting(
            BluetoothConstants.UUID_CHARACTERISTIC_RADAR_LIGHT_CONTROL
        ))
        assertFalse(radarDevice.isMeasurementCharacteristicForTesting(
            BluetoothConstants.UUID_CHARACTERISTIC_BATTERY_LEVEL
        ))
    }

    @Test
    fun testConfigureServices_bondedWithV2_enqueuesMtuAndCccd() {
        val mockGatt = mockk<BluetoothGatt>(relaxed = true)
        val mockDevice = mockk<android.bluetooth.BluetoothDevice>(relaxed = true)
        val mockService = mockk<BluetoothGattService>(relaxed = true)
        val mockV2Char = mockk<BluetoothGattCharacteristic>(relaxed = true)
        val mockDescriptor = mockk<BluetoothGattDescriptor>(relaxed = true)

        every { mockGatt.device } returns mockDevice
        every { mockDevice.bondState } returns android.bluetooth.BluetoothDevice.BOND_BONDED
        every { mockGatt.getService(BluetoothConstants.UUID_SERVICE_RADAR) } returns mockService
        every { mockGatt.getService(BluetoothConstants.UUID_SERVICE_DEVICE_INFORMATION) } returns null
        every { mockService.getCharacteristic(BluetoothConstants.UUID_CHARACTERISTIC_RADAR_V2) } returns mockV2Char
        every { mockService.getCharacteristic(BluetoothConstants.UUID_CHARACTERISTIC_RADAR_LIGHT_CONTROL) } returns null
        every { mockV2Char.getDescriptor(BluetoothConstants.UUID_CHARACTERISTIC_CLIENT_CONFIG) } returns mockDescriptor
        every { mockV2Char.properties } returns BluetoothGattCharacteristic.PROPERTY_NOTIFY

        radarDevice.setBluetoothGattForTesting(mockGatt)
        radarDevice.configureServices(mockGatt)

        assertTrue(radarDevice.isV2Stream)
        assertTrue(radarDevice.isGattOperationPendingForTesting)
        val queue = radarDevice.gattQueueForTesting
        assertEquals(1, queue.size)
        val op = queue.poll() as MyBTLEDevice.WriteDescriptorOp
        assertEquals(mockDescriptor, op.descriptor)
    }

    @Test
    fun testConfigureServices_unbondedWithV2_fallsBackToV1() {
        val mockGatt = mockk<BluetoothGatt>(relaxed = true)
        val mockDevice = mockk<android.bluetooth.BluetoothDevice>(relaxed = true)
        val mockService = mockk<BluetoothGattService>(relaxed = true)
        val mockV2Char = mockk<BluetoothGattCharacteristic>(relaxed = true)
        val mockV1Char = mockk<BluetoothGattCharacteristic>(relaxed = true)
        val mockDescriptor = mockk<BluetoothGattDescriptor>(relaxed = true)

        every { mockGatt.device } returns mockDevice
        every { mockDevice.bondState } returns android.bluetooth.BluetoothDevice.BOND_NONE
        every { mockGatt.getService(BluetoothConstants.UUID_SERVICE_RADAR) } returns mockService
        every { mockService.getCharacteristic(BluetoothConstants.UUID_CHARACTERISTIC_RADAR_V2) } returns mockV2Char
        every { mockService.getCharacteristic(BluetoothConstants.UUID_CHARACTERISTIC_RADAR_V1) } returns mockV1Char
        every { mockV1Char.getDescriptor(BluetoothConstants.UUID_CHARACTERISTIC_CLIENT_CONFIG) } returns mockDescriptor

        radarDevice.configureServices(mockGatt)

        assertFalse(radarDevice.isV2Stream)
        io.mockk.verify { mockGatt.setCharacteristicNotification(mockV1Char, true) }
    }

    @Test
    fun testOnDescriptorWriteCompleted_v2CccdFailure_fallsBackToV1() {
        val mockGatt = mockk<BluetoothGatt>(relaxed = true)
        val mockService = mockk<BluetoothGattService>(relaxed = true)
        val mockV2Char = mockk<BluetoothGattCharacteristic>(relaxed = true)
        val mockV1Char = mockk<BluetoothGattCharacteristic>(relaxed = true)
        val mockDescriptor = mockk<BluetoothGattDescriptor>(relaxed = true)
        val mockV1Desc = mockk<BluetoothGattDescriptor>(relaxed = true)

        every { mockDescriptor.characteristic } returns mockV2Char
        every { mockV2Char.uuid } returns BluetoothConstants.UUID_CHARACTERISTIC_RADAR_V2
        every { mockGatt.getService(BluetoothConstants.UUID_SERVICE_RADAR) } returns mockService
        every { mockService.getCharacteristic(BluetoothConstants.UUID_CHARACTERISTIC_RADAR_V1) } returns mockV1Char
        every { mockV1Char.getDescriptor(BluetoothConstants.UUID_CHARACTERISTIC_CLIENT_CONFIG) } returns mockV1Desc

        radarDevice.onDescriptorWriteCompletedForTesting(
            mockGatt,
            mockDescriptor,
            BluetoothGatt.GATT_INSUFFICIENT_AUTHENTICATION
        )

        assertFalse(radarDevice.isV2Stream)
        io.mockk.verify { mockGatt.setCharacteristicNotification(mockV1Char, true) }
    }

    @Test
    fun testProcessTargets_haltsActiveRadarTrafficSimulator() {
        RadarTrafficSimulator.start(RadarScenario.SINGLE_CAR)
        assertTrue(RadarTrafficSimulator.isSimulating)

        val v1Char = mockk<BluetoothGattCharacteristic>()
        every { v1Char.uuid } returns BluetoothConstants.UUID_CHARACTERISTIC_RADAR_V1
        // V1 payload with 1 target (4 bytes: header 0x00, targetId 1, range 60m, speedAndThreat 0x15)
        every { v1Char.value } returns byteArrayOf(
            0x00.toByte(), 0x01.toByte(), 60.toByte(), 0x15.toByte()
        )

        radarDevice.measurementCharacteristicUpdate(mockk(relaxed = true), v1Char)

        // Verifies hardware packet arrival halted simulation immediately (REQ-CON-029.3)
        assertFalse(RadarTrafficSimulator.isSimulating)
    }
}
