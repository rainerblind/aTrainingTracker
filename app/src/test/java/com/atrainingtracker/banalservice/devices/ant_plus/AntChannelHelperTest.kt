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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.dsi.ant.channel.AntChannel
import com.dsi.ant.channel.AntChannelProvider
import com.dsi.ant.channel.AntCommandFailedException
import com.dsi.ant.channel.Capabilities
import com.dsi.ant.channel.ChannelNotAvailableException
import com.dsi.ant.channel.ChannelNotAvailableReason
import com.dsi.ant.message.ChannelId
import com.dsi.ant.message.ChannelType
import com.dsi.ant.message.ExtendedAssignment
import com.dsi.ant.message.ExtendedData
import com.dsi.ant.message.LibConfig
import com.dsi.ant.message.Rssi
import com.dsi.ant.message.fromant.AcknowledgedDataMessage
import com.dsi.ant.message.fromant.BroadcastDataMessage
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
 * Unit test suite for AntChannelHelper asynchronous channel acquisition
 * and race condition resilience (TST-CON-037.1..TST-CON-037.5, REQ-CON-037).
 */
class AntChannelHelperTest {

    private lateinit var mockContext: Context
    private lateinit var mockProvider: AntChannelProvider
    private lateinit var mockChannel: AntChannel
    private lateinit var mockListener: AntChannelHelper.ChannelAcquisitionListener
    private lateinit var mockHandler: Handler

    private val postedRunnables = mutableListOf<Pair<Runnable, Long>>()
    private val removedRunnables = mutableListOf<Runnable>()

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
        mockkStatic(Looper::class)

        mockkConstructor(Intent::class)
        every { anyConstructed<Intent>().action } returns AntChannelProvider.ACTION_CHANNEL_PROVIDER_STATE_CHANGED
        every { anyConstructed<Intent>().getIntExtra(any(), any()) } returns 8

        mockkConstructor(IntentFilter::class)
        every { anyConstructed<IntentFilter>().hasAction(any()) } answers {
            firstArg<String>() == AntChannelProvider.ACTION_CHANNEL_PROVIDER_STATE_CHANGED
        }

        val mockLooper = mockk<Looper>(relaxed = true)
        every { Looper.getMainLooper() } returns mockLooper

        mockContext = mockk(relaxed = true)
        mockProvider = mockk(relaxed = true)
        mockChannel = mockk(relaxed = true)
        mockListener = mockk(relaxed = true)
        mockHandler = mockk(relaxed = true)

        postedRunnables.clear()
        removedRunnables.clear()

        every { mockHandler.postDelayed(any(), any()) } answers {
            val runnable = firstArg<Runnable>()
            val delay = secondArg<Long>()
            postedRunnables.add(Pair(runnable, delay))
            true
        }

        every { mockHandler.removeCallbacks(any<Runnable>()) } answers {
            val runnable = firstArg<Runnable>()
            removedRunnables.add(runnable)
        }

