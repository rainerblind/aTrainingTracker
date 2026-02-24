package com.atrainingtracker.banalservice.devices

import com.atrainingtracker.banalservice.sensor.SensorType

data class SimpleSensorData (
    val value: String,
    val sensor: SensorType
)