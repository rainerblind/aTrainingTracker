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
 * along with this program.  If not, see https://www.gnu.org/licenses/gpl-3.0
 */

package com.atrainingtracker.trainingtracker.ui.aftermath

/**
 * Encapsulated domain model representing post-workout traffic and cycling rearview radar analytics (REQ-RAD-005).
 */
data class RadarTrafficSummary(
    val totalVehicles: Int = 0,
    val vehiclesPerKm: Double = 0.0,
    val vehiclesPerHour: Double = 0.0,
    val maxApproachSpeedMps: Double = 0.0,
    val maxVehicleSpeedMps: Double = 0.0,
    val highThreatPassCount: Int = 0
)
