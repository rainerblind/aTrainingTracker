/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.atrainingtracker.trainingtracker.exporter

import android.content.Context
import android.os.Build
import android.util.Log
import com.atrainingtracker.BuildConfig
import com.atrainingtracker.banalservice.devices.bluetooth_le.polar.PolarRawTelemetryRecorder
import com.atrainingtracker.trainingtracker.database.LapsDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSamplesDatabaseManager
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Diagnostic bundle packager for raw Polar telemetry troubleshooting (REQ-EXP-044.2, REQ-EXP-044.3).
 * Operates on [Dispatchers.IO] to package raw sidecars, session metrics, computed laps, and 1-Hz series
 * with privacy-preserving GPS stripping.
 */
object SensorDiagnosticBundlePackager {
    private const val TAG = "SensorDiagPackager"

    suspend fun createBundle(
        context: Context,
        fileBaseName: String,
        includeGps: Boolean,
        symptomNotes: String
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val diagDir = File(context.cacheDir, "diagnostics")
            if (!diagDir.exists()) {
                diagDir.mkdirs()
            }
            val zipFile = File(diagDir, "sensor_diagnostic_$fileBaseName.zip")
            if (zipFile.exists()) {
                zipFile.delete()
            }

            val sidecarFile = PolarRawTelemetryRecorder.getSidecarFile(context, fileBaseName)
            val phoneSidecarFile = com.atrainingtracker.banalservice.devices.raw.SmartphoneRawTelemetryRecorder.getSidecarFile(context, fileBaseName)

            val workoutRepo = WorkoutRepository.getInstance(context)
            val workoutId = workoutRepo.getWorkoutIdByBaseFileName(fileBaseName)
            val workoutData = if (workoutId > 0) workoutRepo.getWorkoutDataDirect(workoutId) else null

            val laps = if (workoutId > 0) {
                LapsDatabaseManager.getInstance(context).getLaps(workoutId)
            } else {
                emptyList()
            }

            ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                // 1. Raw Polar sidecar (if exists)
                if (sidecarFile.exists() && sidecarFile.length() > 0) {
                    val entryName = sidecarFile.name
                    zos.putNextEntry(ZipEntry(entryName))
                    FileInputStream(sidecarFile).use { fis ->
                        fis.copyTo(zos)
                    }
                    zos.closeEntry()
                }
                // 1b. Raw smartphone sidecar (contains GNSS lat/lon rows -> strictly gated by includeGps, REQ-EXP-045.4)
                if (includeGps && phoneSidecarFile != null && phoneSidecarFile.exists() && phoneSidecarFile.length() > 0) {
                    zos.putNextEntry(ZipEntry(phoneSidecarFile.name))
                    FileInputStream(phoneSidecarFile).use { fis ->
                        fis.copyTo(zos)
                    }
                    zos.closeEntry()
                }

                // 2. workout_summary.json
                val summaryJson = JSONObject().apply {
                    put("workoutId", workoutId)
                    put("fileBaseName", fileBaseName)
                    workoutData?.let { wd ->
                        put("workoutName", wd.workoutName)
                        put("sportName", wd.sportName)
                        put("startTimeS", wd.startTimeS)
                        put("totalDistance", wd.totalDistance)
                        put("totalTimeSec", wd.totalTimeSec)
                        put("activeTimeSec", wd.activeTimeSec)
                        put("avgSpeedMps", wd.avgSpeedMps)
                        put("ascentMeters", wd.ascentMeters)
                        put("descentMeters", wd.descentMeters)
                        if (includeGps) {
                            put("mapPolyline", wd.mapPolyline)
                        } else {
                            put("mapPolyline", "")
                        }
                    }
                }
                val summaryBytes = summaryJson.toString(2).toByteArray(Charsets.UTF_8)
                zos.putNextEntry(ZipEntry("workout_summary.json"))
                zos.write(summaryBytes)
                zos.closeEntry()

                // Also provide summary.json entry for convenience / backward-compat
                zos.putNextEntry(ZipEntry("summary.json"))
                zos.write(summaryBytes)
                zos.closeEntry()

                // 3. laps.json
                val lapsArray = JSONArray()
                laps.forEach { lap ->
                    val lapObj = JSONObject().apply {
                        put("id", lap.id)
                        put("workoutId", lap.workoutId)
                        put("lapNr", lap.lapNr)
                        put("timeTotalS", lap.timeTotalS)
                        put("distanceTotalM", lap.distanceTotalM)
                        put("speedAverageMps", lap.speedAverageMps)
                        put("name", lap.name ?: "")
                        put("description", lap.description ?: "")
                    }
                    lapsArray.put(lapObj)
                }
                val lapsJson = JSONObject().apply {
                    put("workoutId", workoutId)
                    put("laps", lapsArray)
                }
                zos.putNextEntry(ZipEntry("laps.json"))
                zos.write(lapsJson.toString(2).toByteArray(Charsets.UTF_8))
                zos.closeEntry()

                // 4. samples_1hz.csv
                val csvContent = buildSamplesCsv(context, fileBaseName, includeGps)
                zos.putNextEntry(ZipEntry("samples_1hz.csv"))
                zos.write(csvContent.toByteArray(Charsets.UTF_8))
                zos.closeEntry()

                // 5. diagnostic_metadata.json
                val metadataJson = JSONObject().apply {
                    put("appVersion", BuildConfig.VERSION_NAME)
                    put("appVersionCode", BuildConfig.VERSION_CODE)
                    put("androidVersion", Build.VERSION.RELEASE)
                    put("sdkInt", Build.VERSION.SDK_INT)
                    put("deviceManufacturer", Build.MANUFACTURER)
                    put("deviceModel", Build.MODEL)
                    put("includeGps", includeGps)
                    put("symptomNotes", symptomNotes)
                    put("timestampMs", System.currentTimeMillis())
                    put("fileBaseName", fileBaseName)
                }
                zos.putNextEntry(ZipEntry("diagnostic_metadata.json"))
                zos.write(metadataJson.toString(2).toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }

            Result.success(zipFile)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create diagnostic bundle for $fileBaseName", e)
            Result.failure(e)
        }
    }

    private fun buildSamplesCsv(context: Context, fileBaseName: String, includeGps: Boolean): String {
        return try {
            val dbManager = WorkoutSamplesDatabaseManager.getInstance(context)
            val db = dbManager.database
            val tableName = WorkoutSamplesDatabaseManager.getTableName(fileBaseName)

            db.query(tableName, null, null, null, null, null, null).use { cursor ->
                if (cursor.columnCount == 0) return@use ""

                val columnNames = cursor.columnNames
                val includedColumnIndices = mutableListOf<Int>()
                val headerBuilder = StringBuilder()

                for (i in columnNames.indices) {
                    val colName = columnNames[i]
                    val isGpsCol = colName.contains("LATITUDE", ignoreCase = true) ||
                                   colName.contains("LONGITUDE", ignoreCase = true)
                    if (!includeGps && isGpsCol) {
                        continue
                    }
                    includedColumnIndices.add(i)
                    if (headerBuilder.isNotEmpty()) headerBuilder.append(",")
                    headerBuilder.append(escapeCsv(colName))
                }
                headerBuilder.append("\n")

                val bodyBuilder = StringBuilder()
                while (cursor.moveToNext()) {
                    var first = true
                    for (colIdx in includedColumnIndices) {
                        if (!first) bodyBuilder.append(",")
                        first = false
                        val value = when (cursor.getType(colIdx)) {
                            android.database.Cursor.FIELD_TYPE_NULL -> ""
                            android.database.Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(colIdx).toString()
                            android.database.Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(colIdx).toString()
                            else -> cursor.getString(colIdx) ?: ""
                        }
                        bodyBuilder.append(escapeCsv(value))
                    }
                    bodyBuilder.append("\n")
                }

                headerBuilder.toString() + bodyBuilder.toString()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error building samples CSV for $fileBaseName: ${e.message}")
            ""
        }
    }

    private fun escapeCsv(value: String): String {
        return if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
    }
}
