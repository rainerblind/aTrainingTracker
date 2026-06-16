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
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.HeatmapMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "user_preferences")

class MyPreferenceManager(context: Context) {
    private val dataStore = context.dataStore

    companion object {
        val IS_COMPACT_VIEW = booleanPreferencesKey("is_compact_view")
        val HEATMAP_MODE = stringPreferencesKey("heatmap_mode")
    }

    val isCompactViewFlow: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[IS_COMPACT_VIEW] ?: false // Default to detailed view
    }

    suspend fun setCompactView(isCompact: Boolean) {
        dataStore.edit { preferences ->
            preferences[IS_COMPACT_VIEW] = isCompact
        }
    }

    val heatmapModeFlow: Flow<HeatmapMode> = dataStore.data.map { preferences ->
        val modeName = preferences[HEATMAP_MODE] ?: HeatmapMode.DENSITY.name
        try {
            HeatmapMode.valueOf(modeName)
        } catch (e: Exception) {
            HeatmapMode.DENSITY
        }
    }

    suspend fun setHeatmapMode(mode: HeatmapMode) {
        dataStore.edit { preferences ->
            preferences[HEATMAP_MODE] = mode.name
        }
    }
}