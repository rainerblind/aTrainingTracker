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

import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.zip.GZIPInputStream
import java.util.zip.ZipException

/**
 * Parsed raw telemetry session data (REQ-SET-117.2, REQ-EXP-046.1, ATT-8019, ATT-9349).
 */
data class PolarRawSession(
    val metadata: PolarRawMetadata,
    val rows: List<RawTelemetryRow>
)

/**
 * Pure-JVM deterministic reader and replay parser for Polar raw telemetry GZIP sidecar archives
 * (REQ-SET-117.2, REQ-EXP-046.1).
 *
 * Uses small-chunk [GZIPInputStream] reads (matching `PhoneRawTelemetryReader`), gracefully handles
 * truncated GZIP EOF, and strictly discards any non-newline-terminated trailing bytes.
 */
object PolarRawTelemetryReader {

    private const val METADATA_PREFIX = "# metadata:"
    private const val HEADER_PREFIX = "sensorTimestampNs,"
    private const val GZIP_READ_BUFFER_BYTES = 512
    private const val INFLATE_CHUNK_BYTES = 512
    private const val MAX_LINE_BYTES = 4096

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
        } catch (_: IOException) {
            PolarRawSession(PolarRawMetadata(), emptyList())
        }
    }

    /**
     * Reads from a GZIP compressed input stream in small chunks without Android or `org.json`
     * runtime dependencies.
     */
    @JvmStatic
    fun readStream(inputStream: InputStream): PolarRawSession {
        var metadata = PolarRawMetadata()
        var metadataParsed = false
        val rows = ArrayList<RawTelemetryRow>()

        try {
            GZIPInputStream(inputStream, GZIP_READ_BUFFER_BYTES).use { gzipIn ->
                val chunk = ByteArray(INFLATE_CHUNK_BYTES)
                val lineBuf = ByteArrayOutputStream(INFLATE_CHUNK_BYTES)
                var overflowLine = false

                fun processLine(rawLine: String) {
                    val trimmed = rawLine.trim()
                    if (trimmed.isEmpty()) return
                    when {
                        trimmed.startsWith(METADATA_PREFIX) -> {
                            if (!metadataParsed) {
                                metadata = parseMetadata(trimmed)
                                metadataParsed = true
                            }
                        }
                        trimmed.startsWith(HEADER_PREFIX) -> Unit
                        else -> RawTelemetryRow.fromCsvLine(trimmed)?.let { rows.add(it) }
                    }
                }

                while (true) {
                    val n = try {
                        gzipIn.read(chunk)
                    } catch (_: EOFException) {
                        -1
                    } catch (_: ZipException) {
                        -1
                    } catch (_: IOException) {
                        -1
                    }
                    if (n <= 0) break

                    for (i in 0 until n) {
                        val b = chunk[i]
                        if (b == '\n'.code.toByte()) {
                            if (!overflowLine && lineBuf.size() > 0) {
                                processLine(lineBuf.toString(StandardCharsets.UTF_8.name()))
                            }
                            lineBuf.reset()
                            overflowLine = false
                        } else if (b != '\r'.code.toByte() && !overflowLine) {
                            if (lineBuf.size() < MAX_LINE_BYTES) {
                                lineBuf.write(b.toInt())
                            } else {
                                overflowLine = true
                            }
                        }
                    }
                }
                // Intentionally discard non-newline-terminated trailing bytes in lineBuf so
                // crash-truncated trailing numeric fields never corrupt replayed rows.
            }
        } catch (_: EOFException) {
            // Graceful truncated GZIP header/EOF handling
        } catch (_: ZipException) {
            // Graceful corrupt GZIP handling
        } catch (_: IOException) {
            // Graceful IO error handling
        }

        return PolarRawSession(metadata, rows)
    }

    private fun parseMetadata(line: String): PolarRawMetadata {
        val jsonStr = line.substring(METADATA_PREFIX.length).trim()
        return PolarRawMetadata.fromJsonString(jsonStr)
    }
}
