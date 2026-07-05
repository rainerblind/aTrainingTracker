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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "user_preferences")

class MyPreferenceManager(context: Context) {
    private val dataStore = context.dataStore

    companion object {
        val IS_COMPACT_VIEW = booleanPreferencesKey("is_compact_view")
        val IS_HEATMAP_ENABLED = booleanPreferencesKey("is_heatmap_enabled")
        val ENABLED_PERIOD_MARKER_TYPES = stringSetPreferencesKey("enabled_period_marker_types")
    }

    val isCompactViewFlow: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[IS_COMPACT_VIEW] ?: false // Default to detailed view
    }

    suspend fun setCompactView(isCompact: Boolean) {
        dataStore.edit { preferences ->
            preferences[IS_COMPACT_VIEW] = isCompact
        }
    }

    val isHeatmapEnabledFlow: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[IS_HEATMAP_ENABLED] ?: true // Default to enabled
    }

    suspend fun setHeatmapEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[IS_HEATMAP_ENABLED] = enabled
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
}
