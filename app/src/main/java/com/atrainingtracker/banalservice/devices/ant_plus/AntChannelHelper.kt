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
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.RemoteException
import android.util.Log
import androidx.core.content.ContextCompat
import com.dsi.ant.channel.AntChannel
import com.dsi.ant.channel.AntChannelProvider
import com.dsi.ant.channel.AntCommandFailedException
import com.dsi.ant.channel.ChannelNotAvailableException
import com.dsi.ant.channel.ChannelNotAvailableReason
import com.dsi.ant.channel.ipc.IAntChannelCommunicator
import com.dsi.ant.channel.ipc.IAntChannelProviderCommunicator
import com.dsi.ant.message.ChannelType
import com.dsi.ant.message.ExtendedAssignment
import com.dsi.ant.message.LibConfig
import com.dsi.ant.message.fromant.AcknowledgedDataMessage
import com.dsi.ant.message.fromant.BroadcastDataMessage
import com.dsi.ant.message.fromant.MessageFromAntType
import com.dsi.ant.message.ipc.AntMessageParcel
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * Helper utility for acquiring ANT+ channels on Predefined Network slot 1 (ANT_PLUS)
 * using android_antlib_4-16-0.aar (REQ-CON-032.1, REQ-CON-037.1..4, ATT-5084, ATT-7546).
 *
 * Avoids Android_ANTRadioService rejecting RF frequency 57 on PredefinedNetwork.PUBLIC (slot 0)
 * and avoiding raw network key rejection.
 *
 * Handles asynchronous cold-start channel acquisition races when AntRadioService unfreezes
 * and initializes UsbAntService in the background.
 */
object AntChannelHelper {
    private const val TAG = "AntChannelHelper"

    /** Predefined network number for ANT+ profiles (slot 1) */
    const val PREDEFINED_NETWORK_ANT_PLUS = 1

    /** Standard ANT+ Bike Radar device profile constants */
    const val BIKE_RADAR_DEVICE_TYPE = 40 // 0x28
    const val BIKE_RADAR_RF_FREQUENCY = 57 // 2457 MHz
    const val BIKE_RADAR_CHANNEL_PERIOD = 4084 // ~8.0235 Hz (counts)

    /** Standard ANT+ Bike Lights device profile constants (REQ-CON-033.1, ATT-5103) */
    const val BIKE_LIGHT_DEVICE_TYPE = 35 // 0x23
    const val BIKE_LIGHT_RF_FREQUENCY = 57 // 2457 MHz
    const val BIKE_LIGHT_CHANNEL_PERIOD = 4084 // ~8.0235 Hz (counts)

    /** Default retry and timeout constants for asynchronous channel acquisition (REQ-CON-037.2) */
    const val DEFAULT_MAX_RETRIES = 5
    const val DEFAULT_RETRY_INTERVAL_MS = 1000L
    const val DEFAULT_OVERALL_TIMEOUT_MS = 10000L

    /**
     * Extended device discovery information parsed from ANT+ broadcast/acknowledged messages (REQ-CON-038.5).
     */
    data class ExtendedDeviceInfo(val deviceNumber: Int, val rssi: Int)

    /**
     * Handle to cancel an in-flight asynchronous channel acquisition (REQ-CON-037.1).
     */
    interface AcquisitionCancellationHandle {
        fun cancel()
    }

    /**
     * Callback listener for asynchronous channel acquisition results (REQ-CON-037.1).
     */
    interface ChannelAcquisitionListener {
        fun onChannelAcquired(channel: AntChannel)
        fun onAcquisitionFailed(exception: Exception)
    }

    fun interface ChannelAcquirer {
        @Throws(ChannelNotAvailableException::class, RemoteException::class)
        fun acquire(context: Context, provider: AntChannelProvider): AntChannel
    }

