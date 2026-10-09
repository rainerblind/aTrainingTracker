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

import android.content.Context
import androidx.annotation.VisibleForTesting
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.repositories.BANALServiceRepository
import com.atrainingtracker.trainingtracker.repositories.RoutesRepository
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Coordinates in-ride fork-in-the-road decision alerts and autonomous route snapping.
 *
 * Conforms to REQ-MAP-031 clause 4 and REQ-MAP-038 clause 3.
 */
class ForkNavigationRepository internal constructor(
    private val routesRepository: RoutesRepository,
    private val banalRepository: BANALServiceRepository? = null,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {

    private val _forkDecisionState = MutableStateFlow<ForkDecisionState?>(null)
    val forkDecisionState: StateFlow<ForkDecisionState?> = _forkDecisionState.asStateFlow()

    private var isDismissed = false
    private var lastSearchTimeMs: Long = 0L
    private var lastSearchPos: LatLng? = null

    init {
        if (banalRepository != null) {
            scope.launch {
                banalRepository.currentLocation.collect { latLng ->
                    if (latLng != null) {
                        onLocationChanged(latLng)
                    }
                }
            }
        }
    }

    /**
     * Processes location updates during an active workout session.
     * Evaluates candidates, triggers proximity alerts, and performs autonomous binding.
     * Throttles candidate searches during quiescent tracking (REQ-MAP-038 clause 3).
     */
    fun onLocationChanged(
        currentPos: LatLng,
        recentHistory: List<LatLng>? = null,
        activeSportType: BSportType? = null,
        currentTimeMs: Long = System.currentTimeMillis()
    ) {
        // If athlete is already navigating an active locked route, do not display fork alerts
        if (routesRepository.activeNavigatedRouteId.value != null) {
            if (_forkDecisionState.value != null) {
                _forkDecisionState.value = null
            }
            return
        }

        if (isDismissed) {
            return
        }

        val activeState = _forkDecisionState.value
        val isAlertActive = activeState != null

        // Quiescent tracking throttling: skip if elapsed time < 3000ms AND displacement < 20m
        if (!isAlertActive && lastSearchPos != null) {
            val elapsedMs = currentTimeMs - lastSearchTimeMs
            val distanceMoved = GeoUtils.haversineDistanceMeters(
                lastSearchPos!!.latitude, lastSearchPos!!.longitude,
                currentPos.latitude, currentPos.longitude
            )
            if (elapsedMs < QUIESCENT_THROTTLE_INTERVAL_MS && distanceMoved < QUIESCENT_THROTTLE_DISTANCE_METERS) {
                return
            }
        }

        lastSearchTimeMs = currentTimeMs
        lastSearchPos = currentPos

        val allRoutes = routesRepository.allRoutes.value
        val candidates = ForkRouteMatcher.findCandidateRoutes(allRoutes, currentPos, recentHistory, activeSportType)

        if (activeState != null) {
            // Check for autonomous binding if rider has progressed past divergence point
            val chosenRoute = candidates.firstOrNull { route ->
                val proj = ForkRouteMatcher.projectOntoPolyline(currentPos, route.path)
                val forkProj = ForkRouteMatcher.projectOntoPolyline(activeState.divergenceCoordinate, route.path)
                val distancePastFork = proj.distanceAlongRouteMeters - forkProj.distanceAlongRouteMeters

                distancePastFork >= AUTO_BIND_MIN_DISTANCE_PAST_FORK_METERS &&
                        proj.crossTrackDistanceMeters < AUTO_BIND_MAX_CROSS_TRACK_CHOSEN_METERS
            }

            if (chosenRoute != null) {
                // Verify alternative candidate branches have cross-track error > 50m
                val otherCandidates = candidates.filter { it.summary.id != chosenRoute.summary.id }
                val alternativesFar = otherCandidates.isEmpty() || otherCandidates.all { other ->
                    val projOther = ForkRouteMatcher.projectOntoPolyline(currentPos, other.path)
                    projOther.crossTrackDistanceMeters > AUTO_BIND_MIN_CROSS_TRACK_ALTERNATIVE_METERS
                }

                if (alternativesFar) {
                    routesRepository.setActiveNavigatedRoute(chosenRoute.summary.id)
                    _forkDecisionState.value = null
                    return
                }
            }

            // Check if athlete has deviated from all candidate branches
            val allDeviated = candidates.isNotEmpty() && candidates.all { route ->
                val proj = ForkRouteMatcher.projectOntoPolyline(currentPos, route.path)
                proj.crossTrackDistanceMeters > AUTO_BIND_MIN_CROSS_TRACK_ALTERNATIVE_METERS
            }

            if (allDeviated) {
                _forkDecisionState.value = null
                return
            }

            // Update remaining distance to fork
            val updatedState = RouteDivergenceDetector.detectDivergence(candidates, currentPos)
            _forkDecisionState.value = updatedState
        } else {
            // No alert active; check if a fork is approaching within alert window
            val newState = RouteDivergenceDetector.detectDivergence(candidates, currentPos)
            _forkDecisionState.value = newState
        }
    }

    /**
     * Manually binds a route choice selected by the athlete and dismisses the alert.
     */
    fun selectRouteManually(routeId: Long) {
        routesRepository.setActiveNavigatedRoute(routeId)
        _forkDecisionState.value = null
        isDismissed = false
    }

    /**
     * Dismisses the active fork decision card without binding a route.
     */
    fun dismissPrompt() {
        _forkDecisionState.value = null
        isDismissed = true
    }

    @VisibleForTesting
    fun cancelScope() {
        scope.cancel()
    }

    companion object {
        const val AUTO_BIND_MIN_DISTANCE_PAST_FORK_METERS = 50.0
        const val AUTO_BIND_MAX_CROSS_TRACK_CHOSEN_METERS = 25.0
        const val AUTO_BIND_MIN_CROSS_TRACK_ALTERNATIVE_METERS = 50.0
        const val QUIESCENT_THROTTLE_INTERVAL_MS = 3000L
        const val QUIESCENT_THROTTLE_DISTANCE_METERS = 20.0

        @Volatile
        private var instance: ForkNavigationRepository? = null

        fun getInstance(context: Context): ForkNavigationRepository {
            return instance ?: synchronized(this) {
                instance ?: ForkNavigationRepository(
                    routesRepository = RoutesRepository.getInstance(context.applicationContext),
                    banalRepository = BANALServiceRepository.getInstance(context.applicationContext)
                ).also { instance = it }
            }
        }

        fun getInstance(routesRepository: RoutesRepository): ForkNavigationRepository {
            return instance ?: synchronized(this) {
                instance ?: ForkNavigationRepository(routesRepository).also { instance = it }
            }
        }

        @VisibleForTesting
        fun resetForTesting(newInstance: ForkNavigationRepository? = null) {
            instance?.cancelScope()
            instance = newInstance
        }
    }
}
