package com.atrainingtracker.trainingtracker.ui.components.export

import android.content.Context
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.exporter.ExportStatus
import com.atrainingtracker.trainingtracker.exporter.ExportType
import com.atrainingtracker.trainingtracker.exporter.FileFormat
import com.atrainingtracker.trainingtracker.exporter.db.ExportStatusRepository
import com.atrainingtracker.trainingtracker.ui.components.export.ExportStatusGroupData
import com.atrainingtracker.trainingtracker.helpers.formatListAsString

class ExportStatusUIDataProvider(private val context: Context) {

    /**
     * The central class to collect all data for an ExportType and create the corresponding string for the UI.
     */
    fun createGroupData(fileBaseName: String, exportType: ExportType): ExportStatusGroupData {
        val jobs = getJobs(fileBaseName, exportType)

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

        return ExportStatusGroupData(
            hasContent = true,
            groupTitle = context.getString(exportType.uiId),
            waitingLine = waitingLine,
            runningLine = runningLine,
            succeededLine = succeededLine,
            failedLine = failedLine
        )
    }

    /***********************************************************************************************
     * private helpers
     **********************************************************************************************/


    private fun getJobs(fileBaseName: String, exportType: ExportType): Map<FileFormat, ExportStatus> {
        return ExportStatusRepository.getInstance(context).getExportStatusMap(fileBaseName, exportType)
    }

    private fun getWaitingJobsList(finishedJobs: Map<FileFormat, ExportStatus>): List<String> {
        return finishedJobs.filterValues { it == ExportStatus.WAITING }
            .keys.map { context.getString(it.uiNameId) }
    }

    private fun getRunningJobsList(finishedJobs: Map<FileFormat, ExportStatus>): List<String> {
        return finishedJobs.filterValues { it == ExportStatus.PROCESSING }
            .keys.map { context.getString(it.uiNameId) }
    }

    private fun getSucceededJobsList(finishedJobs: Map<FileFormat, ExportStatus>): List<String> {
        return finishedJobs.filterValues { it == ExportStatus.FINISHED_SUCCESS }
            .keys.map { context.getString(it.uiNameId) }
    }

    private fun getFailedJobsList(finishedJobs: Map<FileFormat, ExportStatus>): List<String> {
        return finishedJobs.filterValues { it == ExportStatus.FINISHED_FAILED }
            .keys.map { context.getString(it.uiNameId) }
    }

    private fun getRunningLine(runningJobs: Set<FileFormat>, pluralsId: Int): String {
        val runningJobNames = runningJobs.map { context.getString(it.uiNameId) }
        val formattedFileFormats = formatListAsString(context, runningJobNames)
        return context.resources.getQuantityString(pluralsId, runningJobs.size, formattedFileFormats)
    }

    private fun getResultLine(filteredJobList: List<String>, pluralResId: Int): String {
        val formattedJobs = formatListAsString(context, filteredJobList)
        return context.resources.getQuantityString(pluralResId, filteredJobList.size, formattedJobs)
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