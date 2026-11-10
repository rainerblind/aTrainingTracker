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

package com.atrainingtracker.trainingtracker.ui.components.workoutextrema

/**
 * Pure immutable UI state model representing a single row in the extrema table (REQ-UI-506.3).
 *
 * Pre-resolves fallback strings ("-") and relevance filtering outside of Compose rendering.
 *
 * @param label The sensor display label (e.g. "HR", "Speed", "Power").
 * @param min Formatted minimum value string, or "-" if not available or not relevant.
 * @param avg Formatted average value string, or "-" if not available.
 * @param max Formatted maximum value string, or "-" if not available.
 * @param unit Unit label string (e.g. "bpm", "km/h", "W").
 * @param iconResId Optional drawable resource ID for the sensor icon.
 * @param boldMin Whether the min value text should be bolded.
 * @param boldAvg Whether the avg value text should be bolded.
 * @param boldMax Whether the max value text should be bolded.
 */
data class ExtremaRowUiState(
    val label: String,
    val min: String,
    val avg: String,
    val max: String,
    val unit: String,
    val iconResId: Int? = null,
    val boldMin: Boolean = false,
    val boldAvg: Boolean = false,
    val boldMax: Boolean = false
)

/**
 * Pure immutable UI state model for the entire extrema table section (REQ-UI-506.3).
 */
data class ExtremaUiState(
    val workoutId: Long = 0L,
    val rows: List<ExtremaRowUiState> = emptyList()
) {
    companion object {
        fun from(data: ExtremaData): ExtremaUiState {
            val rows = data.dataRows.filter { it.hasAnyData() }.map { row ->
                ExtremaRowUiState(
                    label = row.sensorLabel,
                    min = if (row.isMinRelevant) (row.minValue ?: "-") else "-",
                    avg = row.avgValue ?: "-",
                    max = row.maxValue ?: "-",
                    unit = row.unitLabel,
                    iconResId = row.iconResId,
                    boldMin = row.boldMin,
                    boldAvg = row.boldAvg,
                    boldMax = row.boldMax
                )
            }
            return ExtremaUiState(workoutId = data.workoutId, rows = rows)
        }
    }
}
