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
import com.atrainingtracker.banalservice.devices.radar.RadarAudioSnoozeMode
import com.atrainingtracker.banalservice.devices.radar.RadarAudioSnoozeState
import com.atrainingtracker.banalservice.devices.radar.RadarConnectionState
import com.atrainingtracker.banalservice.devices.radar.RadarLightMode
import com.atrainingtracker.banalservice.devices.radar.RadarScenario
import com.atrainingtracker.banalservice.devices.radar.RadarSource
import com.atrainingtracker.banalservice.devices.radar.RadarState
import com.atrainingtracker.banalservice.devices.radar.RadarStateRepository
import com.atrainingtracker.banalservice.devices.radar.RadarTrafficSimulator
import com.atrainingtracker.banalservice.devices.radar.ThreatLevel
import com.atrainingtracker.banalservice.sensor.MySensorManager
import com.atrainingtracker.banalservice.sensor.SensorType
import com.dsi.ant.AntService
import com.dsi.ant.channel.AntChannel
import com.dsi.ant.channel.AntChannelProvider
import com.dsi.ant.channel.AntCommandFailedException
import com.dsi.ant.channel.IAntChannelEventHandler
import com.dsi.ant.message.ChannelId
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
        every { Log.w(any<String>(), any<String>(), any()) } returns 0
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

        val target0 = state.targets.find { it.rangeMeters == 62.5f }
        assertNotNull(target0)
        assertEquals(1, target0!!.targetId)
        assertEquals(62.5f, target0.rangeMeters, 0.01f)
        assertEquals(15.2f, target0.approachSpeedMps, 0.01f)
        assertEquals(ThreatLevel.APPROACHING, target0.threatLevel)
        assertEquals(LaneState.IN_RIDER_LANE, target0.laneState)
        assertEquals(0.0f, target0.lateralOffsetMeters!!, 0.01f)

        val target1 = state.targets.find { it.rangeMeters == 125.0f }
        assertNotNull(target1)
        assertEquals(2, target1!!.targetId)
        assertEquals(125.0f, target1.rangeMeters, 0.01f)
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
        assertEquals(1, target4.targetId)
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

    @Test
    fun testServiceConnection_usesAcquireAntPlusChannelAsync_andConfiguresChannel() {
        mockkConstructor(AntService::class)
        val mockProvider = mockk<AntChannelProvider>(relaxed = true)
        every { anyConstructed<AntService>().channelProvider } returns mockProvider
        every { mockProvider.numChannelsAvailable } returns 8

        val mockChannel = mockk<AntChannel>(relaxed = true)
        AntChannelHelper.setChannelAcquirerForTesting { _, _ -> mockChannel }

        radarDevice.serviceConnection.onServiceConnected(mockk(relaxed = true), mockk(relaxed = true))

        verify { mockChannel.assign(ChannelType.BIDIRECTIONAL_SLAVE) }
        verify { mockChannel.open() }
        assertEquals(mockChannel, radarDevice.antChannel)
    }

    @Test
    fun testPendingHandleCancellation_onShutDown_watchdog_and_disconnect() {
        val mockHandle = mockk<AntChannelHelper.AcquisitionCancellationHandle>(relaxed = true)
        radarDevice.channelAcquisitionHandle = mockHandle

        radarDevice.onWatchdogTimeout()
        verify(exactly = 1) { mockHandle.cancel() }
        assertNull(radarDevice.channelAcquisitionHandle)

        radarDevice.channelAcquisitionHandle = mockHandle
        radarDevice.serviceConnection.onServiceDisconnected(mockk(relaxed = true))
        verify(exactly = 2) { mockHandle.cancel() }
        assertNull(radarDevice.channelAcquisitionHandle)

        radarDevice.channelAcquisitionHandle = mockHandle
        radarDevice.shutDown()
        verify(exactly = 3) { mockHandle.cancel() }
        assertNull(radarDevice.channelAcquisitionHandle)
    }

    /**
     * TST-CON-038.2 / TST-CON-039.3: Tracking Channel Search Re-Open on Timeout Test.
     */
    @Test
    fun testRadarDevice_channelEvent_reOpensChannel_whenSearching() {
        val mockChannel = mockk<AntChannel>(relaxed = true)
        val handlerSlot = slot<IAntChannelEventHandler>()
        every { mockChannel.setChannelEventHandler(capture(handlerSlot)) } returns Unit

        radarDevice.antChannel = mockChannel
        radarDevice.startSearching()
        assertTrue(radarDevice.isSearching)

        radarDevice.configureChannel(mockChannel)
        assertTrue(handlerSlot.isCaptured)
        val handler = handlerSlot.captured

        mockkConstructor(ChannelEventMessage::class)
        every { anyConstructed<ChannelEventMessage>().eventCode } returns EventCode.RX_SEARCH_TIMEOUT

        val mockParcel = mockk<AntMessageParcel>(relaxed = true)
        every { mockParcel.messageId } returns 64
        every { mockParcel.messageContent } returns byteArrayOf(0, 1, 1)
        // Handler receives RX_SEARCH_TIMEOUT
        handler.onReceiveMessage(MessageFromAntType.CHANNEL_EVENT, mockParcel)

        // mockChannel.open() was called in startSearching(), configureChannel(), and now upon RX_SEARCH_TIMEOUT
        verify(atLeast = 3) { mockChannel.open() }

        // Handler receives CHANNEL_CLOSED
        every { anyConstructed<ChannelEventMessage>().eventCode } returns EventCode.CHANNEL_CLOSED
        handler.onReceiveMessage(MessageFromAntType.CHANNEL_EVENT, mockParcel)
        verify(atLeast = 4) { mockChannel.open() }

        // When data is received, isSearching becomes false
        val dummyPayload = byteArrayOf(0x30.toByte(), 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)
        mockkConstructor(com.dsi.ant.message.fromant.BroadcastDataMessage::class)
        every { anyConstructed<com.dsi.ant.message.fromant.BroadcastDataMessage>().payload } returns dummyPayload
        every { mockParcel.messageId } returns 78
        every { mockParcel.messageContent } returns ByteArray(9)

        handler.onReceiveMessage(MessageFromAntType.BROADCAST_DATA, mockParcel)
        assertFalse(radarDevice.isSearching)

        // Under REQ-CON-039.3, channel re-opens even in tracking mode (isSearching == false)
        every { mockParcel.messageId } returns 64
        every { mockParcel.messageContent } returns byteArrayOf(0, 1, 1)
        handler.onReceiveMessage(MessageFromAntType.CHANNEL_EVENT, mockParcel)
        verify(exactly = 5) { mockChannel.open() }

        // Once shutDown() is called, subsequent channel events are dropped
        radarDevice.shutDown()
        handler.onReceiveMessage(MessageFromAntType.CHANNEL_EVENT, mockParcel)
        verify(exactly = 5) { mockChannel.open() }
    }

    /**
     * TST-CON-039.1: Shutdown State Cleanup & Repository Disconnect Test.
     */
    @Test
    fun testRadarDevice_shutDown_clearsTargets_andSetsDisconnectedState() {
        val payload = byteArrayOf(
            0x30.toByte(),
            0x01.toByte(), // Threat level 1 (APPROACHING)
            0x00.toByte(), // Side 0
            0x50.toByte(), 0x00.toByte(), 0x00.toByte(), // Range 80m
            0x10.toByte(), // Speed 16 m/s
            0x00.toByte()
        )
        radarDevice.decodeBroadcastData(payload)

        // Verify active target and CONNECTED state prior to shutdown
        assertEquals(1, radarDevice.getActiveTargets().size)
        assertEquals(RadarConnectionState.CONNECTED, RadarStateRepository.getState().connectionState)

        radarDevice.shutDown()

        assertTrue(radarDevice.isShutDown)
        assertTrue(radarDevice.getActiveTargets().isEmpty())
        val finalState = RadarStateRepository.getState()
        assertEquals(RadarConnectionState.DISCONNECTED, finalState.connectionState)
        assertEquals(ThreatLevel.NONE, finalState.overallThreatLevel)
        assertTrue(finalState.targets.isEmpty())
    }

    /**
     * TST-CON-039.2: Main Thread Ingestion & Concurrency Serialization Test.
     */
    @Test
    fun testRadarDevice_onReceiveMessage_postsToHandler_andSerializesState() {
        val mockChannel = mockk<AntChannel>(relaxed = true)
        val handlerSlot = slot<IAntChannelEventHandler>()
        every { mockChannel.setChannelEventHandler(capture(handlerSlot)) } returns Unit
        radarDevice.configureChannel(mockChannel)
        val handler = handlerSlot.captured

        val dummyPayload = byteArrayOf(0x30.toByte(), 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)
        mockkConstructor(com.dsi.ant.message.fromant.BroadcastDataMessage::class)
        every { anyConstructed<com.dsi.ant.message.fromant.BroadcastDataMessage>().payload } returns dummyPayload

        val mockParcel = mockk<AntMessageParcel>(relaxed = true)
        every { mockParcel.messageId } returns 78
        every { mockParcel.messageContent } returns ByteArray(9)

        handler.onReceiveMessage(MessageFromAntType.BROADCAST_DATA, mockParcel)

        // Verifies handler.post was called to process the broadcast data
        verify(atLeast = 1) { anyConstructed<Handler>().post(any()) }

        // Test onChannelDeath posts to handler as well
        handler.onChannelDeath()
        verify(atLeast = 2) { anyConstructed<Handler>().post(any()) }
    }

    /**
     * TST-CON-039.3: Tracking Mode Channel-Close Reconnect Test.
     */
    @Test
    fun testRadarDevice_channelEvent_reOpensChannel_whenTracking() {
        val mockChannel = mockk<AntChannel>(relaxed = true)
        val handlerSlot = slot<IAntChannelEventHandler>()
        every { mockChannel.setChannelEventHandler(capture(handlerSlot)) } returns Unit

        radarDevice.antChannel = mockChannel
        radarDevice.configureChannel(mockChannel)
        val handler = handlerSlot.captured

        // Transition out of searching mode (isSearching = false)
        val dummyPayload = byteArrayOf(0x30.toByte(), 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)
        mockkConstructor(com.dsi.ant.message.fromant.BroadcastDataMessage::class)
        every { anyConstructed<com.dsi.ant.message.fromant.BroadcastDataMessage>().payload } returns dummyPayload

        val dataParcel = mockk<AntMessageParcel>(relaxed = true)
        every { dataParcel.messageId } returns 78
        every { dataParcel.messageContent } returns ByteArray(9)
        handler.onReceiveMessage(MessageFromAntType.BROADCAST_DATA, dataParcel)
        assertFalse(radarDevice.isSearching)

        val openCallsBefore = 1 // from configureChannel()
        mockkConstructor(ChannelEventMessage::class)
        val eventParcel = mockk<AntMessageParcel>(relaxed = true)
        every { eventParcel.messageId } returns 64
        every { eventParcel.messageContent } returns byteArrayOf(0, 1, 1)

        // EventCode.RX_SEARCH_TIMEOUT while tracking
        every { anyConstructed<ChannelEventMessage>().eventCode } returns EventCode.RX_SEARCH_TIMEOUT
        handler.onReceiveMessage(MessageFromAntType.CHANNEL_EVENT, eventParcel)
        verify(exactly = openCallsBefore + 1) { mockChannel.open() }

        // EventCode.CHANNEL_CLOSED while tracking
        every { anyConstructed<ChannelEventMessage>().eventCode } returns EventCode.CHANNEL_CLOSED
        handler.onReceiveMessage(MessageFromAntType.CHANNEL_EVENT, eventParcel)
        verify(exactly = openCallsBefore + 2) { mockChannel.open() }
    }

    /**
     * TST-CON-039.4: Post-Shutdown Message Ingestion Suppression Test.
     */
    @Test
    fun testRadarDevice_postShutdown_dropsMessagesAndEvents() {
        val mockChannel = mockk<AntChannel>(relaxed = true)
        val handlerSlot = slot<IAntChannelEventHandler>()
        every { mockChannel.setChannelEventHandler(capture(handlerSlot)) } returns Unit

        radarDevice.antChannel = mockChannel
        radarDevice.configureChannel(mockChannel)
        val handler = handlerSlot.captured

        radarDevice.shutDown()
        assertTrue(radarDevice.isShutDown)
        assertEquals(RadarConnectionState.DISCONNECTED, RadarStateRepository.getState().connectionState)

        // Attempt BROADCAST_DATA post-shutdown
        val dummyPayload = byteArrayOf(0x30.toByte(), 0x01, 0x00, 0x50, 0x00, 0x00, 0x10, 0x00)
        mockkConstructor(com.dsi.ant.message.fromant.BroadcastDataMessage::class)
        every { anyConstructed<com.dsi.ant.message.fromant.BroadcastDataMessage>().payload } returns dummyPayload

        val dataParcel = mockk<AntMessageParcel>(relaxed = true)
        every { dataParcel.messageId } returns 78
        every { dataParcel.messageContent } returns ByteArray(9)

        handler.onReceiveMessage(MessageFromAntType.BROADCAST_DATA, dataParcel)
        assertTrue(radarDevice.getActiveTargets().isEmpty())
        assertEquals(RadarConnectionState.DISCONNECTED, RadarStateRepository.getState().connectionState)

        // Attempt CHANNEL_EVENT post-shutdown
        mockkConstructor(ChannelEventMessage::class)
        every { anyConstructed<ChannelEventMessage>().eventCode } returns EventCode.CHANNEL_CLOSED
        val eventParcel = mockk<AntMessageParcel>(relaxed = true)
        every { eventParcel.messageId } returns 64
        every { eventParcel.messageContent } returns byteArrayOf(0, 1, 1)

        handler.onReceiveMessage(MessageFromAntType.CHANNEL_EVENT, eventParcel)
        // No new open() calls occurred
        verify(exactly = 1) { mockChannel.open() }

        // Attempt onChannelDeath post-shutdown
        handler.onChannelDeath()
        assertEquals(RadarConnectionState.DISCONNECTED, RadarStateRepository.getState().connectionState)
    }

    /**
     * TST-CON-038.4: Safe LibConfig Exception Tolerance in configureChannel.
     */
    @Test
    fun testConfigureChannel_continuesWhenLibConfigThrowsAntCommandFailedException() {
        val mockChannel = mockk<AntChannel>(relaxed = true)
        val mockCmdException = mockk<AntCommandFailedException>(relaxed = true)
        every { mockCmdException.message } returns "LibConfig already set"
        every { mockChannel.setAdapterWideLibConfig(any()) } throws mockCmdException

        radarDevice.configureChannel(mockChannel)

        // Verifies channel configuration completed and open() was still invoked
        verify(exactly = 1) { mockChannel.open() }
    }

    /**
     * TST-CON-040.1: ANT+ Track Stability Across Slot Shifts Test.
     * Given two approaching vehicles where the closest passes and slot indices shift,
     * When ANTBikeRadarDevice decodes consecutive frames,
     * Then the remaining vehicle retains its track identity and only one pass is counted.
     */
    @Test
    fun testTrackContinuity_acrossSlotShifts_retainsIdentityAndSinglePass() {
        // Frame 1: Car A at 6.25m in slot 0, Car B at 15.625m in slot 1
        // Threat byte: 0x01 | (0x01 << 2) = 0x05
        // Side byte: 0x00
        // Range 24-bit: 2 | (5 << 6) = 2 | 320 = 322 (0x42, 0x01, 0x00)
        // Speed 16-bit: 4 | (4 << 4) = 0x44 (0x44, 0x00)
        val frame1 = byteArrayOf(
            0x30.toByte(),
            0x05.toByte(),
            0x00.toByte(),
            0x42.toByte(), 0x01.toByte(), 0x00.toByte(),
            0x44.toByte(), 0x00.toByte()
        )
        radarDevice.decodeBroadcastData(frame1)

        val state1 = RadarStateRepository.getState()
        assertEquals(2, state1.targets.size)
        val carA = state1.targets.find { it.rangeMeters == 6.25f }
        val carB = state1.targets.find { it.rangeMeters == 15.625f }
        assertNotNull(carA)
        assertNotNull(carB)
        val carATrackId = carA!!.targetId
        val carBTrackId = carB!!.targetId
        assertTrue(carATrackId != carBTrackId)

        // Frame 2: Car A has passed and disappeared. Car B shifts to slot 0 at 12.5m (rawRange = 4)
        // Threat byte: 0x01
        // Side byte: 0x00
        // Range 24-bit: 4 (0x04, 0x00, 0x00)
        // Speed 16-bit: 4 (0x04, 0x00)
        val frame2 = byteArrayOf(
            0x30.toByte(),
            0x01.toByte(),
            0x00.toByte(),
            0x04.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x04.toByte(), 0x00.toByte()
        )
        radarDevice.decodeBroadcastData(frame2)

        val state2 = RadarStateRepository.getState()
        assertEquals(1, state2.targets.size)
        val remainingCar = state2.targets.first()
        // Car B MUST retain its persistent track ID despite slot shift from slot 1 to slot 0
        assertEquals("Car B must retain its persistent trackId across slot shift", carBTrackId, remainingCar.targetId)
        assertEquals(12.5f, remainingCar.rangeMeters, 0.01f)

        // RadarWindowAggregator must increment vehicle count strictly once (for Car A's pass)
        assertEquals(1, radarDevice.getWindowAggregator().getVehicleCount())
    }

    /**
     * TST-CON-040.2: 0-Bin Target Ingestion and Lateral Latch Test.
     * Given an ANT+ target frame with threatLevelBits > 0 and rawRange == 0,
     * When decoded by ANTBikeRadarDevice,
     * Then the vehicle is retained at 0.0m range and its lateral passing clearance is recorded.
     */
    @Test
    fun testZeroBinTargetIngestion_decodesAtZeroDistanceAndLatchesClearance() {
        // Page 0x30 with rawRange = 0, threatLevel = 1 (APPROACHING), threatSide = 1 (RIGHT lane -> lateralOffset = 1.5m)
        // Threat byte: 0x01
        // Side byte: 0x01
        // Range: 0x00, 0x00, 0x00 (0.0m)
        // Speed: 0x03, 0x00 (rawSpeed = 3 -> 9.12 m/s)
        val zeroBinPayload = byteArrayOf(
            0x30.toByte(),
            0x01.toByte(),
            0x01.toByte(),
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x03.toByte(), 0x00.toByte()
        )
        radarDevice.decodeBroadcastData(zeroBinPayload)

        val state = RadarStateRepository.getState()
        assertEquals(1, state.targets.size)
        val target = state.targets.first()
        assertEquals(0.0f, target.rangeMeters, 0.001f)
        assertEquals(1.5f, target.lateralOffsetMeters!!, 0.01f)
        assertEquals(ThreatLevel.APPROACHING, target.threatLevel)

        // Next frame: road clears (target exits)
        val clearPayload = byteArrayOf(0x30.toByte(), 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)
        radarDevice.decodeBroadcastData(clearPayload)

        // Window snapshot must latch the passing distance of 1.5m
        val snapshot = radarDevice.getWindowAggregator().sampleAndResetWindow()
        assertNotNull("Passing distance must be latched for 0-bin target", snapshot.passingDistanceMeters)
        assertEquals(1.5, snapshot.passingDistanceMeters!!, 0.001)
        assertEquals(1, radarDevice.getWindowAggregator().getVehicleCount())
    }

    /**
     * TST-CON-040.4: 1 Hz Driver Gating for Scalar Metric Sampling (ANTBikeRadarDevice).
     * Deliver 8 consecutive frames spaced 125 ms apart and verify sampleAndResetWindow/newValue execute at 1 Hz cadence.
     */
    @Test
    fun testANTBikeRadarDevice_oneHzDriverGating_limitsScalarSensorUpdates() {
        var currentTimeMs = 1000L
        every { android.os.SystemClock.elapsedRealtime() } answers { currentTimeMs }

        // Initial sensor state
        assertNull(radarDevice.getSensor(SensorType.RADAR_THREAT_COUNT).value)

        // Target packet: 1 approaching target at 21.875m
        val packet = byteArrayOf(
            0x30.toByte(),
            0x01.toByte(),
            0x00.toByte(),
            0x07.toByte(), 0x00.toByte(), 0x00.toByte(), // rawRange = 7 -> 21.875m
            0x04.toByte(), 0x00.toByte()
        )

        // Frame 1 at t = 1000ms: first frame triggers 1 Hz sample (lastWindowSampleMs = 0 -> nowMs = 1000)
        radarDevice.decodeBroadcastData(packet)
        assertEquals(1, radarDevice.getSensor(SensorType.RADAR_THREAT_COUNT).value)

        // Clear packet
        val clearPacket = byteArrayOf(0x30.toByte(), 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)

        // Frames 2..7 spaced 125ms apart (t = 1125, 1250, 1375, 1500, 1625, 1750, 1875)
        for (i in 1..7) {
            currentTimeMs += 125L
            radarDevice.decodeBroadcastData(clearPacket)
            // Scalar sensors MUST NOT update because < 1000ms elapsed since last sample at 1000ms!
            // Threat count sensor remains 1 (retained from t = 1000ms)
            assertEquals(
                "Sensor must not update during sub-second cadence at t=$currentTimeMs",
                1,
                radarDevice.getSensor(SensorType.RADAR_THREAT_COUNT).value
            )
        }

        // Frame 8 at t = 2001ms (rolling 1,000 ms window expired and 1 Hz sample tick elapsed)
        currentTimeMs = 2001L
        radarDevice.decodeBroadcastData(clearPacket)
        // Now 1 Hz window samples again, reflecting the clear road (threat count -> 0)
        assertEquals(0, radarDevice.getSensor(SensorType.RADAR_THREAT_COUNT).value)
    }

    /**
     * TST-CON-041.2: ANTBikeRadarDevice isReceivingData Source Isolation Test.
     * Verifies isReceivingData() queries only RadarSource.ANT_PLUS state and is unaffected by BLE.
     */
    @Test
    fun testIsReceivingData_isolatedToAntPlusSource() {
        // BLE is CONNECTED in repository
        RadarStateRepository.updateState(
            RadarState(connectionState = RadarConnectionState.CONNECTED, source = RadarSource.BLE)
        )
        // ANT+ has not received data, so isolated query must return false despite global CONNECTED
        assertFalse("isReceivingData must be false when ANT+ has not connected", radarDevice.isReceivingData())

        // ANT+ receives packet and publishes CONNECTED
        val validPayload = byteArrayOf(
            0x30.toByte(), 0x01.toByte(), 0x00.toByte(),
            0x10.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x05.toByte(), 0x00.toByte()
        )
        radarDevice.decodeBroadcastData(validPayload)
        assertTrue("isReceivingData must be true when ANT+ is connected", radarDevice.isReceivingData())

        // ANT+ watchdog times out and publishes DISCONNECTED_ERROR
        radarDevice.onWatchdogTimeout()
        assertFalse("isReceivingData must be false after ANT+ watchdog timeout", radarDevice.isReceivingData())
        // Global repository remains CONNECTED on BLE
        assertEquals(RadarConnectionState.CONNECTED, RadarStateRepository.getState().connectionState)
    }

    /**
     * TST-CON-044.3: ANTBikeRadarDevice Multi-Instance Isolation Test (ATT-8109).
     * Verifies that when multiple ANT+ radars are configured, only the instance actively
     * receiving broadcast packets within the watchdog window reports isReceivingData() == true;
     * a silent peer instance reports false even though RadarSource.ANT_PLUS is CONNECTED.
     */
    @Test
    fun testMultiInstanceAntRadar_silentDeviceReturnsFalse() {
        val antRadar2 = ANTBikeRadarDevice(
            context = mockContext,
            mySensorManager = mockSensorManager,
            deviceId = 2L,
            antDeviceNumber = 54321
        )

        val validPayload = byteArrayOf(
            0x30.toByte(), 0x01.toByte(), 0x00.toByte(),
            0x10.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x05.toByte(), 0x00.toByte()
        )
        radarDevice.decodeBroadcastData(validPayload)

        assertEquals(
            RadarConnectionState.CONNECTED,
            RadarStateRepository.getState(RadarSource.ANT_PLUS).connectionState
        )

        assertTrue("Active ANT+ radar must return isReceivingData == true", radarDevice.isReceivingData())
        assertFalse("Silent peer ANT+ radar must return isReceivingData == false", antRadar2.isReceivingData())
    }

    /**
     * TST-CON-042.2 & TST-CON-042.4: ANT+ Packet Ingestion Preserves Snooze & Light Mode.
     * Verifies parseTargetsPage() and publishTargets() retain active audioSnoozeState and lightMode,
     * and bypass simulator preemption when isSimulating is false.
     */
    @Test
    fun testDecodeBroadcastData_preservesAudioSnoozeAndLightMode() {
        // Pre-configure active audio snooze and light mode in repository
        val activeSnooze = RadarAudioSnoozeState(isSnoozed = true, mode = RadarAudioSnoozeMode.SNOOZE_30M)
        RadarStateRepository.updateState(
            RadarState(
                connectionState = RadarConnectionState.DISCONNECTED,
                lightMode = RadarLightMode.PELOTON,
                isLightControlSupported = true,
                audioSnoozeState = activeSnooze,
                source = RadarSource.ANT_PLUS
            )
        )

        // Ensure simulator is not running
        assertFalse(RadarTrafficSimulator.isSimulating)

        // ANT+ receives packet and publishes CONNECTED
        val validPayload = byteArrayOf(
            0x30.toByte(), 0x01.toByte(), 0x00.toByte(),
            0x10.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x05.toByte(), 0x00.toByte()
        )
        radarDevice.decodeBroadcastData(validPayload)

        // Verify repository retains active snooze and light mode
        val state = RadarStateRepository.getState()
        assertEquals(RadarConnectionState.CONNECTED, state.connectionState)
        assertEquals(RadarLightMode.PELOTON, state.lightMode)
        assertTrue(state.audioSnoozeState.isSnoozed)
        assertEquals(RadarAudioSnoozeMode.SNOOZE_30M, state.audioSnoozeState.mode)
        assertEquals(RadarSource.ANT_PLUS, state.source)
    }
}
