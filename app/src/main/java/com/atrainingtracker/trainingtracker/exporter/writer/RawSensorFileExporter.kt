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
import com.atrainingtracker.banalservice.devices.bluetooth_le.polar.PolarRawSidecarRecovery
import com.atrainingtracker.banalservice.devices.bluetooth_le.polar.PolarRawTelemetryRecorder
import com.atrainingtracker.trainingtracker.exporter.BaseExporter
import com.atrainingtracker.trainingtracker.exporter.ExportInfo

/**
 * Exporter for raw Polar sensor sidecar files (.polar_raw.csv.gz).
 * Traceable to REQ-EXP-044.1, REQ-STB-025.2.
 */
class RawSensorFileExporter(context: Context) : BaseExporter(context) {

    override fun doExport(exportInfo: ExportInfo): ExportResult {
        val sidecarFile = PolarRawTelemetryRecorder.getSidecarFile(mContext, exportInfo.fileBaseName)
        if (!sidecarFile.exists() || sidecarFile.length() == 0L) {
            PolarRawSidecarRecovery.recoverAbandonedTmpToFinal(mContext, exportInfo.fileBaseName)
        }
        if (!sidecarFile.exists() || sidecarFile.length() == 0L) {
            Log.i(TAG, "Raw sensor sidecar does not exist for ${exportInfo.fileBaseName} (graceful skip)")
            return ExportResult(true, false, "Raw sensor sidecar does not exist (graceful skip)")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            copyFileToDownloads(exportInfo)
        }

        return ExportResult(true, false, "Successfully exported raw sensor sidecar")
    }

    companion object {
        private const val TAG = "RawSensorFileExporter"
    }
}
