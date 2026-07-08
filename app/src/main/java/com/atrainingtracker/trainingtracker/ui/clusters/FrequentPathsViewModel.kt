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

package com.atrainingtracker.trainingtracker.ui.clusters

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.atrainingtracker.trainingtracker.database.RouteCluster
import com.atrainingtracker.trainingtracker.database.RouteClusterRepository
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FrequentPathsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = RouteClusterRepository.getInstance(application)

    val allClusters: StateFlow<List<RouteCluster>> = repository.allClusters

    private val _clusterWorkouts = MutableStateFlow<List<WorkoutData>>(emptyList())
    val clusterWorkouts: StateFlow<List<WorkoutData>> = _clusterWorkouts.asStateFlow()

    private val _selectedCluster = MutableStateFlow<RouteCluster?>(null)
    val selectedCluster: StateFlow<RouteCluster?> = _selectedCluster.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            repository.refreshClusters()
        }
    }

    fun selectCluster(cluster: RouteCluster?) {
        _selectedCluster.value = cluster
        if (cluster != null) {
            viewModelScope.launch {
                _clusterWorkouts.value = repository.getWorkoutsForCluster(cluster.id)
            }
        } else {
            _clusterWorkouts.value = emptyList()
        }
    }
}
