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

package com.atrainingtracker.trainingtracker.ui.components.workoutdescription

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