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

package com.atrainingtracker.trainingtracker.ui.components.workoutdetails


import com.atrainingtracker.banalservice.BSportType

/**
 * A simple Data Transfer Object (DTO) to hold all the necessary information
 * for the WorkoutDetailsViewHolder.
 *
 * Using a 'data class' provides component functions, equals(), hashCode(), and toString() automatically.
 */
data class WorkoutDetailsData(
    val totalDistance: Double,
    val activeTimeSec: Int,
    val totalTimeSec: Int,
    val avgSpeedMps: Float,
    val ascentMeters: Int,
    val descentMeters: Int,
    val bSportType: BSportType,  // necessary for distinguishing to show speed or pace

    val maxDisplacement: Double?,
    val minAltitude: Double?,
    val maxAltitude: Double?
)