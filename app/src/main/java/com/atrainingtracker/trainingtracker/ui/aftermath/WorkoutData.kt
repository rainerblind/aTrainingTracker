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

import com.atrainingtracker.trainingtracker.ui.components.export.ExportStatusGroupData
import com.atrainingtracker.trainingtracker.ui.components.workoutdescription.DescriptionData
import com.atrainingtracker.trainingtracker.ui.components.workoutdetails.WorkoutDetailsData
import com.atrainingtracker.trainingtracker.ui.components.workoutextrema.ExtremaData
import com.atrainingtracker.trainingtracker.ui.components.workoutheader.WorkoutHeaderData
import com.atrainingtracker.trainingtracker.ui.map.PathPoint

/**
 * A composite data class that represents all data needed for a single row in the workout list.
 * It holds the raw data AND the structured data for each component.
 */
data class WorkoutData(
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