        every { ContextCompat.registerReceiver(any(), any(), any(), any()) } returns null
        every { mockContext.unregisterReceiver(any()) } returns Unit
    }

    @After
    fun tearDown() {
        AntChannelHelper.setChannelAcquirerForTesting(null)
        unmockkAll()
    }

    /**
     * TST-CON-037.1: Immediate Synchronous Channel Acquisition Test.
     * When provider.getNumChannelsAvailable() > 0 and acquireAntPlusChannel succeeds immediately,
     * assert immediate invocation of onChannelAcquired(channel) without registering a BroadcastReceiver.
     */
    @Test
    fun testAcquireAntPlusChannelAsync_immediateSuccess_invokesListenerWithoutReceiver() {
        every { mockProvider.numChannelsAvailable } returns 8
        AntChannelHelper.setChannelAcquirerForTesting { _, _ -> mockChannel }

        val handle = AntChannelHelper.acquireAntPlusChannelAsync(
            mockContext,
            mockProvider,
            mockListener,
            mockHandler
        )

        assertNotNull(handle)
        verify(exactly = 1) { mockListener.onChannelAcquired(mockChannel) }
        verify(exactly = 0) { mockListener.onAcquisitionFailed(any()) }
        verify(exactly = 0) { ContextCompat.registerReceiver(any(), any(), any(), any()) }
        verify(exactly = 0) { mockHandler.postDelayed(any(), any()) }
    }

    /**
     * TST-CON-037.2: Cold-Start Broadcast-Triggered Channel Acquisition Test.
     * When initial acquisition fails with SERVICE_INITIALIZING, registers receiver for
     * ACTION_CHANNEL_PROVIDER_STATE_CHANGED with RECEIVER_EXPORTED.
     * When broadcast arrives with channels available, acquires channel, invokes listener,
     * and cleanly unregisters receiver.
     */
    @Test
    fun testAcquireAntPlusChannelAsync_coldStartBroadcast_acquiresAndUnregisters() {
        every { mockProvider.numChannelsAvailable } returns 0
        AntChannelHelper.setChannelAcquirerForTesting { _, _ -> mockChannel }

        val receiverSlot = slot<BroadcastReceiver>()
        val filterSlot = slot<IntentFilter>()
        val flagsSlot = slot<Int>()

        every {
            ContextCompat.registerReceiver(
                eq(mockContext),
                capture(receiverSlot),
                capture(filterSlot),
                capture(flagsSlot)
            )
        } returns null

        AntChannelHelper.acquireAntPlusChannelAsync(
            mockContext,
            mockProvider,
            mockListener,
            mockHandler
        )

        // Verify receiver registration with RECEIVER_EXPORTED (REQ-CON-037.2, REQ-CON-037.4)
        assertTrue(receiverSlot.isCaptured)
        assertEquals(ContextCompat.RECEIVER_EXPORTED, flagsSlot.captured)
        assertTrue(filterSlot.captured.hasAction(AntChannelProvider.ACTION_CHANNEL_PROVIDER_STATE_CHANGED))
        verify(exactly = 0) { mockListener.onChannelAcquired(any()) }

        // Simulate UsbAntService completing initialization and reporting available channels
        every { mockProvider.numChannelsAvailable } returns 8

        // Simulate broadcast intent from AntRadioService
        val broadcastIntent = mockk<Intent>(relaxed = true)
        every { broadcastIntent.action } returns AntChannelProvider.ACTION_CHANNEL_PROVIDER_STATE_CHANGED
        broadcastIntent.putExtra(AntChannelProvider.NUM_CHANNELS_AVAILABLE, 8)
        receiverSlot.captured.onReceive(mockContext, broadcastIntent)

        // Verify channel acquired and receiver unregistered
        verify(exactly = 1) { mockListener.onChannelAcquired(mockChannel) }
        verify(exactly = 1) { mockContext.unregisterReceiver(receiverSlot.captured) }
    }

    /**
     * TST-CON-037.3: Bounded Retry Backoff Fallback Test.
     * Simulates SERVICE_INITIALIZING with delayed broadcast; verifies scheduled retry runnable
     * executes channel acquisition attempt.
     */
    @Test
    fun testAcquireAntPlusChannelAsync_retryBackoff_acquiresOnRetry() {
        every { mockProvider.numChannelsAvailable } returns 0
        AntChannelHelper.setChannelAcquirerForTesting { _, _ -> mockChannel }

        AntChannelHelper.acquireAntPlusChannelAsync(
            mockContext,
            mockProvider,
            mockListener,
            mockHandler
        )

        // Verify retry runnable scheduled with 1000ms delay
        val retryEntry = postedRunnables.find { it.second == AntChannelHelper.DEFAULT_RETRY_INTERVAL_MS }
        assertNotNull(retryEntry)

        // Simulate channels becoming available on adapter initialization completion
        every { mockProvider.numChannelsAvailable } returns 4

        // Execute scheduled retry runnable
        retryEntry!!.first.run()

        // Assert channel acquired on retry attempt
        verify(exactly = 1) { mockListener.onChannelAcquired(mockChannel) }
    }

    /**
     * TST-CON-037.4: Acquisition Timeout & User-Visible Failure Test.
     * Simulates persistent channel unavailability for 10,000ms; verifies timeout runnable
     * invokes onAcquisitionFailed with timeout message and unregisters receiver.
     */
    @Test
    fun testAcquireAntPlusChannelAsync_overallTimeout_failsCleanly() {
        every { mockProvider.numChannelsAvailable } returns 0
        AntChannelHelper.setChannelAcquirerForTesting { _, _ ->
            throw ChannelNotAvailableException(ChannelNotAvailableReason.SERVICE_INITIALIZING)
        }

        val receiverSlot = slot<BroadcastReceiver>()
        every {
            ContextCompat.registerReceiver(
                eq(mockContext),
                capture(receiverSlot),
                any(),
                any()
            )
        } returns null

        AntChannelHelper.acquireAntPlusChannelAsync(
            mockContext,
            mockProvider,
            mockListener,
            mockHandler
        )

        val timeoutEntry = postedRunnables.find { it.second == AntChannelHelper.DEFAULT_OVERALL_TIMEOUT_MS }
        assertNotNull(timeoutEntry)

        // Execute overall timeout runnable
        timeoutEntry!!.first.run()

        val exceptionSlot = slot<Exception>()
        verify(exactly = 1) { mockListener.onAcquisitionFailed(capture(exceptionSlot)) }
        assertTrue(
            "Exception message should mention timeout: ${exceptionSlot.captured.message}",
            exceptionSlot.captured.message?.contains("timed out", ignoreCase = true) == true
        )
        verify(exactly = 1) { mockContext.unregisterReceiver(receiverSlot.captured) }
    }

    /**
     * TST-CON-037.5: Teardown & Cancellation Contract Tests.
     * Verifies calling cancel() unregisters receiver and removes all pending handler callbacks.
     */
    @Test
    fun testAcquireAntPlusChannelAsync_cancel_unregistersReceiverAndRemovesCallbacks() {
        every { mockProvider.numChannelsAvailable } returns 0
        val receiverSlot = slot<BroadcastReceiver>()
        every {
            ContextCompat.registerReceiver(
                eq(mockContext),
                capture(receiverSlot),
                any(),
                any()
            )
        } returns null

        val handle = AntChannelHelper.acquireAntPlusChannelAsync(
            mockContext,
            mockProvider,
            mockListener,
            mockHandler
        )

        // Cancel in-flight acquisition handle (e.g. from shutDown() or stopAsyncSearch())
        handle.cancel()

        verify(exactly = 1) { mockContext.unregisterReceiver(receiverSlot.captured) }
        verify(atLeast = 2) { mockHandler.removeCallbacks(any<Runnable>()) }

        // Assert subsequent broadcasts or runnables are ignored
        val broadcastIntent = mockk<Intent>(relaxed = true)
        every { broadcastIntent.action } returns AntChannelProvider.ACTION_CHANNEL_PROVIDER_STATE_CHANGED
        receiverSlot.captured.onReceive(mockContext, broadcastIntent)

        verify(exactly = 0) { mockListener.onChannelAcquired(any()) }
        verify(exactly = 0) { mockListener.onAcquisitionFailed(any()) }
    }

    /**
     * TST-CON-038.3: Background Scanning Assignment & Fallback Tests.
     */
    @Test
    fun testAssignScanChannel_whenCapabilitiesSupportBackgroundScanning_assignsWithExtendedAssignment() {
        val mockCapabilities = mockk<Capabilities>()
        every { mockCapabilities.hasBackgroundScanning() } returns true
        every { mockChannel.capabilities } returns mockCapabilities

        val assignedSlot = slot<ExtendedAssignment>()
        every { mockChannel.assign(ChannelType.BIDIRECTIONAL_SLAVE, capture(assignedSlot)) } returns Unit

        val result = AntChannelHelper.assignScanChannel(mockChannel)

        assertTrue(result)
        verify(exactly = 1) { mockChannel.assign(ChannelType.BIDIRECTIONAL_SLAVE, any<ExtendedAssignment>()) }
        assertTrue(assignedSlot.captured.enableBackgroundScanning)
    }

    @Test
    fun testAssignScanChannel_whenCapabilitiesUnsupported_fallsBackToStandardSlave() {
        val mockCapabilities = mockk<Capabilities>()
        every { mockCapabilities.hasBackgroundScanning() } returns false
        every { mockChannel.capabilities } returns mockCapabilities

        every { mockChannel.assign(ChannelType.BIDIRECTIONAL_SLAVE) } returns Unit

        val result = AntChannelHelper.assignScanChannel(mockChannel)

        assertFalse(result)
        verify(exactly = 1) { mockChannel.assign(ChannelType.BIDIRECTIONAL_SLAVE) }
        verify(exactly = 0) { mockChannel.assign(any(), any<ExtendedAssignment>()) }
    }

    @Test
    fun testAssignScanChannel_whenCapabilitiesThrowsException_fallsBackToStandardSlave() {
        every { mockChannel.capabilities } throws RuntimeException("Capabilities not supported")
        every { mockChannel.assign(ChannelType.BIDIRECTIONAL_SLAVE) } returns Unit

        val result = AntChannelHelper.assignScanChannel(mockChannel)

        assertFalse(result)
        verify(exactly = 1) { mockChannel.assign(ChannelType.BIDIRECTIONAL_SLAVE) }
    }

    /**
     * TST-CON-038.4: Safe LibConfig Exception Tolerance Tests.
     */
    @Test
    fun testSafeSetAdapterWideLibConfig_whenSuccessful_returnsTrue() {
        val libConfig = LibConfig(true, true, false)
        every { mockChannel.setAdapterWideLibConfig(libConfig) } returns Unit

        val result = AntChannelHelper.safeSetAdapterWideLibConfig(mockChannel, libConfig)

        assertTrue(result)
        verify(exactly = 1) { mockChannel.setAdapterWideLibConfig(libConfig) }
    }

    @Test
    fun testSafeSetAdapterWideLibConfig_whenAntCommandFailedException_returnsFalseWithoutCrashing() {
        val libConfig = LibConfig(true, true, false)
        val mockCmdException = mockk<AntCommandFailedException>(relaxed = true)
        every { mockCmdException.message } returns "LibConfig already configured on adapter"
        every { mockChannel.setAdapterWideLibConfig(libConfig) } throws mockCmdException

        val result = AntChannelHelper.safeSetAdapterWideLibConfig(mockChannel, libConfig)

        assertFalse(result)
        verify(exactly = 1) { mockChannel.setAdapterWideLibConfig(libConfig) }
    }

    /**
     * TST-CON-038.5: DataMessage ExtendedData & RSSI Extraction Tests.
     */
    @Test
    fun testExtractExtendedDeviceAndRssi_broadcastData_withValidChannelIdAndRssi() {
        val mockParcel = mockk<AntMessageParcel>(relaxed = true)
        every { mockParcel.messageId } returns 78
        every { mockParcel.messageContent } returns ByteArray(9)
        mockkConstructor(BroadcastDataMessage::class)

        val mockExtendedData = mockk<ExtendedData>()
        val mockChannelId = mockk<ChannelId>()
        val mockRssi = mockk<Rssi>()

        every { mockChannelId.deviceNumber } returns 43210
        every { mockRssi.rssiValue } returns -58
        every { mockExtendedData.hasChannelId() } returns true
        every { mockExtendedData.channelId } returns mockChannelId
        every { mockExtendedData.hasRssi() } returns true
        every { mockExtendedData.rssi } returns mockRssi

        every { anyConstructed<BroadcastDataMessage>().hasExtendedData() } returns true
        every { anyConstructed<BroadcastDataMessage>().extendedData } returns mockExtendedData

        val info = AntChannelHelper.extractExtendedDeviceAndRssi(MessageFromAntType.BROADCAST_DATA, mockParcel)

        assertNotNull(info)
        assertEquals(43210, info!!.deviceNumber)
        assertEquals(-58, info.rssi)
    }

    @Test
    fun testExtractExtendedDeviceAndRssi_acknowledgedData_withoutRssi_defaultsToMinusOne() {
        val mockParcel = mockk<AntMessageParcel>(relaxed = true)
        every { mockParcel.messageId } returns 79
        every { mockParcel.messageContent } returns ByteArray(9)
        mockkConstructor(AcknowledgedDataMessage::class)

        val mockExtendedData = mockk<ExtendedData>()
        val mockChannelId = mockk<ChannelId>()

        every { mockChannelId.deviceNumber } returns 12345
        every { mockExtendedData.hasChannelId() } returns true
        every { mockExtendedData.channelId } returns mockChannelId
        every { mockExtendedData.hasRssi() } returns false

        every { anyConstructed<AcknowledgedDataMessage>().hasExtendedData() } returns true
        every { anyConstructed<AcknowledgedDataMessage>().extendedData } returns mockExtendedData

        val info = AntChannelHelper.extractExtendedDeviceAndRssi(MessageFromAntType.ACKNOWLEDGED_DATA, mockParcel)

        assertNotNull(info)
        assertEquals(12345, info!!.deviceNumber)
        assertEquals(-1, info.rssi)
    }

    @Test
    fun testExtractExtendedDeviceAndRssi_nonDataMessage_returnsNull() {
        val mockParcel = mockk<AntMessageParcel>(relaxed = true)
        val info = AntChannelHelper.extractExtendedDeviceAndRssi(MessageFromAntType.CHANNEL_EVENT, mockParcel)
        assertNull(info)
    }

    @Test
    fun testExtractExtendedDeviceAndRssi_broadcastData_withoutExtendedData_returnsNull() {
        val mockParcel = mockk<AntMessageParcel>(relaxed = true)
        every { mockParcel.messageId } returns 78
        every { mockParcel.messageContent } returns ByteArray(9)
        mockkConstructor(BroadcastDataMessage::class)
        every { anyConstructed<BroadcastDataMessage>().hasExtendedData() } returns false

        val info = AntChannelHelper.extractExtendedDeviceAndRssi(MessageFromAntType.BROADCAST_DATA, mockParcel)
        assertNull(info)
    }
}
