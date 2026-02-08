package com.atrainingtracker.trainingtracker.ui.aftermath

import android.database.Cursor
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager
import com.atrainingtracker.trainingtracker.database.EquipmentDbHelper
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries

class EquipmentDataProvider(
    private val equipmentDbHelper: EquipmentDbHelper,
    private val sportTypeDatabaseManager: SportTypeDatabaseManager
) {

    fun getEquipmentData(cursor: Cursor): EquipmentData {
        val sportId = cursor.getLong(cursor.getColumnIndexOrThrow(WorkoutSummaries.SPORT_ID))
        val equipmentId = cursor.getInt(cursor.getColumnIndexOrThrow(WorkoutSummaries.EQUIPMENT_ID))

        val bSportType = sportTypeDatabaseManager.getBSportType(sportId)
        val equipmentName = equipmentDbHelper.getEquipmentNameFromId(equipmentId)

        return EquipmentData(
            bSportType = bSportType,
            equipmentName = equipmentName
        )
    }
}