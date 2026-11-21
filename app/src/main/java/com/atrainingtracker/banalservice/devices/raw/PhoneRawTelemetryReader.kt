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
 * Pure-JVM streaming parser for `.phone_raw.csv.gz` sidecar archives (REQ-EXP-045.2, ATT-9348).
 *
 * Designed for direct consumption by the downstream offline simulation replay harness (`ATT-9349`)
 * with zero Android dependencies and graceful truncated-EOF recovery.
 */
object PhoneRawTelemetryReader {

    private const val GZIP_READ_BUFFER_BYTES = 512
    private const val INFLATE_CHUNK_BYTES = 512
    private const val MAX_LINE_BYTES = 4096

    data class ParsedArchive(
        val metadata: PhoneRawMetadata?,
        val rows: List<PhoneRawTelemetryRow>,
        val skippedMalformedLines: Int,
        val truncatedEof: Boolean
    )

    /**
     * Parses an entire `.phone_raw.csv.gz` archive into memory.
     */
    @JvmStatic
    fun readAll(file: File): ParsedArchive {
        if (!file.exists() || file.length() == 0L) {
            return ParsedArchive(null, emptyList(), 0, false)
        }
        return FileInputStream(file).use { fis -> readAll(fis) }
    }

    /**
     * Parses a `.phone_raw.csv.gz` stream into memory.
     */
    @JvmStatic
    fun readAll(inputStream: InputStream): ParsedArchive {
        val rows = ArrayList<PhoneRawTelemetryRow>()
        var parsedMeta: PhoneRawMetadata? = null
        val stats = streamRows(
            inputStream = inputStream,
            onMetadata = { parsedMeta = it },
            onRow = { rows.add(it) }
        )
        return ParsedArchive(
            metadata = parsedMeta,
            rows = rows,
            skippedMalformedLines = stats.skippedMalformedLines,
            truncatedEof = stats.truncatedEof
        )
    }

    data class StreamStats(
        val rowCount: Long,
        val skippedMalformedLines: Int,
        val truncatedEof: Boolean
    )

    /**
     * Streams metadata and validated [PhoneRawTelemetryRow] records in $O(1)$ memory with
     * graceful handling of truncated GZIP streams.
     */
    @JvmStatic
    fun streamRows(
        file: File,
        onMetadata: (PhoneRawMetadata) -> Unit = {},
        onRow: (PhoneRawTelemetryRow) -> Unit
    ): StreamStats {
        if (!file.exists() || file.length() == 0L) {
            return StreamStats(0L, 0, false)
        }
        return FileInputStream(file).use { fis ->
            streamRows(fis, onMetadata, onRow)
        }
    }

    /**
     * Streams metadata and validated [PhoneRawTelemetryRow] records from a GZIP [InputStream].
     */
    @JvmStatic
    fun streamRows(
        inputStream: InputStream,
        onMetadata: (PhoneRawMetadata) -> Unit = {},
        onRow: (PhoneRawTelemetryRow) -> Unit
    ): StreamStats {
        var rowCount = 0L
        var skipped = 0
        var truncated = false
        var metadataEmitted = false

        try {
            GZIPInputStream(inputStream, GZIP_READ_BUFFER_BYTES).use { gzipIn ->
                val chunk = ByteArray(INFLATE_CHUNK_BYTES)
                val lineBuf = ByteArrayOutputStream(INFLATE_CHUNK_BYTES)
                var overflowLine = false

                fun processLine(rawLine: String) {
                    val trimmed = rawLine.trim()
                    if (trimmed.isEmpty()) return
                    when {
                        trimmed.startsWith(PhoneRawMetadata.METADATA_PREFIX) -> {
                            if (!metadataEmitted) {
                                PhoneRawMetadata.parseHeaderLine(trimmed)?.let {
                                    metadataEmitted = true
                                    onMetadata(it)
                                }
                            }
                        }
                        trimmed == PhoneRawTelemetryRow.CSV_HEADER -> Unit
                        else -> {
                            val row = PhoneRawTelemetryRow.fromCsvLine(trimmed)
                            if (row != null) {
                                rowCount++
                                onRow(row)
                            } else {
                                skipped++
                            }
                        }
                    }
                }

                while (true) {
                    val n = try {
                        gzipIn.read(chunk)
                    } catch (_: EOFException) {
                        truncated = true
                        -1
                    } catch (_: ZipException) {
                        truncated = true
                        -1
                    } catch (_: IOException) {
                        truncated = true
                        -1
                    }
                    if (n <= 0) break

                    for (i in 0 until n) {
                        val b = chunk[i]
                        if (b == '\n'.code.toByte()) {
                            if (!overflowLine && lineBuf.size() > 0) {
                                processLine(lineBuf.toString(StandardCharsets.UTF_8.name()))
                            } else if (overflowLine) {
                                skipped++
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
                // Intentionally discard any non-newline-terminated trailing bytes in lineBuf so
                // crash-truncated trailing numeric fields never corrupt replayed rows.
                if (lineBuf.size() > 0 || overflowLine) {
                    skipped++
                }
            }
        } catch (_: EOFException) {
            truncated = true
        } catch (_: ZipException) {
            truncated = true
        } catch (_: IOException) {
            truncated = true
        }

        return StreamStats(rowCount, skipped, truncated)
    }
}
