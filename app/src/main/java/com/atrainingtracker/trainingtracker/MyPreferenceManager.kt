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
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.PeriodMarkerType
import com.atrainingtracker.trainingtracker.ui.clusters.ClusterMarkerType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "user_preferences")

class MyPreferenceManager(context: Context) {
    private val dataStore = context.dataStore

    companion object {
        val IS_COMPACT_VIEW = booleanPreferencesKey("is_compact_view")
        val ENABLED_PERIOD_MARKER_TYPES = stringSetPreferencesKey("enabled_period_marker_types")
        val ENABLED_CLUSTER_MARKER_TYPES = stringSetPreferencesKey("enabled_cluster_marker_types")
        val ENABLED_TRACK_TYPES = stringSetPreferencesKey("enabled_track_types")
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
}
