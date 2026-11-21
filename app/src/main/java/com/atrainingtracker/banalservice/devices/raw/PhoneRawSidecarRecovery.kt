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

import android.content.Context
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
 * Crash-recovery salvage helper for truncated `.phone_raw.csv.gz.tmp` sidecar archives
 * (REQ-EXP-045.2, ATT-9348, mirroring `PolarRawSidecarRecovery` / `ATT-9344`).
 *
 * Stages an abandoned `.tmp` (or prior finalized `.phone_raw.csv.gz`) to `.salvage`,
 * decompresses complete deflate blocks via small-chunk [GZIPInputStream] reads, validates
 * every row with [PhoneRawTelemetryRow.fromCsvLine] to drop crash-truncated tails, and writes
 * a valid RFC-1952 `.phone_raw.csv.gz` archive (or streams into a live resumed writer).
 */
object PhoneRawSidecarRecovery {

    private const val SALVAGE_SUFFIX = ".salvage"
    private const val GZIP_READ_BUFFER_BYTES = 512
    private const val INFLATE_CHUNK_BYTES = 256
    private const val MAX_LINE_BYTES = 4096

    data class SalvageResult(
        val metadata: PhoneRawMetadata,
        val salvagedRowCount: Long
    )

    /**
     * Atomically moves an existing `.tmp` (preferred) or finalized `.phone_raw.csv.gz` to a
     * `.salvage` staging file before opening a fresh output stream.
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
        return if (source.renameTo(salvageFile)) salvageFile else null
    }

    /**
     * Reads complete metadata and validated CSV rows from [salvageFile], writes the header and
     * every intact row into [targetWriter], and deletes [salvageFile].
     */
    @JvmStatic
    fun salvageIntoWriter(
        salvageFile: File?,
        targetWriter: BufferedWriter,
        fallbackMetadata: PhoneRawMetadata
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
                                    val trimmed = lineBuf.toString(StandardCharsets.UTF_8.name()).trim()
                                    if (trimmed.isNotEmpty()) {
                                        when {
                                            trimmed.startsWith(PhoneRawMetadata.METADATA_PREFIX) -> {
                                                if (!headerWritten) {
                                                    resolvedMetadata = PhoneRawMetadata.parseHeaderLine(trimmed)
                                                        ?: fallbackMetadata
                                                }
                                            }
                                            trimmed == PhoneRawTelemetryRow.CSV_HEADER -> Unit
                                            PhoneRawTelemetryRow.fromCsvLine(trimmed) != null -> {
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
                            } else if (b != '\r'.code.toByte() && !overflowLine) {
                                if (lineBuf.size() < MAX_LINE_BYTES) {
                                    lineBuf.write(b.toInt())
                                } else {
                                    overflowLine = true
                                }
                            }
                        }
                    }
                    // Intentionally drop any non-newline-terminated trailing bytes in lineBuf:
                    // a crash-truncated numeric tail (e.g. GNSS accuracy "15.4" -> "1") may still
                    // parse as 9 columns and must never be salvaged (mirrors PolarRawSidecarRecovery).
                }
            }
        } catch (_: IOException) {
            // Truncated or unreadable GZIP header — fall through and write clean header
        } finally {
            salvageFile.delete()
        }

        if (!headerWritten) {
            writeHeader(targetWriter, resolvedMetadata)
        }
        return SalvageResult(resolvedMetadata, salvagedCount)
    }

    /**
     * Recovers an abandoned `.phone_raw.csv.gz.tmp` file into a finalized valid RFC-1952
     * `.phone_raw.csv.gz` archive (export-time fallback). Returns `true` if a non-empty valid
     * archive was produced.
     */
    @JvmStatic
    fun recoverAbandonedTmpToFinal(tempFile: File, targetFile: File): Boolean {
        if (!tempFile.exists() || tempFile.length() == 0L) {
            if (tempFile.exists()) tempFile.delete()
            return targetFile.exists() && targetFile.length() > 0L
        }
        val salvageFile = prepareSalvageSource(tempFile, targetFile) ?: return false
        val recoveredTmp = File(targetFile.parentFile, "${targetFile.name}.recovered.tmp")
        if (recoveredTmp.exists()) recoveredTmp.delete()

        return try {
            var salvagedRows = 0L
            GZIPOutputStream(FileOutputStream(recoveredTmp), true).use { gzipOut ->
                BufferedWriter(OutputStreamWriter(gzipOut, StandardCharsets.UTF_8)).use { writer ->
                    salvagedRows = salvageIntoWriter(salvageFile, writer, PhoneRawMetadata()).salvagedRowCount
                    writer.flush()
                }
            }
            if (salvagedRows > 0L) {
                if (targetFile.exists()) targetFile.delete()
                if (recoveredTmp.renameTo(targetFile)) {
                    true
                } else {
                    recoveredTmp.delete()
                    false
                }
            } else {
                recoveredTmp.delete()
                false
            }
        } catch (_: IOException) {
            recoveredTmp.delete()
            false
        }
    }

    /**
     * Context-convenience overload for export-time fallback (`RawSensorSidecarFiles`).
     */
    @JvmStatic
    fun recoverAbandonedTmpToFinal(context: Context, fileBaseName: String): Boolean {
        val targetFile = SmartphoneRawTelemetryRecorder.getSidecarFile(context, fileBaseName) ?: return false
        val tempFile = SmartphoneRawTelemetryRecorder.getTempSidecarFile(context, fileBaseName) ?: return false
        return recoverAbandonedTmpToFinal(tempFile, targetFile)
    }

    @JvmStatic
    fun writeHeader(writer: BufferedWriter, metadata: PhoneRawMetadata) {
        writer.write(metadata.toHeaderLine())
        writer.newLine()
        writer.write(PhoneRawTelemetryRow.CSV_HEADER)
        writer.newLine()
    }
}
