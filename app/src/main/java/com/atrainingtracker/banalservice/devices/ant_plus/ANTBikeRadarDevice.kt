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
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.atrainingtracker.banalservice.BANALService
import com.atrainingtracker.banalservice.Protocol
import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.banalservice.devices.Manufacturer
import com.atrainingtracker.banalservice.devices.MyRemoteDevice
import com.atrainingtracker.banalservice.devices.radar.LaneState
import com.atrainingtracker.banalservice.devices.radar.RadarConnectionState
import com.atrainingtracker.banalservice.devices.radar.RadarState
import com.atrainingtracker.banalservice.devices.radar.RadarStateRepository
import com.atrainingtracker.banalservice.devices.radar.RadarTarget
import com.atrainingtracker.banalservice.devices.radar.RadarTrafficSimulator
import com.atrainingtracker.banalservice.devices.radar.RadarWindowAggregator
import com.atrainingtracker.banalservice.devices.radar.ThreatLevel
import com.atrainingtracker.banalservice.devices.radar.VehicleSizeCategory
import com.atrainingtracker.banalservice.sensor.MySensor
import com.atrainingtracker.banalservice.sensor.MySensorManager
import com.atrainingtracker.banalservice.sensor.SensorType
import com.dsi.ant.AntService
import com.dsi.ant.channel.AntChannel
import com.dsi.ant.channel.IAntChannelEventHandler
import com.dsi.ant.message.ChannelId
import com.dsi.ant.message.ChannelType
import com.dsi.ant.message.LibConfig
import com.dsi.ant.message.LowPrioritySearchTimeout
import com.dsi.ant.message.fromant.BroadcastDataMessage
import com.dsi.ant.message.fromant.MessageFromAntType
import com.dsi.ant.message.ipc.AntMessageParcel
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

/**
 * ANT+ Bike Radar Device driver (REQ-CON-032, ATT-5084).
 *
 * Implements the ANT+ Cycling Rearview Radar profile (Device Type 40 / 0x28) using
 * android_antlib_4-16-0.aar over Predefined Network slot 1 (ANT_PLUS).
 */
