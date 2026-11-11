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

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import com.atrainingtracker.banalservice.BANALService
import com.atrainingtracker.banalservice.devices.radar.RadarLightController
import com.atrainingtracker.banalservice.devices.radar.RadarLightDeviceClient
import com.atrainingtracker.banalservice.devices.radar.RadarLightMode
import com.atrainingtracker.banalservice.devices.radar.RadarStateRepository
import com.dsi.ant.AntService
import com.dsi.ant.channel.AntChannel
import com.dsi.ant.channel.AntChannelProvider
import com.dsi.ant.channel.IAntChannelEventHandler
import com.dsi.ant.message.ChannelId
import com.dsi.ant.message.ChannelType
import com.dsi.ant.message.LibConfig
import com.dsi.ant.message.LowPrioritySearchTimeout
import com.dsi.ant.message.fromant.BroadcastDataMessage
import com.dsi.ant.message.fromant.MessageFromAntType
import com.dsi.ant.message.ipc.AntMessageParcel

/**
 * ANT+ Bike Lights protocol driver and companion channel manager (REQ-CON-033, ATT-5103).
 *
 * Implements the ANT+ Bike Lights profile (Device Type 35 / 0x23) over Predefined Network
 * slot 1 (ANT_PLUS) as an automated companion channel bound to an active radar unit.
 *
 * Executes the mandatory 3-step state machine:
 * 1. Broadcast Monitoring: decodes Page 0x01 (Main Light State) and Page 0x02 (Capabilities).
 * 2. Connect Handshake: transmits acknowledged Page 0x21 (Connect Command) assigning Light Index = 1.
 * 3. Mode Control: transmits acknowledged Page 0x22 (Light Settings Command) with sequence incrementing.
 * 4. Disconnect Release: transmits acknowledged Page 0x20 (Disconnect Command) upon session teardown.
 */
