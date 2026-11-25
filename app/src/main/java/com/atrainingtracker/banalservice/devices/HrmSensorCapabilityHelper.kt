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

package com.atrainingtracker.banalservice.devices

import android.content.Context
import android.os.Build
import com.atrainingtracker.banalservice.Protocol
import com.atrainingtracker.banalservice.devices.bluetooth_le.polar.PolarOfflineGattCoordinator
import com.atrainingtracker.banalservice.sensor.SensorType

/**
 * Hardware-grounded capability, opt-in, and OS-level gate for [DeviceType.HRM] source selection
 * across Polar Live-IMU metrics (`CADENCE`, `STRIDES`, `VERTICAL_OSCILLATION`, `GROUND_CONTACT_TIME`),
 * parallel to [BikePowerSensorsHelper] (`REQ-CON-057`, `REQ-CON-064`, `ATT-8991`, `ATT-8446`).
 */
object HrmSensorCapabilityHelper {

    /**
     * Returns whether [sensorType] requires Polar Live-IMU hardware, user opt-in, and Android 13+.
     */
    @JvmStatic
    fun requiresPolarLiveImu(sensorType: SensorType?): Boolean =
        sensorType == SensorType.CADENCE ||
            sensorType == SensorType.STRIDES ||
            sensorType == SensorType.VERTICAL_OSCILLATION ||
            sensorType == SensorType.GROUND_CONTACT_TIME

    private fun isAndroid13PlusOrJvm(sdkInt: Int): Boolean =
        sdkInt == 0 || sdkInt >= Build.VERSION_CODES.TIRAMISU

    /**
     * Returns whether an HRM device with persisted [protocol], [deviceName], [manufacturer],
     * [btAddress], and [polarLiveImuEnabled] opt-in can produce [sensorType] telemetry on [sdkInt].
     *
     * - [SensorType.CADENCE] & [SensorType.STRIDES]: BLE Polar H10 and BLE Polar Verity Sense/OH1 only,
     *   strictly when [polarLiveImuEnabled] is `true` on Android 13+ (`sdkInt >= TIRAMISU`, or `0` in plain-JVM tests).
     * - [SensorType.VERTICAL_OSCILLATION] & [SensorType.GROUND_CONTACT_TIME]: BLE Polar H10 only,
     *   strictly when [polarLiveImuEnabled] is `true` on Android 13+.
     * - All other HRM-mapped sensor types (`HR`, `RR_INTERVAL`, `HRV_*`): always supported (`REQ-HRV-050.1`).
     */
    @JvmStatic
    @JvmOverloads
    fun isSensorSupported(
        context: Context?,
        sensorType: SensorType?,
        protocol: Protocol?,
        deviceName: String?,
        manufacturer: String?,
        btAddress: String?,
        polarLiveImuEnabled: Boolean = false,
        sdkInt: Int = Build.VERSION.SDK_INT
    ): Boolean {
        if (sensorType == null) return false
        return when (sensorType) {
            SensorType.CADENCE,
            SensorType.STRIDES -> {
                if (protocol != Protocol.BLUETOOTH_LE || !polarLiveImuEnabled || !isAndroid13PlusOrJvm(sdkInt)) {
                    return false
                }
                val safeName = deviceName.orEmpty()
                val safeManufacturer = manufacturer.orEmpty()
                isPolarH10(context, safeName, safeManufacturer, btAddress) ||
                    isPolarVeritySense(context, safeName, safeManufacturer, btAddress)
            }
            SensorType.VERTICAL_OSCILLATION,
            SensorType.GROUND_CONTACT_TIME -> {
                if (protocol != Protocol.BLUETOOTH_LE || !polarLiveImuEnabled || !isAndroid13PlusOrJvm(sdkInt)) {
                    return false
                }
                isPolarH10(context, deviceName.orEmpty(), manufacturer.orEmpty(), btAddress)
            }
            else -> true
        }
    }

    private fun isPolarH10(
        context: Context?,
        name: String,
        manufacturer: String,
        btAddress: String?
    ): Boolean {
        return runCatching {
            PolarOfflineGattCoordinator.isPolarH10Device(context, name, manufacturer, btAddress)
        }.getOrElse {
            runCatching {
                PolarOfflineGattCoordinator.isPolarH10Device(null, name, manufacturer, btAddress)
            }.getOrDefault(false)
        }
    }

    private fun isPolarVeritySense(
        context: Context?,
        name: String,
        manufacturer: String,
        btAddress: String?
    ): Boolean {
        return runCatching {
            PolarOfflineGattCoordinator.isPolarVeritySenseDevice(context, name, manufacturer, btAddress)
        }.getOrElse {
            runCatching {
                PolarOfflineGattCoordinator.isPolarVeritySenseDevice(null, name, manufacturer, btAddress)
            }.getOrDefault(false)
        }
    }
}
