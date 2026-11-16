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

import android.util.Log
import org.json.JSONException
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.zip.GZIPInputStream

/**
 * Parsed raw telemetry session data (REQ-SET-117.2, ATT-8019).
 */
data class PolarRawSession(
    val metadata: PolarRawMetadata,
    val rows: List<RawTelemetryRow>
)

/**
 * Deterministic reader and replay parser for Polar raw telemetry GZIP sidecar archives (REQ-SET-117.2).
 */
object PolarRawTelemetryReader {

    private const val TAG = "PolarRawTelemetryReader"
    private const val METADATA_PREFIX = "# metadata:"
    private const val HEADER_PREFIX = "sensorTimestampNs,"

    /**
     * Reads a `.polar_raw.csv.gz` file and parses its metadata header and channel frames.
     */
    @JvmStatic
    fun readFile(file: File): PolarRawSession {
        if (!file.exists() || file.length() == 0L) {
            return PolarRawSession(PolarRawMetadata(), emptyList())
        }
        return try {
            FileInputStream(file).use { fis ->
                readStream(fis)
            }
        } catch (e: IOException) {
            Log.w(TAG, "Failed to open or read file ${file.name}: ${e.message}")
            PolarRawSession(PolarRawMetadata(), emptyList())
        }
    }

    /**
     * Reads from a GZIP compressed input stream.
     */
    @JvmStatic
    fun readStream(inputStream: InputStream): PolarRawSession {
        var metadata = PolarRawMetadata()
        val rows = mutableListOf<RawTelemetryRow>()

        try {
            GZIPInputStream(inputStream).use { gzipIn ->
                BufferedReader(InputStreamReader(gzipIn, StandardCharsets.UTF_8)).use { reader ->
                    var line = reader.readLine()
                    while (line != null) {
                        val trimmed = line.trim()
                        if (trimmed.startsWith(METADATA_PREFIX)) {
                            metadata = parseMetadata(trimmed)
                        } else if (!trimmed.startsWith(HEADER_PREFIX) && trimmed.isNotEmpty()) {
                            RawTelemetryRow.fromCsvLine(trimmed)?.let { rows.add(it) }
                        }
                        line = reader.readLine()
                    }
                }
            }
        } catch (e: IOException) {
            Log.w(TAG, "IOException during GZIP stream read: ${e.message}")
        }

        return PolarRawSession(metadata, rows)
    }

    private fun parseMetadata(line: String): PolarRawMetadata {
        val jsonStr = line.substring(METADATA_PREFIX.length).trim()
        return try {
            PolarRawMetadata.fromJsonString(jsonStr)
        } catch (e: JSONException) {
            Log.w(TAG, "Failed to parse metadata JSON: ${e.message}")
            PolarRawMetadata()
        }
    }
}
