package com.atrainingtracker.trainingtracker.ui.components.workoutheader

import com.atrainingtracker.banalservice.BSportType

data class WorkoutHeaderData(
    val workoutName: String,
    val formattedDate: String,
    val formattedTime: String,
    val bSportType: BSportType,  // necessary to get the icon and the text for an indoor activity
    var sportName: String,
    var equipmentName: String?,
    var commute: Boolean,
    var trainer: Boolean,
    val finished: Boolean
)