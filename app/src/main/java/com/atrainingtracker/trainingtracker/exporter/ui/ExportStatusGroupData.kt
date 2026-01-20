package com.atrainingtracker.trainingtracker.exporter.ui

/**
 * A data class, for the string representations for the export status of an Export Types
 *
 * @param hasContent true when the group has some content
 * @param groupTitle the title of the group
 * @param runningLine formatted text for the running exports; Null when there are no running jobs.
 * @param succeededLine formatted text for the succeeded exports; Null when there are no succeeded jobs.
 * @param failedLine formatted text for the failed exports; NUll when there are no failed jobs.
 */

// TODO: find a better name?
data class ExportStatusGroupData(
    val hasContent: Boolean,
    val groupTitle: String? = null,
    val runningLine: String? = null,
    val succeededLine: String? = null,
    val failedLine: String? = null
)