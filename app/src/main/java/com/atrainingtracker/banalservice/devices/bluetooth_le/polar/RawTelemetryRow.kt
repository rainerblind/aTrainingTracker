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

package com.atrainingtracker.banalservice.devices.bluetooth_le.polar

import java.util.Locale

/**
 * Channel identifier for raw Polar telemetry streams (REQ-SET-117.2).
 */
enum class RawTelemetryChannel {
    ACC,
    GYRO,
    HR,
    RR
}

/**
 * Single data frame row in the raw telemetry sidecar (REQ-SET-117.2).
 */
data class RawTelemetryRow(
    val sensorTimestampNs: Long,
    val elapsedMs: Long,
    val channel: RawTelemetryChannel,
    val axMg: Int? = null,
    val ayMg: Int? = null,
    val azMg: Int? = null,
    val gxMdps: Float? = null,
    val gyMdps: Float? = null,
    val gzMdps: Float? = null,
    val hrBpm: Int? = null,
    val rrMs: Double? = null
) {
    /**
     * Serializes row to CSV string:
     * sensorTimestampNs,elapsedMs,channel,ax_mg,ay_mg,az_mg,gx_mdps,gy_mdps,gz_mdps,hr_bpm,rr_ms
     */
    fun toCsvLine(): String {
        val axStr = axMg?.toString() ?: ""
        val ayStr = ayMg?.toString() ?: ""
        val azStr = azMg?.toString() ?: ""
        val gxStr = gxMdps?.let { String.format(Locale.US, "%.2f", it) } ?: ""
        val gyStr = gyMdps?.let { String.format(Locale.US, "%.2f", it) } ?: ""
        val gzStr = gzMdps?.let { String.format(Locale.US, "%.2f", it) } ?: ""
        val hrStr = hrBpm?.toString() ?: ""
        val rrStr = rrMs?.let { String.format(Locale.US, "%.1f", it) } ?: ""

        return "$sensorTimestampNs,$elapsedMs,${channel.name},$axStr,$ayStr,$azStr,$gxStr,$gyStr,$gzStr,$hrStr,$rrStr"
    }

    companion object {
        const val CSV_COLUMN_COUNT = 11
        const val CSV_HEADER =
            "sensorTimestampNs,elapsedMs,channel,ax_mg,ay_mg,az_mg,gx_mdps,gy_mdps,gz_mdps,hr_bpm,rr_ms"

        fun fromCsvLine(line: String): RawTelemetryRow? {
            val parts = line.split(",")
            if (parts.size < CSV_COLUMN_COUNT) return null
            val ts = parts[0].toLongOrNull() ?: return null
            val ch = try {
                RawTelemetryChannel.valueOf(parts[2].trim())
            } catch (_: IllegalArgumentException) {
                null
            } ?: return null

            return RawTelemetryRow(
                sensorTimestampNs = ts,
                elapsedMs = parts[1].toLongOrNull() ?: 0L,
                channel = ch,
                axMg = parts[3].toIntOrNull(),
                ayMg = parts[4].toIntOrNull(),
                azMg = parts[5].toIntOrNull(),
                gxMdps = parts[6].toFloatOrNull(),
                gyMdps = parts[7].toFloatOrNull(),
                gzMdps = parts[8].toFloatOrNull(),
                hrBpm = parts[9].toIntOrNull(),
                rrMs = parts[10].toDoubleOrNull()
            )
        }
    }
}
