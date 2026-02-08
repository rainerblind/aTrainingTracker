package com.atrainingtracker.trainingtracker.ui.components.workoutheader

import android.content.Context
import android.database.Cursor
import android.util.Log
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager
import com.atrainingtracker.trainingtracker.database.EquipmentDbHelper
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone


class WorkoutHeaderDataProvider(
    private val context: Context,
    private val equipmentDbHelper: EquipmentDbHelper,
    private val sportTypeDatabaseManager: SportTypeDatabaseManager
) {

    fun createWorkoutHeaderData(workoutId: Long): WorkoutHeaderData? {

        val summariesDb = WorkoutSummariesDatabaseManager.getInstance(context).getDatabase()
        val cursor = summariesDb.query(
            WorkoutSummaries.TABLE,
            null,
            WorkoutSummaries.C_ID + "=?",
            arrayOf<String>(workoutId.toString()),
             null, null, null
        )
        if (cursor.moveToFirst()) {
            return createWorkoutHeaderData(cursor)
        }
        // cursor.close()
        // WorkoutSummariesDatabaseManager.getInstance().closeDatabase()

        return null
    }

    fun createWorkoutHeaderData(cursor: Cursor): WorkoutHeaderData {
        val workoutName = cursor.getString(cursor.getColumnIndexOrThrow(WorkoutSummaries.WORKOUT_NAME))
        val sportId = cursor.getLong(cursor.getColumnIndexOrThrow(WorkoutSummaries.SPORT_ID))
        val equipmentId = cursor.getInt(cursor.getColumnIndexOrThrow(WorkoutSummaries.EQUIPMENT_ID))

        val (date, time) = formatDateTime(cursor)

        val sportTypeDatabaseManager = SportTypeDatabaseManager.getInstance(context)
        val bSportType = sportTypeDatabaseManager.getBSportType(sportId)
        val sportName = sportTypeDatabaseManager.getUIName(sportId)
        val equipmentName = equipmentDbHelper.getEquipmentNameFromId(equipmentId)

        val commute = cursor.getInt(cursor.getColumnIndexOrThrow(WorkoutSummaries.COMMUTE)) == 1
        val trainer = cursor.getInt(cursor.getColumnIndexOrThrow(WorkoutSummaries.TRAINER)) == 1
        val finished = cursor.getInt(cursor.getColumnIndexOrThrow(WorkoutSummaries.FINISHED)) == 1


        return WorkoutHeaderData(
            workoutName = workoutName,
            formattedDate = date,
            formattedTime = time,
            bSportType = bSportType,
            sportName = sportName,
            equipmentName = equipmentName,
            commute = commute,
            trainer = trainer,
            finished = finished
        )
    }

    private fun formatDateTime(cursor: Cursor): Pair<String, String> {
        val startTimeString = cursor.getString(cursor.getColumnIndexOrThrow(WorkoutSummaries.TIME_START))
        val dbFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        return try {
            val startTimeDate: Date = dbFormat.parse(startTimeString) ?: throw ParseException("Parsed date is null", 0)
            val localeDateFormat = java.text.DateFormat.getDateInstance(java.text.DateFormat.DEFAULT)
            val localeTimeFormat = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT)
            Pair(localeDateFormat.format(startTimeDate), localeTimeFormat.format(startTimeDate))
        } catch (e: ParseException) {
            Log.e("WorkoutHeaderProvider", "Failed to parse date string: $startTimeString", e)
            Pair(context.getString(R.string.invalid_date), "")
        }
    }
}