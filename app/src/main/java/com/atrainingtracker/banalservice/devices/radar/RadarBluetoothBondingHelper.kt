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

package com.atrainingtracker.banalservice.devices.radar

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import com.atrainingtracker.banalservice.devices.bluetooth_le.MyBTLEDevice

/**
 * Outcome of attempting to bond a V2-capable BLE radar peripheral or opening system Bluetooth settings
 * (`REQ-CON-061.2`).
 */
enum class RadarBondActionResult {
    BONDING_INITIATED,
    ALREADY_BONDED,
    OPENED_BLUETOOTH_SETTINGS,
    FAILED
}

/**
 * Android 12+/13+ permission-safe helper initiating BLE SMP bonding (`createBond()`) for a
 * V2-capable cycling radar, falling back cleanly to [Settings.ACTION_BLUETOOTH_SETTINGS] when
 * `BLUETOOTH_CONNECT` is missing, Bluetooth is unavailable, or `createBond()` fails (`REQ-CON-061.2`).
 */
object RadarBluetoothBondingHelper {

    private const val TAG = "RadarBondingHelper"

    @SuppressLint("NewApi", "MissingPermission")
    fun requestBondOrOpenSettings(
        context: Context,
        macAddress: String?,
        sdkInt: Int = Build.VERSION.SDK_INT,
        hasBluetoothConnectPermission: (Context) -> Boolean = ::defaultHasBluetoothConnectPermission,
        bluetoothAdapterProvider: (Context) -> BluetoothAdapter? = ::defaultBluetoothAdapter,
        settingsLauncher: (Context, Intent) -> Boolean = ::defaultLaunchBluetoothSettings
    ): RadarBondActionResult {
        if (sdkInt >= Build.VERSION_CODES.S && !hasBluetoothConnectPermission(context)) {
            Log.w(TAG, "Missing BLUETOOTH_CONNECT permission; falling back to Bluetooth settings.")
            return openBluetoothSettings(context, settingsLauncher)
        }

        val normalizedMac = macAddress?.trim()?.takeIf { it.isNotEmpty() }
            ?: return openBluetoothSettings(context, settingsLauncher)

        val adapter = try {
            bluetoothAdapterProvider(context)
        } catch (e: RuntimeException) {
            Log.w(TAG, "Unable to obtain BluetoothAdapter; falling back to Bluetooth settings.", e)
            null
        }
        if (adapter == null || !adapter.isEnabled) {
            return openBluetoothSettings(context, settingsLauncher)
        }

        try {
            val device: BluetoothDevice = if (sdkInt >= Build.VERSION_CODES.TIRAMISU) {
                val addressType = if (MyBTLEDevice.isRandomStaticAddress(normalizedMac)) {
                    BluetoothDevice.ADDRESS_TYPE_RANDOM
                } else {
                    BluetoothDevice.ADDRESS_TYPE_PUBLIC
                }
                adapter.getRemoteLeDevice(normalizedMac, addressType)
            } else {
                adapter.getRemoteDevice(normalizedMac)
            }

            if (device.bondState == BluetoothDevice.BOND_BONDED) {
                RadarStateRepository.setV2BondingRequired(false, null)
                return RadarBondActionResult.ALREADY_BONDED
            }
            if (device.bondState == BluetoothDevice.BOND_BONDING) {
                return RadarBondActionResult.BONDING_INITIATED
            }

            if (device.createBond()) {
                return RadarBondActionResult.BONDING_INITIATED
            }
            Log.w(TAG, "BluetoothDevice.createBond() returned false for $normalizedMac; falling back to settings.")
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException initiating BLE bond; falling back to Bluetooth settings.", e)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Invalid Bluetooth MAC '$normalizedMac'; falling back to Bluetooth settings.", e)
        }

        return openBluetoothSettings(context, settingsLauncher)
    }

    private fun openBluetoothSettings(
        context: Context,
        settingsLauncher: (Context, Intent) -> Boolean
    ): RadarBondActionResult {
        val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
            if (context !is Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        return if (settingsLauncher(context, intent)) {
            RadarBondActionResult.OPENED_BLUETOOTH_SETTINGS
        } else {
            RadarBondActionResult.FAILED
        }
    }

    @SuppressLint("NewApi")
    private fun defaultHasBluetoothConnectPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun defaultBluetoothAdapter(context: Context): BluetoothAdapter? {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        return manager?.adapter
    }

    private fun defaultLaunchBluetoothSettings(context: Context, intent: Intent): Boolean {
        return try {
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "No activity found to handle ACTION_BLUETOOTH_SETTINGS.", e)
            false
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException launching ACTION_BLUETOOTH_SETTINGS.", e)
            false
        }
    }
}
