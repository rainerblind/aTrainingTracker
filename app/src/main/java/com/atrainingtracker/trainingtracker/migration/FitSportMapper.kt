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

package com.atrainingtracker.trainingtracker.migration

import com.atrainingtracker.banalservice.BSportType
import com.garmin.fit.Sport
import com.garmin.fit.SubSport

/**
 * Maps Garmin FIT SDK [Sport] and [SubSport] definitions to internal [BSportType]
 * pursuant to REQ-DAT-019.
 */
object FitSportMapper {

    /**
     * Resolves the corresponding [BSportType] from Garmin FIT [Sport] and optional [SubSport].
     *
     * @param sport Garmin FIT sport enum or null
     * @param subSport Garmin FIT subsport enum or null
     * @return Corresponding [BSportType] (BIKE, RUN, or UNKNOWN/CONFLICT)
     */
    @JvmStatic
    fun mapFitSport(sport: Sport?, subSport: SubSport?): BSportType {
        if (sport == null) return BSportType.UNKNOWN

        return when (sport) {
            Sport.CYCLING -> BSportType.BIKE
            Sport.RUNNING, Sport.WALKING -> BSportType.RUN
            Sport.FITNESS_EQUIPMENT -> {
                when (subSport) {
                    SubSport.INDOOR_CYCLING, SubSport.SPIN -> BSportType.BIKE
                    SubSport.INDOOR_RUNNING, SubSport.TREADMILL, SubSport.INDOOR_WALKING -> BSportType.RUN
                    else -> BSportType.UNKNOWN
                }
            }
            Sport.TRANSITION -> BSportType.CONFLICT
            else -> BSportType.UNKNOWN
        }
    }
}
