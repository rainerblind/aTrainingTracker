/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
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
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0/>.
 */

package com.atrainingtracker.banalservice.odometry

/**
 * Identifies the origin of a speed observation for the odometry sensor fusion engine.
 */
enum class SpeedSource {
    FRONT_WHEEL,
    REAR_WHEEL,
    GNSS_DOPPLER
}

/**
 * Represents a scalar speed measurement from an external or internal telemetry source.
 *
 * @property speedMps Forward ground speed in meters per second (m/s).
 * @property variance Measurement error variance \sigma_v^2 in (m/s)^2.
 * @property timestampMs Epoch timestamp in milliseconds.
 * @property source The physical or algorithmic source of the speed measurement.
 */
data class SpeedMeasurement(
    val speedMps: Double,
    val variance: Double,
    val timestampMs: Long,
    val source: SpeedSource
)
