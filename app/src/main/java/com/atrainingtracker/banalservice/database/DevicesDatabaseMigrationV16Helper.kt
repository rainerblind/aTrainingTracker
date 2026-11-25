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
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.Protocol
import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.banalservice.devices.Manufacturer

/**
 * Idempotent data migrations for `Devices.db` legacy device titles and virtual smartphone
 * manufacturers (`ATT-5073`, `ATT-8111`).
 */
object DevicesDatabaseMigrationV16Helper {

    private val LEGACY_ODOMETRY_FUSED_NAMES = arrayOf(
        "ATT Odometry Fused",
        "ATT Odometrie-Fusion",
        "ATT Odometría fusionada",
        "ATT Odométrie fusionnée",
        "ATT Odometria combinata",
        "ATT 複合オドメトリ",
        "ATT Odometrie-fusie",
        "ATT Odometria scalona",
        "ATT Odometria fundida",
        "ATT Fused"
    )

    private val LEGACY_ALTITUDE_SLOPE_FUSED_NAMES = arrayOf(
        "ATT Fused (Altitude & Slope)",
        "ATT Fused (Höhe und Steigung)",
        "ATT Fused (Altitud y pendiente)",
        "ATT Fused (Altitude et pente)",
        "ATT Fused (Altitudine e pendenza)",
        "ATT Fused (高度と勾配)",
        "ATT Fused (Hoogte en helling)",
        "ATT Fused (Wysokość i nachylenie)",
        "ATT Fused (Altitude e inclinação)"
    )

    /**
     * Removes legacy `"ATT "` prefixes from default Fused sensor titles across all 9 locales
     * (preserving custom user renames) and updates virtual smartphone sensor manufacturers to
     * `"a Training Tracker"` / `"Google"` (`Devices.db` v15 -> v16, `ATT-8111`).
     */
    @JvmStatic
    fun migrateAttFusedTitlesAndSmartphoneManufacturers(
        db: SQLiteDatabase,
        context: Context?
    ): Int {
        var updated = 0

        val newOdometryName = getStringSafely(context, R.string.devices_location_ATT_fused, "Odometry Fused")
        updated += updateLegacyDeviceNames(db, DeviceType.SPEED_AND_LOCATION_ATT_FUSED, newOdometryName, LEGACY_ODOMETRY_FUSED_NAMES)

        val newAltitudeName = getStringSafely(context, R.string.devices_altitude_slope_ATT_fused, "Fused (Altitude & Slope)")
        updated += updateLegacyDeviceNames(db, DeviceType.ALTITUDE_AND_SLOPE_ATT_FUSED, newAltitudeName, LEGACY_ALTITUDE_SLOPE_FUSED_NAMES)

        val attMfgValues = ContentValues().apply {
            put(DevicesDatabaseManager.DevicesDbHelper.MANUFACTURER_NAME, Manufacturer.MANUFACTURER_A_TRAINING_TRACKER)
        }
        updated += db.update(
            DevicesDatabaseManager.DevicesDbHelper.DEVICES,
            attMfgValues,
            "${DevicesDatabaseManager.DevicesDbHelper.PROTOCOL} = ? AND " +
                "${DevicesDatabaseManager.DevicesDbHelper.DEVICE_TYPE} IN (?,?,?) AND (" +
                "${DevicesDatabaseManager.DevicesDbHelper.MANUFACTURER_NAME} IS NULL OR " +
                "${DevicesDatabaseManager.DevicesDbHelper.MANUFACTURER_NAME} != ?)",
            arrayOf(
                Protocol.SMARTPHONE.name,
                DeviceType.SPEED_AND_LOCATION_ATT_FUSED.name,
                DeviceType.ALTITUDE_AND_SLOPE_ATT_FUSED.name,
                DeviceType.ALTITUDE_FROM_POSITION.name,
                Manufacturer.MANUFACTURER_A_TRAINING_TRACKER
            )
        )

        val googleMfgValues = ContentValues().apply {
            put(DevicesDatabaseManager.DevicesDbHelper.MANUFACTURER_NAME, Manufacturer.MANUFACTURER_GOOGLE)
        }
        updated += db.update(
            DevicesDatabaseManager.DevicesDbHelper.DEVICES,
            googleMfgValues,
            "${DevicesDatabaseManager.DevicesDbHelper.PROTOCOL} = ? AND " +
                "${DevicesDatabaseManager.DevicesDbHelper.DEVICE_TYPE} = ? AND (" +
                "${DevicesDatabaseManager.DevicesDbHelper.MANUFACTURER_NAME} IS NULL OR " +
                "${DevicesDatabaseManager.DevicesDbHelper.MANUFACTURER_NAME} != ?)",
            arrayOf(
                Protocol.SMARTPHONE.name,
                DeviceType.SPEED_AND_LOCATION_GOOGLE_FUSED.name,
                Manufacturer.MANUFACTURER_GOOGLE
            )
        )

        return updated
    }

    @JvmStatic
    fun updateLegacyDeviceNames(
        db: SQLiteDatabase,
        deviceType: DeviceType,
        newName: String,
        legacyNames: Array<String>
    ): Int {
        if (legacyNames.isEmpty()) return 0
        val placeholders = legacyNames.joinToString(",") { "?" }
        val whereClause = "${DevicesDatabaseManager.DevicesDbHelper.DEVICE_TYPE} = ? AND " +
            "${DevicesDatabaseManager.DevicesDbHelper.NAME} IN ($placeholders)"
        val whereArgs = arrayOf(deviceType.name, *legacyNames)
        val values = ContentValues().apply {
            put(DevicesDatabaseManager.DevicesDbHelper.NAME, newName)
        }
        return db.update(DevicesDatabaseManager.DevicesDbHelper.DEVICES, values, whereClause, whereArgs)
    }

    private fun getStringSafely(context: Context?, resId: Int, fallback: String): String {
        return try {
            context?.getString(resId) ?: fallback
        } catch (_: Exception) {
            fallback
        }
    }
}
