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

package com.atrainingtracker.trainingtracker.ui.components.export

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.exporter.ExportStatus
import com.atrainingtracker.trainingtracker.exporter.ExportStatusChangedBroadcaster
import com.atrainingtracker.trainingtracker.exporter.ExportType
import com.atrainingtracker.trainingtracker.exporter.FileFormat
import com.atrainingtracker.trainingtracker.exporter.db.ExportStatusDatabaseManager
import com.atrainingtracker.trainingtracker.helpers.formatListAsString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*

class ExportStatusRepository private constructor(context: Context) {

    private val appContext = context.applicationContext

    // Acquire the manager internally so we don't have to pass it to getInstance
    private val dbManager = ExportStatusDatabaseManager.getInstance(appContext)

    // Repository scope to keep flows alive independently of specific ViewModels
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Cache to ensure we only have one StateFlow per workout ID
    private val statusFlows = mutableMapOf<String, StateFlow<List<ExportStatusGroupData>>>()

    companion object {
        @Volatile
        private var INSTANCE: ExportStatusRepository? = null

        fun getInstance(context: Context): ExportStatusRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ExportStatusRepository(context).also { INSTANCE = it }
            }
        }
    }

    /**
     * Returns a reactive StateFlow for a specific workout.
     * It updates automatically whenever a broadcast is received for this fileBaseName.
     */
    fun getExportStatusFlow(fileBaseName: String): StateFlow<List<ExportStatusGroupData>> {
        return synchronized(statusFlows) {
            statusFlows.getOrPut(fileBaseName) {
                createStatusFlow(fileBaseName)
            }
        }
    }

    private fun createStatusFlow(fileBaseName: String): StateFlow<List<ExportStatusGroupData>> {
        return callbackFlow {
            // 1. Logic to fetch data from DB and map to UI model
            val refresh = {
                val allRows = dbManager.getExportRows(fileBaseName)
                val groupedRows = allRows.groupBy { it.type }

                val uiData = groupedRows.map { (type, rows) ->
                    createGroupData(exportType = type, rows = rows)
                }.filter { it.hasContent }

                trySend(uiData)
            }

            // 2. Initial fetch
            refresh()

            // 3. Register Receiver for the "Poke" from the Broadcaster
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    val changedFile = intent?.getStringExtra(
                        ExportStatusChangedBroadcaster.EXTRA_FILE_BASE_NAME
                    )
                    // Refresh if it's a global update (null) or matches this specific file
                    if (changedFile == null || changedFile == fileBaseName) {
                        refresh()
                    }
                }
            }

            val filter = IntentFilter(ExportStatusChangedBroadcaster.EXPORT_STATUS_CHANGED_INTENT)
            ContextCompat.registerReceiver(
                appContext, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED
            )

            // 4. Cleanup when the Flow is no longer needed
            awaitClose {
                appContext.unregisterReceiver(receiver)
            }
        }
            .stateIn(
                scope = repositoryScope,
                // Keep active for 5 seconds after the last collector disappears
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )
    }

    fun createGroupData(exportType: ExportType, rows: List<ExportStatusDatabaseManager.ExportRow>): ExportStatusGroupData {
        val jobs = rows.associate { it.format to it.status }

        val waitingJobsList = getWaitingJobsList(jobs)
        val runningJobsList = getRunningJobsList(jobs)
        val succeededJobsList = getSucceededJobsList(jobs)
        val failedJobsList = getFailedJobsList(jobs)

        val hasContent = waitingJobsList.isNotEmpty() || runningJobsList.isNotEmpty() || succeededJobsList.isNotEmpty() || failedJobsList.isNotEmpty()
        if (!hasContent) {
            return ExportStatusGroupData(hasContent = false)
        }

        // plurals for the corresponding exportType
        val (pluralsWaitingID, pluralsRunningID, pluralsSuccessID, pluralsFailedID) = getPluralIdsFor(exportType)

        // create the corresponding strings when the lists are empty
        val waitingLine = waitingJobsList.takeIf { it.isNotEmpty() }?.let { getResultLine(it, pluralsWaitingID) }
        val runningLine = runningJobsList.takeIf { it.isNotEmpty() }?.let { getResultLine(it, pluralsRunningID) }
        val succeededLine = succeededJobsList.takeIf { it.isNotEmpty() }?.let { getResultLine(it, pluralsSuccessID) }
        val failedLine = failedJobsList.takeIf { it.isNotEmpty() }?.let { getResultLine(it, pluralsFailedID) }

        val details = rows.map { row ->
            ExportDetail(
                formatName = appContext.getString(row.format.uiNameId),
                status = row.status.name,
                answer = row.answer
            )
        }

        return ExportStatusGroupData(
            hasContent = true,
            groupTitle = appContext.getString(exportType.uiId),
            waitingLine = waitingLine,
            runningLine = runningLine,
            succeededLine = succeededLine,
            failedLine = failedLine,
            details = details
        )
    }

    private fun getWaitingJobsList(finishedJobs: Map<FileFormat, ExportStatus>): List<String> {
        return finishedJobs.filterValues { it == ExportStatus.WAITING }
            .keys.map { appContext.getString(it.uiNameId) }
    }

    private fun getRunningJobsList(finishedJobs: Map<FileFormat, ExportStatus>): List<String> {
        return finishedJobs.filterValues { it == ExportStatus.PROCESSING }
            .keys.map { appContext.getString(it.uiNameId) }
    }

    private fun getSucceededJobsList(finishedJobs: Map<FileFormat, ExportStatus>): List<String> {
        return finishedJobs.filterValues { it == ExportStatus.FINISHED_SUCCESS }
            .keys.map { appContext.getString(it.uiNameId) }
    }

    private fun getFailedJobsList(finishedJobs: Map<FileFormat, ExportStatus>): List<String> {
        return finishedJobs.filterValues { it == ExportStatus.FINISHED_FAILED }
            .keys.map { appContext.getString(it.uiNameId) }
    }

    private fun getRunningLine(runningJobs: Set<FileFormat>, pluralsId: Int): String {
        val runningJobNames = runningJobs.map { appContext.getString(it.uiNameId) }
        val formattedFileFormats = formatListAsString(appContext, runningJobNames)
        return appContext.resources.getQuantityString(pluralsId, runningJobs.size, formattedFileFormats)
    }

    private fun getResultLine(filteredJobList: List<String>, pluralResId: Int): String {
        val formattedJobs = formatListAsString(appContext, filteredJobList)
        return appContext.resources.getQuantityString(pluralResId, filteredJobList.size, formattedJobs)
    }

    // data class for the plurals
    private data class PluralIds(val waiting: Int, val running: Int, val success: Int, val failed: Int)

    private fun getPluralIdsFor(exportType: ExportType): PluralIds {
        return when (exportType) {
            ExportType.FILE -> PluralIds(
                R.plurals.export_notification__detail__File_waiting,
                R.plurals.export_notification__detail__File_ongoing,
                R.plurals.export_notification__detail__File_success,
                R.plurals.export_notification__detail__File_failed
            )
            ExportType.DROPBOX -> PluralIds(
                R.plurals.export_notification__detail__Dropbox_waiting,
                R.plurals.export_notification__detail__Dropbox_ongoing,
                R.plurals.export_notification__detail__Dropbox_success,
                R.plurals.export_notification__detail__Dropbox_failed
            )
            ExportType.COMMUNITY -> PluralIds(
                R.plurals.export_notification__detail__Community_waiting,
                R.plurals.export_notification__detail__Community_ongoing,
                R.plurals.export_notification__detail__Community_success,
                R.plurals.export_notification__detail__Community_failed
            )
        }
    }
}