    private val defaultAcquirer = ChannelAcquirer { context, provider ->
        val providerField: Field = AntChannelProvider::class.java.getDeclaredField("mAntChannelProvider")
        providerField.isAccessible = true
        val communicator = providerField.get(provider) as? IAntChannelProviderCommunicator
            ?: throw ChannelNotAvailableException(ChannelNotAvailableReason.UNKNOWN)

        val bundle = Bundle()
        val channelComm: IAntChannelCommunicator? = communicator.acquireChannel(
            context,
            PREDEFINED_NETWORK_ANT_PLUS,
            null,
            null,
            bundle
        )

        if (channelComm == null) {
            bundle.classLoader = ChannelNotAvailableException::class.java.classLoader
            val error = bundle.getParcelable<ChannelNotAvailableException>("error")
            if (error != null) {
                throw error
            }
            throw ChannelNotAvailableException(ChannelNotAvailableReason.UNKNOWN)
        }

        val constructor: Constructor<AntChannel> = AntChannel::class.java.getDeclaredConstructor(IAntChannelCommunicator::class.java)
        constructor.isAccessible = true
        val antChannel = constructor.newInstance(channelComm)

        try {
            val voteMethod: Method = AntChannelProvider::class.java.getDeclaredMethod("voteEventBufferToDefault", AntChannel::class.java)
            voteMethod.isAccessible = true
            voteMethod.invoke(null, antChannel)
        } catch (_: Exception) {
            // Best effort event buffer voting
        }

        antChannel
    }

    private var currentAcquirer: ChannelAcquirer = defaultAcquirer

    @JvmStatic
    fun setChannelAcquirerForTesting(acquirer: ChannelAcquirer?) {
        currentAcquirer = acquirer ?: defaultAcquirer
    }

    @JvmStatic
    @Throws(ChannelNotAvailableException::class, RemoteException::class)
    fun acquireAntPlusChannel(context: Context, provider: AntChannelProvider): AntChannel {
        return currentAcquirer.acquire(context, provider)
    }

    /**
     * Asynchronously acquires an ANT+ channel on Predefined Network slot 1 (REQ-CON-037.1..4).
     *
     * If channels are immediately available, invokes [ChannelAcquisitionListener.onChannelAcquired] synchronously.
     * If the service is initializing or no channels/adapters are yet available, registers a broadcast receiver
     * for [AntChannelProvider.ACTION_CHANNEL_PROVIDER_STATE_CHANGED] (using ContextCompat.RECEIVER_EXPORTED)
     * and executes periodic retry backoff until channels become available or the overall timeout expires.
     */
    @JvmStatic
    @JvmOverloads
    fun acquireAntPlusChannelAsync(
        context: Context,
        provider: AntChannelProvider,
        listener: ChannelAcquisitionListener,
        handler: Handler = Handler(Looper.getMainLooper()),
        maxRetries: Int = DEFAULT_MAX_RETRIES,
        retryIntervalMs: Long = DEFAULT_RETRY_INTERVAL_MS,
        overallTimeoutMs: Long = DEFAULT_OVERALL_TIMEOUT_MS
    ): AcquisitionCancellationHandle {
        val session = AsyncAcquisitionSession(
            context = context,
            provider = provider,
            listener = listener,
            handler = handler,
            maxRetries = maxRetries,
            retryIntervalMs = retryIntervalMs,
            overallTimeoutMs = overallTimeoutMs
        )
        session.start()
        return session
    }

    @JvmStatic
    fun acquireAntPlusChannelAsync(
        context: Context,
        provider: AntChannelProvider,
        handler: Handler,
        maxRetries: Int,
        retryIntervalMs: Long,
        overallTimeoutMs: Long,
        listener: ChannelAcquisitionListener
    ): AcquisitionCancellationHandle {
        val session = AsyncAcquisitionSession(
            context = context,
            provider = provider,
            listener = listener,
            handler = handler,
            maxRetries = maxRetries,
            retryIntervalMs = retryIntervalMs,
            overallTimeoutMs = overallTimeoutMs
        )
        session.start()
        return session
    }

