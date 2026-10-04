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

package com.atrainingtracker.trainingtracker.routes

import java.util.Locale

/**
 * State representing return navigation and dynamic elevation-aware arrival metrics (REQ-MAP-029).
 */
data class ReturnNavigationState(
    val isActive: Boolean = false,
    val destinationName: String = "",
    val isHomeDestination: Boolean = false,
    val remainingDistanceMeters: Double = 0.0,
    val remainingElevationGainMeters: Double = 0.0,
    val durationSeconds: Double = 0.0,
    val formattedDuration: String = "",
    val formattedClockTime: String = "",
    val isReverseReturn: Boolean = false,
    val isTakeMeHomeMode: Boolean = false,
    val snappedRouteId: Long? = null,
    val isDismissed: Boolean = false
) {
    val formattedRemainingDistance: String
        get() {
            val km = remainingDistanceMeters / 1000.0
            return String.format(Locale.US, "%.1f km", km)
        }

    val formattedRemainingClimb: String
        get() {
            val climb = remainingElevationGainMeters.toInt()
            return "+$climb m"
        }

    val hasRemainingMetrics: Boolean
        get() = isActive && remainingDistanceMeters > 0.0 && !isDismissed
}
