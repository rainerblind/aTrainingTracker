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
import com.atrainingtracker.banalservice.devices.raw.RawSensorSidecarFiles
import com.atrainingtracker.trainingtracker.settings.RawSensorRetentionPolicy
import com.atrainingtracker.trainingtracker.settings.SettingsDataStoreJavaHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Rolling retention policy manager and cascade deletion pruner for raw Polar sensor sidecars
 * (REQ-SET-117.3, REQ-DAT-042, ATT-8019).
 */
object PolarRawTelemetryPruner {

    private const val TAG = "PolarRawTelemetryPruner"
    private const val MS_PER_DAY = 24L * 60L * 60L * 1000L

    /**
     * Checks whether any raw telemetry sidecar file (`.polar_raw.csv.gz` or `.phone_raw.csv.gz`)
     * exists and is non-empty for [fileBaseName] (REQ-SET-117.3, REQ-EXP-045.4).
     */
    @JvmStatic
    fun hasSidecar(context: Context?, fileBaseName: String?): Boolean {
        return RawSensorSidecarFiles.hasAnySidecar(context, fileBaseName)
    }

    /**
     * Deletes all raw telemetry sidecar files matching [fileBaseName] (`.polar_raw.csv.gz*` and
     * `.phone_raw.csv.gz*`) during workout deletion cascade (REQ-DAT-042, REQ-EXP-045.4).
     */
    @JvmStatic
    fun deleteSidecar(context: Context?, fileBaseName: String?): Boolean {
        if (context == null || fileBaseName.isNullOrBlank()) return false
        var deleted = false
        for (candidate in RawSensorSidecarFiles.getAllSidecarAndTmpFiles(context, fileBaseName)) {
            if (candidate.exists()) {
                deleted = candidate.delete() || deleted
            }
        }
        if (deleted) {
            Log.d(TAG, "Cascade deleted raw telemetry sidecar(s) for $fileBaseName")
        }
        return deleted
    }

    /**
     * Prunes expired raw sensor sidecars matching the given [policy].
     *
     * @return count of deleted sidecar files.
     */
    @JvmStatic
    fun pruneExpiredSidecars(
        context: Context,
        policy: RawSensorRetentionPolicy = SettingsDataStoreJavaHelper.getRawSensorRetentionPolicy(context),
        nowEpochMs: Long = System.currentTimeMillis()
    ): Int {
        if (policy == RawSensorRetentionPolicy.KEEP_ALL) {
            return 0
        }
        val safeContext = resolveSafeStorageContext(context) ?: return 0
        val dir = PolarRawTelemetryRecorder.getRawSensorDir(safeContext)
        val files = dir.listFiles() ?: emptyArray()

        var deletedCount = 0
        val cutoffMs = if (policy.days > 0) nowEpochMs - (policy.days * MS_PER_DAY) else Long.MAX_VALUE
        val dateFormat = SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.US)

        for (file in files) {
            if (isEligibleSidecarFile(file)) {
                val fileTs = resolveFileTimestamp(file, dateFormat)
                if ((policy == RawSensorRetentionPolicy.OFF || fileTs < cutoffMs) && file.delete()) {
                    deletedCount++
                    Log.d(TAG, "Pruned expired raw telemetry sidecar ${file.name} (policy=$policy)")
                }
            }
        }
        if (deletedCount > 0) {
            Log.i(TAG, "Pruned $deletedCount expired raw telemetry sidecars under policy $policy")
        }
        return deletedCount
    }

    private fun isEligibleSidecarFile(file: File): Boolean {
        return RawSensorSidecarFiles.isManagedSidecarFile(file)
    }

    private fun resolveFileTimestamp(file: File, dateFormat: SimpleDateFormat): Long {
        val fileBaseName = RawSensorSidecarFiles.extractFileBaseName(file)

        return try {
            dateFormat.parse(fileBaseName)?.time ?: file.lastModified()
        } catch (_: ParseException) {
            file.lastModified()
        }
    }

    /**
     * Resolves a storage-capable [Context] whose `filesDir` has a non-blank path, or `null` for
     * uninitialized / relaxed MockK contexts (where Objenesis instantiates `File` with `path == null`).
     */
    private fun resolveSafeStorageContext(context: Context): Context? {
        val safeContext = try {
            context.applicationContext ?: context
        } catch (_: Throwable) {
            context
        }
        val filesDirPath: String? = try {
            safeContext.filesDir?.path
        } catch (_: Throwable) {
            null
        }
        return if (filesDirPath.isNullOrBlank()) null else safeContext
    }

    /**
     * Executes retention pruning off the main thread on [Dispatchers.IO].
     */
    @JvmStatic
    fun pruneExpiredSidecarsAsync(context: Context) {
        val safeContext = resolveSafeStorageContext(context) ?: return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                pruneExpiredSidecars(safeContext)
            } catch (e: SecurityException) {
                Log.w(TAG, "SecurityException during raw telemetry pruning: ${e.message}")
            }
        }
    }
}
