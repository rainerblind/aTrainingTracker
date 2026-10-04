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

package com.atrainingtracker.trainingtracker.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Central decoupled navigation event bus for workout-related destinations (ATT-503).
 *
 * Facilitates loosely coupled communication between child composable screens
 * and the hosting Activity navigation controller.
 */
object WorkoutNavigationEvents {
    private val _navigateToEdit = MutableSharedFlow<Long?>(
        replay = 1, // Sticky event
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val navigateToEdit = _navigateToEdit.asSharedFlow()

    @get:JvmStatic
    val navigateToEditLiveData: LiveData<Long?> by lazy { _navigateToEdit.asLiveData() }

    @JvmStatic
    fun triggerEdit(workoutId: Long) {
        _navigateToEdit.tryEmit(workoutId)
    }

    // Clear the edit event after it's handled
    fun reset() {
        _navigateToEdit.tryEmit(null)
    }

    private val _navigateToCluster = MutableSharedFlow<Long?>(
        replay = 1, // Sticky event
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val navigateToCluster = _navigateToCluster.asSharedFlow()

    @get:JvmStatic
    val navigateToClusterLiveData: LiveData<Long?> by lazy { _navigateToCluster.asLiveData() }

    /**
     * Triggers navigation to the cluster detail heatmap screen for [clusterId].
     */
    @JvmStatic
    fun triggerCluster(clusterId: Long) {
        _navigateToCluster.tryEmit(clusterId)
    }

    /**
     * Clears the active cluster navigation event after consumption.
     */
    @JvmStatic
    fun resetCluster() {
        _navigateToCluster.tryEmit(null)
    }

    /**
     * Encapsulates low peripheral sensor battery warning data (ATT-2192).
     */
    data class LowBatteryAlert(
        val message: String,
        val deviceNames: List<String> = emptyList()
    )

    private val _lowBatteryAlert = MutableSharedFlow<LowBatteryAlert?>(
        replay = 1, // Sticky event
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val lowBatteryAlert = _lowBatteryAlert.asSharedFlow()

    /**
     * Triggers a low sensor battery alert across the application.
     */
    @JvmStatic
    fun triggerLowBatteryAlert(alert: LowBatteryAlert) {
        _lowBatteryAlert.tryEmit(alert)
    }

    /**
     * Clears the active low sensor battery alert after consumption.
     */
    @JvmStatic
    fun consumeLowBatteryAlert() {
        _lowBatteryAlert.tryEmit(null)
    }
}