package com.atrainingtracker.trainingtracker.ui.aftermath

import com.atrainingtracker.banalservice.BSportType

data class SportAndEquipmentData (
    val sportId: Long,
    val bSportType: BSportType,
    val sportName: String,
    val avgSpeedMps: Float,
    var equipmentName: String?,
)