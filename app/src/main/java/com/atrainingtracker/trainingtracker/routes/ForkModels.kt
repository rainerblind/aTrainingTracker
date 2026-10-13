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

import androidx.annotation.StringRes
import com.atrainingtracker.R
import com.google.android.gms.maps.model.LatLng

/**
 * Directional indication for a branching route relative to the approaching corridor heading.
 *
 * Conforms to REQ-MAP-031.
 */
enum class ForkDirection(@StringRes val labelRes: Int) {
    LEFT(R.string.fork_direction_left),
    STRAIGHT(R.string.fork_direction_straight),
    RIGHT(R.string.fork_direction_right)
}

/**
 * Comparative metrics and relative direction for a candidate route at a fork.
 *
 * @param routeId Unique database identifier of the route
 * @param routeName Human-readable route name
 * @param totalDistanceMeters Total route length in meters
 * @param totalElevationMeters Total elevation gain in meters
 * @param direction Relative direction from approach vector (Left, Straight, Right)
 * @param bearingDiffDegrees Angular divergence in degrees from approach vector
 */
data class ForkBranchOption(
    val routeId: Long,
    val routeName: String,
    val totalDistanceMeters: Double,
    val totalElevationMeters: Double,
    val direction: ForkDirection,
    val bearingDiffDegrees: Double
)

/**
 * Active decision state when approaching a route fork.
 *
 * @param divergenceCoordinate GPS location where candidate polylines split
 * @param distanceToForkMeters Remaining distance along shared corridor to the fork
 * @param branches Ordered list of branching route choices
 * @param isApproaching Whether athlete is within the alert proximity window (<= 300m)
 */
data class ForkDecisionState(
    val divergenceCoordinate: LatLng,
    val distanceToForkMeters: Double,
    val branches: List<ForkBranchOption>,
    val isApproaching: Boolean = true
)
