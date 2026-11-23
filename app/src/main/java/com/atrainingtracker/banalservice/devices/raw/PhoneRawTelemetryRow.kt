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

package com.atrainingtracker.banalservice.devices.raw

/**
 * Channel discriminator for smartphone raw sensor telemetry rows
 * (REQ-EXP-045.2, ATT-9348; REQ-EXP-047.1, ATT-9604).
 */
enum class PhoneRawChannel {
    ACC,
    GYRO,
    ROTVEC,
    BARO,
    STEP,
    GNSS,
    GNSS_FUSED,
    WHEEL,
    STATE
}

/**
 * Single typed smartphone raw telemetry row for compressed CSV sidecar recording and pure-JVM
 * offline simulation replay (`ATT-9348`, `ATT-9349`, `ATT-9604`, REQ-EXP-045.2, REQ-EXP-047.1).
 *
 * Column schema: `elapsedRealtimeNs,epochMs,channel,v0,v1,v2,v3,v4,v5`
 * - `ACC`:        v0=ax, v1=ay, v2=az (m/s^2)
 * - `GYRO`:       v0=gx, v1=gy, v2=gz (rad/s)
 * - `ROTVEC`:     v0=qx, v1=qy, v2=qz, v3=qw (unit quaternion)
 * - `BARO`:       v0=pressureHpa, v1=isaAltM
 * - `STEP`:       (empty v0..v5)
 * - `GNSS`:       v0=latDeg, v1=lonDeg, v2=altM, v3=speedMps, v4=bearingDeg, v5=accuracyM (raw GPS)
 * - `GNSS_FUSED`: v0=latDeg, v1=lonDeg, v2=altM, v3=speedMps, v4=bearingDeg, v5=accuracyM (Google Fused)
 * - `WHEEL`:      v0=speedMps, v1=variance
 * - `STATE`:      v0=sportId, v1=bSportCode, v2=carriageCode
 */
data class PhoneRawTelemetryRow(
    val elapsedRealtimeNs: Long,
    val epochMs: Long,
    val channel: PhoneRawChannel,
    val v0: Double = Double.NaN,
    val v1: Double = Double.NaN,
    val v2: Double = Double.NaN,
    val v3: Double = Double.NaN,
    val v4: Double = Double.NaN,
    val v5: Double = Double.NaN
) {
    /**
     * Appends this row as a canonical CSV line directly into [sb] without `String.format` /
     * `Formatter` allocations (`REQ-EXP-045.1`, `ATT-9343`).
     */
    fun appendCsvLine(sb: StringBuilder) {
        sb.append(elapsedRealtimeNs).append(',')
        sb.append(epochMs).append(',')
        sb.append(channel.name).append(',')
        appendField(sb, v0).append(',')
        appendField(sb, v1).append(',')
        appendField(sb, v2).append(',')
        appendField(sb, v3).append(',')
        appendField(sb, v4).append(',')
        appendField(sb, v5)
    }

    fun toCsvLine(): String {
        val sb = StringBuilder(96)
        appendCsvLine(sb)
        return sb.toString()
    }

    companion object {
        const val CSV_HEADER = "elapsedRealtimeNs,epochMs,channel,v0,v1,v2,v3,v4,v5"
        private const val EXPECTED_COLUMNS = 9

        private fun appendField(sb: StringBuilder, value: Double): StringBuilder {
            if (value.isFinite()) {
                sb.append(value)
            }
            return sb
        }

        /**
         * Strictly parses and validates a CSV row line, returning `null` for truncated or
         * corrupted lines (used by crash-recovery salvage and pure-JVM replay reader).
         */
        @JvmStatic
        fun fromCsvLine(line: String): PhoneRawTelemetryRow? {
            if (line.isEmpty()) return null
            val parts = line.split(',')
            if (parts.size != EXPECTED_COLUMNS) return null

            val elapsedNs = parts[0].toLongOrNull() ?: return null
            val epochMs = parts[1].toLongOrNull() ?: return null
            val channel = parseChannel(parts[2]) ?: return null

            val v0 = parseOptionalDouble(parts[3]) ?: return null
            val v1 = parseOptionalDouble(parts[4]) ?: return null
            val v2 = parseOptionalDouble(parts[5]) ?: return null
            val v3 = parseOptionalDouble(parts[6]) ?: return null
            val v4 = parseOptionalDouble(parts[7]) ?: return null
            val v5 = parseOptionalDouble(parts[8]) ?: return null

            val validRequiredFields = when (channel) {
                PhoneRawChannel.ACC, PhoneRawChannel.GYRO, PhoneRawChannel.STATE ->
                    v0.isFinite() && v1.isFinite() && v2.isFinite()
                PhoneRawChannel.ROTVEC ->
                    v0.isFinite() && v1.isFinite() && v2.isFinite() && v3.isFinite()
                PhoneRawChannel.BARO, PhoneRawChannel.WHEEL ->
                    v0.isFinite() && v1.isFinite()
                PhoneRawChannel.STEP -> true
                PhoneRawChannel.GNSS, PhoneRawChannel.GNSS_FUSED ->
                    v0.isFinite() && v1.isFinite() && v2.isFinite() &&
                        v3.isFinite() && v4.isFinite() && v5.isFinite()
            }
            if (!validRequiredFields) return null

            return PhoneRawTelemetryRow(
                elapsedRealtimeNs = elapsedNs,
                epochMs = epochMs,
                channel = channel,
                v0 = v0,
                v1 = v1,
                v2 = v2,
                v3 = v3,
                v4 = v4,
                v5 = v5
            )
        }

        private fun parseChannel(token: String): PhoneRawChannel? {
            return when (token) {
                "ACC" -> PhoneRawChannel.ACC
                "GYRO" -> PhoneRawChannel.GYRO
                "ROTVEC" -> PhoneRawChannel.ROTVEC
                "BARO" -> PhoneRawChannel.BARO
                "STEP" -> PhoneRawChannel.STEP
                "GNSS" -> PhoneRawChannel.GNSS
                "GNSS_FUSED" -> PhoneRawChannel.GNSS_FUSED
                "WHEEL" -> PhoneRawChannel.WHEEL
                "STATE" -> PhoneRawChannel.STATE
                else -> null
            }
        }

        /**
         * Returns `Double.NaN` for empty optional column, finite `Double` for valid number,
         * or `null` if the token is non-empty and unparseable/non-finite.
         */
        private fun parseOptionalDouble(token: String): Double? {
            if (token.isEmpty()) return Double.NaN
            val parsed = token.toDoubleOrNull() ?: return null
            return if (parsed.isFinite()) parsed else null
        }
    }
}
