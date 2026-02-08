package com.atrainingtracker.trainingtracker.ui.aftermath

import com.atrainingtracker.banalservice.BSportType

data class SportData (
    val sportId: Long,
    val bSportType: BSportType,
    val sportName: String,
    val avgSpeedMps: Float
)