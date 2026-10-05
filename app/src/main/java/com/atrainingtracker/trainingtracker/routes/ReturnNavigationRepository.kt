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
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.database.KnownLocationsDatabaseManager
import com.atrainingtracker.trainingtracker.database.RouteWithPath
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Coordinates return navigation, "Take Me Home" destination resolution,
 * corridor snapping, and dynamic elevation-aware ETA calculation (REQ-MAP-029 / ATT-1953).
 */
class ReturnNavigationRepository internal constructor(
    private val context: Context,
    private val routesRepository: RoutesRepository,
    private val banalRepository: BANALServiceRepository,
    private val knownLocationsManager: KnownLocationsDatabaseManager = KnownLocationsDatabaseManager.getInstance(context),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {

    private val _navigationState = MutableStateFlow(ReturnNavigationState())
    val navigationState: StateFlow<ReturnNavigationState> = _navigationState.asStateFlow()

    private var isTakeMeHomeMode: Boolean = false
    private var isReverseReturn: Boolean = false
    private var isDismissed: Boolean = false

    private var cachedHomeDestination: HomeDestination? = null
    private var currentActiveRoute: RouteWithPath? = null
    private var allSavedRoutes: List<RouteWithPath> = emptyList()

    init {
        // 1. Observe active navigated route & all saved routes
        scope.launch {
            combine(
                routesRepository.activeNavigatedRouteId,
                routesRepository.allRoutes
            ) { activeId, allRoutes ->
                allSavedRoutes = allRoutes
                allRoutes.find { it.summary.id == activeId }
            }.collect { activeRoute ->
                currentActiveRoute = activeRoute
                recalculateNavigationMetrics()
            }
        }

        // 2. Observe athlete location updates
        scope.launch {
            banalRepository.currentLocation.collect { latLng ->
                if (latLng != null) {
                    recalculateNavigationMetrics(currentLocation = latLng)
                }
            }
        }
    }

    /**
     * Activates "Take Me Home" mode, resolving the athlete's home destination
     * and calculating return route corridor.
     */
    fun startTakeMeHome() {
        isTakeMeHomeMode = true
        isDismissed = false
        cachedHomeDestination = HomeLocationResolver.resolveHomeLocation(knownLocationsManager)
        recalculateNavigationMetrics()
    }

    /**
     * Deactivates "Take Me Home" mode.
     */
    fun stopTakeMeHome() {
        isTakeMeHomeMode = false
        recalculateNavigationMetrics()
    }

    /**
     * Toggles or sets reverse return along the currently navigated route.
     */
    fun setReverseReturn(reverse: Boolean) {
        isReverseReturn = reverse
        recalculateNavigationMetrics()
    }

    /**
     * Dismisses the HUD banner for the current tracking session.
     */
    fun dismissHud() {
        isDismissed = true
        _navigationState.value = _navigationState.value.copy(isDismissed = true)
    }

    /**
     * Restores the HUD banner visibility.
     */
    fun restoreHud() {
        isDismissed = false
        _navigationState.value = _navigationState.value.copy(isDismissed = false)
    }

    @Synchronized
    private fun recalculateNavigationMetrics(
        currentLocation: LatLng? = banalRepository.currentLocation.value
    ) {
        if (currentLocation == null) {
            _navigationState.value = ReturnNavigationState()
            return
        }

        if (cachedHomeDestination == null) {
            cachedHomeDestination = HomeLocationResolver.resolveHomeLocation(knownLocationsManager)
        }

        val activeRoute = currentActiveRoute
        val hasActiveRoute = activeRoute != null && activeRoute.path.isNotEmpty()

        // Active if either navigating a route OR athlete explicitly requested "Take Me Home"
        val isActive = hasActiveRoute || isTakeMeHomeMode

        if (!isActive) {
            _navigationState.value = ReturnNavigationState()
            return
        }

        val snap = ReturnCorridorSnapper.snapToCorridor(
            currentLat = currentLocation.latitude,
            currentLng = currentLocation.longitude,
            currentAltitude = 0.0,
            activeRoute = if (hasActiveRoute) activeRoute else null,
            homeDestination = cachedHomeDestination,
            savedRoutes = allSavedRoutes,
            forceReverseReturn = isReverseReturn
        )

        if (snap == null) {
            _navigationState.value = ReturnNavigationState(isActive = false)
            return
        }

        val currentSpeed = banalRepository.currentSpeed.value
        val sportType = banalRepository.bSportType.value

        val eta = ElevationAwareEtaCalculator.calculateEta(
            remainingDistanceMeters = snap.remainingDistanceMeters,
            remainingClimbMeters = snap.remainingClimbMeters,
            currentSpeedMps = currentSpeed,
            sportType = sportType
        )

        _navigationState.value = ReturnNavigationState(
            isActive = true,
            destinationName = snap.destinationName,
            isHomeDestination = snap.isHomeDestination,
            remainingDistanceMeters = snap.remainingDistanceMeters,
            remainingElevationGainMeters = snap.remainingClimbMeters,
            durationSeconds = eta.durationSeconds,
            formattedDuration = eta.formattedDuration,
            formattedClockTime = eta.formattedClockTime,
            isReverseReturn = snap.isReverseReturn,
            isTakeMeHomeMode = isTakeMeHomeMode,
            snappedRouteId = snap.snappedRouteId,
            isDismissed = isDismissed
        )
    }

    fun release() {
        scope.cancel()
    }

    companion object {
        @Volatile
        private var instance: ReturnNavigationRepository? = null

        fun getInstance(context: Context): ReturnNavigationRepository {
            return instance ?: synchronized(this) {
                instance ?: ReturnNavigationRepository(
                    context = context.applicationContext,
                    routesRepository = RoutesRepository.getInstance(context.applicationContext),
                    banalRepository = BANALServiceRepository.getInstance(context.applicationContext)
                ).also { instance = it }
            }
        }

        fun resetForTesting(newInstance: ReturnNavigationRepository? = null) {
            instance?.release()
            instance = newInstance
        }
    }
}
