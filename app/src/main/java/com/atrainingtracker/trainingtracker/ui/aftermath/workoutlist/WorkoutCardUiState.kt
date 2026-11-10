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

package com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist

import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData
import com.atrainingtracker.trainingtracker.ui.theme.TTAlpha
import com.atrainingtracker.trainingtracker.ui.util.MetricFormatterContext

/**
 * Pure immutable UI state model for workout list cards (e.g. [WorkoutSummaryCompact]) (REQ-UI-506.2).
 *
 * Decouples Compose rendering from framework formatters, string concatenation,
 * and actively-tracked process queries during composition.
 */
data class WorkoutCardUiState(
    val workoutId: Long,
    val workoutName: String,
    val sportIconResId: Int,
    val sportName: String,
    val equipmentName: String? = null,
    val formattedDateTime: String,
    val activeTimeFormatted: String,
    val distanceFormatted: String,
    val speedPaceFormatted: String,
    val ascentFormatted: String,
    val isFinished: Boolean = true,
    val isActivelyTracked: Boolean = false,
    val contentAlpha: Float = TTAlpha.High
) {
    val canDelete: Boolean get() = !isActivelyTracked
    val canMarkFinished: Boolean get() = !isFinished && !isActivelyTracked
    val hasContextMenu: Boolean get() = canDelete || canMarkFinished

    companion object {
        fun from(
            workoutData: WorkoutData,
            formatters: MetricFormatterContext = MetricFormatterContext(),
            isActivelyTracked: Boolean = false
        ): WorkoutCardUiState {
            val contentAlpha = if (workoutData.headerData.finished) TTAlpha.High else 0.5f
            val dateTime = "${workoutData.headerData.formattedDate}  ${workoutData.headerData.formattedTime}"
            return WorkoutCardUiState(
                workoutId = workoutData.id,
                workoutName = workoutData.workoutName,
                sportIconResId = workoutData.bSportType.iconResId,
                sportName = workoutData.headerData.sportName,
                equipmentName = workoutData.headerData.equipmentName.takeIf { !it.isNullOrBlank() },
                formattedDateTime = dateTime,
                activeTimeFormatted = formatters.time.format_with_units(workoutData.detailsData.activeTimeSec),
                distanceFormatted = formatters.distance.format_with_units(workoutData.detailsData.totalDistance),
                speedPaceFormatted = formatters.speed.format_with_units(workoutData.detailsData.avgSpeedMps),
                ascentFormatted = formatters.altitude.format_with_units(workoutData.ascentMeters),
                isFinished = workoutData.headerData.finished,
                isActivelyTracked = isActivelyTracked,
                contentAlpha = contentAlpha
            )
        }
    }
}
