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

@file:Suppress("PackageNaming")

package com.atrainingtracker.banalservice.devices.bluetooth_le

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Context
import com.atrainingtracker.banalservice.devices.bluetooth_le.polar.PolarOfflineCapabilityStore
import com.atrainingtracker.banalservice.devices.bluetooth_le.polar.PolarOfflineGattCoordinator
import java.util.Queue
import java.util.UUID

/**
 * Coordinates BLE Device Information Service (`0x180A`) reconnect metadata backfill and
 * deduplication across [MyBTLEDevice] subclasses (`REQ-CON-062`, `ATT-8723`).
 *
 * Ownership is partitioned strictly to eliminate duplicate GATT reads:
 * - [enqueueMissingManufacturerRead] + [onDisCharacteristicRead] handle `0x2A29` Manufacturer Name
 *   for all [MyBTLEDevice] subclasses (post-`configureServices`, serialized behind telemetry CCCDs
 *   and persisted off the Binder thread via `mHandler`).
 * - [enqueueMissingPolarDisReads] handles conditional Polar `0x2A24`/`0x2A25` reads on
 *   [BTLEHeartRateDevice] strictly when `hasPmd || hasPftp` and the corresponding entry is missing
 *   from [PolarOfflineCapabilityStore], while re-hydrating in-memory [PolarOfflineGattCoordinator]
 *   aliases on cold-start reconnects when `0x2A25` is already persisted.
 */
object BleDisReconnectBackfillHelper {

    /**
     * Returns `true` when [currentManufacturerName] is missing (`null`), blank/whitespace-only,
     * or contains any `'\u0000'` byte (`REQ-CON-062.1`).
     */
    @JvmStatic
    fun needsManufacturerBackfill(currentManufacturerName: String?): Boolean {
        if (currentManufacturerName == null) return true
        if (BleDisStringSanitizer.containsNul(currentManufacturerName)) return true
        return BleDisStringSanitizer.sanitize(currentManufacturerName) == null
    }

    /**
     * Returns `true` when [queue] already contains a pending [MyBTLEDevice.ReadCharacteristicOp]
     * targeting [charUuid].
     */
    @JvmStatic
    fun isCharacteristicReadQueued(
        queue: Queue<MyBTLEDevice.GattOperation>?,
        charUuid: UUID
    ): Boolean {
        if (queue == null) return false
        for (op in queue) {
            if (op is MyBTLEDevice.ReadCharacteristicOp && charUuid == op.characteristic?.uuid) {
                return true
            }
        }
        return false
    }

    /**
     * Enqueues a serialized `0x2A29` Manufacturer Name read on [device] if its persisted
     * manufacturer name is missing or NUL-dirty and `0x180A`/`0x2A29` is advertised by [gatt]
     * (`REQ-CON-062.1`).
     */
    @JvmStatic
    fun enqueueMissingManufacturerRead(device: MyBTLEDevice, gatt: BluetoothGatt?): Boolean {
        if (gatt == null || device.mIsShutDown || device.isGattPaused || device.deviceId <= 0L) {
            return false
        }
        if (!needsManufacturerBackfill(device.manufacturerName)) {
            return false
        }
        if (isCharacteristicReadQueued(
                device.mGattQueue,
                BluetoothConstants.UUID_CHARACTERISTIC_MANUFACTURER_NAME
            )
        ) {
            return false
        }
        val disService = gatt.getService(BluetoothConstants.UUID_SERVICE_DEVICE_INFORMATION)
            ?: return false
        val manufacturerChar = disService.getCharacteristic(
            BluetoothConstants.UUID_CHARACTERISTIC_MANUFACTURER_NAME
        ) ?: return false
        device.enqueueReadCharacteristic(manufacturerChar)
        return true
    }

    /**
     * Handles a successful (`GATT_SUCCESS`) cloned [snapshot] read callback for `0x2A29`,
     * sanitizing via [BleDisStringSanitizer.readSanitizedString] and dispatching SQLite persistence
     * onto `device.mHandler` so the Binder thread advances `mGattQueue` immediately (`REQ-CON-062.2`).
     */
    @JvmStatic
    fun onDisCharacteristicRead(
        device: MyBTLEDevice,
        gatt: BluetoothGatt?,
        snapshot: BluetoothGattCharacteristic?
    ): Boolean {
        if (gatt == null || snapshot == null || device.mIsShutDown) {
            return false
        }
        if (BluetoothConstants.UUID_CHARACTERISTIC_MANUFACTURER_NAME != snapshot.uuid) {
            return false
        }
        val cleanManufacturer = BleDisStringSanitizer.readSanitizedString(snapshot) ?: return false
        val handler = device.mHandler ?: return false
        return handler.post { device.applyBackfilledManufacturerName(gatt, cleanManufacturer) }
    }

    /**
     * Conditionally enqueues missing Polar DIS `0x2A24` (Model Number) and `0x2A25` (Serial Number)
     * reads on [device] when `hasPmd || hasPftp`, skipping already-populated store entries and
     * re-hydrating [PolarOfflineGattCoordinator] in-memory aliases on cold-start reconnects
     * (`REQ-CON-062.3`, preserving `REQ-CON-049.2` non-Polar isolation).
     */
    @JvmStatic
    fun enqueueMissingPolarDisReads(
        device: MyBTLEDevice,
        context: Context?,
        gatt: BluetoothGatt?,
        hasPmd: Boolean,
        hasPftp: Boolean
    ): Int {
        if (!(hasPmd || hasPftp) || gatt == null || device.mIsShutDown || device.isGattPaused) {
            return 0
        }
        val address = device.address
        val existingPolarId = PolarOfflineCapabilityStore.getPolarDeviceId(context, address)
        if (existingPolarId != null) {
            PolarOfflineGattCoordinator.registerDeviceAliases(existingPolarId, address)
        }
        val disService = gatt.getService(BluetoothConstants.UUID_SERVICE_DEVICE_INFORMATION)
            ?: return 0
        var enqueued = 0
        val needsModel = BleDisStringSanitizer.sanitize(
            PolarOfflineCapabilityStore.getModelNumber(context, address)
        ) == null
        if (needsModel && !isCharacteristicReadQueued(
                device.mGattQueue,
                BluetoothConstants.UUID_CHARACTERISTIC_MODEL_NUMBER
            )
        ) {
            val modelChar = disService.getCharacteristic(
                BluetoothConstants.UUID_CHARACTERISTIC_MODEL_NUMBER
            )
            if (modelChar != null) {
                device.enqueueReadCharacteristic(modelChar)
                enqueued++
            }
        }
        if (existingPolarId == null && !isCharacteristicReadQueued(
                device.mGattQueue,
                BluetoothConstants.UUID_CHARACTERISTIC_SERIAL_NUMBER
            )
        ) {
            val serialChar = disService.getCharacteristic(
                BluetoothConstants.UUID_CHARACTERISTIC_SERIAL_NUMBER
            )
            if (serialChar != null) {
                device.enqueueReadCharacteristic(serialChar)
                enqueued++
            }
        }
        return enqueued
    }
}
