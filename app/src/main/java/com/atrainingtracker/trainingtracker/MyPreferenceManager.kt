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
import com.atrainingtracker.trainingtracker.ui.clusters.ClusterFilterCriteria
import com.atrainingtracker.trainingtracker.ui.components.workoutlaps.LapDisplayMode
import com.atrainingtracker.trainingtracker.ui.routes.RouteFilterCriteria
import com.atrainingtracker.trainingtracker.ui.segments.segmentlist.SegmentFilterCriteria
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private val Context.dataStore by preferencesDataStore(name = "user_preferences")

/**
 * Configurable section visibility preferences for detailed workout journal cards (REQ-UI-210 / ATT-1714 / REQ-UI-229).
 */
data class WorkoutCardSectionPreferences(
    val showDescription: Boolean = true,
    val showExtrema: Boolean = true,
    val showLaps: Boolean = true,
    val showStrava: Boolean = true,
    val showMapPreview: Boolean = true,
    val showElevationProfile: Boolean = true,
    val showTelemetryCharts: Boolean = false,
    val showZoneAnalysis: Boolean = false,
    val lapDisplayMode: LapDisplayMode = LapDisplayMode.VISUALIZER_ONLY
)

/**
 * Configurable field visibility preferences for the Edit Workout dialog (REQ-UI-211 / ATT-1713).
 */
