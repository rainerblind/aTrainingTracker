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

package com.atrainingtracker.trainingtracker

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.PeriodMarkerType
import com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist.WorkoutFilterCriteria
import com.atrainingtracker.trainingtracker.ui.clusters.ClusterMarkerType
import com.atrainingtracker.trainingtracker.ui.routes.RouteFilterCriteria
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private val Context.dataStore by preferencesDataStore(name = "user_preferences")

class MyPreferenceManager(context: Context) {
    private val dataStore = context.dataStore
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        val IS_COMPACT_VIEW = booleanPreferencesKey("is_compact_view")
        val ENABLED_PERIOD_MARKER_TYPES = stringSetPreferencesKey("enabled_period_marker_types")
        val ENABLED_CLUSTER_MARKER_TYPES = stringSetPreferencesKey("enabled_cluster_marker_types")
        val ENABLED_TRACK_TYPES = stringSetPreferencesKey("enabled_track_types")
        val WORKOUT_FILTER_CRITERIA_JSON = stringPreferencesKey("workout_filter_criteria_json")
        val ROUTE_FILTER_CRITERIA_JSON = stringPreferencesKey("route_filter_criteria_json")
    }

    val isCompactViewFlow: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[IS_COMPACT_VIEW] ?: false // Default to detailed view
    }

    suspend fun setCompactView(isCompact: Boolean) {
        dataStore.edit { preferences ->
            preferences[IS_COMPACT_VIEW] = isCompact
        }
    }

    val enabledPeriodMarkerTypesFlow: Flow<Set<String>> = dataStore.data.map { preferences ->
        preferences[ENABLED_PERIOD_MARKER_TYPES] ?: setOf(
            PeriodMarkerType.ALTITUDE.name,
            PeriodMarkerType.DISTANCE.name
        )
    }

    suspend fun setPeriodMarkerTypeEnabled(type: String, enabled: Boolean) {
        dataStore.edit { preferences ->
            val current = preferences[ENABLED_PERIOD_MARKER_TYPES] ?: setOf(
                PeriodMarkerType.ALTITUDE.name,
                PeriodMarkerType.DISTANCE.name
            )
            val updated = if (enabled) current + type else current - type
            preferences[ENABLED_PERIOD_MARKER_TYPES] = updated
        }
    }

    val enabledClusterMarkerTypesFlow: Flow<Set<String>> = dataStore.data.map { preferences ->
        preferences[ENABLED_CLUSTER_MARKER_TYPES] ?: setOf(
            ClusterMarkerType.START.name,
            ClusterMarkerType.END.name,
            ClusterMarkerType.DISTANCE.name
        )
    }

    suspend fun setClusterMarkerTypeEnabled(type: String, enabled: Boolean) {
        dataStore.edit { preferences ->
            val current = preferences[ENABLED_CLUSTER_MARKER_TYPES] ?: setOf(
                ClusterMarkerType.START.name,
                ClusterMarkerType.END.name,
                ClusterMarkerType.DISTANCE.name
            )
            val updated = if (enabled) current + type else current - type
            preferences[ENABLED_CLUSTER_MARKER_TYPES] = updated
        }
    }

    val enabledTrackTypesFlow: Flow<Set<String>> = dataStore.data.map { preferences ->
        preferences[ENABLED_TRACK_TYPES] ?: setOf(com.atrainingtracker.trainingtracker.ui.map.TrackType.BEST.name)
    }

    suspend fun setTrackTypeEnabled(type: String, enabled: Boolean) {
        dataStore.edit { preferences ->
            val current = preferences[ENABLED_TRACK_TYPES] ?: setOf(com.atrainingtracker.trainingtracker.ui.map.TrackType.BEST.name)
            val updated = if (enabled) current + type else current - type
            preferences[ENABLED_TRACK_TYPES] = updated
        }
    }

    val workoutFilterCriteriaFlow: Flow<WorkoutFilterCriteria> = dataStore.data.map { preferences ->
        WorkoutFilterCriteria.fromJson(preferences[WORKOUT_FILTER_CRITERIA_JSON])
    }

    suspend fun setWorkoutFilterCriteria(criteria: WorkoutFilterCriteria) {
        dataStore.edit { preferences ->
            if (criteria.isEmpty) {
                preferences.remove(WORKOUT_FILTER_CRITERIA_JSON)
            } else {
                preferences[WORKOUT_FILTER_CRITERIA_JSON] = criteria.toJson()
            }
        }
    }

    fun clearWorkoutFilterCriteria() {
        appScope.launch {
            dataStore.edit { preferences ->
                preferences.remove(WORKOUT_FILTER_CRITERIA_JSON)
            }
        }
    }

    val routeFilterCriteriaFlow: Flow<RouteFilterCriteria> = dataStore.data.map { preferences ->
        RouteFilterCriteria.fromJson(preferences[ROUTE_FILTER_CRITERIA_JSON])
    }

    suspend fun setRouteFilterCriteria(criteria: RouteFilterCriteria) {
        dataStore.edit { preferences ->
            if (criteria.isEmpty) {
                preferences.remove(ROUTE_FILTER_CRITERIA_JSON)
            } else {
                preferences[ROUTE_FILTER_CRITERIA_JSON] = criteria.toJson()
            }
        }
    }

    fun clearRouteFilterCriteria() {
        appScope.launch {
            dataStore.edit { preferences ->
                preferences.remove(ROUTE_FILTER_CRITERIA_JSON)
            }
        }
    }
}
