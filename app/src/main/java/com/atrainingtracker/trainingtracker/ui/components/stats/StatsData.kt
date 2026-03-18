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

package com.atrainingtracker.trainingtracker.ui.components.stats

import com.atrainingtracker.banalservice.sensor.formater.DistanceFormatter
import com.atrainingtracker.banalservice.sensor.formater.TimeFormatter
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.Stats

data class StatsData(
    val title: String,
    val totalWorkouts: Int,
    val totalDistanceWithUnits: String,
    val timeWithUnits: String,
    val totalAscentWithUnits: String,
    // Navigation Metadata
    val filterSportTypeId: Long? = null,    // The ID of the SportType in the DB
    val filterEquipmentId: Long? = null,    // The ID of the Equipment in the DB
    val startTimeS: Long? = null,          // From StatsPeriodHelper
    val endTimeS: Long? = null             // From StatsPeriodHelper
) {
    companion object {
        fun fromDatabase(title: String,
                         stats: Stats,
                         sportTypeId: Long? = null,
                         equipmentId: Long? = null,
                         startTimeS: Long? = null,
                         endTimeS: Long? = null
        ): StatsData {
            val distanceFormater = DistanceFormatter()

            return StatsData (
                title = title,
                totalWorkouts = stats.count,
                totalDistanceWithUnits = distanceFormater.format_with_units(stats.totalDistanceM),
                timeWithUnits = TimeFormatter().format_with_units(stats.totalActiveTimeS),
                totalAscentWithUnits = distanceFormater.format_with_units(stats.totalAscentM),
                // Pass the filters through
                filterSportTypeId = sportTypeId,
                filterEquipmentId = equipmentId,
                startTimeS = startTimeS,
                endTimeS = endTimeS
            )
        }
    }
}