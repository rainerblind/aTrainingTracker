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
import com.atrainingtracker.trainingtracker.repositories.BANALServiceRepository
import com.atrainingtracker.trainingtracker.repositories.RoutesRepository
import com.atrainingtracker.trainingtracker.settings.TuningConfig
import com.atrainingtracker.trainingtracker.settings.TuningPreferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Central repository managing turn-by-turn navigation orchestration, auditory alerts,
 * and real-time route progress tracking (REQ-MAP-028 / ATT-1450).
 */
class TurnByTurnNavigationRepository internal constructor(
    private val context: Context,
    private val routesRepository: RoutesRepository,
    private val banalRepository: BANALServiceRepository,
    private val tuningDataStore: TuningPreferencesDataStore,
    private val audioAlertManager: NavigationAudioAlertManager,
    private val navigationEngine: TurnByTurnNavigationEngine = TurnByTurnNavigationEngine(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {
    val navigationState: StateFlow<TurnNavigationState> = navigationEngine.navigationState

    private var previousState = TurnNavigationState()
    private var activeConfig = TuningConfig()

    init {
        // 1. Observe tuning configuration
        scope.launch {
            tuningDataStore.tuningConfigFlow.collect { config ->
                activeConfig = config
            }
        }

        // 2. Observe active route selection
        scope.launch {
            combine(
                routesRepository.activeNavigatedRouteId,
                routesRepository.allRoutes
            ) { activeId, allRoutes ->
                allRoutes.find { it.summary.id == activeId }
            }.collect { activeRoute ->
                navigationEngine.setActiveRoute(activeRoute)
                previousState = navigationEngine.navigationState.value
            }
        }

        // 3. Observe athlete location updates
        scope.launch {
            banalRepository.currentLocation.collect { latLng ->
                if (latLng != null) {
                    val newState = navigationEngine.onLocationChanged(latLng, activeConfig)
                    dispatchAlertsIfTriggered(previousState, newState, activeConfig)
                    previousState = newState
                }
            }
        }
    }

    private fun dispatchAlertsIfTriggered(
        prev: TurnNavigationState,
        curr: TurnNavigationState,
        config: TuningConfig
    ) {
        if (!config.turnAudioAlertsEnabled) return

        // 1. Approaching transition (false -> true)
        if (!prev.isApproaching && curr.isApproaching) {
            audioAlertManager.playApproachAlert()
        }

        // 2. Immediate execution transition (false -> true)
        if (!prev.isTurnNow && curr.isTurnNow) {
            audioAlertManager.playTurnNowAlert()
        }

        // 3. Off-route deviation (false -> true)
        if (!prev.isOffRoute && curr.isOffRoute) {
            audioAlertManager.playOffRouteAlert()
        }

        // 4. Back on route recovery (true -> false)
        if (prev.isOffRoute && !curr.isOffRoute) {
            audioAlertManager.playBackOnRouteAlert()
        }
    }

    /**
     * Cancels coroutine jobs and releases audio hardware.
     */
    fun release() {
        scope.cancel()
        audioAlertManager.release()
    }

    companion object {
        @Volatile
        private var instance: TurnByTurnNavigationRepository? = null

        fun getInstance(context: Context): TurnByTurnNavigationRepository {
            return instance ?: synchronized(this) {
                instance ?: TurnByTurnNavigationRepository(
                    context = context.applicationContext,
                    routesRepository = RoutesRepository.getInstance(context.applicationContext),
                    banalRepository = BANALServiceRepository.getInstance(context.applicationContext),
                    tuningDataStore = TuningPreferencesDataStore(context.applicationContext),
                    audioAlertManager = NavigationAudioAlertManager()
                ).also { instance = it }
            }
        }

        fun resetForTesting(newInstance: TurnByTurnNavigationRepository? = null) {
            instance?.release()
            instance = newInstance
        }
    }
}
