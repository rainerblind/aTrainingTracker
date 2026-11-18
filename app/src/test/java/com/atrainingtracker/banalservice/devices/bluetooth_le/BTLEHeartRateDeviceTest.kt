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

import android.Manifest
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager
import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.banalservice.devices.ProtocolType
import com.atrainingtracker.banalservice.devices.RrBeatEvent
import com.atrainingtracker.banalservice.devices.bluetooth_le.polar.PolarOfflineCapabilityStore
import com.atrainingtracker.banalservice.devices.bluetooth_le.polar.PolarOfflineGattCoordinator
import com.atrainingtracker.banalservice.sensor.MySensorManager
import com.atrainingtracker.banalservice.sensor.SensorType
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
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
 * Unit test suite verifying Bluetooth LE Heart Rate parsing (GATT 0x2A37), multi-RR interval unpacking,
 * cumulative beat-timestamp reconstruction, drift guard, and optical HR backward compatibility
 * (REQ-CON-027, TST-CON-027.2, TST-CON-027.3, TST-CON-027.5, ATT-3540).
 */
class BTLEHeartRateDeviceTest {

    private lateinit var mockContext: Context
    private lateinit var mockSensorManager: MySensorManager

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>(), any()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any()) } returns 0

        mockkStatic(ContextCompat::class)
        every {
            ContextCompat.checkSelfPermission(any(), Manifest.permission.BLUETOOTH_CONNECT)
        } returns PackageManager.PERMISSION_GRANTED
        every { ContextCompat.registerReceiver(any(), any(), any(), any()) } returns null

        mockkStatic(DevicesDatabaseManager::class)
        val mockDevicesDbManager = mockk<DevicesDatabaseManager>(relaxed = true)
        every { DevicesDatabaseManager.getInstance(any()) } returns mockDevicesDbManager

        mockContext = mockk(relaxed = true)
        val mockLooper = mockk<Looper>(relaxed = true)
        every { mockContext.mainLooper } returns mockLooper

        mockkConstructor(Handler::class)
        every { anyConstructed<Handler>().removeCallbacks(any()) } just Runs
        every { anyConstructed<Handler>().postDelayed(any(), any()) } returns true
        every { anyConstructed<Handler>().post(any()) } returns true

        mockSensorManager = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private class TestBTLEHeartRateDevice(
        context: Context,
        sensorManager: MySensorManager,
        deviceId: Long,
        address: String
    ) : BTLEHeartRateDevice(context, sensorManager, deviceId, address) {
        override fun notifySensorsChanged() {
            // No-op for unit testing
        }

        public override fun unregisterSensors() {
            super.unregisterSensors()
        }

        public fun callConfigureServices(gatt: BluetoothGatt) {
            super.configureServices(gatt)
        }

        public fun callOnOtherCharacteristicUpdate(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            super.onOtherCharacteristicUpdate(gatt, characteristic)
        }
    }

    private fun createDevice(): BTLEHeartRateDevice {
        return TestBTLEHeartRateDevice(mockContext, mockSensorManager, 101L, "AA:BB:CC:DD:EE:01")
    }

    /**
     * TST-CON-027.2: Multi-RR packet decoding with 0, 1, 2, and 3 bundled RR intervals.
     */
    @Test
    fun testMultiRrPacketDecoding_bundledBeats() {
        val device = createDevice()
        val receivedEvents = mutableListOf<RrBeatEvent>()
        device.addRrBeatListener { receivedEvents.add(it) }

        // Packet 1: flag = 0x10 (UINT8 HR, RR present), HR = 60 bpm, 1 RR interval: 1024 raw (1000.0 ms)
        // 1024 in hex Little-Endian: 0x00, 0x04
        val packet1 = byteArrayOf(0x10.toByte(), 60.toByte(), 0x00.toByte(), 0x04.toByte())
        device.parseHeartRateMeasurement(packet1, 100_000L)

        assertEquals(60, device.heartRateSensor.value)
        assertEquals(1, receivedEvents.size)
        assertEquals(1000.0, receivedEvents[0].rrMs, 0.01)
        assertEquals(100_000L, receivedEvents[0].beatTimestampMs)
        assertEquals(ProtocolType.BLE, receivedEvents[0].protocol)
        assertEquals(0, receivedEvents[0].gapBeats)
        assertEquals(RrBeatEvent.ARTIFACT_NORMAL, receivedEvents[0].artifactFlag)
        assertNotNull(device.rrIntervalSensor)
        assertEquals(1000, device.rrIntervalSensor?.value)

        // Packet 2: flag = 0x10, HR = 120 bpm, 2 bundled RR intervals: 512 raw (500.0 ms) and 512 raw (500.0 ms)
        // 512 in hex Little-Endian: 0x00, 0x02
        receivedEvents.clear()
        val packet2 = byteArrayOf(
            0x10.toByte(), 120.toByte(),
            0x00.toByte(), 0x02.toByte(),
            0x00.toByte(), 0x02.toByte()
        )
        device.parseHeartRateMeasurement(packet2, 101_000L)

        assertEquals(120, device.heartRateSensor.value)
        assertEquals(2, receivedEvents.size)
        assertEquals(500.0, receivedEvents[0].rrMs, 0.01)
        assertEquals(100_500L, receivedEvents[0].beatTimestampMs)
        assertEquals(500.0, receivedEvents[1].rrMs, 0.01)
        assertEquals(101_000L, receivedEvents[1].beatTimestampMs)
        assertEquals(500, device.rrIntervalSensor?.value)

        // Packet 3: flag = 0x10, HR = 150 bpm, 3 bundled RR intervals: 1024 (1000ms), 512 (500ms), 768 (750ms)
        // 768 in hex Little-Endian: 0x00, 0x03
        receivedEvents.clear()
        val packet3 = byteArrayOf(
            0x10.toByte(), 150.toByte(),
            0x00.toByte(), 0x04.toByte(),
            0x00.toByte(), 0x02.toByte(),
            0x00.toByte(), 0x03.toByte()
        )
        device.parseHeartRateMeasurement(packet3, 103_250L)

        assertEquals(3, receivedEvents.size)
        assertEquals(1000.0, receivedEvents[0].rrMs, 0.01)
        assertEquals(102_000L, receivedEvents[0].beatTimestampMs)
        assertEquals(500.0, receivedEvents[1].rrMs, 0.01)
        assertEquals(102_500L, receivedEvents[1].beatTimestampMs)
        assertEquals(750.0, receivedEvents[2].rrMs, 0.01)
        assertEquals(103_250L, receivedEvents[2].beatTimestampMs)
    }

    /**
     * TST-CON-027.2: UINT16 Heart Rate format & Energy Expended offset calculation.
     */
    @Test
    fun testUint16HrAndEnergyExpendedOffset() {
        val device = createDevice()
        val receivedEvents = mutableListOf<RrBeatEvent>()
        device.addRrBeatListener { receivedEvents.add(it) }

        // flag = 0x19: Bit 0 (UINT16 HR), Bit 3 (Energy Expended present), Bit 4 (RR present)
        // HR = 260 bpm (0x0104 Little Endian: 0x04, 0x01)
        // Energy Expended = 50 kcal (0x0032 Little Endian: 0x32, 0x00)
        // RR = 512 raw = 500 ms (0x0002 Little Endian: 0x02, 0x00)
        val payload = byteArrayOf(
            0x19.toByte(),
            0x04.toByte(), 0x01.toByte(), // HR = 260
            0x32.toByte(), 0x00.toByte(), // Energy Expended
            0x00.toByte(), 0x02.toByte()  // RR = 512 -> 500ms
        )
        device.parseHeartRateMeasurement(payload, 200_000L)

        assertEquals(260, device.heartRateSensor.value)
        assertEquals(1, receivedEvents.size)
        assertEquals(500.0, receivedEvents[0].rrMs, 0.01)
        assertEquals(200_000L, receivedEvents[0].beatTimestampMs)
    }

    /**
     * TST-CON-027.3: Monotonic cumulative timestamps & drift guard (> 2000 ms jump).
     */
    @Test
    fun testCumulativeTimestampsAndDriftGuard() {
        val device = createDevice()
        val receivedEvents = mutableListOf<RrBeatEvent>()
        device.addRrBeatListener { receivedEvents.add(it) }

        // Packet 1: Initial beat at t_pkt = 100_000, rr = 1000 ms
        val p1 = byteArrayOf(0x10.toByte(), 60.toByte(), 0x00.toByte(), 0x04.toByte())
        device.parseHeartRateMeasurement(p1, 100_000L)
        assertEquals(100_000L, receivedEvents[0].beatTimestampMs)
        assertEquals(0, receivedEvents[0].gapBeats)
        assertEquals(RrBeatEvent.ARTIFACT_NORMAL, receivedEvents[0].artifactFlag)

        // Packet 2: Next beat arrives normally at t_pkt = 101_020 (+20ms jitter).
        // Candidate = 100_000 + 1000 = 101_000. Drift = |101_020 - 101_000| = 20ms <= 2000ms.
        receivedEvents.clear()
        val p2 = byteArrayOf(0x10.toByte(), 60.toByte(), 0x00.toByte(), 0x04.toByte())
        device.parseHeartRateMeasurement(p2, 101_020L)
        assertEquals(1, receivedEvents.size)
        assertEquals(101_000L, receivedEvents[0].beatTimestampMs)
        assertEquals(0, receivedEvents[0].gapBeats)
        assertEquals(RrBeatEvent.ARTIFACT_NORMAL, receivedEvents[0].artifactFlag)

        // Packet 3: Disconnect/dropout for 5000 ms! Packet arrives at t_pkt = 106_000L with rr = 1000 ms.
        // Candidate = 101_000 + 1000 = 102_000. Drift = |106_000 - 102_000| = 4000ms > 2000ms.
        // Gap beats = round((106_000 - 101_000) / 1000) - 1 = 5 - 1 = 4.
        receivedEvents.clear()
        val p3 = byteArrayOf(0x10.toByte(), 60.toByte(), 0x00.toByte(), 0x04.toByte())
        device.parseHeartRateMeasurement(p3, 106_000L)
        assertEquals(1, receivedEvents.size)
        assertEquals(106_000L, receivedEvents[0].beatTimestampMs) // Re-anchored to packet arrival
        assertEquals(4, receivedEvents[0].gapBeats)
        assertEquals(RrBeatEvent.ARTIFACT_GAP, receivedEvents[0].artifactFlag)
    }

    /**
     * TST-CON-027.5: Optical sensor compatibility (Bit 4 absent) and truncated packets crash resilience.
     */
    @Test
    fun testOpticalHrCompatibilityAndTruncatedPackets() {
        val device = createDevice()
        val receivedEvents = mutableListOf<RrBeatEvent>()
        device.addRrBeatListener { receivedEvents.add(it) }

        // Optical sensor: flag = 0x00 (no RR intervals, UINT8 HR)
        val opticalPacket = byteArrayOf(0x00.toByte(), 75.toByte())
        device.parseHeartRateMeasurement(opticalPacket, 50_000L)

        assertEquals(75, device.heartRateSensor.value)
        assertNull("mRrIntervalSensor must remain null for optical sensors", device.rrIntervalSensor)
        assertNull("SensorType.RR_INTERVAL must not be registered in mSensorMap", device.getSensor(SensorType.RR_INTERVAL))
        assertEquals(0, receivedEvents.size)

        // Truncated packets (REQ-CON-014)
        device.parseHeartRateMeasurement(null, 51_000L)
        device.parseHeartRateMeasurement(byteArrayOf(0x00.toByte()), 51_000L)
        device.parseHeartRateMeasurement(byteArrayOf(0x01.toByte(), 0x50.toByte()), 51_000L) // flag indicates UINT16 HR but length=2
        device.parseHeartRateMeasurement(byteArrayOf(0x18.toByte(), 0x50.toByte(), 0x01.toByte()), 51_000L) // flag indicates Energy Expended but truncated

        // State remains valid and uncorrupted
        assertEquals(75, device.heartRateSensor.value)
    }

    /**
     * TST-TRK-069.3: Initial bundled RR packet anchors newest beat to packetArrivalMs and back-dates prior beats.
     */
    @Test
    fun testInitialBundledBeatsBackwardAnchoring() {
        val device = createDevice()
        val receivedEvents = mutableListOf<RrBeatEvent>()
        device.addRrBeatListener { receivedEvents.add(it) }

        // Initial packet arriving at 10_000L with 3 bundled intervals:
        // raw1 = 1024 (1000.0ms, 0x0400 LE: 0x00, 0x04)
        // raw2 = 512 (500.0ms, 0x0200 LE: 0x00, 0x02)
        // raw3 = 256 (250.0ms, 0x0100 LE: 0x00, 0x01)
        val packet = byteArrayOf(
            0x10.toByte(), 75.toByte(),
            0x00.toByte(), 0x04.toByte(),
            0x00.toByte(), 0x02.toByte(),
            0x00.toByte(), 0x01.toByte()
        )
        device.parseHeartRateMeasurement(packet, 10_000L)

        assertEquals(3, receivedEvents.size)
        // Beat 2 (newest) anchored to packetArrivalMs = 10_000L
        assertEquals(250.0, receivedEvents[2].rrMs, 0.01)
        assertEquals(10_000L, receivedEvents[2].beatTimestampMs)

        // Beat 1 back-calculated: 10_000 - 250 = 9_750L
        assertEquals(500.0, receivedEvents[1].rrMs, 0.01)
        assertEquals(9_750L, receivedEvents[1].beatTimestampMs)

        // Beat 0 back-calculated: 9_750 - 500 = 9_250L
        assertEquals(1000.0, receivedEvents[0].rrMs, 0.01)
        assertEquals(9_250L, receivedEvents[0].beatTimestampMs)

        // All beats should be marked normal with 0 gap beats
        for (event in receivedEvents) {
            assertEquals(0, event.gapBeats)
            assertEquals(RrBeatEvent.ARTIFACT_NORMAL, event.artifactFlag)
        }
    }

    /**
     * TST-TRK-069.2: unregisterSensors() resets mRrIntervalSensor to null, allowing clean re-registration on reconnect.
     */
    @Test
    fun testReconnectReRegistersRrIntervalSensor() {
        val device = createDevice()
        val packet = byteArrayOf(0x10.toByte(), 60.toByte(), 0x00.toByte(), 0x04.toByte())

        // Initial connect
        device.parseHeartRateMeasurement(packet, 100_000L)
        assertNotNull("mRrIntervalSensor must be non-null after first RR packet", device.rrIntervalSensor)

        // Mid-ride disconnect
        device.unregisterSensors()
        assertNull("mRrIntervalSensor must be reset to null in unregisterSensors()", device.rrIntervalSensor)

        // Reconnect
        device.parseHeartRateMeasurement(packet, 105_000L)
        assertNotNull("mRrIntervalSensor must be re-registered upon reconnect", device.rrIntervalSensor)
        assertEquals(1000, device.rrIntervalSensor?.value)
    }

    /**
     * TST-TRK-069.3: Continuous double precision accumulation prevents Math.round quantization drift across 3600 beats.
     */
    @Test
    fun testContinuousDoublePrecisionDriftBounding_3600Beats() {
        val device = createDevice()
        val receivedEvents = mutableListOf<RrBeatEvent>()
        device.addRrBeatListener { receivedEvents.add(it) }

        // raw = 819 -> 819 * 1000.0 / 1024.0 = 799.8046875 ms
        // Math.round(799.8046875) = 800 ms (+0.1953 ms/beat error if rounded!)
        val rawRr = 819
        val rawByte0 = (rawRr and 0xFF).toByte()
        val rawByte1 = ((rawRr shr 8) and 0xFF).toByte()
        val packet = byteArrayOf(0x10.toByte(), 75.toByte(), rawByte0, rawByte1)

        val beatDurationMs = 819 * 1000.0 / 1024.0
        var arrivalMs = 100_000.0

        // Stream 3600 beats (~48 minutes of riding)
        for (i in 0 until 3600) {
            device.parseHeartRateMeasurement(packet, Math.round(arrivalMs))
            arrivalMs += beatDurationMs
        }

        assertEquals(3600, receivedEvents.size)

        // Zero ARTIFACT_GAP events must be emitted (no fake gap from integer drift!)
        val gapEvents = receivedEvents.filter { it.artifactFlag == RrBeatEvent.ARTIFACT_GAP }
        assertEquals("Zero false ARTIFACT_GAP events should occur over 3600 beats", 0, gapEvents.size)

        // Cumulative drift must be strictly bounded (< 5 ms)
        val lastEvent = receivedEvents.last()
        val expectedTimestamp = Math.round(100_000.0 + (3599 * beatDurationMs))
        assertEquals(expectedTimestamp, lastEvent.beatTimestampMs)
    }

    /**
     * TST-CON-027.6: Clean RR_INTERVAL sensor teardown on disconnect and reconnect continuity (ATT-7530).
     */
    @Test
    fun testUnregisterSensors_cleansUpRrIntervalSensor_andReconnectCreatesNewInstance() {
        val device = createDevice() as TestBTLEHeartRateDevice
        assertNull("RR sensor must initially be null", device.rrIntervalSensor)

        // Parse RR packet
        val packet = byteArrayOf(0x10.toByte(), 70.toByte(), 0x00.toByte(), 0x04.toByte()) // 1024 raw -> 1000ms
        device.parseHeartRateMeasurement(packet, 1000L)
        val firstSensor = device.rrIntervalSensor
        assertNotNull("RR sensor should be created on first RR packet", firstSensor)
        assertEquals(1000, firstSensor?.value)

        // Verify it is present in device sensors
        val rrInSensorsBefore = device.sensors.any { it.sensorType == SensorType.RR_INTERVAL }
        assertTrue("RR_INTERVAL must be present in device sensors before disconnect", rrInSensorsBefore)

        // Unregister sensors (simulating disconnect)
        device.unregisterSensors()

        assertNull("mRrIntervalSensor field must be null after unregisterSensors()", device.rrIntervalSensor)
        val rrInSensorsAfter = device.sensors.any { it.sensorType == SensorType.RR_INTERVAL }
        org.junit.Assert.assertFalse("RR_INTERVAL must be removed from device sensors after unregisterSensors()", rrInSensorsAfter)

        // Simulate reconnect and arrival of another RR packet
        device.parseHeartRateMeasurement(packet, 2000L)
        val secondSensor = device.rrIntervalSensor
        assertNotNull("RR sensor should be re-created after reconnect", secondSensor)
        org.junit.Assert.assertNotSame("Reconnected sensor must be a fresh instance", firstSensor, secondSensor)
        assertEquals(1000, secondSensor?.value)
    }

    /**
     * TST-TRK-085.1: Drift re-anchoring must clamp bundled beat timestamps monotonically (>= lastBeatTimestampMs).
     */
    @Test
    fun testDriftReAnchor_clampsBackwardBundleTimestampsMonotonically() {
        val device = createDevice()
        val receivedEvents = mutableListOf<RrBeatEvent>()
        device.addRrBeatListener { receivedEvents.add(it) }

        // Anchor at 100_000L with 1000 ms beat
        val p1 = byteArrayOf(0x10.toByte(), 60.toByte(), 0x00.toByte(), 0x04.toByte())
        device.parseHeartRateMeasurement(p1, 100_000L)

        // Arrival at 101_000L with 4 x 1000 ms bundled beats -> candidate = 104_000L, drift = 3000ms > 2000ms!
        // Unclamped back-calculation would emit 98_000, 99_000, 100_000, 101_000 (-2000 ms jump).
        receivedEvents.clear()
        val p2 = byteArrayOf(
            0x10.toByte(), 60.toByte(),
            0x00.toByte(), 0x04.toByte(),
            0x00.toByte(), 0x04.toByte(),
            0x00.toByte(), 0x04.toByte(),
            0x00.toByte(), 0x04.toByte()
        )
        device.parseHeartRateMeasurement(p2, 101_000L)

        assertEquals(4, receivedEvents.size)
        var prevTs = 100_000L
        for (event in receivedEvents) {
            assertTrue(
                "Beat timestamp ${event.beatTimestampMs} must be >= $prevTs",
                event.beatTimestampMs >= prevTs
            )
            prevTs = event.beatTimestampMs
        }
    }

    /**
     * TST-TRK-085.2: Physiological RR bounds (> 2500 ms or < 250 ms) flagged as ARTIFACT_GAP even at 0 drift.
     */
    @Test
    fun testMultiSecondAndSubMinimumRrIntervals_atLowDriftFlaggedAsArtifactGap() {
        val device = createDevice()
        val receivedEvents = mutableListOf<RrBeatEvent>()
        device.addRrBeatListener { receivedEvents.add(it) }

        // Initial normal 800 ms beat (819 / 1024 * 1000 = 799.8 ms) at 100_000L
        val normalRaw = 819
        val p1 = byteArrayOf(0x10.toByte(), 75.toByte(), (normalRaw and 0xFF).toByte(), (normalRaw shr 8).toByte())
        device.parseHeartRateMeasurement(p1, 100_000L)

        // Multi-second 5196.29 ms RR (5321 raw ticks) arriving right on time (drift = 0 ms <= 2000 ms)
        val gapRaw = 5321
        val pGap = byteArrayOf(0x10.toByte(), 75.toByte(), (gapRaw and 0xFF).toByte(), (gapRaw shr 8).toByte())
        device.parseHeartRateMeasurement(pGap, 105_196L)

        // Sub-minimum 195.31 ms RR (200 raw ticks) arriving on time
        val shortRaw = 200
        val pShort = byteArrayOf(0x10.toByte(), 75.toByte(), (shortRaw and 0xFF).toByte(), (shortRaw shr 8).toByte())
        device.parseHeartRateMeasurement(pShort, 105_391L)

        assertEquals(3, receivedEvents.size)
        assertEquals(RrBeatEvent.ARTIFACT_NORMAL, receivedEvents[0].artifactFlag)
        assertEquals(RrBeatEvent.ARTIFACT_GAP, receivedEvents[1].artifactFlag)
        assertTrue("Multi-second gap must estimate gapBeats > 0", receivedEvents[1].gapBeats > 0)
        assertEquals(RrBeatEvent.ARTIFACT_GAP, receivedEvents[2].artifactFlag)
    }

    /**
     * REQ-CON-049.3 / TST-CON-049.2: Standard BLE HRM reconnect enqueues DIS 0x2A24/0x2A25,
     * persists Polar PMD/PFTP service UUIDs & DIS hardware metadata, and seeds aliases.
     */
    @Test
    fun testConfigureServicesAndDisUpdate_hydratesPolarMetadataWithoutNonPolarRegression() {
        PolarOfflineCapabilityStore.resetForTesting()
        PolarOfflineGattCoordinator.resetForTesting()
        try {
            val device = TestBTLEHeartRateDevice(mockContext, mockSensorManager, 101L, "AA:BB:CC:DD:EE:77")
            val mockGatt = mockk<BluetoothGatt>(relaxed = true)
            val hrService = mockk<BluetoothGattService>(relaxed = true)
            val hrChar = mockk<BluetoothGattCharacteristic>(relaxed = true)
            val disService = mockk<BluetoothGattService>(relaxed = true)
            val modelChar = mockk<BluetoothGattCharacteristic>(relaxed = true)
            val serialChar = mockk<BluetoothGattCharacteristic>(relaxed = true)

            every { mockGatt.getService(BluetoothConstants.getServiceUUID(DeviceType.HRM)) } returns hrService
            every { hrService.getCharacteristic(BluetoothConstants.getCharacteristicUUID(DeviceType.HRM)) } returns hrChar
            every { hrChar.getDescriptor(BluetoothConstants.UUID_CHARACTERISTIC_CLIENT_CONFIG) } returns null

            every { mockGatt.getService(BluetoothConstants.UUID_SERVICE_POLAR_PMD) } returns mockk(relaxed = true)
            every { mockGatt.getService(BluetoothConstants.UUID_SERVICE_POLAR_PFTP) } returns mockk(relaxed = true)
            every { mockGatt.getService(BluetoothConstants.UUID_SERVICE_DEVICE_INFORMATION) } returns disService
            every { disService.getCharacteristic(BluetoothConstants.UUID_CHARACTERISTIC_MODEL_NUMBER) } returns modelChar
            every { disService.getCharacteristic(BluetoothConstants.UUID_CHARACTERISTIC_SERIAL_NUMBER) } returns serialChar
            every { modelChar.uuid } returns BluetoothConstants.UUID_CHARACTERISTIC_MODEL_NUMBER
            every { modelChar.getStringValue(0) } returns "Verity Sense"
            every { serialChar.uuid } returns BluetoothConstants.UUID_CHARACTERISTIC_SERIAL_NUMBER
            every { serialChar.getStringValue(0) } returns "BEEF1234"

            device.callConfigureServices(mockGatt)
            assertTrue(device.isGattOperationPendingForTesting)
            assertEquals(1, device.gattQueueForTesting.size)
            val queuedOp = device.gattQueueForTesting.peek() as MyBTLEDevice.ReadCharacteristicOp
            assertEquals(BluetoothConstants.UUID_CHARACTERISTIC_SERIAL_NUMBER, queuedOp.characteristic.uuid)
            assertTrue(PolarOfflineCapabilityStore.hasPmdService(mockContext, "AA:BB:CC:DD:EE:77"))
            assertTrue(PolarOfflineCapabilityStore.hasPftpService(mockContext, "AA:BB:CC:DD:EE:77"))

            device.callOnOtherCharacteristicUpdate(mockGatt, modelChar)
            device.callOnOtherCharacteristicUpdate(mockGatt, serialChar)

            assertEquals("Verity Sense", PolarOfflineCapabilityStore.getModelNumber(mockContext, "AA:BB:CC:DD:EE:77"))
            assertEquals("BEEF1234", PolarOfflineCapabilityStore.getPolarDeviceId(mockContext, "AA:BB:CC:DD:EE:77"))
            assertEquals(
                PolarOfflineCapabilityStore.PolarDeviceType.VERITY_SENSE,
                PolarOfflineCapabilityStore.getDeviceType(mockContext, "AA:BB:CC:DD:EE:77")
            )
            assertTrue(PolarOfflineGattCoordinator.matchesDeviceIdentifier("BEEF1234", "AA:BB:CC:DD:EE:77"))

            // Non-Polar BLE HRM reconnect leaves store untouched
            val nonPolar = TestBTLEHeartRateDevice(mockContext, mockSensorManager, 102L, "AA:BB:CC:DD:EE:78")
            val nonPolarGatt = mockk<BluetoothGatt>(relaxed = true)
            every { nonPolarGatt.getService(BluetoothConstants.getServiceUUID(DeviceType.HRM)) } returns hrService
            every { nonPolarGatt.getService(BluetoothConstants.UUID_SERVICE_POLAR_PMD) } returns null
            every { nonPolarGatt.getService(BluetoothConstants.UUID_SERVICE_POLAR_PFTP) } returns null
            every { nonPolarGatt.getService(BluetoothConstants.UUID_SERVICE_DEVICE_INFORMATION) } returns null
            nonPolar.callConfigureServices(nonPolarGatt)
            assertFalse(PolarOfflineCapabilityStore.hasPmdService(mockContext, "AA:BB:CC:DD:EE:78"))
            assertEquals(
                PolarOfflineCapabilityStore.PolarDeviceType.UNKNOWN,
                PolarOfflineCapabilityStore.getDeviceType(mockContext, "AA:BB:CC:DD:EE:78")
            )
        } finally {
            PolarOfflineCapabilityStore.resetForTesting()
            PolarOfflineGattCoordinator.resetForTesting()
        }
    }

    @Test
    fun testConfigureServices_enqueuesDisBeforeHrCccdAndIgnoresNonPolarCardioSense() {
        PolarOfflineCapabilityStore.resetForTesting()
        PolarOfflineGattCoordinator.resetForTesting()
        try {
            // 1. Polar sensor with HR CCCD descriptor: 0x2A24 & 0x2A25 must enqueue BEFORE HR CCCD write
            val polarMac = "AA:BB:CC:DD:EE:79"
            val polarDevice = TestBTLEHeartRateDevice(mockContext, mockSensorManager, 103L, polarMac)
            val polarGatt = mockk<BluetoothGatt>(relaxed = true)
            val hrService = mockk<BluetoothGattService>(relaxed = true)
            val hrChar = mockk<BluetoothGattCharacteristic>(relaxed = true)
            val hrDescriptor = mockk<BluetoothGattDescriptor>(relaxed = true)
            val disService = mockk<BluetoothGattService>(relaxed = true)
            val modelChar = mockk<BluetoothGattCharacteristic>(relaxed = true)
            val serialChar = mockk<BluetoothGattCharacteristic>(relaxed = true)

            every { polarGatt.getService(BluetoothConstants.getServiceUUID(DeviceType.HRM)) } returns hrService
            every { hrService.getCharacteristic(BluetoothConstants.getCharacteristicUUID(DeviceType.HRM)) } returns hrChar
            every { hrChar.getDescriptor(BluetoothConstants.UUID_CHARACTERISTIC_CLIENT_CONFIG) } returns hrDescriptor
            every { polarGatt.getService(BluetoothConstants.UUID_SERVICE_POLAR_PMD) } returns mockk(relaxed = true)
            every { polarGatt.getService(BluetoothConstants.UUID_SERVICE_POLAR_PFTP) } returns null
            every { polarGatt.getService(BluetoothConstants.UUID_SERVICE_DEVICE_INFORMATION) } returns disService
            every { disService.getCharacteristic(BluetoothConstants.UUID_CHARACTERISTIC_MODEL_NUMBER) } returns modelChar
            every { disService.getCharacteristic(BluetoothConstants.UUID_CHARACTERISTIC_SERIAL_NUMBER) } returns serialChar
            every { modelChar.uuid } returns BluetoothConstants.UUID_CHARACTERISTIC_MODEL_NUMBER
            every { serialChar.uuid } returns BluetoothConstants.UUID_CHARACTERISTIC_SERIAL_NUMBER

            polarDevice.callConfigureServices(polarGatt)
            // First op (0x2A24) was popped immediately by processNextGattOperation(); remaining ops must be 0x2A25 then HR CCCD write
            val ops = polarDevice.gattQueueForTesting.toList()
            assertEquals(2, ops.size)
            assertTrue(ops[0] is MyBTLEDevice.ReadCharacteristicOp)
            assertEquals(
                BluetoothConstants.UUID_CHARACTERISTIC_SERIAL_NUMBER,
                (ops[0] as MyBTLEDevice.ReadCharacteristicOp).characteristic.uuid
            )
            assertTrue(ops[1] is MyBTLEDevice.WriteDescriptorOp)

            // 2. Non-Polar HRM with DIS 0x180A ("CardioSense", 13-digit serial) must NEVER pollute store
            val cardioMac = "AA:BB:CC:DD:EE:7A"
            val cardioDevice = TestBTLEHeartRateDevice(mockContext, mockSensorManager, 104L, cardioMac)
            val cardioGatt = mockk<BluetoothGatt>(relaxed = true)
            val cardioModelChar = mockk<BluetoothGattCharacteristic>(relaxed = true)
            val cardioSerialChar = mockk<BluetoothGattCharacteristic>(relaxed = true)
            every { cardioGatt.getService(BluetoothConstants.getServiceUUID(DeviceType.HRM)) } returns hrService
            every { cardioGatt.getService(BluetoothConstants.UUID_SERVICE_POLAR_PMD) } returns null
            every { cardioGatt.getService(BluetoothConstants.UUID_SERVICE_POLAR_PFTP) } returns null
            every { cardioGatt.getService(BluetoothConstants.UUID_SERVICE_DEVICE_INFORMATION) } returns disService
            every { cardioModelChar.uuid } returns BluetoothConstants.UUID_CHARACTERISTIC_MODEL_NUMBER
            every { cardioModelChar.getStringValue(0) } returns "CardioSense"
            every { cardioSerialChar.uuid } returns BluetoothConstants.UUID_CHARACTERISTIC_SERIAL_NUMBER
            every { cardioSerialChar.getStringValue(0) } returns "1234567890123"

            cardioDevice.callConfigureServices(cardioGatt)
            // Only HR CCCD write queued (neither 0x2A24 nor 0x2A25 read)
            assertEquals(0, cardioDevice.gattQueueForTesting.size)
            cardioDevice.callOnOtherCharacteristicUpdate(cardioGatt, cardioModelChar)
            cardioDevice.callOnOtherCharacteristicUpdate(cardioGatt, cardioSerialChar)

            assertFalse(PolarOfflineCapabilityStore.hasPolarServiceCapability(mockContext, cardioMac))
            assertNull(PolarOfflineCapabilityStore.getModelNumber(mockContext, cardioMac))
            assertNull(PolarOfflineCapabilityStore.getPolarDeviceId(mockContext, cardioMac))
            assertFalse(PolarOfflineGattCoordinator.isPolarVeritySenseDevice(mockContext, "CardioSense", "Generic", cardioMac))
            assertFalse(PolarOfflineGattCoordinator.isPolarH10Device(mockContext, "CardioSense", "Generic", cardioMac))
            assertFalse(PolarOfflineGattCoordinator.isPolarOfflineCapableDevice(mockContext, "CardioSense", "Generic", cardioMac))
        } finally {
            PolarOfflineCapabilityStore.resetForTesting()
            PolarOfflineGattCoordinator.resetForTesting()
        }
    }
}
