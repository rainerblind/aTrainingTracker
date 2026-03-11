package com.atrainingtracker.banalservice.ui.sporttype

import com.atrainingtracker.trainingtracker.ui.components.stats.StatsData

data class SportTypeItem(
    val id: Long,
    val name: String,
    val minSpeed: Double,
    val maxSpeed: Double,
    val stravaName: String,
    val tcxName: String,
    val gcName: String,
    val isEditable: Boolean,
    val firstUsed: String?,
    val lastUsed: String?,
    val statsData: StatsData
)