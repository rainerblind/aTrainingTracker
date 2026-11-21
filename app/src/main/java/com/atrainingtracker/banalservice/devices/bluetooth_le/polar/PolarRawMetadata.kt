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

/**
 * Metadata header for Polar raw telemetry sidecars (REQ-SET-117.2, REQ-EXP-046.1, ATT-8019, ATT-9349).
 *
 * Pure JVM (zero Android or `org.json` dependencies) so standalone offline replay (`:app:replayRawTelemetry`)
 * parses `.polar_raw.csv.gz` archives identically on JVM and Android.
 */
data class PolarRawMetadata(
    val schemaVersion: Int = 1,
    val appVersion: String = "5.0.0",
    val sensorModel: String = "",
    val firmwareVersion: String = "",
    val streamMode: String = "LIVE", // "LIVE" or "OFFLINE"
    val accRateHz: Int = 0,
    val gyroRateHz: Int = 0,
    val sportId: Int = 0,
    val poolLengthM: Float? = null
) {
    fun toJsonString(): String {
        val sb = StringBuilder(192)
        sb.append('{')
        sb.append("\"schemaVersion\":").append(schemaVersion)
        sb.append(",\"appVersion\":\"").append(escapeJson(appVersion)).append('"')
        sb.append(",\"sensorModel\":\"").append(escapeJson(sensorModel)).append('"')
        sb.append(",\"firmwareVersion\":\"").append(escapeJson(firmwareVersion)).append('"')
        sb.append(",\"streamMode\":\"").append(escapeJson(streamMode)).append('"')
        sb.append(",\"accRateHz\":").append(accRateHz)
        sb.append(",\"gyroRateHz\":").append(gyroRateHz)
        sb.append(",\"sportId\":").append(sportId)
        if (poolLengthM != null && poolLengthM.isFinite()) {
            sb.append(",\"poolLength_m\":").append(poolLengthM.toDouble())
        }
        sb.append('}')
        return sb.toString()
    }

    companion object {
        private val SCHEMA_VER_RE = Regex("\"schemaVersion\"\\s*:\\s*(-?\\d+)")
        private val ACC_RATE_RE = Regex("\"accRateHz\"\\s*:\\s*(-?\\d+)")
        private val GYRO_RATE_RE = Regex("\"gyroRateHz\"\\s*:\\s*(-?\\d+)")
        private val SPORT_ID_RE = Regex("\"sportId\"\\s*:\\s*(-?\\d+)")
        private val POOL_LEN_RE = Regex("\"poolLength_m\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?)")
        private val APP_VER_RE = Regex("\"appVersion\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"")
        private val SENSOR_MODEL_RE = Regex("\"sensorModel\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"")
        private val FIRMWARE_RE = Regex("\"firmwareVersion\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"")
        private val STREAM_MODE_RE = Regex("\"streamMode\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"")

        /**
         * Parses a JSON object string into [PolarRawMetadata], returning default [PolarRawMetadata]
         * if malformed rather than throwing Android-specific exceptions.
         */
        @JvmStatic
        fun fromJsonString(jsonStr: String): PolarRawMetadata {
            val trimmed = jsonStr.trim()
            if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
                return PolarRawMetadata()
            }
            return PolarRawMetadata(
                schemaVersion = SCHEMA_VER_RE.find(trimmed)?.groupValues?.get(1)?.toIntOrNull() ?: 1,
                appVersion = APP_VER_RE.find(trimmed)?.groupValues?.get(1)?.let(::unescapeJson) ?: "5.0.0",
                sensorModel = SENSOR_MODEL_RE.find(trimmed)?.groupValues?.get(1)?.let(::unescapeJson) ?: "",
                firmwareVersion = FIRMWARE_RE.find(trimmed)?.groupValues?.get(1)?.let(::unescapeJson) ?: "",
                streamMode = STREAM_MODE_RE.find(trimmed)?.groupValues?.get(1)?.let(::unescapeJson) ?: "LIVE",
                accRateHz = ACC_RATE_RE.find(trimmed)?.groupValues?.get(1)?.toIntOrNull() ?: 0,
                gyroRateHz = GYRO_RATE_RE.find(trimmed)?.groupValues?.get(1)?.toIntOrNull() ?: 0,
                sportId = SPORT_ID_RE.find(trimmed)?.groupValues?.get(1)?.toIntOrNull() ?: 0,
                poolLengthM = POOL_LEN_RE.find(trimmed)?.groupValues?.get(1)?.toDoubleOrNull()?.toFloat()
            )
        }

        private fun escapeJson(value: String): String {
            val out = StringBuilder(value.length + 8)
            for (ch in value) {
                when (ch) {
                    '\\' -> out.append("\\\\")
                    '"' -> out.append("\\\"")
                    '\n' -> out.append("\\n")
                    '\r' -> out.append("\\r")
                    '\t' -> out.append("\\t")
                    else -> if (ch.code >= 0x20) out.append(ch)
                }
            }
            return out.toString()
        }

        private fun unescapeJson(value: String): String {
            val out = StringBuilder(value.length)
            var i = 0
            while (i < value.length) {
                val ch = value[i]
                if (ch == '\\' && i + 1 < value.length) {
                    when (val next = value[i + 1]) {
                        '\\' -> out.append('\\')
                        '"' -> out.append('"')
                        'n' -> out.append('\n')
                        'r' -> out.append('\r')
                        't' -> out.append('\t')
                        'u' -> {
                            if (i + 5 < value.length) {
                                val code = value.substring(i + 2, i + 6).toIntOrNull(16)
                                if (code != null) {
                                    out.append(code.toChar())
                                    i += 6
                                    continue
                                }
                            }
                            out.append('u')
                        }
                        else -> out.append(next)
                    }
                    i += 2
                } else {
                    out.append(ch)
                    i++
                }
            }
            return out.toString()
        }
    }
}
