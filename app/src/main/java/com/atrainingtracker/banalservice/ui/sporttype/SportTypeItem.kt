package com.atrainingtracker.banalservice.ui.sporttype

data class SportTypeItem(
    val id: Long,
    val name: String,
    val minSpeed: Double,
    val maxSpeed: Double,
    val stravaName: String,
    val tcxName: String,
    val gcName: String,
    val isEditable: Boolean
)