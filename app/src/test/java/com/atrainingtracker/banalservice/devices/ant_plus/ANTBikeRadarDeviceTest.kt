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

package com.atrainingtracker.banalservice.devices.ant_plus

import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager
import com.atrainingtracker.banalservice.devices.radar.LaneState
import com.atrainingtracker.banalservice.devices.radar.RadarConnectionState
import com.atrainingtracker.banalservice.devices.radar.RadarScenario
import com.atrainingtracker.banalservice.devices.radar.RadarStateRepository
import com.atrainingtracker.banalservice.devices.radar.RadarTrafficSimulator
import com.atrainingtracker.banalservice.devices.radar.ThreatLevel
import com.atrainingtracker.banalservice.sensor.MySensorManager
import com.dsi.ant.channel.AntChannel
import com.dsi.ant.message.ChannelId
import com.dsi.ant.message.ChannelType
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit test suite for ANTBikeRadarDevice (TST-CON-032, REQ-CON-032).
 *
 * Verifies ANT+ channel configuration, Page 0x30/0x31 bit unpacking,
 * fail-safe 3.5s starvation watchdog, and simulator preemption.
 */
class ANTBikeRadarDeviceTest {

    private lateinit var mockContext: Context
    private lateinit var mockSensorManager: MySensorManager
    private lateinit var radarDevice: ANTBikeRadarDevice

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any()) } returns 0

        mockkStatic(android.os.SystemClock::class)
        every { android.os.SystemClock.elapsedRealtime() } returns 2000L

        mockkStatic(ContextCompat::class)
        every { ContextCompat.checkSelfPermission(any(), any()) } returns PackageManager.PERMISSION_GRANTED
        every { ContextCompat.registerReceiver(any(), any(), any(), any()) } returns null

        mockkStatic(DevicesDatabaseManager::class)
        val mockDevicesDbManager = mockk<DevicesDatabaseManager>(relaxed = true)
        every { DevicesDatabaseManager.getInstance(any()) } returns mockDevicesDbManager

        mockkStatic(Looper::class)
        val mockLooper = mockk<Looper>(relaxed = true)
        every { Looper.getMainLooper() } returns mockLooper

        mockkConstructor(Handler::class)
        every { anyConstructed<Handler>().post(any()) } answers {
            firstArg<Runnable>().run()
            true
        }
        every { anyConstructed<Handler>().postDelayed(any(), any()) } answers {
            true
        }
        every { anyConstructed<Handler>().removeCallbacks(any<Runnable>()) } answers {}
        every { anyConstructed<Handler>().removeCallbacksAndMessages(any()) } answers {}

        mockkConstructor(android.content.Intent::class)
        every { anyConstructed<android.content.Intent>().setPackage(any()) } answers { self as android.content.Intent }
        every { anyConstructed<android.content.Intent>().putExtra(any<String>(), any<String>()) } answers { self as android.content.Intent }
        every { anyConstructed<android.content.Intent>().putExtra(any<String>(), any<Boolean>()) } answers { self as android.content.Intent }
        every { anyConstructed<android.content.Intent>().putExtra(any<String>(), any<Long>()) } answers { self as android.content.Intent }

        mockContext = mockk(relaxed = true)
        every { mockContext.mainLooper } returns mockLooper
        mockSensorManager = mockk(relaxed = true)

        RadarStateRepository.reset()
        RadarTrafficSimulator.stop()

        radarDevice = ANTBikeRadarDevice(
            mockContext,
            mockSensorManager,
            deviceId = 42L,
            antDeviceNumber = 12345
        )
    }

    @After
    fun tearDown() {
        radarDevice.shutDown()
        RadarStateRepository.reset()
        RadarTrafficSimulator.stop()
        unmockkAll()
    }

    @Test
    fun testConfigureChannel_setsCorrectAntParameters() {
        val mockChannel = mockk<AntChannel>(relaxed = true)
        val channelIdSlot = slot<ChannelId>()

        every { mockChannel.setChannelId(capture(channelIdSlot)) } returns Unit

        radarDevice.configureChannel(mockChannel)

        verify { mockChannel.assign(ChannelType.BIDIRECTIONAL_SLAVE) }
        verify { mockChannel.setPeriod(ANTBikeRadarDevice.BIKE_RADAR_CHANNEL_PERIOD) }
        verify { mockChannel.setRfFrequency(ANTBikeRadarDevice.BIKE_RADAR_RF_FREQUENCY) }
        verify { mockChannel.setAdapterWideLibConfig(match { it.enableChannelIdOutput && it.enableRssiOutput }) }
        verify { mockChannel.open() }

        assertTrue(channelIdSlot.isCaptured)
        val channelId = channelIdSlot.captured
        assertEquals(12345, channelId.deviceNumber)
        assertEquals(ANTBikeRadarDevice.BIKE_RADAR_DEVICE_TYPE, channelId.deviceType)
    }

    @Test
    fun testDecodeBroadcastData_page0x30_unpacksTargetsAccurately() {
        // Page 0x30 with 2 targets:
        // Target 0: Threat = 1 (APPROACHING), Side = 0 (BEHIND), Range = 20 (62.5m), Speed = 5 (15.2 m/s)
        // Target 1: Threat = 2 (FAST_APPROACHING), Side = 1 (RIGHT), Range = 40 (125.0m), Speed = 10 (30.4 m/s)
        // Threat byte: 0x01 | (0x02 << 2) = 0x09
        // Side byte: 0x00 | (0x01 << 2) = 0x04
        // Range 24-bit: 20 | (40 << 6) = 20 | 2560 = 2580 -> bytes [0x14, 0x0A, 0x00]
        // Speed 16-bit: 5 | (10 << 4) = 0xA5 -> bytes [0xA5, 0x00]

        val payload = byteArrayOf(
            0x30.toByte(), // Page 0x30
            0x09.toByte(), // Threats
            0x04.toByte(), // Sides
            0x14.toByte(), // Range LSB
            0x0A.toByte(), // Range Mid
            0x00.toByte(), // Range MSB
            0xA5.toByte(), // Speed LSB
            0x00.toByte()  // Speed MSB
        )

        radarDevice.decodeBroadcastData(payload)

        val state = RadarStateRepository.getState()
        assertEquals(RadarConnectionState.CONNECTED, state.connectionState)
        assertEquals(ThreatLevel.FAST_APPROACHING, state.overallThreatLevel)
        assertEquals(2, state.targets.size)

        val target0 = state.targets.find { it.targetId == 0 }
        assertNotNull(target0)
        assertEquals(62.5f, target0!!.rangeMeters, 0.01f)
        assertEquals(15.2f, target0.approachSpeedMps, 0.01f)
        assertEquals(ThreatLevel.APPROACHING, target0.threatLevel)
        assertEquals(LaneState.IN_RIDER_LANE, target0.laneState)
        assertEquals(0.0f, target0.lateralOffsetMeters!!, 0.01f)

        val target1 = state.targets.find { it.targetId == 1 }
        assertNotNull(target1)
        assertEquals(125.0f, target1!!.rangeMeters, 0.01f)
        assertEquals(30.4f, target1.approachSpeedMps, 0.01f)
        assertEquals(ThreatLevel.FAST_APPROACHING, target1.threatLevel)
        assertEquals(LaneState.PASSING_LANE, target1.laneState)
        assertEquals(1.5f, target1.lateralOffsetMeters!!, 0.01f)
    }

    @Test
    fun testDecodeBroadcastData_page0x31_unpacksTargets5to8() {
        // Target 4 (index 0 in Page 0x31):
        // Threat = 1 (APPROACHING), Side = 2 (LEFT), Range = 10 (31.25m), Speed = 3 (9.12 m/s)
        val payload = byteArrayOf(
            0x31.toByte(), // Page 0x31
            0x01.toByte(), // Threat 1 for target 0 in page
            0x02.toByte(), // Side 2 (LEFT) for target 0 in page
            0x0A.toByte(), // Range 10
            0x00.toByte(),
            0x00.toByte(),
            0x03.toByte(), // Speed 3
            0x00.toByte()
        )

        radarDevice.decodeBroadcastData(payload)

        val state = RadarStateRepository.getState()
        assertEquals(1, state.targets.size)
        val target4 = state.targets.first()
        assertEquals(4, target4.targetId)
        assertEquals(31.25f, target4.rangeMeters, 0.01f)
        assertEquals(9.12f, target4.approachSpeedMps, 0.01f)
        assertEquals(ThreatLevel.APPROACHING, target4.threatLevel)
        assertEquals(LaneState.PASSING_LANE, target4.laneState)
        assertEquals(-1.5f, target4.lateralOffsetMeters!!, 0.01f)
    }

    @Test
    fun testWatchdogTimeout_resetsStateToDisconnectedError() {
        val payload = byteArrayOf(
            0x30.toByte(), 0x01.toByte(), 0x00.toByte(),
            0x10.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x05.toByte(), 0x00.toByte()
        )
        radarDevice.decodeBroadcastData(payload)
        assertEquals(RadarConnectionState.CONNECTED, RadarStateRepository.getState().connectionState)

        radarDevice.onWatchdogTimeout()

        val state = RadarStateRepository.getState()
        assertEquals(RadarConnectionState.DISCONNECTED_ERROR, state.connectionState)
        assertEquals(ThreatLevel.NONE, state.overallThreatLevel)
        assertTrue(state.targets.isEmpty())
        assertTrue(radarDevice.getActiveTargets().isEmpty())
    }

    @Test
    fun testHardwarePacket_preemptsActiveRadarTrafficSimulator() {
        RadarTrafficSimulator.start(RadarScenario.SINGLE_CAR)
        assertTrue(RadarTrafficSimulator.isSimulating)

        val payload = byteArrayOf(
            0x30.toByte(), 0x01.toByte(), 0x00.toByte(),
            0x10.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x05.toByte(), 0x00.toByte()
        )
        radarDevice.decodeBroadcastData(payload)

        // Verifies physical hardware packet halted simulation (REQ-CON-029.3, REQ-CON-032.6)
        assertFalse(RadarTrafficSimulator.isSimulating)
    }

    @Test
    fun testDecodeBroadcastData_systemStatusError_triggersWatchdog() {
        val validPayload = byteArrayOf(
            0x30.toByte(), 0x01.toByte(), 0x00.toByte(),
            0x10.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x05.toByte(), 0x00.toByte()
        )
        radarDevice.decodeBroadcastData(validPayload)
        assertEquals(RadarConnectionState.CONNECTED, RadarStateRepository.getState().connectionState)

        // Page 0x01 with state = 2 (Error)
        val statusErrorPayload = byteArrayOf(
            0x01.toByte(), 0x02.toByte(), 0, 0, 0, 0, 0, 0
        )
        radarDevice.decodeBroadcastData(statusErrorPayload)

        assertEquals(RadarConnectionState.DISCONNECTED_ERROR, RadarStateRepository.getState().connectionState)
    }

    @Test
    fun testDecodeBroadcastData_commonPages_handledCleanly() {
        // Page 0x50: Manufacturer
        val page50 = byteArrayOf(0x50.toByte(), 0, 0, 1, 1, 0, 10, 0)
        radarDevice.decodeBroadcastData(page50)

        // Page 0x51: Product Info
        val page51 = byteArrayOf(0x51.toByte(), 0, 1, 0x39.toByte(), 0x30.toByte(), 0, 0, 0)
        radarDevice.decodeBroadcastData(page51)

        // Page 0x52: Battery Status (voltage coarse 3V, frac 0.5V, status 2=Good -> 80%)
        // descriptiveByte = (2 << 4) | 3 = 0x23
        val page52 = byteArrayOf(0x52.toByte(), 0, 0, 0, 0, 0, 128.toByte(), 0x23.toByte())
        radarDevice.decodeBroadcastData(page52)
    }
}
