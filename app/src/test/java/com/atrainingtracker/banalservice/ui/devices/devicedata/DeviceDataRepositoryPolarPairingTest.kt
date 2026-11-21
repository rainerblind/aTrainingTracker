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

package com.atrainingtracker.banalservice.ui.devices.devicedata

import android.content.ContentValues
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager
import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.banalservice.devices.bluetooth_le.polar.PolarOfflineCapabilityStore
import com.atrainingtracker.banalservice.devices.bluetooth_le.polar.PolarOfflineGattCoordinator
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests verifying encapsulated live pairing-state detection in [DeviceDataRepository.isPolarRetrySensorPaired]
 * against SQLite `Devices.db` (`REQ-SET-125.1`, `TST-SET-127.1`, `ATT-9509`).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DeviceDataRepositoryPolarPairingTest {

    private lateinit var appContext: Context
    private lateinit var db: DevicesDatabaseManager
    private lateinit var repository: DeviceDataRepository

    @Before
    fun setUp() {
        appContext = ApplicationProvider.getApplicationContext()
        DeviceDataRepository.resetForTesting(null)
        DevicesDatabaseManager.resetForTesting()
        PolarOfflineCapabilityStore.resetForTesting()
        PolarOfflineGattCoordinator.resetForTesting()
        db = DevicesDatabaseManager.getInstance(appContext)
        repository = DeviceDataRepository(
            application = appContext,
            ioDispatcher = Dispatchers.Unconfined,
            mainDispatcher = Dispatchers.Unconfined
        )
    }

    @After
    fun tearDown() {
        repository.cancelScopeForTesting()
        DeviceDataRepository.resetForTesting(null)
        DevicesDatabaseManager.resetForTesting()
        PolarOfflineCapabilityStore.resetForTesting()
        PolarOfflineGattCoordinator.resetForTesting()
    }

    @Test
    fun pairedTargetVeritySensor_returnsTrue_byPolarIdAndMac() {
        // Verifies REQ-SET-125 / TST-SET-127
        db.insertNewBluetoothDeviceIntoDB(
            DeviceType.HRM,
            "A0:9E:1A:82:56:01",
            "Polar Sense 24825632",
            "Polar Electro Oy",
            85,
            true
        )

        assertTrue(repository.isPolarRetrySensorPaired("24825632", isVeritySense = true))
        assertTrue(repository.isPolarRetrySensorPaired("A0:9E:1A:82:56:01", isVeritySense = true))
    }

    @Test
    fun unpairedTargetSensor_withRetainedRowPairedZero_returnsFalse() {
        // Verifies REQ-SET-125 / TST-SET-127
        db.insertNewBluetoothDeviceIntoDB(
            DeviceType.HRM,
            "A0:9E:1A:82:56:02",
            "Polar Sense 24825632",
            "Polar Electro Oy",
            85,
            false
        )

        assertFalse(repository.isPolarRetrySensorPaired("24825632", isVeritySense = true))
    }

    @Test
    fun deletedTargetSensor_returnsFalse() {
        // Verifies REQ-SET-125 / TST-SET-127
        val deviceId = db.insertNewBluetoothDeviceIntoDB(
            DeviceType.HRM,
            "A0:9E:1A:82:56:03",
            "Polar H10 9A1B2C3D",
            "Polar Electro Oy",
            90,
            true
        )
        assertTrue(repository.isPolarRetrySensorPaired("9A1B2C3D", isVeritySense = false))

        db.deleteDevice(deviceId)

        assertFalse(repository.isPolarRetrySensorPaired("9A1B2C3D", isVeritySense = false))
    }

    @Test
    fun renamedPairedSensor_matchesPersistedPolarIdInCapabilityStore() {
        // Verifies REQ-SET-125 / TST-SET-127
        val mac = "A0:9E:1A:82:56:04"
        db.insertNewBluetoothDeviceIntoDB(
            DeviceType.HRM,
            mac,
            "My Custom Armband",
            "Polar Electro Oy",
            80,
            true
        )
        PolarOfflineCapabilityStore.recordSdkDeviceType(
            context = appContext,
            macAddress = mac,
            deviceType = PolarOfflineCapabilityStore.PolarDeviceType.VERITY_SENSE,
            polarDeviceId = "24825632"
        )

        assertTrue(repository.isPolarRetrySensorPaired("24825632", isVeritySense = true))
    }

    @Test
    fun siblingPolarIsolation_pairedH10DoesNotMaskUnpairedVeritySense() {
        // Verifies REQ-SET-125 / TST-SET-127
        db.insertNewBluetoothDeviceIntoDB(
            DeviceType.HRM,
            "A0:9E:1A:11:22:33",
            "Polar H10 9A1B2C3D",
            "Polar Electro Oy",
            95,
            true
        )
        db.insertNewBluetoothDeviceIntoDB(
            DeviceType.HRM,
            "A0:9E:1A:82:56:05",
            "Polar Sense 24825632",
            "Polar Electro Oy",
            85,
            false
        )

        assertFalse(repository.isPolarRetrySensorPaired("24825632", isVeritySense = true))
        assertTrue(repository.isPolarRetrySensorPaired("9A1B2C3D", isVeritySense = false))
    }

    @Test
    fun nullOrBlankPolarDeviceId_usesManufacturerAwareFamilyFallback() {
        // Verifies REQ-SET-125 / TST-SET-127
        db.insertNewBluetoothDeviceIntoDB(
            DeviceType.HRM,
            "A0:9E:1A:44:55:66",
            "Verity Armband",
            "Polar Electro Oy",
            80,
            true
        )

        assertTrue(repository.isPolarRetrySensorPaired(null, isVeritySense = true))
        assertTrue(repository.isPolarRetrySensorPaired("   ", isVeritySense = true))
        assertFalse(repository.isPolarRetrySensorPaired(null, isVeritySense = false))
    }

    @Test
    fun rePairingFlip_transitionsFromFalseToTrueImmediately() {
        // Verifies REQ-SET-125 / TST-SET-127
        val deviceId = db.insertNewBluetoothDeviceIntoDB(
            DeviceType.HRM,
            "A0:9E:1A:82:56:07",
            "Polar Sense 24825632",
            "Polar Electro Oy",
            85,
            false
        )
        assertFalse(repository.isPolarRetrySensorPaired("24825632", isVeritySense = true))

        val values = ContentValues().apply {
            put(DevicesDatabaseManager.DevicesDbHelper.PAIRED, 1)
        }
        db.database.update(
            DevicesDatabaseManager.DevicesDbHelper.DEVICES,
            values,
            "${DevicesDatabaseManager.DevicesDbHelper.C_ID} = ?",
            arrayOf(deviceId.toString())
        )

        assertTrue(repository.isPolarRetrySensorPaired("24825632", isVeritySense = true))
    }
}
