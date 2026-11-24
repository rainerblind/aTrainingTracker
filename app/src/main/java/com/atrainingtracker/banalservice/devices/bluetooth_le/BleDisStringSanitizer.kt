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

import android.bluetooth.BluetoothGattCharacteristic
import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager
import java.nio.charset.StandardCharsets

/**
 * Shared BLE GATT / Device Information Service (`0x180A`: `0x2A29`, `0x2A24`, `0x2A25`) and GAP
 * device-name string sanitizer (`REQ-CON-060`, `ATT-8996`).
 *
 * BLE peripherals frequently append C-string NUL terminators (`0x00`) to GATT string payloads.
 * Android's `BluetoothGattCharacteristic.getStringValue(0)` preserves these `'\u0000'` bytes,
 * and Kotlin's `CharSequence.trim()` / `isNotBlank()` treats `'\u0000'` as non-whitespace
 * (`'\u0000'.isWhitespace() == false`). All Java and Kotlin BLE DIS readers, `PolarOfflineCapabilityStore`,
 * and `Devices.db` persistence paths route through this stateless `@JvmStatic` utility.
 */
object BleDisStringSanitizer {

    /**
     * Strips all `'\u0000'` bytes (leading, embedded, trailing) and trims leading/trailing whitespace,
     * returning `null` when [raw] is `null`, empty, whitespace-only, or NUL-only.
     */
    @JvmStatic
    fun sanitize(raw: String?): String? {
        if (raw == null) return null
        val cleaned = if (raw.indexOf('\u0000') >= 0) raw.replace("\u0000", "") else raw
        return cleaned.trim().takeIf { it.isNotEmpty() }
    }

    /**
     * Reads and sanitizes a UTF-8 string payload from [characteristic] via `getStringValue(0)`
     * (falling back to raw `value` UTF-8 bytes when `getStringValue(0)` is `null`).
     */
    @JvmStatic
    @Suppress("DEPRECATION")
    fun readSanitizedString(characteristic: BluetoothGattCharacteristic?): String? {
        if (characteristic == null) return null
        val raw = characteristic.getStringValue(0)
            ?: characteristic.value?.let { String(it, StandardCharsets.UTF_8) }
        return sanitize(raw)
    }

    /**
     * Returns `true` when [value] contains one or more `'\u0000'` bytes.
     */
    @JvmStatic
    fun containsNul(value: String?): Boolean = value != null && value.indexOf('\u0000') >= 0

    /**
     * Idempotently strips `'\u0000'` bytes and surrounding whitespace from existing `Devices4`
     * `name` and `manufacturerName` rows (`Devices.db` v14 -> v15, `REQ-CON-060.3`).
     *
     * Collects dirty rows and closes the query `Cursor` strictly before issuing `db.update(...)`
     * writes; already-clean rows are left untouched.
     */
    @JvmStatic
    fun sanitizeExistingDevicesTable(db: SQLiteDatabase): Int {
        data class DirtyRow(
            val id: Long,
            val updateName: Boolean,
            val cleanName: String?,
            val updateManufacturer: Boolean,
            val cleanManufacturer: String?
        )

        val dirtyRows = mutableListOf<DirtyRow>()
        db.query(
            DevicesDatabaseManager.DevicesDbHelper.DEVICES,
            arrayOf(
                DevicesDatabaseManager.DevicesDbHelper.C_ID,
                DevicesDatabaseManager.DevicesDbHelper.NAME,
                DevicesDatabaseManager.DevicesDbHelper.MANUFACTURER_NAME
            ),
            null,
            null,
            null,
            null,
            null
        ).use { cursor ->
            val idIdx = cursor.getColumnIndex(DevicesDatabaseManager.DevicesDbHelper.C_ID)
            val nameIdx = cursor.getColumnIndex(DevicesDatabaseManager.DevicesDbHelper.NAME)
            val mfgIdx = cursor.getColumnIndex(DevicesDatabaseManager.DevicesDbHelper.MANUFACTURER_NAME)
            if (idIdx >= 0 && nameIdx >= 0 && mfgIdx >= 0) {
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idIdx)
                    val rawName = if (cursor.isNull(nameIdx)) null else cursor.getString(nameIdx)
                    val rawMfg = if (cursor.isNull(mfgIdx)) null else cursor.getString(mfgIdx)

                    val needsNameClean = rawName != null && (containsNul(rawName) || rawName != rawName.trim())
                    val needsMfgClean = rawMfg != null && (containsNul(rawMfg) || rawMfg != rawMfg.trim())
                    if (needsNameClean || needsMfgClean) {
                        dirtyRows.add(
                            DirtyRow(
                                id = id,
                                updateName = needsNameClean,
                                cleanName = if (needsNameClean) sanitize(rawName) else null,
                                updateManufacturer = needsMfgClean,
                                cleanManufacturer = if (needsMfgClean) sanitize(rawMfg) else null
                            )
                        )
                    }
                }
            }
        }

        var updatedCount = 0
        for (row in dirtyRows) {
            val values = ContentValues()
            if (row.updateName) {
                if (row.cleanName != null) {
                    values.put(DevicesDatabaseManager.DevicesDbHelper.NAME, row.cleanName)
                } else {
                    values.putNull(DevicesDatabaseManager.DevicesDbHelper.NAME)
                }
            }
            if (row.updateManufacturer) {
                if (row.cleanManufacturer != null) {
                    values.put(DevicesDatabaseManager.DevicesDbHelper.MANUFACTURER_NAME, row.cleanManufacturer)
                } else {
                    values.putNull(DevicesDatabaseManager.DevicesDbHelper.MANUFACTURER_NAME)
                }
            }
            updatedCount += db.update(
                DevicesDatabaseManager.DevicesDbHelper.DEVICES,
                values,
                "${DevicesDatabaseManager.DevicesDbHelper.C_ID}=?",
                arrayOf(row.id.toString())
            )
        }
        return updatedCount
    }
}
