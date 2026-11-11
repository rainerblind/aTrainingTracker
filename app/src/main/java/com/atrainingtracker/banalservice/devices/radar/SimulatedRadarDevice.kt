/*
 * aTrainingTracker
 * Copyright (C) 2013 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see https://www.gnu.org/licenses/gpl-3.0
 */

package com.atrainingtracker.banalservice.devices.radar

import android.content.Context
import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.banalservice.devices.MyDevice
import com.atrainingtracker.banalservice.sensor.MySensor
import com.atrainingtracker.banalservice.sensor.MySensorManager
import com.atrainingtracker.banalservice.sensor.SensorType

/**
 * Simulated radar device bridge (REQ-CON-029.2).
 *
 * Exposes 6 scalar radar channels on [MySensorManager] during simulation:
 * - [SensorType.RADAR_THREAT_COUNT]
 * - [SensorType.RADAR_CLOSEST_DISTANCE_m]
 * - [SensorType.RADAR_APPROACH_SPEED_mps]
 * - [SensorType.RADAR_VEHICLE_COUNT]
 * - [SensorType.RADAR_PASSING_DISTANCE_m]
 * - [SensorType.RADAR_LARGE_VEHICLE_COUNT]
 *
 * Registers the sensors in [init] and cleanly unregisters upon [shutDown].
 */
class SimulatedRadarDevice(
    context: Context,
    mySensorManager: MySensorManager
) : MyDevice(context, mySensorManager, DeviceType.BIKE_RADAR) {

    private lateinit var threatCountSensor: MySensor<Int>
    private lateinit var closestDistanceSensor: MySensor<Double>
    private lateinit var approachSpeedSensor: MySensor<Double>
    private lateinit var vehicleCountSensor: MySensor<Int>
    private lateinit var passingDistanceSensor: MySensor<Double>
    private lateinit var largeVehicleCountSensor: MySensor<Int>

    init {
        registerSensors()
    }

    override fun getName(): String = "Simulated Radar"

    override fun addSensors() {
        threatCountSensor = MySensor(this, SensorType.RADAR_THREAT_COUNT)
        addSensor(threatCountSensor)

        closestDistanceSensor = MySensor(this, SensorType.RADAR_CLOSEST_DISTANCE_m)
        addSensor(closestDistanceSensor)

        approachSpeedSensor = MySensor(this, SensorType.RADAR_APPROACH_SPEED_mps)
        addSensor(approachSpeedSensor)

        vehicleCountSensor = MySensor(this, SensorType.RADAR_VEHICLE_COUNT)
        addSensor(vehicleCountSensor)

        passingDistanceSensor = MySensor(this, SensorType.RADAR_PASSING_DISTANCE_m)
        addSensor(passingDistanceSensor)

        largeVehicleCountSensor = MySensor(this, SensorType.RADAR_LARGE_VEHICLE_COUNT)
        addSensor(largeVehicleCountSensor)
    }

    /**
     * Publishes a 1 Hz aggregated snapshot to all 6 scalar radar sensors.
     */
    fun updateMetrics(snapshot: RadarLatchedSnapshot) {
        if (::threatCountSensor.isInitialized) {
            threatCountSensor.newValue(snapshot.threatCount)
        }
        if (::closestDistanceSensor.isInitialized) {
            closestDistanceSensor.newValue(snapshot.closestDistanceMeters)
        }
        if (::approachSpeedSensor.isInitialized) {
            approachSpeedSensor.newValue(snapshot.approachSpeedMps)
        }
        if (::vehicleCountSensor.isInitialized) {
            vehicleCountSensor.newValue(snapshot.vehicleCount)
        }
        if (::passingDistanceSensor.isInitialized) {
            passingDistanceSensor.newValue(snapshot.passingDistanceMeters)
        }
        if (::largeVehicleCountSensor.isInitialized) {
            largeVehicleCountSensor.newValue(snapshot.largeVehicleCount)
        }
    }

    /**
     * Resets dynamic radar sensors to clear state (0 threats/speed, null distances).
     */
    fun resetSensorsToClear() {
        if (::threatCountSensor.isInitialized) {
            threatCountSensor.newValue(0)
        }
        if (::closestDistanceSensor.isInitialized) {
            closestDistanceSensor.newValue(null)
        }
        if (::approachSpeedSensor.isInitialized) {
            approachSpeedSensor.newValue(0.0)
        }
        if (::passingDistanceSensor.isInitialized) {
            passingDistanceSensor.newValue(null)
        }
    }

    public override fun onAccumulatorsReset() {
        super.onAccumulatorsReset()
        if (::vehicleCountSensor.isInitialized) {
            vehicleCountSensor.newValue(0)
        }
        if (::largeVehicleCountSensor.isInitialized) {
            largeVehicleCountSensor.newValue(0)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    override fun notifySensorsChanged() {
        try {
            super.notifySensorsChanged()
        } catch (_: Exception) {
            // Unit test / headless environment guard where Intent or package manager is not mocked
        }
    }
}
