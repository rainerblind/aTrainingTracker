package com.atrainingtracker.trainingtracker.ui.aftermath

import android.database.Cursor
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager
import com.atrainingtracker.trainingtracker.database.EquipmentDbHelper
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries

class SportAndEquipmentDataProvider(
    private val equipmentDbHelper: EquipmentDbHelper,
    private val sportTypeDatabaseManager: SportTypeDatabaseManager
) {

    fun getSportAndDescriptionData(cursor: Cursor): SportAndEquipmentData {
        val sportId = cursor.getLong(cursor.getColumnIndexOrThrow(WorkoutSummaries.SPORT_ID))
        val equipmentId = cursor.getInt(cursor.getColumnIndexOrThrow(WorkoutSummaries.EQUIPMENT_ID))
        val avgSpeed = cursor.getFloat(cursor.getColumnIndexOrThrow(WorkoutSummaries.SPEED_AVERAGE_mps))

        val bSportType = sportTypeDatabaseManager.getBSportType(sportId)
        val sportName = sportTypeDatabaseManager.getUIName(sportId)
        val equipmentName = equipmentDbHelper.getEquipmentNameFromId(equipmentId)

        return SportAndEquipmentData(
            sportId = sportId,
            bSportType = bSportType,
            sportName = sportName,
            avgSpeedMps = avgSpeed,
            equipmentName = equipmentName
        )
    }
}