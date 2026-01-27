package com.atrainingtracker.trainingtracker.ui.components.workoutheader

import com.atrainingtracker.banalservice.BSportType

data class WorkoutHeaderData(
    val workoutName: String,
    val formattedDate: String,
    val formattedTime: String,
    val sportType: BSportType,
    val sportName: String,
    val equipmentName: String?
)