package com.atrainingtracker.banalservice.devices

import com.atrainingtracker.banalservice.sensor.SensorType

data class SensorData (
    val value: String,
    val sensor: SensorType
)