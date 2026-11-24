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

package com.atrainingtracker.banalservice.database

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.banalservice.Protocol
import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.trainingtracker.migration.DatabaseSchemaRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Unit tests verifying `Devices.db` v13->v14 migration, `polarLiveImuEnabled` DEFAULT 0 parity
 * between `onCreate` and `onUpgrade`, and `DatabaseSchemaRegistry` sync (`ATT-8016`, `REQ-SET-112.1`, `TST-SET-114.1`).
 */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34])
class DevicesDatabaseManagerMigrationV14Test {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun testDbVersion14_andSchemaRegistryParity() {
        // TST-SET-114.1
        assertTrue(
            "DevicesDbHelper.DB_VERSION must be >= 14",
            DevicesDatabaseManager.DevicesDbHelper.DB_VERSION >= 14
        )
        assertEquals("polarLiveImuEnabled", DevicesDatabaseManager.DevicesDbHelper.POLAR_LIVE_IMU_ENABLED)
        val maxVersion = DatabaseSchemaRegistry.getMaxSupportedVersion("Devices.db")
        assertNotNull("Devices.db must be registered in DatabaseSchemaRegistry", maxVersion)
        assertEquals(DevicesDatabaseManager.DevicesDbHelper.DB_VERSION, maxVersion)
    }

    @Test
    fun testOnCreateAndOnUpgrade13To14_haveIdenticalColumnsAndDefaultZero() {
        // TST-SET-114.1
        val freshDb = SQLiteDatabase.create(null)
        val helper = DevicesDatabaseManager.DevicesDbHelper(context)
        helper.onCreate(freshDb)
        val freshColumns = readTableColumns(freshDb)

        val upgradedDb = SQLiteDatabase.create(null)
        upgradedDb.execSQL(
            """
            CREATE TABLE ${DevicesDatabaseManager.DevicesDbHelper.DEVICES} (
                ${DevicesDatabaseManager.DevicesDbHelper.C_ID} INTEGER PRIMARY KEY AUTOINCREMENT,
                ${DevicesDatabaseManager.DevicesDbHelper.PROTOCOL} text,
                ${DevicesDatabaseManager.DevicesDbHelper.ANT_DEVICE_NUMBER} int,
                ${DevicesDatabaseManager.DevicesDbHelper.BT_ADDRESS} text,
                ${DevicesDatabaseManager.DevicesDbHelper.DEVICE_TYPE} text,
                ${DevicesDatabaseManager.DevicesDbHelper.PAIRED} int,
                ${DevicesDatabaseManager.DevicesDbHelper.NAME} text,
                ${DevicesDatabaseManager.DevicesDbHelper.MANUFACTURER_NAME} text,
                ${DevicesDatabaseManager.DevicesDbHelper.CALIBRATION_FACTOR} real,
                ${DevicesDatabaseManager.DevicesDbHelper.LAST_ACTIVE} text,
                ${DevicesDatabaseManager.DevicesDbHelper.LAST_BATTERY_PERCENTAGE} int,
                ${DevicesDatabaseManager.DevicesDbHelper.BASELINE_CIRCUMFERENCE} real DEFAULT 0.0,
                ${DevicesDatabaseManager.DevicesDbHelper.POLAR_H10_OFFLINE_RECORDING} int DEFAULT 1
            )
            """.trimIndent()
        )
        val preUpgradeValues = ContentValues().apply {
            put(DevicesDatabaseManager.DevicesDbHelper.PROTOCOL, Protocol.BLUETOOTH_LE.name)
            put(DevicesDatabaseManager.DevicesDbHelper.BT_ADDRESS, "AA:BB:CC:DD:EE:10")
            put(DevicesDatabaseManager.DevicesDbHelper.DEVICE_TYPE, DeviceType.HRM.name)
            put(DevicesDatabaseManager.DevicesDbHelper.PAIRED, 1)
            put(DevicesDatabaseManager.DevicesDbHelper.NAME, "Polar H10 12345678")
            put(DevicesDatabaseManager.DevicesDbHelper.POLAR_H10_OFFLINE_RECORDING, 1)
        }
        val rowId = upgradedDb.insert(DevicesDatabaseManager.DevicesDbHelper.DEVICES, null, preUpgradeValues)
        assertTrue(rowId > 0)

        helper.onUpgrade(upgradedDb, 13, 14)
        // Idempotent re-invocation check
        helper.onUpgrade(upgradedDb, 13, 14)

        val upgradedColumns = readTableColumns(upgradedDb)
        assertEquals("onCreate and onUpgrade(13, 14) schemas must match", freshColumns, upgradedColumns)
        assertTrue(upgradedColumns.containsKey(DevicesDatabaseManager.DevicesDbHelper.POLAR_LIVE_IMU_ENABLED))

        upgradedDb.rawQuery(
            "SELECT ${DevicesDatabaseManager.DevicesDbHelper.POLAR_H10_OFFLINE_RECORDING}, " +
                "${DevicesDatabaseManager.DevicesDbHelper.POLAR_LIVE_IMU_ENABLED} " +
                "FROM ${DevicesDatabaseManager.DevicesDbHelper.DEVICES} WHERE ${DevicesDatabaseManager.DevicesDbHelper.C_ID} = ?",
            arrayOf(rowId.toString())
        ).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(1, c.getInt(0))
            assertEquals(0, c.getInt(1))
        }

        freshDb.close()
        upgradedDb.close()
    }

    @Test
    fun testIsAndSetPolarLiveImuEnabled_persistsOptInSafely() {
        // TST-SET-114.1
        val dbManager = DevicesDatabaseManager.getInstance(context)
        val deviceId = dbManager.insertNewBluetoothDeviceIntoDB(
            DeviceType.HRM,
            "AA:BB:CC:DD:EE:20",
            "Polar Verity Sense AABBCCDD",
            "Polar Electro",
            90,
            true
        )
        assertTrue(deviceId > 0)
        assertFalse("Opt-in must default to false", dbManager.isPolarLiveImuEnabled(deviceId))

        dbManager.setPolarLiveImuEnabled(deviceId, true)
        assertTrue(dbManager.isPolarLiveImuEnabled(deviceId))

        dbManager.setPolarLiveImuEnabled(deviceId, false)
        assertFalse(dbManager.isPolarLiveImuEnabled(deviceId))
    }

    private fun readTableColumns(db: SQLiteDatabase): Map<String, String> {
        val cols = linkedMapOf<String, String>()
        db.rawQuery("PRAGMA table_info(${DevicesDatabaseManager.DevicesDbHelper.DEVICES})", null).use { c ->
            val nameIdx = c.getColumnIndexOrThrow("name")
            val typeIdx = c.getColumnIndexOrThrow("type")
            while (c.moveToNext()) {
                cols[c.getString(nameIdx)] = c.getString(typeIdx)
            }
        }
        return cols
    }
}
