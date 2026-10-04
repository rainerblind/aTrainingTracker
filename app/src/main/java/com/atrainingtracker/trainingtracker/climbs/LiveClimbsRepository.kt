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

package com.atrainingtracker.trainingtracker.climbs

import android.content.Context
import android.location.Location
import com.atrainingtracker.trainingtracker.repositories.BANALServiceRepository
import com.atrainingtracker.trainingtracker.database.ClimbsDatabaseManager
import com.atrainingtracker.trainingtracker.repositories.RoutesRepository
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.SphericalUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Repository orchestrating real-time detection, tracking, and telemetry for climbs (REQ-MAP-027).
 * Supports both Route Context (ordered climbs along active route) and Free Riding (any nearby climb).
 */
class LiveClimbsRepository private constructor(
    private val context: Context,
    private val banalRepository: BANALServiceRepository = BANALServiceRepository.getInstance(context),
    private val routesRepository: RoutesRepository = RoutesRepository.getInstance(context),
    private val climbsDb: ClimbsDatabaseManager = ClimbsDatabaseManager.getInstance(context),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {

    private val _activeLiveClimb = MutableStateFlow<LiveClimbData?>(null)
    val activeLiveClimb: StateFlow<LiveClimbData?> = _activeLiveClimb.asStateFlow()

    private val _allClimbs = MutableStateFlow<List<Climb>>(emptyList())
    val allClimbs: StateFlow<List<Climb>> = _allClimbs.asStateFlow()

    private var activeClimbStartTimeMs: Long = 0L

    init {
        // 1. Observe active navigated route changes to refresh climb list
        scope.launch {
            routesRepository.activeNavigatedRouteId.collect { activeRouteId ->
                refreshClimbs(activeRouteId)
            }
        }

        // 2. Observe location updates
        scope.launch {
            banalRepository.currentLocation.collect { location ->
                if (location != null) {
                    val bearing = banalRepository.currentBearing.value ?: 0.0
                    updateTracking(location, bearing)
                }
            }
        }
    }

    /**
     * Refreshes the active candidate climbs list based on route context or all stored climbs.
     */
    suspend fun refreshClimbs(activeRouteId: Long? = routesRepository.activeNavigatedRouteId.value) {
        val list = if (activeRouteId != null) {
            climbsDb.getClimbsForRoute(activeRouteId)
        } else {
            climbsDb.getAllClimbs()
        }
        _allClimbs.value = list
    }

    /**
     * Updates live climb tracking telemetry based on athlete's current [location] and [bearing].
     */
    fun updateTracking(location: LatLng, bearing: Double) {
        val candidates = _allClimbs.value
        if (candidates.isEmpty()) {
            _activeLiveClimb.value = null
            return
        }

        val currentLive = _activeLiveClimb.value

        // If currently on a climb or finishing, continue tracking that climb
        if (currentLive != null && (currentLive.status == LiveClimbStatus.ON_CLIMB || currentLive.status == LiveClimbStatus.APPROACHING)) {
            val updated = evaluateClimbProgress(currentLive, location, bearing)
            _activeLiveClimb.value = updated
            return
        }

        // If finished, hold for 5 seconds before clearing
        if (currentLive != null && currentLive.status == LiveClimbStatus.FINISHED) {
            if (System.currentTimeMillis() - activeClimbStartTimeMs > 5000L) {
                _activeLiveClimb.value = null
            } else {
                return
            }
        }

        // Otherwise scan candidates for approaching or starting climb
        val results = FloatArray(1)
        var bestCandidate: LiveClimbData? = null
        var minDistanceToStart = Double.MAX_VALUE

        val activeRouteId = routesRepository.activeNavigatedRouteId.value
        val totalRouteClimbs = if (activeRouteId != null) candidates.size else null

        for ((index, climb) in candidates.withIndex()) {
            Location.distanceBetween(location.latitude, location.longitude, climb.startLat, climb.startLng, results)
            val distToStart = results[0].toDouble()

            if (distToStart <= CLIMB_APPROACH_THRESHOLD_METERS) {
                // Check heading alignment to initial climb segment
                val initialBearing = if (climb.pathPoints.size >= 2) {
                    SphericalUtil.computeHeading(climb.pathPoints[0].latLng, climb.pathPoints[1].latLng)
                } else {
                    SphericalUtil.computeHeading(climb.startLatLng, climb.endLatLng)
                }

                val bearingDiff = getBearingDifference(bearing, initialBearing)
                if (bearingDiff <= 45.0) {
                    if (distToStart < minDistanceToStart) {
                        minDistanceToStart = distToStart
                        bestCandidate = LiveClimbData(
                            climb = climb,
                            status = if (distToStart <= CLIMB_START_GATE_THRESHOLD_METERS) LiveClimbStatus.ON_CLIMB else LiveClimbStatus.APPROACHING,
                            distanceToStart = if (distToStart <= CLIMB_START_GATE_THRESHOLD_METERS) 0.0 else distToStart,
                            distanceToSummit = climb.distanceMeters,
                            remainingElevationGain = climb.elevationGainMeters,
                            currentGradePercent = climb.avgGradePercent,
                            currentProgressFraction = 0f,
                            routeIndex = if (activeRouteId != null) index + 1 else null,
                            totalRouteClimbs = totalRouteClimbs
                        )
                    }
                }
            }
        }

        if (bestCandidate != null) {
            if (bestCandidate.status == LiveClimbStatus.ON_CLIMB) {
                activeClimbStartTimeMs = System.currentTimeMillis()
            }
            _activeLiveClimb.value = bestCandidate
        } else {
            _activeLiveClimb.value = null
        }
    }

    private fun evaluateClimbProgress(
        current: LiveClimbData,
        location: LatLng,
        bearing: Double
    ): LiveClimbData? {
        val climb = current.climb
        val results = FloatArray(1)

        // Check distance to summit
        Location.distanceBetween(location.latitude, location.longitude, climb.endLat, climb.endLng, results)
        val distToSummit = results[0].toDouble()

        if (distToSummit <= CLIMB_SUMMIT_GATE_THRESHOLD_METERS) {
            // Summit reached!
            activeClimbStartTimeMs = System.currentTimeMillis()
            return current.copy(
                status = LiveClimbStatus.FINISHED,
                distanceToSummit = 0.0,
                remainingElevationGain = 0.0,
                currentProgressFraction = 1.0f
            )
        }

        // Distance to start
        Location.distanceBetween(location.latitude, location.longitude, climb.startLat, climb.startLng, results)
        val distToStart = results[0].toDouble()

        // If approaching and now crosses start gate
        if (current.status == LiveClimbStatus.APPROACHING) {
            if (distToStart <= CLIMB_START_GATE_THRESHOLD_METERS) {
                activeClimbStartTimeMs = System.currentTimeMillis()
                return current.copy(
                    status = LiveClimbStatus.ON_CLIMB,
                    distanceToStart = 0.0
                )
            } else if (distToStart > CLIMB_APPROACH_THRESHOLD_METERS) {
                // Moved away
                return null
            }
            return current.copy(distanceToStart = distToStart)
        }

        // Currently ON_CLIMB: estimate progress and remaining ascent
        val path = climb.pathPoints
        if (path.isEmpty()) {
            val progress = ((climb.distanceMeters - distToSummit) / climb.distanceMeters).coerceIn(0.0, 1.0).toFloat()
            val remGain = (climb.elevationGainMeters * (1.0f - progress)).coerceAtLeast(0.0)
            return current.copy(
                distanceToSummit = distToSummit,
                remainingElevationGain = remGain,
                currentProgressFraction = progress
            )
        }

        // Find closest point along climb path
        var closestIdx = 0
        var minPointDist = Double.MAX_VALUE
        for (i in path.indices) {
            Location.distanceBetween(location.latitude, location.longitude, path[i].latLng.latitude, path[i].latLng.longitude, results)
            val d = results[0].toDouble()
            if (d < minPointDist) {
                minPointDist = d
                closestIdx = i
            }
        }

        // Off-climb check
        if (minPointDist > CLIMB_OFF_ROUTE_THRESHOLD_METERS) {
            return null
        }

        val completedDist = path[closestIdx].distance - path.first().distance
        val remainingDist = (climb.distanceMeters - completedDist).coerceAtLeast(0.0)
        val currentAlt = path[closestIdx].altitude
        val summitAlt = path.last().altitude
        val remainingGain = (summitAlt - currentAlt).coerceAtLeast(0.0)
        val progressFraction = (completedDist / climb.distanceMeters).coerceIn(0.0, 1.0).toFloat()

        val grade = if (closestIdx < path.size - 1) {
            val dD = path[closestIdx + 1].distance - path[closestIdx].distance
            val dA = path[closestIdx + 1].altitude - path[closestIdx].altitude
            if (dD > 0) (dA / dD) * 100.0 else climb.avgGradePercent
        } else {
            climb.avgGradePercent
        }

        return current.copy(
            distanceToSummit = remainingDist,
            remainingElevationGain = remainingGain,
            currentGradePercent = grade,
            currentProgressFraction = progressFraction
        )
    }

    private fun getBearingDifference(b1: Double, b2: Double): Double {
        val diff = abs(b1 - b2) % 360
        return if (diff > 180) 360 - diff else diff
    }

    fun cancelScope() {
        scope.cancel()
    }

    companion object {
        const val CLIMB_APPROACH_THRESHOLD_METERS = 250.0
        const val CLIMB_START_GATE_THRESHOLD_METERS = 30.0
        const val CLIMB_SUMMIT_GATE_THRESHOLD_METERS = 35.0
        const val CLIMB_OFF_ROUTE_THRESHOLD_METERS = 100.0

        @Volatile
        private var instance: LiveClimbsRepository? = null

        fun getInstance(context: Context): LiveClimbsRepository {
            return instance ?: synchronized(this) {
                instance ?: LiveClimbsRepository(context.applicationContext).also { instance = it }
            }
        }

        fun resetForTesting(newInstance: LiveClimbsRepository? = null) {
            instance?.cancelScope()
            instance = newInstance
        }
    }
}
