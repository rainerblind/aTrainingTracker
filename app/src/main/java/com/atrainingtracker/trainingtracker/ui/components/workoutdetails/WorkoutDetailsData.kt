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