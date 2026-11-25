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
import android.database.MatrixCursor
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.Protocol
import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.banalservice.devices.bluetooth_le.BleDisStringSanitizer
import com.atrainingtracker.banalservice.ui.devices.devicedata.RawDeviceDataProvider
import com.atrainingtracker.trainingtracker.database.EquipmentAndSportTypeDiscoveryManager
import com.atrainingtracker.trainingtracker.database.EquipmentDbHelper
import com.atrainingtracker.trainingtracker.migration.DatabaseSchemaRegistry
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Unit & Robolectric verification suite for `Devices.db` v14 -> v15 NUL-byte row cleanup migration,
 * `onCreate`/`onUpgrade` schema parity, `DatabaseSchemaRegistry` sync, and write/read sanitization
 * across [DevicesDatabaseManager] & [RawDeviceDataProvider] (`REQ-CON-060.3`, `TST-CON-060.3`, `ATT-8996`).
 */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34])
class DevicesDatabaseManagerMigrationV15Test {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        DevicesDatabaseManager.resetForTesting()
    }

    @After
    fun tearDown() {
        DevicesDatabaseManager.resetForTesting()
    }

    @Test
    fun testDbVersion15_andSchemaRegistryParity_TST_CON_060_3() {
        assertTrue(
            "DevicesDbHelper.DB_VERSION must be >= 15",
            DevicesDatabaseManager.DevicesDbHelper.DB_VERSION >= 15
        )
        val maxVersion = DatabaseSchemaRegistry.getMaxSupportedVersion("Devices.db")
        assertNotNull("Devices.db must be registered in DatabaseSchemaRegistry", maxVersion)
        assertEquals(DevicesDatabaseManager.DevicesDbHelper.DB_VERSION, maxVersion)
    }

    @Test
    fun testOnCreateAndOnUpgrade14To15_identicalSchemasAndIdempotentNulSanitization_TST_CON_060_3() {
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
                ${DevicesDatabaseManager.DevicesDbHelper.POLAR_H10_OFFLINE_RECORDING} int DEFAULT 1,
                ${DevicesDatabaseManager.DevicesDbHelper.POLAR_LIVE_IMU_ENABLED} int DEFAULT 0
            )
            """.trimIndent()
        )

        val h10Id = insertRow(upgradedDb, "AA:BB:CC:DD:EE:25", "Polar H10 12345678\u0000", "Polar Electro Oy\u0000")
        val verityId = insertRow(upgradedDb, "AA:BB:CC:DD:EE:28", "  Polar Sense A1B2C3D4 \u0000", "Polar Electro Oy\u0000\u0000")
        val cleanId = insertRow(upgradedDb, "AA:BB:CC:DD:EE:30", "Garmin Varia RTL516", "Garmin")
        val nulOnlyMfgId = insertRow(upgradedDb, "AA:BB:CC:DD:EE:31", "Bad Sensor\u0000", "\u0000")
        val emptyRowId = insertRow(upgradedDb, "AA:BB:CC:DD:EE:32", "", null)

        helper.onUpgrade(upgradedDb, 14, 15)

        val upgradedColumns = readTableColumns(upgradedDb)
        assertEquals("onCreate and onUpgrade(14, 15) schemas must match", freshColumns, upgradedColumns)

        assertEquals("Polar H10 12345678" to "Polar Electro Oy", readNameAndMfg(upgradedDb, h10Id))
        assertEquals("Polar Sense A1B2C3D4" to "Polar Electro Oy", readNameAndMfg(upgradedDb, verityId))
        assertEquals("Garmin Varia RTL516" to "Garmin", readNameAndMfg(upgradedDb, cleanId))
        assertEquals("Bad Sensor" to null, readNameAndMfg(upgradedDb, nulOnlyMfgId))
        assertEquals("" to null, readNameAndMfg(upgradedDb, emptyRowId))

        // Second invocation must be 100% idempotent with 0 rows modified
        assertEquals(0, BleDisStringSanitizer.sanitizeExistingDevicesTable(upgradedDb))

        freshDb.close()
        upgradedDb.close()
    }

    @Test
    fun testDevicesDatabaseManagerWriteReadAndRawDeviceDataProvider_stripNulBytes_TST_CON_060_3() {
        val dbManager = DevicesDatabaseManager.getInstance(context)
        val deviceId = dbManager.insertNewBluetoothDeviceIntoDB(
            DeviceType.HRM,
            "AA:BB:CC:DD:EE:99",
            "Polar H10 99887766\u0000",
            "Polar Electro Oy\u0000",
            9,
            true
        )
        assertTrue(deviceId > 0)
        assertEquals("Polar H10 99887766", dbManager.getDeviceName(deviceId))
        assertEquals("Polar Electro Oy", dbManager.getManufacturerName(deviceId))

        // setManufacturerName strips trailing NUL and converts NUL-only to null
        dbManager.setManufacturerName(deviceId, "Polar Electro Oy\u0000\u0000")
        assertEquals("Polar Electro Oy", dbManager.getManufacturerName(deviceId))
        dbManager.setManufacturerName(deviceId, "\u0000")
        assertNull(dbManager.getManufacturerName(deviceId))

        // All cursor-based mappers return sanitized strings even if a dirty row existed directly in SQLite
        val rawDirty = ContentValues().apply {
            put(DevicesDatabaseManager.DevicesDbHelper.NAME, "Polar H10 Direct\u0000")
            put(DevicesDatabaseManager.DevicesDbHelper.MANUFACTURER_NAME, "Polar Electro Oy\u0000")
        }
        dbManager.database.update(
            DevicesDatabaseManager.DevicesDbHelper.DEVICES,
            rawDirty,
            "${DevicesDatabaseManager.DevicesDbHelper.C_ID}=?",
            arrayOf(deviceId.toString())
        )
        assertEquals("Polar H10 Direct", dbManager.getDeviceName(deviceId))
        assertEquals("Polar Electro Oy", dbManager.getManufacturerName(deviceId))
        assertEquals("Polar H10 Direct", dbManager.allDevicesForCache.first { it.id == deviceId }.name)
        assertEquals("Polar H10 Direct", dbManager.getCriticalBatteryDevices(20).first { it.deviceId == deviceId }.name)
        assertEquals("Polar H10 Direct", dbManager.getSensorsForSportType(BSportType.RUN).first { it.id == deviceId }.name)
        assertEquals("Polar H10 Direct", dbManager.allRemoteSensors.first { it.id == deviceId }.name)

        // RawDeviceDataProvider strips NULs and retains non-null "" fallback on NUL-only columns
        val mockEqDb = mockk<EquipmentDbHelper>(relaxed = true)
        val mockDiscovery = mockk<EquipmentAndSportTypeDiscoveryManager>(relaxed = true)
        every { mockEqDb.getLinkedEquipmentFromDeviceId(any()) } returns emptyList()
        every { mockEqDb.getEquipment(any()) } returns emptyList()
        val provider = RawDeviceDataProvider(dbManager, mockEqDb, mockDiscovery)

        val cursor = MatrixCursor(
            arrayOf(
                DevicesDatabaseManager.DevicesDbHelper.C_ID,
                DevicesDatabaseManager.DevicesDbHelper.DEVICE_TYPE,
                DevicesDatabaseManager.DevicesDbHelper.PROTOCOL,
                DevicesDatabaseManager.DevicesDbHelper.LAST_ACTIVE,
                DevicesDatabaseManager.DevicesDbHelper.LAST_BATTERY_PERCENTAGE,
                DevicesDatabaseManager.DevicesDbHelper.MANUFACTURER_NAME,
                DevicesDatabaseManager.DevicesDbHelper.NAME,
                DevicesDatabaseManager.DevicesDbHelper.PAIRED,
                DevicesDatabaseManager.DevicesDbHelper.BT_ADDRESS
            )
        )
        cursor.addRow(arrayOf<Any?>(deviceId, DeviceType.HRM.name, Protocol.BLUETOOTH_LE.name, "", 80, "Polar Electro Oy\u0000", "Polar H10\u0000", 1, "AA:BB:CC:DD:EE:99"))
        cursor.addRow(arrayOf<Any?>(deviceId + 1, DeviceType.HRM.name, Protocol.BLUETOOTH_LE.name, "", 80, "\u0000", "\u0000", 1, "AA:BB:CC:DD:EE:98"))

        cursor.use {
            assertTrue(it.moveToFirst())
            val first = provider.getDeviceData(it)
            assertEquals("Polar Electro Oy", first.manufacturer)
            assertEquals("Polar H10", first.deviceName)

            assertTrue(it.moveToNext())
            val second = provider.getDeviceData(it)
            assertEquals("", second.manufacturer)
            assertEquals("", second.deviceName)
        }
    }

    private fun insertRow(db: SQLiteDatabase, mac: String, name: String?, mfg: String?): Long {
        val values = ContentValues().apply {
            put(DevicesDatabaseManager.DevicesDbHelper.PROTOCOL, Protocol.BLUETOOTH_LE.name)
            put(DevicesDatabaseManager.DevicesDbHelper.BT_ADDRESS, mac)
            put(DevicesDatabaseManager.DevicesDbHelper.DEVICE_TYPE, DeviceType.HRM.name)
            put(DevicesDatabaseManager.DevicesDbHelper.PAIRED, 1)
            if (name != null) put(DevicesDatabaseManager.DevicesDbHelper.NAME, name) else putNull(DevicesDatabaseManager.DevicesDbHelper.NAME)
            if (mfg != null) put(DevicesDatabaseManager.DevicesDbHelper.MANUFACTURER_NAME, mfg) else putNull(DevicesDatabaseManager.DevicesDbHelper.MANUFACTURER_NAME)
        }
        return db.insert(DevicesDatabaseManager.DevicesDbHelper.DEVICES, null, values)
    }

    private fun readNameAndMfg(db: SQLiteDatabase, rowId: Long): Pair<String?, String?> {
        db.rawQuery(
            "SELECT ${DevicesDatabaseManager.DevicesDbHelper.NAME}, ${DevicesDatabaseManager.DevicesDbHelper.MANUFACTURER_NAME} " +
                "FROM ${DevicesDatabaseManager.DevicesDbHelper.DEVICES} WHERE ${DevicesDatabaseManager.DevicesDbHelper.C_ID}=?",
            arrayOf(rowId.toString())
        ).use { c ->
            assertTrue(c.moveToFirst())
            val name = if (c.isNull(0)) null else c.getString(0)
            val mfg = if (c.isNull(1)) null else c.getString(1)
            return name to mfg
        }
    }

    private fun readTableColumns(db: SQLiteDatabase): Map<String, Pair<String, String?>> {
        val result = linkedMapOf<String, Pair<String, String?>>()
        db.rawQuery("PRAGMA table_info(${DevicesDatabaseManager.DevicesDbHelper.DEVICES})", null).use { c ->
            while (c.moveToNext()) {
                val name = c.getString(1)
                val type = c.getString(2)
                val dflt = if (c.isNull(4)) null else c.getString(4)
                result[name] = type to dflt
            }
        }
        return result
    }
}
