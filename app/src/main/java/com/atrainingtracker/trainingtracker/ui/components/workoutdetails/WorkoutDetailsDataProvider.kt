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

package com.atrainingtracker.trainingtracker.ui.components.workoutdetails

import android.content.Context
import android.database.Cursor
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.database.ExtremaType
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries

/**
 * A provider class responsible for fetching all data needed by the WorkoutDetailsViewHolder.
 * It acts as a bridge between the data source (Cursor, DatabaseManager) and the UI component,
 * creating a clean data object (WorkoutDetailsData).
 */
class WorkoutDetailsDataProvider(private val context: Context) {

    /**
     * Gathers all necessary data from a database cursor and the Extrema database,
     * then constructs and returns a WorkoutDetailsData object.
     *
     * @param cursor The database cursor, positioned at the correct row for the workout.
     * @param workoutId The ID of the workout to fetch extra details for.
     * @return A populated WorkoutDetailsData object.
     */
    fun getWorkoutDetailsData(cursor: Cursor): WorkoutDetailsData {
        // 1. Get data from the main cursor
        val workoutId = cursor.getLong(cursor.getColumnIndex(WorkoutSummaries.C_ID))
        val totalDistance = cursor.getDouble(cursor.getColumnIndexOrThrow(WorkoutSummaries.DISTANCE_TOTAL_m))
        val activeTime = cursor.getLong(cursor.getColumnIndexOrThrow(WorkoutSummaries.TIME_ACTIVE_s))
        val totalTime = cursor.getLong(cursor.getColumnIndexOrThrow(WorkoutSummaries.TIME_TOTAL_s))
        val avgSpeed = cursor.getDouble(cursor.getColumnIndexOrThrow(WorkoutSummaries.SPEED_AVERAGE_mps))
        val ascent = cursor.getLong(cursor.getColumnIndexOrThrow(WorkoutSummaries.ASCENDING))
        val descent = cursor.getLong(cursor.getColumnIndexOrThrow(WorkoutSummaries.DESCENDING))
        val sportId = cursor.getLong(cursor.getColumnIndexOrThrow(WorkoutSummaries.SPORT_ID))
        val bSportType = SportTypeDatabaseManager.getInstance(context).getBSportType(sportId)

        // 2. Fetch the extra data from the database manager
        val workoutSummariesDatabaseManager = WorkoutSummariesDatabaseManager.getInstance(context)
        val maxDisplacement = workoutSummariesDatabaseManager.getExtremaValue(workoutId, SensorType.LINE_DISTANCE_m, ExtremaType.MAX)
        val minAlt = workoutSummariesDatabaseManager.getExtremaValue(workoutId, SensorType.ALTITUDE, ExtremaType.MIN)
        val maxAlt = workoutSummariesDatabaseManager.getExtremaValue(workoutId, SensorType.ALTITUDE, ExtremaType.MAX)

        // 3. Create and return the clean data object
        return WorkoutDetailsData(
            totalDistance = totalDistance,
            activeTimeSec = activeTime,
            totalTimeSec = totalTime,
            avgSpeedMps = avgSpeed,
            ascentMeters = ascent,
            descentMeters = descent,
            bSportType = bSportType,
            maxDisplacement = maxDisplacement,
            minAltitude = minAlt,
            maxAltitude = maxAlt
        )
    }
}