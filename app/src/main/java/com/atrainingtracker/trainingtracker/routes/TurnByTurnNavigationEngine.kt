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

import android.location.Location
import com.atrainingtracker.trainingtracker.database.RouteWithPath
import com.atrainingtracker.trainingtracker.geo.GeoMath
import com.atrainingtracker.trainingtracker.settings.TuningConfig
import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Real-time navigation engine tracking athlete progress along an active route (REQ-MAP-028 / ATT-1450).
 *
 * Evaluates athlete GPS coordinates against the active route polyline to:
 * 1. Compute distance along route ($D_{\text{current}}$) and cross-track error ($D_{\text{cross}}$).
 * 2. Track approaching turn cues and countdown meters.
 * 3. Monitor corridor bounds to trigger off-route warnings and recoveries.
 */
class TurnByTurnNavigationEngine {

    private val _navigationState = MutableStateFlow(TurnNavigationState())
    val navigationState: StateFlow<TurnNavigationState> = _navigationState.asStateFlow()

    private var activeRoute: RouteWithPath? = null
    private var turnCues: List<TurnCue> = emptyList()
    private var consecutiveOffRouteFixes: Int = 0
    private var isCurrentlyOffRoute: Boolean = false

    /**
     * Updates the currently active navigated route.
     * Extracts turn cues (or generates them geometrically) and resets state.
     */
    @Synchronized
    fun setActiveRoute(route: RouteWithPath?) {
        activeRoute = route
        consecutiveOffRouteFixes = 0
        isCurrentlyOffRoute = false

        if (route == null || route.path.isEmpty()) {
            turnCues = emptyList()
            _navigationState.value = TurnNavigationState()
            return
        }

        turnCues = TurnCueDetector.extractOrDetectCues(route)
        _navigationState.value = TurnNavigationState(
            activeRouteId = route.summary.id,
            upcomingCue = turnCues.firstOrNull(),
            distanceToNextCueMeters = turnCues.firstOrNull()?.distanceFromStart ?: 0.0,
            totalCuesCount = turnCues.size
        )
    }

    /**
     * Evaluates athlete's current location against the route polyline.
     */
    @Synchronized
    fun onLocationChanged(
        location: Location,
        tuningConfig: TuningConfig = TuningConfig()
    ): TurnNavigationState {
        return onLocationChanged(location.latitude, location.longitude, tuningConfig)
    }

    @Synchronized
    fun onLocationChanged(
        latLng: LatLng,
        tuningConfig: TuningConfig = TuningConfig()
    ): TurnNavigationState {
        return onLocationChanged(latLng.latitude, latLng.longitude, tuningConfig)
    }

    @Synchronized
    fun onLocationChanged(
        locLat: Double,
        locLng: Double,
        tuningConfig: TuningConfig = TuningConfig()
    ): TurnNavigationState {
        val route = activeRoute ?: return TurnNavigationState()
        val path = route.path
        if (path.isEmpty()) return TurnNavigationState()

        // 1. Orthogonal projection onto route polyline via canonical GeoMath (REQ-GEO-003)
        val projection = GeoMath.projectOntoPolyline(path, locLat, locLng) ?: return TurnNavigationState()
        val totalDist = path.last().distance
        val currentDist = (path.first().distance + projection.alongTrackDistance).coerceIn(0.0, totalDist)
        val crossTrackDist = projection.crossTrackDistanceMeters

        // 2. Off-Route Corridor Evaluation
        val offRouteCorridorMeters = tuningConfig.offRouteCorridorThresholdMeters.toDouble()
        if (crossTrackDist > offRouteCorridorMeters) {
            consecutiveOffRouteFixes++
            if (consecutiveOffRouteFixes >= 3) {
                isCurrentlyOffRoute = true
            }
        } else if (crossTrackDist <= 30.0) {
            consecutiveOffRouteFixes = 0
            isCurrentlyOffRoute = false
        }

        // 3. Upcoming Cue Tracking & Countdown
        // Cues that are at least within 20m behind or ahead of current position
        val remainingCues = turnCues.filter { it.distanceFromStart >= (currentDist - 20.0) }
        val upcomingCue = remainingCues.firstOrNull()
        val passedCount = turnCues.size - remainingCues.size

        val distToCue = if (upcomingCue != null) {
            (upcomingCue.distanceFromStart - currentDist).coerceAtLeast(0.0)
        } else 0.0

        val countdownThreshold = tuningConfig.turnCueCountdownDistanceMeters.toDouble()
        val isApproaching = upcomingCue != null && distToCue <= countdownThreshold
        val isTurnNow = upcomingCue != null && distToCue <= 25.0

        val newState = TurnNavigationState(
            activeRouteId = route.summary.id,
            upcomingCue = upcomingCue,
            distanceToNextCueMeters = distToCue,
            isApproaching = isApproaching,
            isTurnNow = isTurnNow,
            isOffRoute = isCurrentlyOffRoute,
            crossTrackDistanceMeters = crossTrackDist,
            passedCuesCount = passedCount,
            totalCuesCount = turnCues.size
        )

        _navigationState.value = newState
        return newState
    }
}
