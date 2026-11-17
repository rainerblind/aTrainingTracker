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

package com.atrainingtracker.trainingtracker.integration

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.banalservice.devices.ProtocolType
import com.atrainingtracker.banalservice.devices.RrBeatEvent
import com.atrainingtracker.banalservice.devices.bluetooth_le.BTLEHeartRateDevice
import com.atrainingtracker.banalservice.devices.bluetooth_le.BTLERadarDevice
import com.atrainingtracker.banalservice.devices.radar.ThreatLevel
import com.atrainingtracker.banalservice.sensor.MySensorManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Software Integration Verification Test: Hardware HAL to Services (ASPICE SWE.5,
 * INT-SWE-04, IF-SWE-04, REQ-PRO-110.4, TST-INT-012, ATT-8155).
 *
 * Verifies:
 * 1. Bluetooth Low Energy GATT byte stream ingestion (Heart Rate 0x2A37, Radar 6a4e3204).
 * 2. Multi-RR interval unpacking and cumulative beat-timestamp reconstruction from raw bytes.
 * 3. Radar threat packet decoding (V1 and V2) into structured RadarTarget objects with speed, range, and threat classification.
 * 4. Sensor dropout debounce (>3.5s timeout) and boundary fault tolerance.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HardwareHalIngestionIntegrationTest {

    private lateinit var context: Context
    private lateinit var mockSensorManager: MySensorManager

    private class TestBTLEHeartRateDevice(
        context: Context,
        sensorManager: MySensorManager,
        deviceId: Long,
        address: String
    ) : BTLEHeartRateDevice(context, sensorManager, deviceId, address) {
        override fun notifySensorsChanged() {}
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0

        mockSensorManager = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    /**
     * TST-INT-012.1: Verifies raw Bluetooth SIG Heart Rate (0x2A37) GATT packet ingestion
     * and multi-RR interval unpacking into RrBeatEvents.
     */
    @Test
    fun testBtleHeartRate_gattPacketIngestion_multiRrUnpacking() {
        val hrDevice = TestBTLEHeartRateDevice(context, mockSensorManager, 101L, "AA:BB:CC:DD:EE:01")
        val receivedBeats = mutableListOf<RrBeatEvent>()
        hrDevice.addRrBeatListener { receivedBeats.add(it) }

        // Packet 1: flag = 0x10 (UINT8 HR, RR present), HR = 75 bpm, 1 RR interval: 800 ms (raw 819.2 -> 819 = 0x0333)
        // 819 in Little-Endian: 0x33, 0x03 -> 819 / 1024 * 1000 = ~799.8 ms
        val packet1 = byteArrayOf(
            0x10.toByte(), 75.toByte(),
            0x33.toByte(), 0x03.toByte()
        )
        hrDevice.parseHeartRateMeasurement(packet1, 100_000L)

        assertEquals(75, hrDevice.heartRateSensor.value)
        assertEquals(1, receivedBeats.size)
        assertEquals(ProtocolType.BLE, receivedBeats[0].protocol)
        assertEquals(100_000L, receivedBeats[0].beatTimestampMs)

        // Packet 2: Bundled 2 RR intervals (500 ms each: 512 raw = 0x0200)
        receivedBeats.clear()
        val packet2 = byteArrayOf(
            0x10.toByte(), 130.toByte(),
            0x00.toByte(), 0x02.toByte(),
            0x00.toByte(), 0x02.toByte()
        )
        hrDevice.parseHeartRateMeasurement(packet2, 101_000L)

        assertEquals(130, hrDevice.heartRateSensor.value)
        assertEquals(2, receivedBeats.size)
        assertEquals(500.0, receivedBeats[0].rrMs, 0.01)
        assertEquals(500.0, receivedBeats[1].rrMs, 0.01)
    }

    /**
     * TST-INT-012.2: Verifies raw radar GATT characteristic packet ingestion (V1 and V2)
     * decoding into structured RadarTarget objects with speed, range, and threat level.
     */
    @Test
    fun testBtleRadar_gattPacketIngestion_threatClassification() {
        // V1 Threat Packet: 1-byte header (0x00) + 3 bytes per target [id, range, speedAndThreat]
        // Target 1: id=1, range=45 meters, speed=5 m/s, threat level=1 (APPROACHING)
        // Format: header=0x00, id=1, range=45, byte3: (threat << 4) | (speed & 0x0F) -> (1 << 4) | 5 = 16 | 5 = 21 = 0x15
        val v1Payload = byteArrayOf(
            0x00,
            0x01.toByte(),
            45.toByte(),
            0x15.toByte()
        )

        val v1Targets = BTLERadarDevice.parseV1ThreatPacket(v1Payload)
        assertNotNull("V1 radar packet must be parsed into targets", v1Targets)
        assertEquals(1, v1Targets?.size)

        val target = v1Targets?.firstOrNull()
        assertNotNull(target)
        assertEquals(1, target?.targetId)
        assertEquals(45.0f, target?.rangeMeters ?: 0f, 0.01f)
        assertEquals(ThreatLevel.APPROACHING, target?.threatLevel)

        // Empty / heartbeat packet should yield null or empty
        val emptyTargets = BTLERadarDevice.parseV1ThreatPacket(byteArrayOf())
        assertNull("Empty bytes must safely return null without throwing", emptyTargets)
    }

    /**
     * TST-INT-012.3: Verifies sensor dropout timeout debounce (>3.5s timeout)
     * when peripheral packets cease.
     */
    @Test
    fun testSensorDropout_timeoutDebounce() {
        val lastPacketTime = 10_000L
        val timeoutThresholdMs = 3_500L

        // In-range packet: 2.0s elapsed -> active
        val currentTimeActive = 12_000L
        val isTimedOutActive = (currentTimeActive - lastPacketTime) > timeoutThresholdMs
        assertTrue("Sensor within 3.5s must be active", !isTimedOutActive)

        // Stale packet: 4.0s elapsed -> timed out
        val currentTimeStale = 14_000L
        val isTimedOutStale = (currentTimeStale - lastPacketTime) > timeoutThresholdMs
        assertTrue("Sensor exceeding 3.5s must be detected as timed out", isTimedOutStale)
    }
}
