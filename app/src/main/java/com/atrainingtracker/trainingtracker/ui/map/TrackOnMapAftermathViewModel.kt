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
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TrackOnMapAftermathViewModel(application: Application) : AndroidViewModel(application) {

    private val _aftermathState = MutableStateFlow(MapState(isFollowMeEnabled = false))
    val aftermathState = _aftermathState.asStateFlow()

    // TODO: Move to WorkoutRepository.
    private val summariesDb = WorkoutSummariesDatabaseManager.getInstance(application)
    private val samplesDb = WorkoutSamplesDatabaseManager.getInstance(application)

    private val workoutRepository = WorkoutRepository.getInstance(application)

    private val extremaSensorTypes = arrayOf(
        SensorType.ALTITUDE, SensorType.TEMPERATURE,
        SensorType.HR, SensorType.POWER, SensorType.LINE_DISTANCE_m, SensorType.SPEED_mps
    )

    fun loadAftermathData(workoutData: WorkoutData) {
        viewModelScope.launch(Dispatchers.IO) {
            // val baseFileName = summariesDb.getBaseFileName(workoutId) ?: return@launch

            // Get the Sport Type
            // val workout = workoutRepository.getWorkoutById(workoutId).value
            val bSportType = workoutData.bSportType

            // Load Track
            val trackList = TrackType.entries.mapNotNull { type ->
                val path = workoutRepository.getWorkoutTrackPoints(workoutData.id, Roughness.ALL, type)
                if (path.isNotEmpty()) MapTrack(id = type.ordinal.toLong(), type = type, path = path) else null
            }

            // Load Markers
            val markerList = mutableListOf<LocationMarker>()

            // Start/Stop Markers
            workoutData.fileBaseName?.let { getExtremaPos(workoutData.id, it, ExtremaType.START) }?.let {
                markerList.add(LocationMarker(it, R.drawable.control_start, application.getString(R.string.Start)))
            }
            workoutData.fileBaseName?.let { getExtremaPos(workoutData.id, it, ExtremaType.END) }?.let {
                markerList.add(LocationMarker(it, R.drawable.control_stop, application.getString(R.string.Stop)))
            }

            // Sensor Extrema (MAX for all, MIN for specific sensors)
            extremaSensorTypes.forEach { sensor ->
                // Always check for MAX
                addExtremaMarkerIfPresent(workoutData.id, sensor, ExtremaType.MAX, markerList)

                // Additionally check for MIN for Altitude and Temperature
                if (sensor == SensorType.ALTITUDE || sensor == SensorType.TEMPERATURE) {
                    addExtremaMarkerIfPresent(workoutData.id, sensor, ExtremaType.MIN, markerList)
                }
            }

            withContext(Dispatchers.Main) {
                _aftermathState.value = _aftermathState.value.copy(
                    tracks = trackList,
                    markers = markerList,
                    isFollowMeEnabled = false,
                    bSportType = bSportType
                )
            }
        }
    }

    /**
     * Helper to fetch extrema from DB and create a LocationMarker
     */
    private fun addExtremaMarkerIfPresent(
        workoutId: Long,
        sensor: SensorType,
        type: ExtremaType,
        markerList: MutableList<LocationMarker>
    ) {
        val extrema = samplesDb.getExtremaPosition(summariesDb, workoutId, sensor, type)
        extrema?.let {
            val title = application.getString(
                R.string.location_extrema_format,
                type.name, // Will be "MAX" or "MIN"
                sensor.getFullName(application),
                sensor.myFormatter.format(it.value),
                application.getString(MyHelper.getShortUnitsId(sensor))
            )
            markerList.add(LocationMarker(it.latLng, getExtremaIcon(sensor, type), title))
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
        val lat = summariesDb.getExtremaValue(id, SensorType.LATITUDE, type)
            ?: samplesDb.calcExtremaValue(summariesDb, file, type, SensorType.LATITUDE)
        val lon = summariesDb.getExtremaValue(id, SensorType.LONGITUDE, type)
            ?: samplesDb.calcExtremaValue(summariesDb, file, type, SensorType.LONGITUDE)

        return if (lat != null && lon != null) LatLng(lat, lon) else null
    }
}