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

package com.atrainingtracker.trainingtracker.ui.map

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.application
import androidx.lifecycle.viewModelScope
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.MyHelper
import com.atrainingtracker.trainingtracker.database.ExtremaType
import com.atrainingtracker.trainingtracker.database.WorkoutSamplesDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.repositories.RoutesRepository
import com.atrainingtracker.trainingtracker.segments.SegmentsRepository
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository
import com.atrainingtracker.trainingtracker.ui.utils.NumericalEncodingUtils
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.PolyUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AftermathMapUIState(
    val tracks: List<MapTrack> = emptyList(),
    val availableTrackTypes: Set<TrackType> = setOf(TrackType.BEST),
    val segments: List<MapSegment> = emptyList(),
    val routes: List<MapRoute> = emptyList(),
    val markers: List<LocationMarker> = emptyList(),
    val bSportType: BSportType = BSportType.UNKNOWN,
    val zoomFocus: MapZoomFocus = MapZoomFocus.FIT_PRIMARY
)

class TrackOnMapAftermathViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(AftermathMapUIState())
    val uiState = _uiState.asStateFlow()

    // TODO: Move to WorkoutRepository.
    private val summariesDb = WorkoutSummariesDatabaseManager.getInstance(application)
    private val samplesDb = WorkoutSamplesDatabaseManager.getInstance(application)

    private val workoutRepository = WorkoutRepository.getInstance(application)
    private val segmentsRepository = SegmentsRepository.getInstance(application)
    private val routesRepository = RoutesRepository.getInstance(application)
    private val prefManager = com.atrainingtracker.trainingtracker.MyPreferenceManager(application)

    private val extremaSensorTypes = arrayOf(
        SensorType.ALTITUDE, SensorType.TEMPERATURE,
        SensorType.HR, SensorType.POWER, SensorType.LINE_DISTANCE_m, SensorType.SPEED_mps
    )

    val enabledTrackTypes: StateFlow<Set<TrackType>> = prefManager.enabledTrackTypesFlow
        .map { strings ->
            strings.mapNotNull {
                try { TrackType.valueOf(it) } catch(e: Exception) { null }
            }.toSet()
        }
        .stateIn(
            scope = viewModelScope,
            started = kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),
            initialValue = setOf(TrackType.BEST)
        )

    fun toggleTrackTypeEnabled(type: TrackType) {
        viewModelScope.launch {
            val isEnabled = enabledTrackTypes.value.contains(type)
            prefManager.setTrackTypeEnabled(type.name, !isEnabled)
        }
    }

    fun loadAftermathData(workoutData: WorkoutData) {
        viewModelScope.launch(Dispatchers.IO) {
            val workoutId = workoutData.id
            val bSportType = workoutData.bSportType

            // --- PHASE 1: Immediate Reset ---
            // Clear previous state so the user doesn't see "ghost" data from another workout
            withContext(Dispatchers.Main) {
                _uiState.value = AftermathMapUIState(
                    zoomFocus = MapZoomFocus.FIT_PRIMARY,
                    bSportType = bSportType,
                    tracks = emptyList(),
                    markers = emptyList()
                )
            }

            // --- PHASE 2: Fast Track (From WorkoutData Polyline & Streams) ---
            // Decodes the thinned data already present in workoutData for instant UI feedback.
            if (workoutData.mapPolyline.isNotEmpty()) {
                val latLngs = PolyUtil.decode(workoutData.mapPolyline)

                // Decode elevation streams
                val alts = if (workoutData.encodedAltitudes.isNotEmpty()) {
                    NumericalEncodingUtils.decodeDoubles(workoutData.encodedAltitudes)
                } else emptyList()

                val dists = if (workoutData.encodedDistances.isNotEmpty()) {
                    NumericalEncodingUtils.decodeDoubles(workoutData.encodedDistances)
                } else emptyList()

                // Map points directly since sampling is identical
                val fastPath = latLngs.mapIndexed { index, latLng ->
                    PathPoint(
                        distance = dists.getOrElse(index) { 0.0 },
                        latLng = latLng,
                        altitude = alts.getOrElse(index) { 0.0 }
                    )
                }

                val fastTrack = MapTrack(
                    id = workoutId,
                    type = TrackType.BEST,
                    bSportType = bSportType,
                    path = fastPath
                )

                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        tracks = listOf(fastTrack)
                    )
                }
            }

            // --- PHASE 3: Calculate Extrema (Markers) ---
            // We keep the logic for markers separate so they appear as soon as computed
            val markerList = mutableListOf<LocationMarker>()

            // Add Start/Stop Markers
            workoutData.fileBaseName?.let { baseFile ->
                getExtremaPos(workoutId, baseFile, ExtremaType.START)?.let {
                    markerList.add(LocationMarker(it, R.drawable.control_start, application.getString(R.string.Start)))
                }
                getExtremaPos(workoutId, baseFile, ExtremaType.END)?.let {
                    markerList.add(LocationMarker(it, R.drawable.control_stop, application.getString(R.string.Stop)))
                }
            }

            // Add Sensor Max/Min Markers
            extremaSensorTypes.forEach { sensor ->
                addExtremaMarkerIfPresent(workoutId, sensor, ExtremaType.MAX, markerList)
                if (sensor == SensorType.ALTITUDE || sensor == SensorType.TEMPERATURE) {
                    addExtremaMarkerIfPresent(workoutId, sensor, ExtremaType.MIN, markerList)
                }
            }

            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(markers = markerList)
            }

            // --- PHASE 4: High-Resolution Tracks (From Samples DB) ---
            // Load full fidelity data and simplify aggressively for map performance
            val fullTracks = TrackType.entries.mapNotNull { type ->
                val fullPath = workoutRepository.getWorkoutTrackPoints(workoutId, type)
                if (fullPath.isNotEmpty()) {
                    // PERFORMANCE: Cap tracks at ~800 points for smooth UI interaction
                    val simplifiedPath = if (fullPath.size > 800) {
                        val latLngs = fullPath.map { it.latLng }
                        
                        var tolerance = if (type == TrackType.BEST) 1.0 else 3.0
                        var simplifiedLatLngs = PolyUtil.simplify(latLngs, tolerance)
                        
                        // Iteratively increase tolerance if path is still too large
                        var iterations = 0
                        while (simplifiedLatLngs.size > 1000 && iterations < 6) {
                            tolerance *= 2.0
                            simplifiedLatLngs = PolyUtil.simplify(latLngs, tolerance)
                            iterations++
                        }
                        
                        // Reconstruction of PathPoints
                        val result = ArrayList<PathPoint>(simplifiedLatLngs.size)
                        var originalIdx = 0
                        for (target in simplifiedLatLngs) {
                            while (originalIdx < fullPath.size && fullPath[originalIdx].latLng != target) {
                                originalIdx++
                            }
                            if (originalIdx < fullPath.size) {
                                result.add(fullPath[originalIdx])
                                originalIdx++
                            }
                        }
                        result
                    } else fullPath

                    MapTrack(
                        id = type.ordinal.toLong(),
                        type = type,
                        bSportType = bSportType,
                        path = simplifiedPath
                    )
                } else null
            }

            if (fullTracks.isNotEmpty()) {
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        tracks = fullTracks,
                        availableTrackTypes = fullTracks.map { it.type }.toSet()
                    )
                }
            }

            // --- PHASE 5: Segments (All for spatial context) ---
            val allSegments = segmentsRepository.allSegmentsWithPath.value
            val mapSegments = allSegments
                .map { segment ->
                    MapSegment(
                        stravaId = segment.summary.stravaId,
                        name = segment.summary.name,
                        path = segment.path,
                        bSportType = segment.summary.bSportType,
                        showStartAndFinishText = false,
                    )
                }

            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(
                    segments = mapSegments
                )
            }

            // --- PHASE 6: Routes (All for spatial context) ---
            val allRoutes = routesRepository.allRoutes.value
            val mapRoutes = allRoutes
                .map { it.toMapRoute() }

            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(
                    routes = mapRoutes
                )
            }
        }
    }

    private fun addExtremaMarkerIfPresent(
        workoutId: Long,
        sensor: SensorType,
        type: ExtremaType,
        markerList: MutableList<LocationMarker>
    ) {
        // Try to get both from the summary record first (efficient, new way)
        var value: Double? = summariesDb.getExtremaValue(workoutId, sensor, type)
        var pos: LatLng? = summariesDb.getExtremaPosition(workoutId, sensor, type)

        // Fallback for legacy data if position is missing in the summary table
        if (pos == null) {
            val legacyExtrema = samplesDb.getExtremaPosition(summariesDb, workoutId, sensor, type)
            if (legacyExtrema != null) {
                pos = legacyExtrema.latLng
                value = legacyExtrema.value
            }
        }

        if (value != null && pos != null) {
            val title = application.getString(
                R.string.location_extrema_format,
                type.toString(), // Use localized name ("Max", "Min")
                sensor.getFullName(application),
                sensor.myFormatter.format(value),
                application.getString(MyHelper.getShortUnitsId(sensor))
            )
            markerList.add(LocationMarker(pos, getExtremaIcon(sensor, type), title))
        }
    }

    private fun getExtremaIcon(sensor: SensorType, type: ExtremaType): Int {
        return when (sensor) {
            SensorType.ALTITUDE -> if (type == ExtremaType.MAX) { R.drawable.ic_altitude_max} else { R.drawable.ic_altitude_min }
            SensorType.TEMPERATURE -> if (type == ExtremaType.MAX) R.drawable.ic_temp_max else R.drawable.ic_temp_min
            SensorType.HR -> R.drawable.ic_heart_rate
            SensorType.POWER -> R.drawable.ic_power
            SensorType.LINE_DISTANCE_m -> R.drawable.ic_distance
            SensorType.SPEED_mps -> R.drawable.ic_speed
            else -> -1
        }
    }

    private fun getExtremaPos(id: Long, file: String, type: ExtremaType): LatLng? {
        // Try the optimized summary record first
        return summariesDb.getExtremaPosition(id, SensorType.LATITUDE, type)
            ?: summariesDb.getExtremaValue(id, SensorType.LATITUDE, type)?.let { lat ->
                summariesDb.getExtremaValue(id, SensorType.LONGITUDE, type)?.let { lon ->
                    LatLng(lat, lon)
                }
            } ?: samplesDb.calcExtremaValue(summariesDb, file, type, SensorType.LATITUDE)?.let { lat ->
                samplesDb.calcExtremaValue(summariesDb, file, type, SensorType.LONGITUDE)?.let { lon ->
                    LatLng(lat, lon)
                }
            }
    }
}
