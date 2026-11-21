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
 * Self-describing metadata header for smartphone raw sensor sidecar archives
 * (`.phone_raw.csv.gz`, REQ-EXP-045.2, ATT-9348).
 *
 * Pure JVM (zero Android or org.json dependencies) so downstream offline replay (`ATT-9349`)
 * parses archives identically on JVM and Android.
 */
data class PhoneRawMetadata(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val appVersion: String = "unknown",
    val deviceModel: String = "unknown",
    val sportId: Long = -1L,
    val sessionStartTimeMs: Long = 0L
) {
    /**
     * Serializes metadata to the canonical single-line `# metadata: {...}` header.
     */
    fun toHeaderLine(): String {
        val sb = StringBuilder(160)
        sb.append(METADATA_PREFIX).append(" {")
        sb.append("\"schemaVersion\":").append(schemaVersion)
        sb.append(",\"appVersion\":\"").append(escapeJson(appVersion)).append('"')
        sb.append(",\"deviceModel\":\"").append(escapeJson(deviceModel)).append('"')
        sb.append(",\"sportId\":").append(sportId)
        sb.append(",\"sessionStartTimeMs\":").append(sessionStartTimeMs)
        sb.append('}')
        return sb.toString()
    }

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
        const val METADATA_PREFIX = "# metadata:"

        private val INT_FIELD = mapOf(
            "schemaVersion" to Regex("\"schemaVersion\"\\s*:\\s*(-?\\d+)")
        )
        private val LONG_FIELD = mapOf(
            "sportId" to Regex("\"sportId\"\\s*:\\s*(-?\\d+)"),
            "sessionStartTimeMs" to Regex("\"sessionStartTimeMs\"\\s*:\\s*(-?\\d+)")
        )
        private val STRING_FIELD = mapOf(
            "appVersion" to Regex("\"appVersion\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\""),
            "deviceModel" to Regex("\"deviceModel\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"")
        )

        /**
         * Parses a `# metadata: {...}` line, returning `null` if malformed.
         */
        @JvmStatic
        fun parseHeaderLine(line: String): PhoneRawMetadata? {
            val trimmed = line.trim()
            if (!trimmed.startsWith(METADATA_PREFIX)) return null
            val json = trimmed.removePrefix(METADATA_PREFIX).trim()
            if (!json.startsWith("{") || !json.endsWith("}")) return null

            val schemaVersion = INT_FIELD["schemaVersion"]!!.find(json)?.groupValues?.get(1)?.toIntOrNull()
                ?: CURRENT_SCHEMA_VERSION
            val appVersion = STRING_FIELD["appVersion"]!!.find(json)?.groupValues?.get(1)
                ?.let(::unescapeJson) ?: "unknown"
            val deviceModel = STRING_FIELD["deviceModel"]!!.find(json)?.groupValues?.get(1)
                ?.let(::unescapeJson) ?: "unknown"
            val sportId = LONG_FIELD["sportId"]!!.find(json)?.groupValues?.get(1)?.toLongOrNull() ?: -1L
            val sessionStartTimeMs = LONG_FIELD["sessionStartTimeMs"]!!.find(json)?.groupValues?.get(1)?.toLongOrNull() ?: 0L

            return PhoneRawMetadata(
                schemaVersion = schemaVersion,
                appVersion = appVersion,
                deviceModel = deviceModel,
                sportId = sportId,
                sessionStartTimeMs = sessionStartTimeMs
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