data class EditWorkoutFieldPreferences(
    val showCluster: Boolean = true,
    val showCommuteTrainer: Boolean = true,
    val showRace: Boolean = true,
    val showStravaUpload: Boolean = true,
    val showDescription: Boolean = true,
    val showGoal: Boolean = true,
    val showMethod: Boolean = true
)

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
        val SEGMENT_FILTER_CRITERIA_JSON = stringPreferencesKey("segment_filter_criteria_json")
        val CLUSTER_FILTER_CRITERIA_JSON = stringPreferencesKey("cluster_filter_criteria_json")

        val WORKOUT_CARD_SHOW_DESCRIPTION = booleanPreferencesKey("workout_card_show_description")
        val WORKOUT_CARD_SHOW_EXTREMA = booleanPreferencesKey("workout_card_show_extrema")
        val WORKOUT_CARD_SHOW_LAPS = booleanPreferencesKey("workout_card_show_laps")
        val WORKOUT_CARD_SHOW_STRAVA = booleanPreferencesKey("workout_card_show_strava")
        val WORKOUT_CARD_SHOW_MAP = booleanPreferencesKey("workout_card_show_map")
        val WORKOUT_CARD_SHOW_ELEVATION = booleanPreferencesKey("workout_card_show_elevation")
        val WORKOUT_CARD_SHOW_CHARTS = booleanPreferencesKey("workout_card_show_charts")
        val WORKOUT_CARD_SHOW_ZONES = booleanPreferencesKey("workout_card_show_zones")
        val WORKOUT_CARD_LAP_DISPLAY_MODE = stringPreferencesKey("workout_card_lap_display_mode")

        val EDIT_WORKOUT_SHOW_CLUSTER = booleanPreferencesKey("edit_workout_show_cluster")
        val EDIT_WORKOUT_SHOW_COMMUTE_TRAINER = booleanPreferencesKey("edit_workout_show_commute_trainer")
        val EDIT_WORKOUT_SHOW_RACE = booleanPreferencesKey("edit_workout_show_race")
        val EDIT_WORKOUT_SHOW_STRAVA_UPLOAD = booleanPreferencesKey("edit_workout_show_strava_upload")
        val EDIT_WORKOUT_SHOW_DESCRIPTION = booleanPreferencesKey("edit_workout_show_description")
        val EDIT_WORKOUT_SHOW_GOAL = booleanPreferencesKey("edit_workout_show_goal")
        val EDIT_WORKOUT_SHOW_METHOD = booleanPreferencesKey("edit_workout_show_method")
    }

    val workoutCardPreferencesFlow: Flow<WorkoutCardSectionPreferences> = dataStore.data.map { preferences ->
        WorkoutCardSectionPreferences(
            showDescription = preferences[WORKOUT_CARD_SHOW_DESCRIPTION] ?: true,
            showExtrema = preferences[WORKOUT_CARD_SHOW_EXTREMA] ?: true,
            showLaps = preferences[WORKOUT_CARD_SHOW_LAPS] ?: true,
            showStrava = preferences[WORKOUT_CARD_SHOW_STRAVA] ?: true,
            showMapPreview = preferences[WORKOUT_CARD_SHOW_MAP] ?: true,
            showElevationProfile = preferences[WORKOUT_CARD_SHOW_ELEVATION] ?: true,
            showTelemetryCharts = preferences[WORKOUT_CARD_SHOW_CHARTS] ?: false,
            showZoneAnalysis = preferences[WORKOUT_CARD_SHOW_ZONES] ?: false,
            lapDisplayMode = try {
                val rawMode = preferences[WORKOUT_CARD_LAP_DISPLAY_MODE]
                if (rawMode != null && rawMode != "BOTH") LapDisplayMode.valueOf(rawMode) else LapDisplayMode.VISUALIZER_ONLY
            } catch (e: Exception) {
                LapDisplayMode.VISUALIZER_ONLY
            }
        )
    }

    suspend fun setWorkoutCardPreferences(prefs: WorkoutCardSectionPreferences) {
        dataStore.edit { preferences ->
            preferences[WORKOUT_CARD_SHOW_DESCRIPTION] = prefs.showDescription
            preferences[WORKOUT_CARD_SHOW_EXTREMA] = prefs.showExtrema
            preferences[WORKOUT_CARD_SHOW_LAPS] = prefs.showLaps
            preferences[WORKOUT_CARD_SHOW_STRAVA] = prefs.showStrava
            preferences[WORKOUT_CARD_SHOW_MAP] = prefs.showMapPreview
            preferences[WORKOUT_CARD_SHOW_ELEVATION] = prefs.showElevationProfile
            preferences[WORKOUT_CARD_SHOW_CHARTS] = prefs.showTelemetryCharts
            preferences[WORKOUT_CARD_SHOW_ZONES] = prefs.showZoneAnalysis
            preferences[WORKOUT_CARD_LAP_DISPLAY_MODE] = prefs.lapDisplayMode.name
        }
    }

    val editWorkoutFieldPreferencesFlow: Flow<EditWorkoutFieldPreferences> = dataStore.data.map { preferences ->
        EditWorkoutFieldPreferences(
            showCluster = preferences[EDIT_WORKOUT_SHOW_CLUSTER] ?: true,
            showCommuteTrainer = preferences[EDIT_WORKOUT_SHOW_COMMUTE_TRAINER] ?: true,
            showRace = preferences[EDIT_WORKOUT_SHOW_RACE] ?: true,
            showStravaUpload = preferences[EDIT_WORKOUT_SHOW_STRAVA_UPLOAD] ?: true,
            showDescription = preferences[EDIT_WORKOUT_SHOW_DESCRIPTION] ?: true,
            showGoal = preferences[EDIT_WORKOUT_SHOW_GOAL] ?: true,
            showMethod = preferences[EDIT_WORKOUT_SHOW_METHOD] ?: true
        )
    }

    suspend fun setEditWorkoutFieldPreferences(prefs: EditWorkoutFieldPreferences) {
        dataStore.edit { preferences ->
            preferences[EDIT_WORKOUT_SHOW_CLUSTER] = prefs.showCluster
            preferences[EDIT_WORKOUT_SHOW_COMMUTE_TRAINER] = prefs.showCommuteTrainer
            preferences[EDIT_WORKOUT_SHOW_RACE] = prefs.showRace
            preferences[EDIT_WORKOUT_SHOW_STRAVA_UPLOAD] = prefs.showStravaUpload
            preferences[EDIT_WORKOUT_SHOW_DESCRIPTION] = prefs.showDescription
            preferences[EDIT_WORKOUT_SHOW_GOAL] = prefs.showGoal
            preferences[EDIT_WORKOUT_SHOW_METHOD] = prefs.showMethod
        }
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
            ClusterMarkerType.DISTANCE.name,
            ClusterMarkerType.ALTITUDE_MIN.name,
            ClusterMarkerType.ALTITUDE_MAX.name
        )
    }

    suspend fun setClusterMarkerTypeEnabled(type: String, enabled: Boolean) {
        dataStore.edit { preferences ->
            val current = preferences[ENABLED_CLUSTER_MARKER_TYPES] ?: setOf(
                ClusterMarkerType.START.name,
                ClusterMarkerType.END.name,
                ClusterMarkerType.DISTANCE.name,
                ClusterMarkerType.ALTITUDE_MIN.name,
                ClusterMarkerType.ALTITUDE_MAX.name
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

    val segmentFilterCriteriaFlow: Flow<SegmentFilterCriteria> = dataStore.data.map { preferences ->
        SegmentFilterCriteria.fromJson(preferences[SEGMENT_FILTER_CRITERIA_JSON])
    }

    suspend fun setSegmentFilterCriteria(criteria: SegmentFilterCriteria) {
        dataStore.edit { preferences ->
            if (criteria.isEmpty) {
                preferences.remove(SEGMENT_FILTER_CRITERIA_JSON)
            } else {
                preferences[SEGMENT_FILTER_CRITERIA_JSON] = criteria.toJson()
            }
        }
    }

    fun clearSegmentFilterCriteria() {
        appScope.launch {
            dataStore.edit { preferences ->
                preferences.remove(SEGMENT_FILTER_CRITERIA_JSON)
            }
        }
    }

    val clusterFilterCriteriaFlow: Flow<ClusterFilterCriteria> = dataStore.data.map { preferences ->
        ClusterFilterCriteria.fromJson(preferences[CLUSTER_FILTER_CRITERIA_JSON])
    }

    suspend fun setClusterFilterCriteria(criteria: ClusterFilterCriteria) {
        dataStore.edit { preferences ->
            if (criteria.isEmpty) {
                preferences.remove(CLUSTER_FILTER_CRITERIA_JSON)
            } else {
                preferences[CLUSTER_FILTER_CRITERIA_JSON] = criteria.toJson()
            }
        }
    }

    fun clearClusterFilterCriteria() {
        appScope.launch {
            dataStore.edit { preferences ->
                preferences.remove(CLUSTER_FILTER_CRITERIA_JSON)
            }
        }
    }
}
