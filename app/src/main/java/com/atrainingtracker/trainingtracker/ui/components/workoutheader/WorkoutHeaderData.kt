package com.atrainingtracker.trainingtracker.ui.components.workoutheader

import com.atrainingtracker.banalservice.BSportType

data class WorkoutHeaderData(
    val workoutName: String,
    val formattedDate: String,
    val formattedTime: String,
    val sportId: Long,
    val bSportType: BSportType,
    var sportName: String,
    var equipmentName: String?
)