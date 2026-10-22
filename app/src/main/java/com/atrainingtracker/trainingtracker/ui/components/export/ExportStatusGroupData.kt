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

/**
 * Represents the detailed status for a single file format within an export group.
 */
data class ExportDetail(
    val formatName: String,
    val status: String,
    val answer: String?
)

/**
 * A data class, for the string representations for the export status of an Export Types
 *
 * @param hasContent true when the group has some content
 * @param groupTitle the title of the group
 * @param waitingLine formatted text for the waiting exports; Null when there are no waiting jobs.
 * @param runningLine formatted text for the running exports; Null when there are no running jobs.
 * @param succeededLine formatted text for the succeeded exports; Null when there are no succeeded jobs.
 * @param failedLine formatted text for the failed exports; NUll when there are no failed jobs.
 * @param details a list of detailed information for each export job in this group
 */

data class ExportStatusGroupData(
    val hasContent: Boolean,
    val groupTitle: String = "",
    val waitingLine: String? = null,
    val runningLine: String? = null,
    val succeededLine: String? = null,
    val failedLine: String? = null,
    val details: List<ExportDetail> = emptyList()
) {
    /**
     * True when this group has content and all active jobs succeeded without any waiting,
     * running, or failed lines (`REQ-UI-407.1`).
     */
    val isFullySucceeded: Boolean
        get() = hasContent &&
            !succeededLine.isNullOrBlank() &&
            waitingLine.isNullOrBlank() &&
            runningLine.isNullOrBlank() &&
            failedLine.isNullOrBlank()

    /**
     * True when this group has content and at least one waiting, running, or failed status line
     * that requires immediate user visibility (`REQ-UI-407.1`).
     */
    val needsAttentionOrInProgress: Boolean
        get() = hasContent && (
            !waitingLine.isNullOrBlank() ||
                !runningLine.isNullOrBlank() ||
                !failedLine.isNullOrBlank()
            )
}

/**
 * Formats a compact single-line summary of all fully succeeded export groups
 * (e.g. `"✅ File, Dropbox, Communities"`), or returns `null` if no group is fully succeeded (`REQ-UI-407.1`).
 */
fun formatCollapsedSucceededSummary(groups: List<ExportStatusGroupData>): String? {
    val succeededTitles = groups
        .filter { it.isFullySucceeded && it.groupTitle.isNotBlank() }
        .map { it.groupTitle }
    return if (succeededTitles.isNotEmpty()) {
        "✅ ${succeededTitles.joinToString(", ")}"
    } else {
        null
    }
}

/**
 * Returns the subset of active export groups that have waiting, running, or failed jobs (`REQ-UI-407.1`).
 */
fun filterAttentionGroups(groups: List<ExportStatusGroupData>): List<ExportStatusGroupData> {
    return groups.filter { it.needsAttentionOrInProgress }
}