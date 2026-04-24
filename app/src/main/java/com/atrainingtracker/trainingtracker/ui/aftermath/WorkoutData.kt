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

package com.atrainingtracker.trainingtracker.ui.aftermath

import androidx.compose.runtime.Immutable
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.ui.components.export.ExportStatusGroupData
import com.atrainingtracker.trainingtracker.ui.components.workoutdescription.DescriptionData
import com.atrainingtracker.trainingtracker.ui.components.workoutdetails.WorkoutDetailsData
import com.atrainingtracker.trainingtracker.ui.components.workoutextrema.ExtremaData
import com.atrainingtracker.trainingtracker.ui.components.workoutextrema.ExtremaDataRow
import com.atrainingtracker.trainingtracker.ui.components.workoutheader.WorkoutHeaderData
import com.atrainingtracker.trainingtracker.ui.map.PathPoint

/**
 * A composite data class that represents all data needed for a single row in the workout list.
 * It holds the raw data AND the structured data for each component.
 */
data class WorkoutDataClassic(
    // --- Raw Data (Primary Key) ---
    val id: Long,
    val fileBaseName: String?,
    val activeTime: Long,

    // --- Composed Component Data ---
    val sportData: SportData,
    val equipmentData: EquipmentData,
    val headerData: WorkoutHeaderData,
    val detailsData: WorkoutDetailsData,
    val descriptionData: DescriptionData,
    val extremaData: ExtremaData,
    val trackPoints: List<PathPoint>,
    val exportStatuses: List<ExportStatusGroupData>
)

// TODO: move the methods to update redundant data to here???

@Immutable
data class WorkoutData(
    // --- 1. Raw Data (The "Source of Truth") ---
    val id: Long,
    val finished: Boolean,
    val fileBaseName: String?,
    val workoutName: String,
    val sportId: Long,
    val sportName: String,
    val startTimeS: Long,
    val formattedDate: String,
    val formattedTime: String,
    val bSportType: BSportType,
    val equipmentName: String?,
    val equipmentId: Long,
    val commute: Boolean,
    val trainer: Boolean,

    val totalDistance: Double,
    val maxDisplacement: Double?,
    val activeTimeSec: Long,
    val totalTimeSec: Long,
    val avgSpeedMps: Double,
    val ascentMeters: Long,
    val descentMeters: Long,
    val minAltitude: Double?,
    val maxAltitude: Double?,

    val description: String,
    val goal: String,
    val method: String,

    val isCalculatingExtrema: Boolean = false,
    val extremaCalculationMessage: String? = null,

    // --- 2. Heavy/Live Data ---
    // Nullable so the UI knows if GPS points are still loading from the DB
    val trackPoints: List<PathPoint>? = emptyList(),
    val exportStatuses: List<ExportStatusGroupData> = emptyList(),
    val extremaRows: List<ExtremaDataRow> = emptyList()
) {

    // --- 3. Computed Component Properties ---
    // These replace the previous nested constructor objects.
    // They are computed on-demand, keeping the data class "flat".

    val headerData: WorkoutHeaderData
        get() = WorkoutHeaderData(
            workoutName = workoutName,
            sportName = sportName,
            bSportType = bSportType,
            startTimeS = startTimeS,
            finished = finished,
            formattedDate = formattedDate,
            formattedTime = formattedTime,
            equipmentName = equipmentName,
            commute = commute,
            trainer = trainer
        )

    val detailsData: WorkoutDetailsData
        get() = WorkoutDetailsData(
            totalDistance = totalDistance,
            activeTimeSec = activeTimeSec,
            totalTimeSec = totalTimeSec,
            avgSpeedMps = avgSpeedMps,
            bSportType = bSportType,
            ascentMeters = ascentMeters,
            descentMeters = descentMeters,
            maxDisplacement = maxDisplacement,
            minAltitude = minAltitude,
            maxAltitude = maxAltitude
        )

    val descriptionData: DescriptionData
        get() = DescriptionData(
            description = description,
            goal = goal,
            method = method
        )

    val extremaData: ExtremaData
        get() = ExtremaData(
            dataRows = extremaRows,
            isCalculating = isCalculatingExtrema,
            workoutId = id,
            calculationMessage = extremaCalculationMessage
        )

    // --- 4. Logic Helpers ---
    val hasTrackPoints: Boolean get() = !trackPoints.isNullOrEmpty()
}