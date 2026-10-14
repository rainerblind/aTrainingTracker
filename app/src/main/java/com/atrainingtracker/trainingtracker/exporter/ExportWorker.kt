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

package com.atrainingtracker.trainingtracker.exporter

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.exporter.db.ExportStatusDatabaseManager
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository
import com.atrainingtracker.trainingtracker.ui.components.export.ExportNotificationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONException

class ExportWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private lateinit var mExportInfo: ExportInfo
    private lateinit var mExporter: BaseExporter

    private val mExportNotificationManager = ExportNotificationManager.getInstance(context)
    private val repository = ExportStatusDatabaseManager.getInstance(applicationContext)

    @SuppressLint("MissingPermission")
    override suspend fun doWork(): Result {
        val inputData = inputData
        val exportInfoJson = inputData.getString(KEY_EXPORT_INFO) ?: run {
            Log.e(TAG, "Cannot perform export: ExportInfo was not provided.")
            return Result.failure()
        }

        try {
            mExportInfo = ExportInfo.fromJson(exportInfoJson)
        } catch (e: JSONException) {
            return Result.failure()
        }

        mExporter = ExportManager.getExporter(applicationContext, mExportInfo)
        Log.i(TAG, "Resolved exporter: ${mExporter.javaClass.simpleName} for $mExportInfo")

        informOthersStarted()

        Log.i(TAG, "Starting export execution for $mExportInfo")
        val result = WorkoutRepository.cropMutex.withLock {
            withContext(Dispatchers.IO) {
                mExporter.export(mExportInfo)
            }
        }
        Log.i(TAG, "Export execution finished for $mExportInfo: success=${result.success()}, retry=${result.shallRetry()}, answer=${result.answer()}")

        return if (result.success()) {
            informOthersSuccess(result.answer())
            Result.success()
        } else {
            informOthersFailed(result.answer())
            if (result.shallRetry()) Result.retry() else Result.failure()
        }
    }

    private fun informOthersStarted() {
        Log.i(TAG, "Export started: $mExportInfo")
        updateStatus(mExportInfo.fileBaseName, ExportStatus.PROCESSING, "Export is being prepared...")
        mExportNotificationManager.updateNotification(mExportInfo, false)
    }

    private fun informOthersSuccess(answer: String?) {
        Log.i(TAG, "Export successful: $mExportInfo Answer: $answer")
        updateStatus(mExportInfo.fileBaseName, ExportStatus.FINISHED_SUCCESS, answer)
        mExportNotificationManager.updateNotification(mExportInfo, true)
    }

    private fun informOthersFailed(answer: String?) {
        Log.i(TAG, "Export failed: $mExportInfo Answer: $answer")
        updateStatus(mExportInfo.fileBaseName, ExportStatus.FINISHED_FAILED, answer)
        mExportNotificationManager.updateNotification(mExportInfo, true)
    }

    private fun updateStatus(fileBaseName: String, status: ExportStatus, answer: String?) {
        val values = ContentValues().apply {
            put(ExportStatusDatabaseManager.EXPORT_STATUS, status.name)
            if (answer != null) {
                put(ExportStatusDatabaseManager.ANSWER, answer)
            }
        }
        repository.updateExportStatus(values, mExportInfo)
        ExportStatusChangedBroadcaster.broadcastExportStatusChanged(applicationContext, fileBaseName)
    }

    companion object {
        private const val TAG = "ExportAndUploadWorker"
        private val DEBUG = TrainingApplication.getDebug(true)
        const val KEY_EXPORT_INFO = "export-info"
    }
}
