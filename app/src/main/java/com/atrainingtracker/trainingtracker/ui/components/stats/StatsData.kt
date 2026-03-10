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
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.EquipmentStats

data class StatsData(
    val title: String,
    val totalWorkouts: Int,
    val totalDistanceWithUnits: String,
    val timeWithUnits: String,
    val totalAscentWithUnits: String
) {
    companion object {
        fun fromDatabase(title: String, equipmentStats: EquipmentStats): StatsData {
            val distanceFormater = DistanceFormatter()

            return StatsData (
                title = title,
                totalWorkouts = equipmentStats.count,
                totalDistanceWithUnits = distanceFormater.format_with_units(equipmentStats.totalDistanceM),
                timeWithUnits = TimeFormatter().format_with_units(equipmentStats.totalActiveTimeS),
                totalAscentWithUnits = distanceFormater.format_with_units(equipmentStats.totalAscentM)
            )
        }
    }
}