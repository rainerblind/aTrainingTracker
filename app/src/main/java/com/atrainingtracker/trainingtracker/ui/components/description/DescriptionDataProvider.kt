package com.atrainingtracker.trainingtracker.ui.components.description

import android.database.Cursor
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries

class DescriptionDataProvider {
    fun createDescriptionData(cursor: Cursor): DescriptionData {
        val description = cursor.getString(cursor.getColumnIndexOrThrow(WorkoutSummaries.DESCRIPTION))
        val goal = cursor.getString(cursor.getColumnIndexOrThrow(WorkoutSummaries.GOAL))
        val method = cursor.getString(cursor.getColumnIndexOrThrow(WorkoutSummaries.METHOD))

        return DescriptionData(description, goal, method)
    }
}