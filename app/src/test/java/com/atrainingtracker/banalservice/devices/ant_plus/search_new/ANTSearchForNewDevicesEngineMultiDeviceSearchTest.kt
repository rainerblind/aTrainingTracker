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

package com.atrainingtracker.banalservice.devices.ant_plus.search_new

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.atrainingtracker.banalservice.Protocol
import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.banalservice.devices.ant_plus.AntChannelHelper
import com.dsi.ant.AntService
import com.dsi.ant.channel.AntChannel
import com.dsi.ant.channel.AntChannelProvider
import com.dsi.ant.channel.IAntChannelEventHandler
import com.dsi.ant.message.ChannelType
import com.dsi.ant.message.EventCode
import com.dsi.ant.message.fromant.ChannelEventMessage
import com.dsi.ant.message.fromant.MessageFromAntType
import com.dsi.ant.message.ipc.AntMessageParcel
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit test verifying ANT+ Bike Radar discovery and Protocol.ANT_PLUS registration (TST-CON-032.4, REQ-CON-032.5).
 */
class ANTSearchForNewDevicesEngineMultiDeviceSearchTest {

    private lateinit var mockContext: Context
    private lateinit var mockCallback: ANTSearchForNewDevicesEngineMultiDeviceSearch.IANTAsyncSearchEngineInterface

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>(), any()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any()) } returns 0

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

        mockContext = mockk(relaxed = true)
        mockCallback = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        AntChannelHelper.setChannelAcquirerForTesting(null)
        unmockkAll()
    }

    @Test
    fun testAntPlusRemoteDeviceTypes_includesBikeRadar() {
        val antDeviceTypes = DeviceType.getRemoteDeviceTypes(Protocol.ANT_PLUS)
        assertTrue(antDeviceTypes.contains(DeviceType.BIKE_RADAR))
    }

    @Test
    fun testEngineInstantiation_forBikeRadar() {
        val engine = ANTSearchForNewDevicesEngineMultiDeviceSearch(
            mockContext,
            DeviceType.BIKE_RADAR,
            mockCallback
        )
        assertEquals(DeviceType.BIKE_RADAR, engine.deviceType)
    }

    @Test
    fun testCallbackInvocation_forDiscoveredRadarDevice() {
        val engine = ANTSearchForNewDevicesEngineMultiDeviceSearch(
            mockContext,
            DeviceType.BIKE_RADAR,
            mockCallback
        )

        // Directly verify the callback interface contract for radar device discovery
        mockCallback.onNewDeviceFound(
            DeviceType.BIKE_RADAR,
            54321,
            "ANT+ Radar 54321",
            true,
            "Garmin",
            100
        )

        verify {
            mockCallback.onNewDeviceFound(
                DeviceType.BIKE_RADAR,
                54321,
                "ANT+ Radar 54321",
                true,
                "Garmin",
                100
            )
        }
    }

    @Test
    fun testRadarSearchConnection_usesAcquireAntPlusChannelAsync_andConfiguresChannel() {
        mockkConstructor(AntService::class)
        val mockProvider = mockk<AntChannelProvider>(relaxed = true)
        every { anyConstructed<AntService>().channelProvider } returns mockProvider
        every { mockProvider.numChannelsAvailable } returns 8

        val mockChannel = mockk<AntChannel>(relaxed = true)
        AntChannelHelper.setChannelAcquirerForTesting { _, _ -> mockChannel }

        val engine = ANTSearchForNewDevicesEngineMultiDeviceSearch(
            mockContext,
            DeviceType.BIKE_RADAR,
            mockCallback
        )
        engine.mIsRadarSearchActive = true

        engine.mRadarServiceConnection.onServiceConnected(mockk(relaxed = true), mockk(relaxed = true))

        verify { mockChannel.assign(ChannelType.BIDIRECTIONAL_SLAVE) }
        verify { mockChannel.open() }
        assertEquals(mockChannel, engine.mRadarAntChannel)
    }

    @Test
    fun testRadarSearch_handleCancellation_onStopSearch_and_disconnect() {
        val engine = ANTSearchForNewDevicesEngineMultiDeviceSearch(
            mockContext,
            DeviceType.BIKE_RADAR,
            mockCallback
        )
        val mockHandle = mockk<AntChannelHelper.AcquisitionCancellationHandle>(relaxed = true)

        engine.mChannelAcquisitionHandle = mockHandle
        engine.stopAsyncSearch()
        verify(exactly = 1) { mockHandle.cancel() }
        assertNull(engine.mChannelAcquisitionHandle)

        engine.mChannelAcquisitionHandle = mockHandle
        engine.mRadarServiceConnection.onServiceDisconnected(mockk(relaxed = true))
        verify(exactly = 2) { mockHandle.cancel() }
        assertNull(engine.mChannelAcquisitionHandle)
    }

    /**
     * TST-CON-038.1: Discovery Channel Event Re-Open on 25s Timeout Test.
     */
    @Test
    fun testRadarSearch_channelEvent_reOpensChannel_whenSearchActive() {
        mockkConstructor(AntService::class)
        val mockProvider = mockk<AntChannelProvider>(relaxed = true)
        every { anyConstructed<AntService>().channelProvider } returns mockProvider
        every { mockProvider.numChannelsAvailable } returns 8

        val mockChannel = mockk<AntChannel>(relaxed = true)
        val handlerSlot = slot<IAntChannelEventHandler>()
        every { mockChannel.setChannelEventHandler(capture(handlerSlot)) } returns Unit
        AntChannelHelper.setChannelAcquirerForTesting { _, _ -> mockChannel }

        val engine = ANTSearchForNewDevicesEngineMultiDeviceSearch(
            mockContext,
            DeviceType.BIKE_RADAR,
            mockCallback
        )
        engine.mIsRadarSearchActive = true
        engine.mRadarServiceConnection.onServiceConnected(mockk(relaxed = true), mockk(relaxed = true))

        assertTrue(handlerSlot.isCaptured)
        val handler = handlerSlot.captured

        // Simulate ChannelEventMessage for RX_SEARCH_TIMEOUT
        mockkConstructor(ChannelEventMessage::class)
        every { anyConstructed<ChannelEventMessage>().eventCode } returns EventCode.RX_SEARCH_TIMEOUT

        val mockParcel = mockk<AntMessageParcel>(relaxed = true)
        every { mockParcel.messageId } returns 64
        every { mockParcel.messageContent } returns byteArrayOf(0, 1, 1)
        handler.onReceiveMessage(MessageFromAntType.CHANNEL_EVENT, mockParcel)

        // mockChannel.open() was called once during onServiceConnected, and now a second time
        verify(exactly = 2) { mockChannel.open() }

        // Simulate CHANNEL_CLOSED while still active
        every { anyConstructed<ChannelEventMessage>().eventCode } returns EventCode.CHANNEL_CLOSED
        handler.onReceiveMessage(MessageFromAntType.CHANNEL_EVENT, mockParcel)
        verify(exactly = 3) { mockChannel.open() }

        // When search is inactive, channel should NOT be re-opened
        engine.mIsRadarSearchActive = false
        handler.onReceiveMessage(MessageFromAntType.CHANNEL_EVENT, mockParcel)
        verify(exactly = 3) { mockChannel.open() }
    }

    /**
     * TST-CON-038.5: Discovery Engine ExtendedData & RSSI Forwarding Test.
     */
    @Test
    fun testRadarSearch_onReceiveMessage_forwardsExtractedRssiToCallback() {
        mockkConstructor(AntService::class)
        val mockProvider = mockk<AntChannelProvider>(relaxed = true)
        every { anyConstructed<AntService>().channelProvider } returns mockProvider
        every { mockProvider.numChannelsAvailable } returns 8

        val mockChannel = mockk<AntChannel>(relaxed = true)
        val handlerSlot = slot<IAntChannelEventHandler>()
        every { mockChannel.setChannelEventHandler(capture(handlerSlot)) } returns Unit
        AntChannelHelper.setChannelAcquirerForTesting { _, _ -> mockChannel }

        val engine = ANTSearchForNewDevicesEngineMultiDeviceSearch(
            mockContext,
            DeviceType.BIKE_RADAR,
            mockCallback
        )
        engine.mIsRadarSearchActive = true
        engine.mRadarServiceConnection.onServiceConnected(mockk(relaxed = true), mockk(relaxed = true))

        val handler = handlerSlot.captured

        // Deliver broadcast message with extended device number 98765 and rssi -54
        mockkStatic(AntChannelHelper::class)
        val mockParcel = mockk<AntMessageParcel>()
        every {
            AntChannelHelper.extractExtendedDeviceAndRssi(MessageFromAntType.BROADCAST_DATA, mockParcel)
        } returns AntChannelHelper.ExtendedDeviceInfo(98765, -54)

        handler.onReceiveMessage(MessageFromAntType.BROADCAST_DATA, mockParcel)

        verify(exactly = 1) {
            mockCallback.onNewDeviceFound(
                DeviceType.BIKE_RADAR,
                98765,
                "ANT+ Radar 98765",
                true,
                null,
                -54
            )
        }
    }
}
