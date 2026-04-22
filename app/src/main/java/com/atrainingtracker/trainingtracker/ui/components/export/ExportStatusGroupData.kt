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
 * A data class, for the string representations for the export status of an Export Types
 *
 * @param hasContent true when the group has some content
 * @param groupTitle the title of the group
 * @param waitingLine formatted text for the waiting exports; Null when there are no waiting jobs.
 * @param runningLine formatted text for the running exports; Null when there are no running jobs.
 * @param succeededLine formatted text for the succeeded exports; Null when there are no succeeded jobs.
 * @param failedLine formatted text for the failed exports; NUll when there are no failed jobs.
 */

// TODO: find a better name?
data class ExportStatusGroupData(
    val hasContent: Boolean,
    val groupTitle: String = "",
    val waitingLine: String? = null,
    val runningLine: String? = null,
    val succeededLine: String? = null,
    val failedLine: String? = null
)