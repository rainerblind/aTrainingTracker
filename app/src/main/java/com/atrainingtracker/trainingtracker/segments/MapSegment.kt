package com.atrainingtracker.trainingtracker.segments

import com.atrainingtracker.banalservice.BSportType
import com.google.android.gms.maps.model.LatLng

data class MapSegment(
    val id: Long,
    val name: String,
    val bSportType: BSportType,
    val path: List<LatLng>,
)