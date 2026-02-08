package com.atrainingtracker.trainingtracker.ui.aftermath

import android.database.Cursor
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries

class SportDataProvider(
    private val sportTypeDatabaseManager: SportTypeDatabaseManager
) {

    fun getSportData(cursor: Cursor): SportData {
        val sportId = cursor.getLong(cursor.getColumnIndexOrThrow(WorkoutSummaries.SPORT_ID))
        val avgSpeed = cursor.getFloat(cursor.getColumnIndexOrThrow(WorkoutSummaries.SPEED_AVERAGE_mps))

        val bSportType = sportTypeDatabaseManager.getBSportType(sportId)
        val sportName = sportTypeDatabaseManager.getUIName(sportId)

        return SportData(
            sportId = sportId,
            bSportType = bSportType,
            sportName = sportName,
            avgSpeedMps = avgSpeed
        )
    }
}