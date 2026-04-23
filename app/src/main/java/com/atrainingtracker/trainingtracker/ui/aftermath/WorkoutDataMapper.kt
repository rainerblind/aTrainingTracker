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

import android.database.Cursor
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries
import com.atrainingtracker.trainingtracker.ui.components.workoutdescription.DescriptionDataProvider
import com.atrainingtracker.trainingtracker.ui.components.workoutdetails.WorkoutDetailsDataProvider
import com.atrainingtracker.trainingtracker.ui.components.workoutextrema.ExtremaDataProvider
import com.atrainingtracker.trainingtracker.ui.components.workoutheader.WorkoutHeaderDataProvider

/**
 * A class to map database Cursors to UI-specific data classes like WorkoutData.
 * This lives in the UI package because WorkoutData is a UI model.
 * It depends on specific data providers to build the complex WorkoutData object.
 */
class WorkoutDataMapper(
    // Dependencies are passed into the constructor
    private val sportDataProvider: SportDataProvider,
    private val equipmentDataProvider: EquipmentDataProvider,
    private val headerDataProvider: WorkoutHeaderDataProvider,
    private val detailsDataProvider: WorkoutDetailsDataProvider,
    private val descriptionDataProvider: DescriptionDataProvider,
    private val extremaDataProvider: ExtremaDataProvider
) {

    /**
     * Creates a WorkoutData object from the current position of a cursor.
     *
     * @param cursor The cursor, already positioned at the desired row.
     * @return A new WorkoutData object.
     */
    fun fromCursor(cursor: Cursor): WorkoutData {

        // Use the injected providers to create parts of the WorkoutData object
        val sportData = sportDataProvider.getSportData(cursor)
        val equipmentData = equipmentDataProvider.getEquipmentData(cursor)
        val headerData = headerDataProvider.createWorkoutHeaderData(cursor)
        val detailsData = detailsDataProvider.getWorkoutDetailsData(cursor)
        val descriptionData = descriptionDataProvider.createDescriptionData(cursor)
        val extremaData = extremaDataProvider.getExtremaData(cursor)

        // The mapper is responsible for assembling the final object from its constituent parts.
        return WorkoutData(
            id = cursor.getLong(cursor.getColumnIndexOrThrow(WorkoutSummaries.C_ID)),
            fileBaseName = cursor.getString(cursor.getColumnIndexOrThrow(WorkoutSummaries.FILE_BASE_NAME)),
            activeTime = cursor.getLong(cursor.getColumnIndexOrThrow(WorkoutSummaries.TIME_ACTIVE_s)),

            // Assign the data created by the providers
            sportData = sportData,
            equipmentData = equipmentData,
            headerData = headerData,
            detailsData = detailsData,
            descriptionData = descriptionData,
            extremaData = extremaData,
            trackPoints = emptyList(),  // added by the repository
            exportStatuses = emptyList() // TODO
        )
    }
}