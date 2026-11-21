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
import com.atrainingtracker.banalservice.devices.bluetooth_le.polar.PolarRawSidecarRecovery
import com.atrainingtracker.banalservice.devices.bluetooth_le.polar.PolarRawTelemetryRecorder
import java.io.File

/**
 * Shared sidecar discovery, salvage, and path resolution across all `FileFormat.RAW_SENSOR`
 * archives (`.polar_raw.csv.gz` and `.phone_raw.csv.gz`) (REQ-EXP-045.3, REQ-EXP-045.4, ATT-9348).
 */
object RawSensorSidecarFiles {

    /**
     * Triggers abandoned `.tmp` crash-recovery salvage for both Polar and smartphone raw sidecars
     * and returns all existing non-empty finalized sidecar files in deterministic order:
     * `[.polar_raw.csv.gz, .phone_raw.csv.gz]` (omitting whichever is absent).
     */
    @JvmStatic
    fun resolveExportableSidecars(context: Context?, fileBaseName: String?): List<File> {
        if (context == null || fileBaseName.isNullOrBlank()) return emptyList()
        val result = ArrayList<File>(2)

        try {
            val polarFile = PolarRawTelemetryRecorder.getSidecarFile(context, fileBaseName)
            if (!polarFile.exists() || polarFile.length() == 0L) {
                PolarRawSidecarRecovery.recoverAbandonedTmpToFinal(context, fileBaseName)
            }
            if (polarFile.exists() && polarFile.length() > 0L) {
                result.add(polarFile)
            }
        } catch (_: Throwable) {
            // Safe fallback in headless unit tests
        }

        try {
            val phoneFile = SmartphoneRawTelemetryRecorder.getSidecarFile(context, fileBaseName)
            if (phoneFile != null) {
                if (!phoneFile.exists() || phoneFile.length() == 0L) {
                    PhoneRawSidecarRecovery.recoverAbandonedTmpToFinal(context, fileBaseName)
                }
                if (phoneFile.exists() && phoneFile.length() > 0L) {
                    result.add(phoneFile)
                }
            }
        } catch (_: Throwable) {
            // Safe fallback in headless unit tests
        }

        return result
    }

    /**
     * Returns `true` if either a Polar or smartphone raw sidecar exists and is non-empty.
     */
    @JvmStatic
    fun hasAnySidecar(context: Context?, fileBaseName: String?): Boolean {
        if (context == null || fileBaseName.isNullOrBlank()) return false
        val polarExists = try {
            val f = PolarRawTelemetryRecorder.getSidecarFile(context, fileBaseName)
            f.exists() && f.length() > 0L
        } catch (_: Throwable) {
            false
        }
        if (polarExists) return true
        return try {
            val f = SmartphoneRawTelemetryRecorder.getSidecarFile(context, fileBaseName)
            f != null && f.exists() && f.length() > 0L
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Returns all sidecar and active `.tmp` file candidates for [fileBaseName] (Polar + Phone)
     * for workout cascade deletion.
     */
    @JvmStatic
    fun getAllSidecarAndTmpFiles(context: Context?, fileBaseName: String?): List<File> {
        if (context == null || fileBaseName.isNullOrBlank()) return emptyList()
        val out = ArrayList<File>(4)
        try {
            val dir = PolarRawTelemetryRecorder.getRawSensorDir(context)
            val polarName = "$fileBaseName${PolarRawTelemetryRecorder.SIDECAR_EXTENSION}"
            out.add(File(dir, polarName))
            out.add(File(dir, "$polarName${PolarRawTelemetryRecorder.TMP_EXTENSION}"))
            val phoneName = "$fileBaseName${SmartphoneRawTelemetryRecorder.SIDECAR_EXTENSION}"
            out.add(File(dir, phoneName))
            out.add(File(dir, "$phoneName${SmartphoneRawTelemetryRecorder.TMP_EXTENSION}"))
        } catch (_: Throwable) {
            // Safe fallback
        }
        return out
    }

    /**
     * Returns `true` if [file] is a Polar or smartphone raw sidecar (or `.tmp`) eligible for
     * retention pruning.
     */
    @JvmStatic
    fun isManagedSidecarFile(file: File): Boolean {
        val name = file.name
        return name.endsWith(PolarRawTelemetryRecorder.SIDECAR_EXTENSION) ||
            name.endsWith("${PolarRawTelemetryRecorder.SIDECAR_EXTENSION}${PolarRawTelemetryRecorder.TMP_EXTENSION}") ||
            name.endsWith(SmartphoneRawTelemetryRecorder.SIDECAR_EXTENSION) ||
            name.endsWith("${SmartphoneRawTelemetryRecorder.SIDECAR_EXTENSION}${SmartphoneRawTelemetryRecorder.TMP_EXTENSION}") ||
            name.endsWith(PolarRawTelemetryRecorder.TMP_EXTENSION)
    }

    /**
     * Extracts the workout `fileBaseName` prefix from any Polar or smartphone raw sidecar file.
     */
    @JvmStatic
    fun extractFileBaseName(file: File): String {
        return file.name
            .removeSuffix(PolarRawTelemetryRecorder.TMP_EXTENSION)
            .removeSuffix(PolarRawTelemetryRecorder.SIDECAR_EXTENSION)
            .removeSuffix(SmartphoneRawTelemetryRecorder.SIDECAR_EXTENSION)
    }

    /**
     * Returns the relative export short path (e.g. `raw_sensor/2026-11-21_080000.phone_raw.csv.gz`).
     */
    @JvmStatic
    fun shortPathFor(sidecarFile: File): String {
        return "${SmartphoneRawTelemetryRecorder.RAW_SENSOR_DIR}/${sidecarFile.name}"
    }
}
