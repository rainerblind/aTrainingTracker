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
import android.content.Context
import android.content.pm.PackageManager
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager
import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.banalservice.devices.radar.RadarConnectionState
import com.atrainingtracker.banalservice.devices.radar.RadarStateRepository
import com.atrainingtracker.banalservice.devices.radar.RadarTarget
import com.atrainingtracker.banalservice.devices.radar.ThreatLevel
import com.atrainingtracker.banalservice.sensor.MySensorManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
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
}