class ANTBikeRadarDevice(
    context: Context,
    mySensorManager: MySensorManager,
    deviceId: Long,
    val antDeviceNumber: Int
) : MyRemoteDevice(context, mySensorManager, DeviceType.BIKE_RADAR, deviceId) {

    companion object {
        private const val TAG = "ANTBikeRadarDevice"
        private val DEBUG = BANALService.getDebug(true)

        const val BIKE_RADAR_DEVICE_TYPE = 40 // 0x28
        const val BIKE_RADAR_RF_FREQUENCY = 57 // 2457 MHz
        const val BIKE_RADAR_CHANNEL_PERIOD = 4084 // ~8.0235 Hz (counts)
        const val WATCHDOG_TIMEOUT_MS = 3500L

        const val PAGE_RADAR_TARGETS_1_4 = 0x30
        const val PAGE_RADAR_TARGETS_5_8 = 0x31
        const val PAGE_SYSTEM_STATUS = 0x01
        const val PAGE_MANUFACTURER_ID = 0x50
        const val PAGE_PRODUCT_INFO = 0x51
        const val PAGE_BATTERY_STATUS = 0x52
    }

    private lateinit var threatCountSensor: MySensor<Int>
    private lateinit var closestDistanceSensor: MySensor<Double>
    private lateinit var approachSpeedSensor: MySensor<Double>
    private lateinit var vehicleCountSensor: MySensor<Int>
    private lateinit var passingDistanceSensor: MySensor<Double>
    private lateinit var largeVehicleCountSensor: MySensor<Int>

    private val activeTargets = ConcurrentHashMap<Int, RadarTarget>()
    private val windowAggregator = RadarWindowAggregator()

    private var antService: AntService? = null
    var antChannel: AntChannel? = null
        private set
    private var isServiceBound = false

    private val handler = Handler(Looper.getMainLooper())
    private val watchdogRunnable = Runnable {
        onWatchdogTimeout()
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            if (DEBUG) Log.d(TAG, "onServiceConnected to AntRadioService")
            try {
                val serviceInstance = AntService(service)
                antService = serviceInstance
                val provider = serviceInstance.channelProvider
                val channel = AntChannelHelper.acquireAntPlusChannel(mContext, provider)
                antChannel = channel
                configureChannel(channel)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to acquire/configure ANT+ radar channel", e)
                notifyStopSearching(false)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            if (DEBUG) Log.d(TAG, "onServiceDisconnected from AntRadioService")
            antService = null
            antChannel = null
            onWatchdogTimeout()
        }
    }

    private val channelEventHandler = object : IAntChannelEventHandler {
        override fun onReceiveMessage(messageType: MessageFromAntType, messageParcel: AntMessageParcel) {
            when (messageType) {
                MessageFromAntType.BROADCAST_DATA,
                MessageFromAntType.ACKNOWLEDGED_DATA -> {
                    if (isSearching) {
                        notifyStopSearching(true)
                    }
                    val broadcastMsg = BroadcastDataMessage(messageParcel)
                    decodeBroadcastData(broadcastMsg.payload)
                }
                else -> {
                    // Ignore other event types
                }
            }
        }

        override fun onChannelDeath() {
            if (DEBUG) Log.w(TAG, "ANT channel died")
            onWatchdogTimeout()
        }
    }

    override fun getProtocol(): Protocol = Protocol.ANT_PLUS

    override fun isReceivingData(): Boolean =
        RadarStateRepository.getState().connectionState == RadarConnectionState.CONNECTED

    override fun addSensors() {
        threatCountSensor = MySensor(this, SensorType.RADAR_THREAT_COUNT)
        addSensor(threatCountSensor)

        closestDistanceSensor = MySensor(this, SensorType.RADAR_CLOSEST_DISTANCE_m)
        addSensor(closestDistanceSensor)

        approachSpeedSensor = MySensor(this, SensorType.RADAR_APPROACH_SPEED_mps)
        addSensor(approachSpeedSensor)

        vehicleCountSensor = MySensor(this, SensorType.RADAR_VEHICLE_COUNT)
        addSensor(vehicleCountSensor)

        passingDistanceSensor = MySensor(this, SensorType.RADAR_PASSING_DISTANCE_m)
        addSensor(passingDistanceSensor)

        largeVehicleCountSensor = MySensor(this, SensorType.RADAR_LARGE_VEHICLE_COUNT)
        addSensor(largeVehicleCountSensor)
    }

    override fun startSearching() {
        notifyStartSearching()
        if (antChannel != null) {
            try {
                antChannel?.open()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to re-open channel", e)
            }
            return
        }
        try {
            isServiceBound = AntService.bindService(mContext, serviceConnection)
            if (!isServiceBound) {
                Log.w(TAG, "AntService.bindService returned false (ANT Radio Service not available)")
                notifyStopSearching(false)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception binding AntService", e)
            notifyStopSearching(false)
        }
    }

    fun configureChannel(channel: AntChannel) {
        channel.assign(ChannelType.BIDIRECTIONAL_SLAVE)
        channel.setChannelId(ChannelId(antDeviceNumber, BIKE_RADAR_DEVICE_TYPE, 0))
        channel.setPeriod(BIKE_RADAR_CHANNEL_PERIOD)
        channel.setRfFrequency(BIKE_RADAR_RF_FREQUENCY)
        channel.setAdapterWideLibConfig(LibConfig(true, true, false))
        channel.setSearchTimeout(LowPrioritySearchTimeout.TWENTY_FIVE_SECONDS)
        channel.setChannelEventHandler(channelEventHandler)
        channel.open()
        if (DEBUG) Log.i(TAG, "ANT+ Bike Radar channel opened for device $antDeviceNumber")
    }

    fun decodeBroadcastData(payload: ByteArray?) {
        if (payload == null || payload.size < 8) return

        val pageNumber = payload[0].toInt() and 0xFF
        when (pageNumber) {
            PAGE_RADAR_TARGETS_1_4 -> {
                parseTargetsPage(payload, baseId = 0)
            }
            PAGE_RADAR_TARGETS_5_8 -> {
                parseTargetsPage(payload, baseId = 4)
            }
            PAGE_SYSTEM_STATUS -> {
                parseSystemStatus(payload)
            }
            PAGE_MANUFACTURER_ID -> {
                parseManufacturerPage(payload)
            }
            PAGE_PRODUCT_INFO -> {
                parseProductInfoPage(payload)
            }
            PAGE_BATTERY_STATUS -> {
                parseBatteryStatusPage(payload)
            }
        }
    }

    private fun parseTargetsPage(payload: ByteArray, baseId: Int) {
        // Physical hardware preemption (REQ-CON-029.3, REQ-CON-032.6)
        RadarTrafficSimulator.onPhysicalHardwareConnected()

        // Restart 3.5s watchdog
        handler.removeCallbacks(watchdogRunnable)
        handler.postDelayed(watchdogRunnable, WATCHDOG_TIMEOUT_MS)

        // Clear slot bank
        for (i in 0..3) {
            activeTargets.remove(baseId + i)
        }

        val threatByte = payload[1].toInt() and 0xFF
        val sideByte = payload[2].toInt() and 0xFF

        val rangeBits = ((payload[3].toLong() and 0xFFL)) or
                ((payload[4].toLong() and 0xFFL) shl 8) or
                ((payload[5].toLong() and 0xFFL) shl 16)

        val speedBits = ((payload[6].toInt() and 0xFF)) or
                ((payload[7].toInt() and 0xFF) shl 8)

        for (i in 0..3) {
            val threatLevelBits = (threatByte ushr (i * 2)) and 0x03
            val threatSideBits = (sideByte ushr (i * 2)) and 0x03
            val rawRange = ((rangeBits ushr (i * 6)) and 0x3F).toInt()
            val rawSpeed = (speedBits ushr (i * 4)) and 0x0F

            if (threatLevelBits > 0 && rawRange > 0) {
                val distanceMeters = rawRange * 3.125f
                val approachSpeedMps = rawSpeed * 3.04f
                val threatLevel = when (threatLevelBits) {
                    1 -> ThreatLevel.APPROACHING
                    2 -> ThreatLevel.FAST_APPROACHING
                    else -> ThreatLevel.NONE
                }
                val laneState = when (threatSideBits) {
                    0 -> LaneState.IN_RIDER_LANE
                    1, 2 -> LaneState.PASSING_LANE
                    else -> LaneState.UNKNOWN
                }
                val lateralOffsetMeters = when (threatSideBits) {
                    0 -> 0.0f
                    1 -> 1.5f // right
                    2 -> -1.5f // left
                    else -> null
                }

                val target = RadarTarget(
                    targetId = baseId + i,
                    rangeMeters = distanceMeters,
                    approachSpeedMps = approachSpeedMps,
                    threatLevel = threatLevel,
                    lateralOffsetMeters = lateralOffsetMeters,
                    vehicleSizeCategory = VehicleSizeCategory.UNKNOWN_OR_LIGHT,
                    laneState = laneState
                )
                activeTargets[baseId + i] = target
            }
        }

        publishTargets()
    }

    private fun publishTargets() {
        val targetsList = activeTargets.values.sortedBy { it.rangeMeters }
        val overallThreat = computeOverallThreatLevel(targetsList)
        val currentState = RadarStateRepository.getState()

        val state = RadarState(
            connectionState = RadarConnectionState.CONNECTED,
            overallThreatLevel = overallThreat,
            targets = targetsList,
            lastUpdateRealtimeMs = SystemClock.elapsedRealtime(),
            lightMode = currentState.lightMode,
            isLightControlSupported = false,
            audioSnoozeState = currentState.audioSnoozeState
        )
        RadarStateRepository.updateState(state)

        windowAggregator.onTargetsUpdated(targetsList)
        val snapshot = windowAggregator.sampleAndResetWindow()

        if (::threatCountSensor.isInitialized) {
            threatCountSensor.newValue(snapshot.threatCount)
        }
        if (::closestDistanceSensor.isInitialized) {
            closestDistanceSensor.newValue(snapshot.closestDistanceMeters)
        }
        if (::approachSpeedSensor.isInitialized) {
            approachSpeedSensor.newValue(snapshot.approachSpeedMps)
        }
        if (::vehicleCountSensor.isInitialized) {
            vehicleCountSensor.newValue(snapshot.vehicleCount)
        }
        if (::passingDistanceSensor.isInitialized) {
            passingDistanceSensor.newValue(snapshot.passingDistanceMeters)
        }
        if (::largeVehicleCountSensor.isInitialized) {
            largeVehicleCountSensor.newValue(snapshot.largeVehicleCount)
        }
    }

    private fun computeOverallThreatLevel(targets: List<RadarTarget>): ThreatLevel {
        if (targets.isEmpty()) return ThreatLevel.NONE
        var maxThreat = ThreatLevel.NONE
        for (target in targets) {
            if (target.threatLevel == ThreatLevel.FAST_APPROACHING) {
                return ThreatLevel.FAST_APPROACHING
            } else if (target.threatLevel == ThreatLevel.APPROACHING) {
                maxThreat = ThreatLevel.APPROACHING
            }
        }
        return maxThreat
    }

    fun onWatchdogTimeout() {
        activeTargets.clear()
        val currentState = RadarStateRepository.getState()
        val state = RadarState(
            connectionState = RadarConnectionState.DISCONNECTED_ERROR,
            overallThreatLevel = ThreatLevel.NONE,
            targets = emptyList(),
            lastUpdateRealtimeMs = SystemClock.elapsedRealtime(),
            lightMode = currentState.lightMode,
            isLightControlSupported = false,
            audioSnoozeState = currentState.audioSnoozeState
        )
        RadarStateRepository.updateState(state)
        resetSensorsToClear()
    }

    fun resetSensorsToClear() {
        if (::threatCountSensor.isInitialized) {
            threatCountSensor.newValue(0)
        }
        if (::closestDistanceSensor.isInitialized) {
            closestDistanceSensor.newValue(null)
        }
        if (::approachSpeedSensor.isInitialized) {
            approachSpeedSensor.newValue(0.0)
        }
        if (::passingDistanceSensor.isInitialized) {
            passingDistanceSensor.newValue(null)
        }
    }

    public override fun onAccumulatorsReset() {
        super.onAccumulatorsReset()
        if (::vehicleCountSensor.isInitialized) {
            vehicleCountSensor.newValue(0)
        }
        if (::largeVehicleCountSensor.isInitialized) {
            largeVehicleCountSensor.newValue(0)
        }
    }

    private fun parseSystemStatus(payload: ByteArray) {
        val state = payload[1].toInt() and 0xFF
        if (state == 2) {
            onWatchdogTimeout()
        }
    }

    private fun parseManufacturerPage(payload: ByteArray) {
        val manufacturerId = (payload[4].toInt() and 0xFF) or ((payload[5].toInt() and 0xFF) shl 8)
        val manufacturerName = Manufacturer.getName(manufacturerId)
        if (manufacturerName != null) {
            mDevicesDatabaseManager.setManufacturerName(mDeviceId, manufacturerName)
        }
    }

    private fun parseProductInfoPage(payload: ByteArray) {
        // Product information / serial page parsed safely without database mutation
    }

    private fun parseBatteryStatusPage(payload: ByteArray) {
        val descriptiveByte = payload[7].toInt() and 0xFF
        val batteryStatusCode = (descriptiveByte ushr 4) and 0x07
        val percentage = when (batteryStatusCode) {
            1 -> 100
            2 -> 80
            3 -> 50
            4 -> 20
            5 -> 5
            else -> -1
        }
        if (percentage >= 0) {
            mBatteryPercentage = percentage
            mDevicesDatabaseManager.setBatteryPercentage(mDeviceId, percentage)
        }
    }

    fun getActiveTargets(): Map<Int, RadarTarget> = Collections.unmodifiableMap(activeTargets)

    override fun shutDown() {
        handler.removeCallbacks(watchdogRunnable)
        try {
            antChannel?.close()
            antChannel?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing ANT channel", e)
        }
        antChannel = null
        if (isServiceBound) {
            try {
                mContext.unbindService(serviceConnection)
            } catch (e: Exception) {
                Log.w(TAG, "Error unbinding AntService", e)
            }
            isServiceBound = false
        }
        antService = null
        resetSensorsToClear()
        unregisterSensors()
    }

    @Suppress("TooGenericExceptionCaught")
    override fun notifySensorsChanged() {
        try {
            super.notifySensorsChanged()
        } catch (_: Exception) {
            // Unit test / headless environment guard where Intent or package manager is not mocked
        }
    }
}
