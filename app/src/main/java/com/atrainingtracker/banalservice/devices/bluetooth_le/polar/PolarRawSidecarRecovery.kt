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

import android.content.Context
import android.util.Log
import org.json.JSONException
import java.io.BufferedWriter
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipException

/**
 * Crash-recovery salvage helper for truncated `.polar_raw.csv.gz.tmp` sidecar files
 * (`ATT-9344`, `REQ-STB-025.2`).
 *
 * When the process terminates mid-recording (e.g., OOM/SIGKILL), `GZIPOutputStream.close()` never
 * writes the RFC-1952 CRC32/ISIZE footer, leaving a truncated `.tmp` archive on disk. This helper
 * atomically moves the `.tmp` (or prior finalized `.polar_raw.csv.gz`) to `.salvage`, decompresses
 * every complete deflate block via [GZIPInputStream] (gracefully terminating on [EOFException] /
 * [ZipException]), validates every CSV data line through [RawTelemetryRow.fromCsvLine] to drop any
 * crash-truncated partial trailing line, and streams the clean rows into a valid GZIP archive.
 */
object PolarRawSidecarRecovery {

    private const val TAG = "PolarRawSidecarRecovery"
    private const val METADATA_PREFIX = "# metadata:"
    private const val HEADER_PREFIX = "sensorTimestampNs,"
    private const val SALVAGE_SUFFIX = ".salvage"
    private const val GZIP_READ_BUFFER_BYTES = 512
    private const val INFLATE_CHUNK_BYTES = 256
    private const val MAX_LINE_BYTES = 4096

    data class SalvageResult(
        val metadata: PolarRawMetadata,
        val salvagedRowCount: Long
    )

    /**
     * Atomically stages an existing `.tmp` (preferred, or existing finalized `.polar_raw.csv.gz`)
     * to a `.salvage` file before opening a fresh output `.tmp` stream. Returns `null` if neither
     * exists or is empty.
     */
    @JvmStatic
    fun prepareSalvageSource(tempFile: File, targetFile: File): File? {
        val source = when {
            tempFile.exists() && tempFile.length() > 0L -> tempFile
            targetFile.exists() && targetFile.length() > 0L -> targetFile
            else -> return null
        }
        val salvageFile = File(source.parentFile, "${tempFile.name}$SALVAGE_SUFFIX")
        if (salvageFile.exists()) {
            salvageFile.delete()
        }
        return if (source.renameTo(salvageFile)) {
            salvageFile
        } else {
            Log.w(TAG, "Failed to stage ${source.name} to ${salvageFile.name} for salvage")
            null
        }
    }

    /**
     * Reads complete metadata and validated CSV rows from [salvageFile] (which may be a truncated
     * GZIP stream), writes the header (`# metadata:` + CSV column header) and every valid row into
     * [targetWriter], and deletes [salvageFile].
     */
    @JvmStatic
    fun salvageIntoWriter(
        salvageFile: File?,
        targetWriter: BufferedWriter,
        fallbackMetadata: PolarRawMetadata
    ): SalvageResult {
        if (salvageFile == null || !salvageFile.exists() || salvageFile.length() == 0L) {
            salvageFile?.delete()
            writeHeader(targetWriter, fallbackMetadata)
            return SalvageResult(fallbackMetadata, 0L)
        }

        var resolvedMetadata = fallbackMetadata
        var headerWritten = false
        var salvagedCount = 0L

        try {
            FileInputStream(salvageFile).use { fis ->
                GZIPInputStream(fis, GZIP_READ_BUFFER_BYTES).use { gzipIn ->
                    val chunk = ByteArray(INFLATE_CHUNK_BYTES)
                    val lineBuf = ByteArrayOutputStream(INFLATE_CHUNK_BYTES)
                    var overflowLine = false

                    // Read small byte chunks directly from GZIPInputStream rather than wrapping in
                    // BufferedReader/InputStreamReader (whose 8 KB StreamDecoder pre-fill triggers
                    // EOFException before emitting early lines on truncated <8 KB streams).
                    while (true) {
                        val n = try {
                            gzipIn.read(chunk)
                        } catch (e: EOFException) {
                            Log.d(TAG, "Reached truncated GZIP EOF while salvaging ${salvageFile.name} after $salvagedCount rows")
                            -1
                        } catch (e: ZipException) {
                            Log.w(TAG, "Truncated/corrupt GZIP tail in ${salvageFile.name} after $salvagedCount rows: ${e.message}")
                            -1
                        } catch (e: IOException) {
                            Log.w(TAG, "IOException reading ${salvageFile.name} after $salvagedCount rows: ${e.message}")
                            -1
                        }
                        if (n <= 0) break

                        for (i in 0 until n) {
                            val b = chunk[i]
                            if (b == '\n'.code.toByte()) {
                                if (!overflowLine && lineBuf.size() > 0) {
                                    val line = lineBuf.toString(StandardCharsets.UTF_8.name())
                                    val trimmed = line.trim()
                                    if (trimmed.isNotEmpty()) {
                                        when {
                                            trimmed.startsWith(METADATA_PREFIX) -> {
                                                if (!headerWritten) {
                                                    resolvedMetadata = parseMetadataOrFallback(trimmed, fallbackMetadata)
                                                }
                                            }
                                            trimmed.startsWith(HEADER_PREFIX) -> Unit
                                            // Validate line structure to drop crash-truncated partial rows (REQ-STB-025.2)
                                            RawTelemetryRow.fromCsvLine(trimmed) != null -> {
                                                if (!headerWritten) {
                                                    writeHeader(targetWriter, resolvedMetadata)
                                                    headerWritten = true
                                                }
                                                targetWriter.write(trimmed)
                                                targetWriter.newLine()
                                                salvagedCount++
                                            }
                                        }
                                    }
                                }
                                lineBuf.reset()
                                overflowLine = false
                            } else if (!overflowLine) {
                                if (lineBuf.size() < MAX_LINE_BYTES) {
                                    lineBuf.write(b.toInt() and 0xFF)
                                } else {
                                    overflowLine = true
                                }
                            }
                        }
                    }
                    // Any remaining non-newline-terminated trailing bytes in lineBuf are dropped
                    // intentionally because a crash-truncated tail without '\n' may be incomplete.
                }
            }
        } catch (e: EOFException) {
            Log.d(TAG, "Truncated GZIP stream header/tail in ${salvageFile.name} (salvaged=$salvagedCount)")
        } catch (e: ZipException) {
            Log.w(TAG, "Invalid GZIP stream in ${salvageFile.name} (salvaged=$salvagedCount): ${e.message}")
        } catch (e: IOException) {
            Log.w(TAG, "IOException opening ${salvageFile.name} for salvage: ${e.message}")
        } finally {
            salvageFile.delete()
        }

        if (!headerWritten) {
            writeHeader(targetWriter, resolvedMetadata)
        }
        return SalvageResult(resolvedMetadata, salvagedCount)
    }

