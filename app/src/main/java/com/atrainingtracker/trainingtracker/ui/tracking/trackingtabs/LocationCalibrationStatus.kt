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

package com.atrainingtracker.trainingtracker.ui.tracking.trackingtabs

import com.atrainingtracker.trainingtracker.elevation.ElevationSource

/**
 * Represents the live cockpit feedback state when an athlete is within a recognized
 * favorite start location (Lieblingsort) and the barometric altimeter is calibrated.
 *
 * @param locationId Database ID of the recognized start location.
 * @param locationName User-assigned or geocoded name (e.g. "Zuhause").
 * @param referenceAltitude Ground-truth reference altitude in meters.
 * @param isCalibrated True if altimeter calibration has established baseline.
 * @param source Provenance of reference elevation.
 */
data class LocationCalibrationStatus(
    val locationId: Long,
    val locationName: String,
    val referenceAltitude: Double,
    val isCalibrated: Boolean = true,
    val source: ElevationSource? = null
)
