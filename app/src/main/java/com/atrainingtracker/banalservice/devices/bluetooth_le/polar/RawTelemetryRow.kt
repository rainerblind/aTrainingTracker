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
import kotlin.math.abs
import kotlin.math.floor

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
     * Appends the 11-column CSV representation directly into [sb] without per-row `String.format` /
     * `Formatter` allocations (`REQ-SET-117.2`, `REQ-STB-023.4`):
     * `sensorTimestampNs,elapsedMs,channel,ax_mg,ay_mg,az_mg,gx_mdps,gy_mdps,gz_mdps,hr_bpm,rr_ms`
     */
    fun appendCsvLine(sb: StringBuilder) {
        sb.append(sensorTimestampNs).append(',')
            .append(elapsedMs).append(',')
            .append(channel.name).append(',')
        axMg?.let { sb.append(it) }
        sb.append(',')
        ayMg?.let { sb.append(it) }
        sb.append(',')
        azMg?.let { sb.append(it) }
        sb.append(',')
        gxMdps?.let { appendFixedDecimal(sb, it.toDouble(), decimals = 2) }
        sb.append(',')
        gyMdps?.let { appendFixedDecimal(sb, it.toDouble(), decimals = 2) }
        sb.append(',')
        gzMdps?.let { appendFixedDecimal(sb, it.toDouble(), decimals = 2) }
        sb.append(',')
        hrBpm?.let { sb.append(it) }
        sb.append(',')
        rrMs?.let { appendFixedDecimal(sb, it, decimals = 1) }
    }

    /**
     * Serializes row to CSV string:
     * sensorTimestampNs,elapsedMs,channel,ax_mg,ay_mg,az_mg,gx_mdps,gy_mdps,gz_mdps,hr_bpm,rr_ms
     */
    fun toCsvLine(): String = StringBuilder(CSV_LINE_INITIAL_CAPACITY).also { appendCsvLine(it) }.toString()

    companion object {
        const val CSV_COLUMN_COUNT = 11
        private const val CSV_LINE_INITIAL_CAPACITY = 96
        private const val MAX_FAST_FORMAT_ABS = 1e13
        const val CSV_HEADER =
            "sensorTimestampNs,elapsedMs,channel,ax_mg,ay_mg,az_mg,gx_mdps,gy_mdps,gz_mdps,hr_bpm,rr_ms"

        private fun appendFixedDecimal(sb: StringBuilder, value: Double, decimals: Int) {
            if (value.isNaN()) {
                sb.append("NaN")
                return
            }
            if (value.isInfinite()) {
                if (value < 0.0) sb.append("-Infinity") else sb.append("Infinity")
                return
            }
            val absVal = abs(value)
            if (absVal >= MAX_FAST_FORMAT_ABS) {
                val pattern = if (decimals == 2) "%.2f" else "%.1f"
                sb.append(String.format(Locale.US, pattern, value))
                return
            }
            // Raw sign-bit check preserves "-0.00" / "-0.0" parity with String.format(Locale.US, "%.2f"/"%.1f")
            if (java.lang.Double.doubleToRawLongBits(value) < 0L) {
                sb.append('-')
            }
            if (decimals == 2) {
                val scaled = floor(absVal * 100.0 + 0.5).toLong()
                val intPart = scaled / 100L
                val fracPart = (scaled % 100L).toInt()
                sb.append(intPart).append('.')
                    .append(('0'.code + (fracPart / 10)).toChar())
                    .append(('0'.code + (fracPart % 10)).toChar())
            } else {
                val scaled = floor(absVal * 10.0 + 0.5).toLong()
                val intPart = scaled / 10L
                val fracPart = (scaled % 10L).toInt()
                sb.append(intPart).append('.')
                    .append(('0'.code + fracPart).toChar())
            }
        }

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
