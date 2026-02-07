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
        val activeTime = cursor.getInt(cursor.getColumnIndexOrThrow(WorkoutSummaries.TIME_ACTIVE_s))
        val totalTime = cursor.getInt(cursor.getColumnIndexOrThrow(WorkoutSummaries.TIME_TOTAL_s))
        val avgSpeed = cursor.getFloat(cursor.getColumnIndexOrThrow(WorkoutSummaries.SPEED_AVERAGE_mps))
        val ascent = cursor.getInt(cursor.getColumnIndexOrThrow(WorkoutSummaries.ASCENDING))
        val descent = cursor.getInt(cursor.getColumnIndexOrThrow(WorkoutSummaries.DESCENDING))
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