    /**
     * Offline/export-time fallback: if an abandoned `.polar_raw.csv.gz.tmp` exists for
     * [fileBaseName] while no valid finalized `.polar_raw.csv.gz` exists yet, salvages all
     * complete valid rows into a valid RFC-1952 `.polar_raw.csv.gz` file. Leaves zero orphan
     * files if the `.tmp` contains zero recoverable rows (`REQ-SET-117.1`, `REQ-STB-025.2`).
     */
    @JvmStatic
    fun recoverAbandonedTmpToFinal(context: Context, fileBaseName: String): Boolean {
        if (fileBaseName.isBlank()) return false
        val dir = PolarRawTelemetryRecorder.getRawSensorDir(context)
        val targetFile = PolarRawTelemetryRecorder.getSidecarFile(context, fileBaseName)
        if (targetFile.exists() && targetFile.length() > 0L) {
            return true
        }
        val tempFile = File(
            dir,
            "$fileBaseName${PolarRawTelemetryRecorder.SIDECAR_EXTENSION}${PolarRawTelemetryRecorder.TMP_EXTENSION}"
        )
        if (!tempFile.exists() || tempFile.length() == 0L) {
            if (tempFile.exists()) tempFile.delete()
            return false
        }

        val salvageFile = prepareSalvageSource(tempFile, targetFile) ?: return false
        val rebuiltTmp = File(dir, "${tempFile.name}.rebuilt")
        var salvagedRows = 0L
        try {
            FileOutputStream(rebuiltTmp).use { fos ->
                GZIPOutputStream(fos).use { gzipOut ->
                    BufferedWriter(OutputStreamWriter(gzipOut, StandardCharsets.UTF_8)).use { writer ->
                        val result = salvageIntoWriter(salvageFile, writer, PolarRawMetadata(streamMode = "LIVE"))
                        salvagedRows = result.salvagedRowCount
                        writer.flush()
                    }
                }
            }
            if (salvagedRows > 0L) {
                if (targetFile.exists()) targetFile.delete()
                val renamed = rebuiltTmp.renameTo(targetFile)
                if (renamed) {
                    Log.i(TAG, "Recovered abandoned .tmp sidecar for $fileBaseName ($salvagedRows rows -> ${targetFile.name})")
                    return true
                }
                Log.w(TAG, "Failed to rename rebuilt sidecar ${rebuiltTmp.name} -> ${targetFile.name}")
            }
        } catch (e: IOException) {
            Log.w(TAG, "Failed to recover abandoned .tmp sidecar for $fileBaseName: ${e.message}")
        }
        rebuiltTmp.delete()
        return false
    }

    private fun writeHeader(writer: BufferedWriter, metadata: PolarRawMetadata) {
        writer.write("$METADATA_PREFIX ${metadata.toJsonString()}")
        writer.newLine()
        writer.write(RawTelemetryRow.CSV_HEADER)
        writer.newLine()
    }

    private fun parseMetadataOrFallback(line: String, fallback: PolarRawMetadata): PolarRawMetadata {
        val jsonStr = line.substring(METADATA_PREFIX.length).trim()
        return try {
            PolarRawMetadata.fromJsonString(jsonStr)
        } catch (_: JSONException) {
            fallback
        }
    }
}