    /**
     * Safely sets adapter-wide [LibConfig] on [channel] (REQ-CON-038.4).
     * If another active channel on the adapter already owns or set the LibConfig,
     * [AntCommandFailedException] is caught and logged, allowing channel configuration
     * and opening to proceed without failure.
     *
     * @return true if configuration succeeded, false if an AntCommandFailedException occurred.
     */
    @JvmStatic
    fun safeSetAdapterWideLibConfig(channel: AntChannel, libConfig: LibConfig): Boolean {
        return try {
            channel.setAdapterWideLibConfig(libConfig)
            true
        } catch (e: AntCommandFailedException) {
            Log.w(TAG, "Adapter-wide lib config already owned or failed on channel: ${e.message}")
            false
        } catch (e: Exception) {
            Log.w(TAG, "Unexpected error setting adapter-wide lib config: ${e.message}", e)
            false
        }
    }

    /**
     * Assigns the channel for discovery scanning (REQ-CON-038.3).
     * If the adapter capabilities support background scanning, assigns with
     * ExtendedAssignment enabling background scanning. Otherwise falls back to
     * standard BIDIRECTIONAL_SLAVE assignment.
     *
     * @return true if background scanning was assigned, false if standard slave was used.
     */
    @JvmStatic
    fun assignScanChannel(channel: AntChannel): Boolean {
        return try {
            val capabilities = try { channel.capabilities } catch (_: Exception) { null }
            if (capabilities?.hasBackgroundScanning() == true) {
                val extendedAssignment = ExtendedAssignment()
                extendedAssignment.enableBackgroundScanning()
                channel.assign(ChannelType.BIDIRECTIONAL_SLAVE, extendedAssignment)
                true
            } else {
                channel.assign(ChannelType.BIDIRECTIONAL_SLAVE)
                false
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to assign background scanning, falling back to BIDIRECTIONAL_SLAVE", e)
            channel.assign(ChannelType.BIDIRECTIONAL_SLAVE)
            false
        }
    }

    /**
     * Extracts device number and RSSI from extended data in BROADCAST_DATA or ACKNOWLEDGED_DATA (REQ-CON-038.5).
     *
     * @return ExtendedDeviceInfo if valid channel ID present with deviceNumber > 0, or null otherwise.
     */
    @JvmStatic
    fun extractExtendedDeviceAndRssi(
        messageType: MessageFromAntType,
        messageParcel: AntMessageParcel
    ): ExtendedDeviceInfo? {
        if (messageType != MessageFromAntType.BROADCAST_DATA && messageType != MessageFromAntType.ACKNOWLEDGED_DATA) {
            return null
        }
        val dataMsg = if (messageType == MessageFromAntType.BROADCAST_DATA) {
            BroadcastDataMessage(messageParcel)
        } else {
            AcknowledgedDataMessage(messageParcel)
        }
        if (dataMsg.hasExtendedData() && dataMsg.extendedData.hasChannelId()) {
            val deviceNumber = dataMsg.extendedData.channelId.deviceNumber
            val rssi = if (dataMsg.extendedData.hasRssi()) {
                dataMsg.extendedData.rssi.rssiValue
            } else {
                -1
            }
            if (deviceNumber > 0) {
                return ExtendedDeviceInfo(deviceNumber, rssi)
            }
        }
        return null
    }

    private class AsyncAcquisitionSession(
        private val context: Context,
        private val provider: AntChannelProvider,
        private val listener: ChannelAcquisitionListener,
        private val handler: Handler,
        private val maxRetries: Int,
        private val retryIntervalMs: Long,
        private val overallTimeoutMs: Long
    ) : AcquisitionCancellationHandle {
        private var isCompleted = false
        private var isCancelled = false
        private var isReceiverRegistered = false
        private var retryCount = 0
        private var lastException: ChannelNotAvailableException? = null

        private val receiver = object : BroadcastReceiver() {
            override fun onReceive(recvContext: Context?, intent: Intent?) {
                if (intent?.action == AntChannelProvider.ACTION_CHANNEL_PROVIDER_STATE_CHANGED) {
                    synchronized(this@AsyncAcquisitionSession) {
                        attemptAcquisitionLocked()
                    }
                }
            }
        }

        private val retryRunnable = object : Runnable {
            override fun run() {
                synchronized(this@AsyncAcquisitionSession) {
                    if (isCompleted || isCancelled) return
                    retryCount++
                    attemptAcquisitionLocked()
                    if (!isCompleted && !isCancelled && retryCount < maxRetries) {
                        handler.postDelayed(this, retryIntervalMs)
                    }
                }
            }
        }

        private val timeoutRunnable = object : Runnable {
            override fun run() {
                synchronized(this@AsyncAcquisitionSession) {
                    if (isCompleted || isCancelled) return
                    isCompleted = true
                    cleanupLocked()
                    val timeoutEx = ChannelNotAvailableException(
                        lastException?.reasonCode ?: ChannelNotAvailableReason.SERVICE_INITIALIZING,
                        "Timed out waiting for ANT+ channel after ${overallTimeoutMs}ms"
                    )
                    listener.onAcquisitionFailed(timeoutEx)
                }
            }
        }

        fun start() {
            synchronized(this) {
                if (isCancelled) return
                try {
                    val available = try {
                        provider.numChannelsAvailable
                    } catch (_: Exception) {
                        0
                    }
                    if (available > 0) {
                        val channel = acquireAntPlusChannel(context, provider)
                        isCompleted = true
                        listener.onChannelAcquired(channel)
                        return
                    }
                } catch (e: ChannelNotAvailableException) {
                    lastException = e
                    if (!isRetryable(e)) {
                        isCompleted = true
                        listener.onAcquisitionFailed(e)
                        return
                    }
                } catch (e: Exception) {
                    isCompleted = true
                    listener.onAcquisitionFailed(e)
                    return
                }

                try {
                    val filter = IntentFilter(AntChannelProvider.ACTION_CHANNEL_PROVIDER_STATE_CHANGED)
                    ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
                    isReceiverRegistered = true
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to register ACTION_CHANNEL_PROVIDER_STATE_CHANGED receiver", e)
                }

                handler.postDelayed(retryRunnable, retryIntervalMs)
                handler.postDelayed(timeoutRunnable, overallTimeoutMs)
            }
        }

        private fun attemptAcquisitionLocked() {
            if (isCompleted || isCancelled) return
            try {
                val available = try {
                    provider.numChannelsAvailable
                } catch (_: Exception) {
                    0
                }
                if (available > 0) {
                    val channel = acquireAntPlusChannel(context, provider)
                    isCompleted = true
                    cleanupLocked()
                    listener.onChannelAcquired(channel)
                }
            } catch (e: ChannelNotAvailableException) {
                lastException = e
                if (!isRetryable(e)) {
                    isCompleted = true
                    cleanupLocked()
                    listener.onAcquisitionFailed(e)
                }
            } catch (e: Exception) {
                isCompleted = true
                cleanupLocked()
                listener.onAcquisitionFailed(e)
            }
        }

        private fun cleanupLocked() {
            handler.removeCallbacks(retryRunnable)
            handler.removeCallbacks(timeoutRunnable)
            if (isReceiverRegistered) {
                com.atrainingtracker.util.LifecycleUtils.safeUnregisterReceiver(context, receiver, TAG)
                isReceiverRegistered = false
            }
        }

        override fun cancel() {
            synchronized(this) {
                if (isCompleted || isCancelled) return
                isCancelled = true
                cleanupLocked()
            }
        }

        private fun isRetryable(e: ChannelNotAvailableException): Boolean {
            return e.reasonCode == ChannelNotAvailableReason.SERVICE_INITIALIZING ||
                    e.reasonCode == ChannelNotAvailableReason.NO_ADAPTERS_EXIST
        }
    }
}
