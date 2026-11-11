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
import android.util.Log
import com.atrainingtracker.banalservice.devices.radar.RadarLightController
import com.atrainingtracker.banalservice.devices.radar.RadarLightDeviceClient
import com.atrainingtracker.banalservice.devices.radar.RadarLightMode
import com.atrainingtracker.banalservice.devices.radar.RadarStateRepository
import com.dsi.ant.channel.AntChannel
import com.dsi.ant.message.ChannelId
import com.dsi.ant.message.ChannelType
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit test suite for ANTBikeLightDevice (TST-CON-033, REQ-CON-033).
 *
 * Verifies ANT+ Bike Lights profile (Device Type 35 / 0x23) channel configuration,
 * Page 0x01/0x02 decoding, 3-step state machine handshake (Connect 0x21, Settings 0x22,
 * Disconnect 0x20), and RadarLightController integration.
 */
class ANTBikeLightDeviceTest {

    private lateinit var mockContext: Context
    private lateinit var mockAntChannel: AntChannel
    private lateinit var lightDevice: ANTBikeLightDevice

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>(), any()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any()) } returns 0

        RadarLightController.reset()
        RadarStateRepository.reset()

        mockContext = mockk(relaxed = true)
        mockAntChannel = mockk(relaxed = true)

        lightDevice = ANTBikeLightDevice(mockContext, antDeviceNumber = 12345)
        lightDevice.antChannel = mockAntChannel
    }

    @After
    fun tearDown() {
        lightDevice.stop()
        RadarLightController.reset()
        RadarStateRepository.reset()
        unmockkAll()
    }

    @Test
    fun testChannelConstantsAndConfiguration_tstCon033_1() {
        assertEquals(35, AntChannelHelper.BIKE_LIGHT_DEVICE_TYPE)
        assertEquals(57, AntChannelHelper.BIKE_LIGHT_RF_FREQUENCY)
        assertEquals(4084, AntChannelHelper.BIKE_LIGHT_CHANNEL_PERIOD)

        val channelIdSlot = slot<ChannelId>()
        val channelTypeSlot = slot<ChannelType>()
        every { mockAntChannel.assign(capture(channelTypeSlot)) } returns Unit
        every { mockAntChannel.setChannelId(capture(channelIdSlot)) } returns Unit

        lightDevice.configureChannel(mockAntChannel)

        assertEquals(ChannelType.BIDIRECTIONAL_SLAVE, channelTypeSlot.captured)
        assertEquals(12345, channelIdSlot.captured.deviceNumber)
        assertEquals(35, channelIdSlot.captured.deviceType)
        verify { mockAntChannel.setPeriod(4084) }
        verify { mockAntChannel.setRfFrequency(57) }
        verify { mockAntChannel.open() }
    }

    @Test
    fun testDecodeBroadcastData_page0x01_unassociatedLight_tstCon033_2() {
        // Page 0x01: Byte 0=0x01, Byte 1: index=0 (unassociated), Byte 2: type=2 (Tail light), Byte 3: mode=0 (OFF)
        val payload = byteArrayOf(
            0x01.toByte(),
            0x00.toByte(), // Index 0
            0x02.toByte(), // Tail Light
            0x00.toByte(), // OFF
            0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x22.toByte()
        )

        lightDevice.decodeBroadcastData(payload)

        assertEquals(0, lightDevice.lightIndex)
        assertFalse(lightDevice.isAssociated)
        assertEquals(RadarLightMode.OFF, lightDevice.currentLightMode)
    }

    @Test
    fun testDecodeBroadcastData_page0x01_associatedLight_registersController_tstCon033_2() {
        // Page 0x01: Byte 1: index=1, Byte 2: type=2 (Tail light), Byte 3: mode=5 (DAY_FLASH)
        val payload = byteArrayOf(
            0x01.toByte(),
            0x01.toByte(), // Index 1
            0x02.toByte(), // Tail Light
            0x05.toByte(), // DAY_FLASH
            0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x22.toByte()
        )

        lightDevice.decodeBroadcastData(payload)

        assertEquals(1, lightDevice.lightIndex)
        assertTrue(lightDevice.isAssociated)
        assertEquals(RadarLightMode.DAY_FLASH, lightDevice.currentLightMode)
        assertTrue(RadarLightController.isLightControlAvailable.value)
        assertTrue(RadarStateRepository.getState().isLightControlSupported)
    }

    @Test
    fun testThreeStepHandshake_connectThenSettings_tstCon033_3() {
        val payloadSlot = slot<ByteArray>()
        every { mockAntChannel.startSendAcknowledgedData(capture(payloadSlot)) } returns Unit

        // Step 1: User requests DAY_FLASH while peripheral is unassociated (lightIndex == 0)
        val requested = lightDevice.setLightMode(RadarLightMode.DAY_FLASH)
        assertTrue(requested)
        assertEquals(RadarLightMode.DAY_FLASH, lightDevice.pendingTargetMode)

        // Verifies Page 0x21 (Connect Command) was sent with Index = 1 and Type = 2
        val connectPayload = payloadSlot.captured
        assertEquals(0x21.toByte(), connectPayload[0])
        assertEquals(0x01.toByte(), connectPayload[1]) // Light Index = 1
        assertEquals(0x02.toByte(), connectPayload[2]) // Tail Light = 2

        // Step 2: Peripheral confirms association via broadcast Page 0x01 with Light Index = 1
        val confirmPayload = byteArrayOf(
            0x01.toByte(),
            0x01.toByte(), // Confirmed Index = 1
            0x02.toByte(),
            0x01.toByte(), // Currently SOLID
            0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x22.toByte()
        )
        lightDevice.decodeBroadcastData(confirmPayload)

        // Verifies pending mode was automatically flushed via Page 0x22 (Settings Command)
        val settingsPayload = payloadSlot.captured
        assertEquals(0x22.toByte(), settingsPayload[0])
        assertEquals(0x05.toByte(), settingsPayload[2]) // Mode DAY_FLASH = 0x05
        assertEquals(RadarLightMode.DAY_FLASH, lightDevice.currentLightMode)
    }

    @Test
    fun testDisconnectCommand_tstCon033_3() {
        val payloadSlot = slot<ByteArray>()
        every { mockAntChannel.startSendAcknowledgedData(capture(payloadSlot)) } returns Unit

        // Setup associated state
        val confirmPayload = byteArrayOf(0x01.toByte(), 0x01.toByte(), 0x02.toByte(), 0x01.toByte(), 0, 0, 0, 0)
        lightDevice.decodeBroadcastData(confirmPayload)
        assertTrue(lightDevice.isAssociated)

        // Trigger disconnect
        val sent = lightDevice.sendDisconnectCommand()
        assertTrue(sent)
        assertFalse(lightDevice.isAssociated)
        assertEquals(0, lightDevice.lightIndex)

        val disconnectPayload = payloadSlot.captured
        assertEquals(0x20.toByte(), disconnectPayload[0])
        assertEquals(0x01.toByte(), disconnectPayload[1]) // Assigned Index
    }

    @Test
    fun testUnifiedControllerIntegration_tstCon033_4() {
        assertTrue(lightDevice is RadarLightDeviceClient)

        // Associate device
        val confirmPayload = byteArrayOf(0x01.toByte(), 0x01.toByte(), 0x02.toByte(), 0x01.toByte(), 0, 0, 0, 0)
        lightDevice.decodeBroadcastData(confirmPayload)

        val payloadSlot = slot<ByteArray>()
        every { mockAntChannel.startSendAcknowledgedData(capture(payloadSlot)) } returns Unit

        // Dispatch via global RadarLightController
        val success = RadarLightController.setLightMode(RadarLightMode.PELOTON)
        assertTrue(success)
        assertEquals(RadarLightMode.PELOTON, RadarLightController.currentMode.value)

        val settingsPayload = payloadSlot.captured
        assertEquals(0x22.toByte(), settingsPayload[0])
        assertEquals(0x02.toByte(), settingsPayload[2]) // PELOTON = 0x02
    }

    @Test
    fun testStop_releasesResourcesCleanly_tstCon033_5() {
        // Associate device
        val confirmPayload = byteArrayOf(0x01.toByte(), 0x01.toByte(), 0x02.toByte(), 0x01.toByte(), 0, 0, 0, 0)
        lightDevice.decodeBroadcastData(confirmPayload)
        assertTrue(RadarLightController.isLightControlAvailable.value)

        lightDevice.stop()

        assertFalse(lightDevice.isAssociated)
        assertFalse(RadarLightController.isLightControlAvailable.value)
        verify { mockAntChannel.close() }
        verify { mockAntChannel.release() }
    }
}
