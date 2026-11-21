/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.atrainingtracker.trainingtracker.exporter.writer

import android.content.Context
import android.os.Build
import android.util.Log
import com.atrainingtracker.banalservice.devices.raw.RawSensorSidecarFiles
import com.atrainingtracker.trainingtracker.exporter.BaseExporter
import com.atrainingtracker.trainingtracker.exporter.ExportInfo

/**
 * Exporter for raw sensor sidecar files (`.polar_raw.csv.gz` and `.phone_raw.csv.gz`).
 * Traceable to REQ-EXP-044.1, REQ-EXP-045.3, REQ-STB-025.2.
 */
class RawSensorFileExporter(context: Context) : BaseExporter(context) {

    override fun doExport(exportInfo: ExportInfo): ExportResult {
        val sidecars = RawSensorSidecarFiles.resolveExportableSidecars(mContext, exportInfo.fileBaseName)
        if (sidecars.isEmpty()) {
            Log.i(TAG, "Raw sensor sidecar does not exist for ${exportInfo.fileBaseName} (graceful skip)")
            return ExportResult(true, false, "Raw sensor sidecar does not exist (graceful skip)")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val mimeType = exportInfo.fileFormat.mimeType
            for (sidecar in sidecars) {
                copyFileToDownloads(sidecar, mimeType)
            }
        }

        val msg = if (sidecars.size == 1) {
            "Successfully exported raw sensor sidecar"
        } else {
            "Successfully exported ${sidecars.size} raw sensor sidecars"
        }
        return ExportResult(true, false, msg)
    }

    companion object {
        private const val TAG = "RawSensorFileExporter"
    }
}