class ANTBikeLightDevice(
    private val context: Context,
    val antDeviceNumber: Int,
    private val controllerId: Int = DEFAULT_CONTROLLER_ID
) : RadarLightDeviceClient {

    companion object {
        private const val TAG = "ANTBikeLightDevice"
        private val DEBUG = BANALService.getDebug(true)

        const val BIKE_LIGHT_DEVICE_TYPE = AntChannelHelper.BIKE_LIGHT_DEVICE_TYPE // 35
        const val BIKE_LIGHT_RF_FREQUENCY = AntChannelHelper.BIKE_LIGHT_RF_FREQUENCY // 57
        const val BIKE_LIGHT_CHANNEL_PERIOD = AntChannelHelper.BIKE_LIGHT_CHANNEL_PERIOD // 4084

        const val PAGE_MAIN_LIGHT_STATE = 0x01
        const val PAGE_LIGHT_CAPABILITIES = 0x02
        const val PAGE_DISCONNECT_COMMAND = 0x20 // 32
        const val PAGE_CONNECT_COMMAND = 0x21 // 33
        const val PAGE_LIGHT_SETTINGS_COMMAND = 0x22 // 34

        const val LIGHT_TYPE_TAIL_LIGHT = 2
        const val ASSIGNED_LIGHT_INDEX = 1
        const val DEFAULT_CONTROLLER_ID = 0x41545431 // 'ATT1' in hex

        // ANT+ Bike Lights Mode Constants (Page 0x22 / Page 0x01)
        const val ANT_LIGHT_MODE_OFF = 0x00
        const val ANT_LIGHT_MODE_SOLID_HIGH = 0x01
        const val ANT_LIGHT_MODE_SOLID_LOW = 0x02
        const val ANT_LIGHT_MODE_FLASH_FAST = 0x03
        const val ANT_LIGHT_MODE_FLASH_SLOW = 0x04
        const val ANT_LIGHT_MODE_DAY_FLASH = 0x05
        const val ANT_LIGHT_MODE_NIGHT_FLASH = 0x06
    }

    var antChannel: AntChannel? = null
        internal set

    private var antService: AntService? = null
    private var isServiceBound = false

    var lightIndex: Int = 0
        private set

    var isAssociated: Boolean = false
        private set

    var currentLightMode: RadarLightMode = RadarLightMode.OFF
        private set

    var pendingTargetMode: RadarLightMode? = null
        internal set

    var sequenceNumber: Int = 0
        internal set

    private val channelEventHandler = object : IAntChannelEventHandler {
        override fun onReceiveMessage(messageType: MessageFromAntType, messageParcel: AntMessageParcel) {
            when (messageType) {
                MessageFromAntType.BROADCAST_DATA,
                MessageFromAntType.ACKNOWLEDGED_DATA -> {
                    val broadcastMessage = BroadcastDataMessage(messageParcel)
                    decodeBroadcastData(broadcastMessage.payload)
                }
                else -> {}
            }
        }

        override fun onChannelDeath() {
            if (DEBUG) Log.w(TAG, "ANT+ Bike Light channel died")
            stop()
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            if (DEBUG) Log.d(TAG, "onServiceConnected to AntRadioService")
            try {
                val serviceInstance = AntService(service)
                antService = serviceInstance
                val provider = serviceInstance.channelProvider
                val channel = AntChannelHelper.acquireAntPlusChannel(context, provider)
                antChannel = channel
                configureChannel(channel)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to acquire/configure ANT+ light channel", e)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            if (DEBUG) Log.d(TAG, "onServiceDisconnected from AntRadioService")
            antService = null
            antChannel = null
            isAssociated = false
            lightIndex = 0
        }
    }

    fun start(channelProvider: AntChannelProvider? = null) {
        if (antChannel != null) {
            try {
                antChannel?.open()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to re-open ANT+ light channel", e)
            }
            return
        }

        if (channelProvider != null) {
            try {
                val channel = AntChannelHelper.acquireAntPlusChannel(context, channelProvider)
                antChannel = channel
                configureChannel(channel)
                return
            } catch (e: Exception) {
                Log.e(TAG, "Failed to acquire channel from provided AntChannelProvider", e)
            }
        }

        try {
            isServiceBound = AntService.bindService(context, serviceConnection)
            if (!isServiceBound) {
                Log.w(TAG, "AntService.bindService returned false for bike lights")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception binding AntService for bike lights", e)
        }
    }

    fun configureChannel(channel: AntChannel) {
        try {
            channel.assign(ChannelType.BIDIRECTIONAL_SLAVE)
            channel.setChannelId(ChannelId(antDeviceNumber, BIKE_LIGHT_DEVICE_TYPE, 0))
            channel.setPeriod(BIKE_LIGHT_CHANNEL_PERIOD)
            channel.setRfFrequency(BIKE_LIGHT_RF_FREQUENCY)
            channel.setAdapterWideLibConfig(LibConfig(true, true, false))
            channel.setSearchTimeout(LowPrioritySearchTimeout.TWENTY_FIVE_SECONDS)
            channel.setChannelEventHandler(channelEventHandler)
            channel.open()
            if (DEBUG) Log.i(TAG, "ANT+ Bike Light channel opened for device $antDeviceNumber")
        } catch (e: Exception) {
            Log.e(TAG, "Error configuring ANT+ Bike Light channel", e)
        }
    }

    fun decodeBroadcastData(payload: ByteArray?) {
        if (payload == null || payload.size < 8) return

        val pageNumber = payload[0].toInt() and 0xFF
        when (pageNumber) {
            PAGE_MAIN_LIGHT_STATE -> {
                parseMainLightState(payload)
            }
            PAGE_LIGHT_CAPABILITIES -> {
                parseLightCapabilities(payload)
            }
        }
    }

    private fun parseMainLightState(payload: ByteArray) {
        val rawIndex = payload[1].toInt() and 0x3F
        lightIndex = rawIndex

        val lightType = payload[2].toInt() and 0x07
        val rawMode = payload[3].toInt() and 0xFF

        val decodedMode = when (rawMode) {
            ANT_LIGHT_MODE_OFF -> RadarLightMode.OFF
            ANT_LIGHT_MODE_SOLID_HIGH -> RadarLightMode.SOLID
            ANT_LIGHT_MODE_SOLID_LOW -> RadarLightMode.PELOTON
            ANT_LIGHT_MODE_DAY_FLASH -> RadarLightMode.DAY_FLASH
            ANT_LIGHT_MODE_NIGHT_FLASH -> RadarLightMode.NIGHT_FLASH
            else -> RadarLightMode.SOLID
        }
        currentLightMode = decodedMode

        if (rawIndex == 0) {
            // Peripheral is unassociated (Index == 0).
            isAssociated = false
            if (pendingTargetMode != null) {
                sendConnectCommand()
            }
        } else {
            // Peripheral is associated (Index > 0).
            val previouslyAssociated = isAssociated
            isAssociated = true
            if (!previouslyAssociated) {
                RadarLightController.registerRadarDevice(this, true, false)
                val current = RadarStateRepository.getState()
                RadarStateRepository.updateState(current.copy(isLightControlSupported = true, lightMode = decodedMode))
            }

            val pending = pendingTargetMode
            if (pending != null) {
                pendingTargetMode = null
                sendLightSettingsCommand(pending)
            } else {
                RadarLightController.updateHardwareLightMode(decodedMode)
            }
        }
    }

    private fun parseLightCapabilities(payload: ByteArray) {
        // Discovered capabilities confirm light support
        if (!isAssociated && lightIndex > 0) {
            isAssociated = true
            RadarLightController.registerRadarDevice(this, true, false)
        }
    }

    fun sendConnectCommand(): Boolean {
        val payload = ByteArray(8)
        payload[0] = PAGE_CONNECT_COMMAND.toByte() // 0x21
        payload[1] = ASSIGNED_LIGHT_INDEX.toByte() // 1
        payload[2] = LIGHT_TYPE_TAIL_LIGHT.toByte() // 2 = Tail Light
        payload[3] = (controllerId and 0xFF).toByte()
        payload[4] = ((controllerId shr 8) and 0xFF).toByte()
        payload[5] = ((controllerId shr 16) and 0xFF).toByte()
        payload[6] = ((controllerId shr 24) and 0xFF).toByte()
        payload[7] = 0xFF.toByte()

        return sendAcknowledgedData(payload)
    }

    fun sendLightSettingsCommand(mode: RadarLightMode): Boolean {
        val targetIndex = if (lightIndex > 0) lightIndex else ASSIGNED_LIGHT_INDEX
        sequenceNumber = (sequenceNumber + 1) and 0x03

        val payload = ByteArray(8)
        payload[0] = PAGE_LIGHT_SETTINGS_COMMAND.toByte() // 0x22
        payload[1] = ((sequenceNumber shl 6) or (targetIndex and 0x3F)).toByte()
        payload[2] = mapModeToAntByte(mode)
        payload[3] = 0xFF.toByte() // Beam Intensity (default)
        payload[4] = 0xFF.toByte()
        payload[5] = 0xFF.toByte()
        payload[6] = 0xFF.toByte()
        payload[7] = 0xFF.toByte()

        val sent = sendAcknowledgedData(payload)
        if (sent) {
            currentLightMode = mode
            RadarLightController.updateHardwareLightMode(mode)
        }
        return sent
    }

    fun sendDisconnectCommand(): Boolean {
        val targetIndex = if (lightIndex > 0) lightIndex else ASSIGNED_LIGHT_INDEX
        val payload = ByteArray(8)
        payload[0] = PAGE_DISCONNECT_COMMAND.toByte() // 0x20
        payload[1] = targetIndex.toByte()
        payload[2] = 0xFF.toByte()
        payload[3] = (controllerId and 0xFF).toByte()
        payload[4] = ((controllerId shr 8) and 0xFF).toByte()
        payload[5] = ((controllerId shr 16) and 0xFF).toByte()
        payload[6] = ((controllerId shr 24) and 0xFF).toByte()
        payload[7] = 0xFF.toByte()

        val sent = sendAcknowledgedData(payload)
        isAssociated = false
        lightIndex = 0
        return sent
    }

    private fun sendAcknowledgedData(payload: ByteArray): Boolean {
        val channel = antChannel ?: return false
        return try {
            channel.startSendAcknowledgedData(payload)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error sending acknowledged ANT+ data for Page 0x${Integer.toHexString(payload[0].toInt() and 0xFF)}", e)
            false
        }
    }

    override fun setLightMode(mode: RadarLightMode): Boolean {
        if (!isAssociated || lightIndex == 0) {
            pendingTargetMode = mode
            return sendConnectCommand()
        }
        return sendLightSettingsCommand(mode)
    }

    fun mapModeToAntByte(mode: RadarLightMode): Byte {
        return when (mode) {
            RadarLightMode.OFF -> ANT_LIGHT_MODE_OFF.toByte()
            RadarLightMode.SOLID -> ANT_LIGHT_MODE_SOLID_HIGH.toByte()
            RadarLightMode.PELOTON -> ANT_LIGHT_MODE_SOLID_LOW.toByte()
            RadarLightMode.DAY_FLASH -> ANT_LIGHT_MODE_DAY_FLASH.toByte()
            RadarLightMode.NIGHT_FLASH -> ANT_LIGHT_MODE_NIGHT_FLASH.toByte()
        }
    }

    fun stop() {
        if (isAssociated && lightIndex > 0) {
            try {
                sendDisconnectCommand()
            } catch (e: Exception) {
                Log.w(TAG, "Error sending disconnect command on stop", e)
            }
        }
        RadarLightController.unregisterRadarDevice(this)

        try {
            antChannel?.close()
            antChannel?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing ANT+ light channel", e)
        }
        antChannel = null

        if (isServiceBound) {
            try {
                context.unbindService(serviceConnection)
            } catch (e: Exception) {
                Log.w(TAG, "Error unbinding AntService", e)
            }
            isServiceBound = false
        }
        antService = null
        isAssociated = false
        lightIndex = 0
        pendingTargetMode = null
    }
}
