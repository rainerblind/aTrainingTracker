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

package com.atrainingtracker.trainingtracker.exporter.writer

import android.content.Context
import android.os.Build
import android.util.Log
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.database.DatabaseConstants
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.exporter.BaseExporter
import com.atrainingtracker.trainingtracker.exporter.ExportInfo
import com.atrainingtracker.trainingtracker.exporter.ExportType
import com.atrainingtracker.trainingtracker.exporter.FileFormat
import com.atrainingtracker.trainingtracker.repositories.SportTypesRepository
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutExportHeader
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository
import java.io.File

/**
 * Base exporter class providing shared export header hydration, output file management,
 * and scoped storage downloads copying for all workout file writers.
 *
 * Traceability:
 * - REQ-ARC-010: Java Core Migration: Streaming File Writers Kotlin Migration & Interoperability Parity.
 * - REQ-EXP-022: Multi-format export interoperability & scoped storage Downloads copying.
 */
abstract class BaseFileWriter(context: Context) : BaseExporter(context) {

    companion object {
        protected const val MIN_DATA_POINTS_FOR_UPLOAD: Int = 10
        private const val TAG: String = "BaseFileExporter"
        private const val DEBUG: Boolean = false
    }

    @JvmField
    var startTime: String? = null

    @JvmField
    var totalTime: String? = null

    @JvmField
    var data: String? = null

    @JvmField
    var goal: String? = null

    @JvmField
    var method: String? = null

    @JvmField
    var totalDistance: String? = null

    @JvmField
    var description: String? = null

    @JvmField
    var workoutName: String? = null

    @JvmField
    var indoorTrainerSession: Boolean = false

    @JvmField
    var haveDistance: Boolean = false

    @JvmField
    var haveSpeed: Boolean = false

    @JvmField
    var havePower: Boolean = false

    @JvmField
    var haveHR: Boolean = false

    @JvmField
    var haveCadence: Boolean = false

    @JvmField
    var haveRunCadence: Boolean = false

    @JvmField
    var haveBikeCadence: Boolean = false

    @JvmField
    var haveTorque: Boolean = false

    @JvmField
    var haveAltitude: Boolean = false

    @JvmField
    var haveGeo: Boolean = false

    @JvmField
    var workoutID: Long = 0L

    @JvmField
    var sportTypeId: Long = 0L

    @JvmField
    var exportHeader: WorkoutExportHeader? = null

    open fun getOutputFile(exportInfo: ExportInfo): File {
        return File(getBaseDirFile(mContext), exportInfo.shortPath)
    }

    open fun deleteOutputFileSafely(exportInfo: ExportInfo) {
        val file = getOutputFile(exportInfo)
        if (file.exists()) {
            val deleted = file.delete()
            Log.i(TAG, "Deleted output file on cancellation or failure: ${file.absolutePath} (success=$deleted)")
        }
    }

    /**
     * Determines whether copying exported files to the shared public Downloads directory
     * is supported on the active Android runtime (Android 10+ / API 29+ Scoped Storage).
     */
    protected open fun isDownloadsCopySupported(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    }

    /**
     * Post-export hook invoked upon successful export completion.
     * Preserves public Downloads copy for explicit user file exports (CSV, TCX, GPX, FIT, GC)
     * while strictly excluding internal uploads (STRAVA or COMMUNITY) to protect athlete privacy
     * from leaking GPS traces into public shared storage (REQ-EXP-022, ATT-4013).
     */
    override fun onFinished(exportInfo: ExportInfo) {
        if (exportInfo.fileFormat != FileFormat.STRAVA && exportInfo.exportType != ExportType.COMMUNITY) {
            if (isDownloadsCopySupported()) {
                copyFileToDownloads(exportInfo)
            }
        }
    }

    protected open fun getHeaderData(exportInfo: ExportInfo) {
        val header = WorkoutRepository.getInstance(mContext).getWorkoutExportHeader(exportInfo.fileBaseName)
        exportHeader = header
        if (header != null) {
            startTime = header.startTime ?: ""
            totalTime = header.totalTime ?: "0"
            data = header.data ?: "--------"
            goal = header.goal ?: ""
            method = header.method ?: ""
            totalDistance = header.totalDistance ?: "0"
            description = header.description ?: ""
            workoutName = header.workoutName ?: ""
            workoutID = header.workoutId
            sportTypeId = header.sportTypeId
            indoorTrainerSession = header.indoorTrainerSession
        } else {
            startTime = ""
            totalTime = "0"
            data = "--------"
            goal = ""
            method = ""
            totalDistance = "0"
            description = ""
            workoutName = ""
            workoutID = DatabaseConstants.INVALID_ID
            sportTypeId = DatabaseConstants.INVALID_ID
            indoorTrainerSession = false
        }

        val d = data ?: ""
        haveDistance = d.indexOf('D') > 0
        haveSpeed = d.indexOf('S') > 0
        havePower = d.indexOf('P') > 0
        haveHR = d.indexOf('H') > 0
        haveCadence = d.indexOf('C') > 0
        haveTorque = d.indexOf('N') > 0
        haveAltitude = d.indexOf('A') > 0
        haveGeo = d.indexOf('G') > 0

        // selective upload
        if (exportInfo.exportType == ExportType.FILE) {
            if (exportInfo.fileFormat == FileFormat.STRAVA) {
                haveGeo = haveGeo && TrainingApplication.uploadStravaGPS()
                haveAltitude = haveAltitude && TrainingApplication.uploadStravaAltitude()
                haveHR = haveHR && TrainingApplication.uploadStravaHR()
                havePower = havePower && TrainingApplication.uploadStravaPower()
                haveCadence = haveCadence && TrainingApplication.uploadStravaCadence()
            }
        }

        val bSportType = SportTypesRepository.getInstance(mContext).getBSportType(sportTypeId)
        haveBikeCadence = haveCadence && bSportType == BSportType.BIKE
        haveRunCadence = haveCadence && bSportType == BSportType.RUN
    }
}
