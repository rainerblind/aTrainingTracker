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
import android.util.Log
import androidx.activity.result.launch
import androidx.compose.animation.core.copy
import androidx.lifecycle.application
import androidx.lifecycle.viewModelScope
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.MyHelper
import com.atrainingtracker.trainingtracker.database.ExtremaType
import com.atrainingtracker.trainingtracker.database.WorkoutSamplesDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.ui.tracking.tracking.TrackingMapViewModel
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TrackOnMapAftermathViewModel(application: Application) : TrackingMapViewModel(application) {

    private val _aftermathState = MutableStateFlow(MapState(isFollowMeEnabled = false))
    val aftermathState = _aftermathState.asStateFlow()

    private val summariesDb = WorkoutSummariesDatabaseManager.getInstance(application)
    private val samplesDb = WorkoutSamplesDatabaseManager.getInstance(application)

    private val extremaSensorTypes = arrayOf(
        SensorType.ALTITUDE, SensorType.TEMPERATURE,
        SensorType.HR, SensorType.POWER, SensorType.LINE_DISTANCE_m, SensorType.SPEED_mps
    )

    fun loadAftermathData(workoutId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val baseFileName = summariesDb.getBaseFileName(workoutId) ?: return@launch
            val tableName = WorkoutSamplesDatabaseManager.getTableName(baseFileName)

            // 1. Load Tracks
            val trackList = TrackType.entries.mapNotNull { type ->
                val path = fetchPath(tableName, type)
                if (path.isNotEmpty()) MapTrack(id = type.ordinal.toLong(), type = type, path = path) else null
            }

            // 2. Load Markers
            val markerList = mutableListOf<LocationMarker>()

            // Start/Stop Markers
            getExtremaPos(workoutId, baseFileName, ExtremaType.START)?.let {
                markerList.add(LocationMarker(it, R.drawable.control_start, getApplication<Application>().getString(R.string.Start)))
            }
            getExtremaPos(workoutId, baseFileName, ExtremaType.END)?.let {
                markerList.add(LocationMarker(it, R.drawable.control_stop, getApplication<Application>().getString(R.string.Stop)))
            }

            // Sensor Extrema (MAX for all, MIN for specific sensors)
            extremaSensorTypes.forEach { sensor ->
                // Always check for MAX
                addExtremaMarkerIfPresent(workoutId, sensor, ExtremaType.MAX, markerList)

                // Additionally check for MIN for Altitude and Temperature
                if (sensor == SensorType.ALTITUDE || sensor == SensorType.TEMPERATURE) {
                    addExtremaMarkerIfPresent(workoutId, sensor, ExtremaType.MIN, markerList)
                }
            }

            withContext(Dispatchers.Main) {
                _aftermathState.value = _aftermathState.value.copy(
                    tracks = trackList,
                    markers = markerList,
                    isFollowMeEnabled = false
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
            val title = getApplication<Application>().getString(
                R.string.location_extrema_format,
                type.name, // Will be "MAX" or "MIN"
                sensor.getFullName(getApplication()),
                sensor.myFormatter.format(it.value),
                getApplication<Application>().getString(MyHelper.getShortUnitsId(sensor))
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


    private fun fetchPath(tableName: String, type: TrackType): List<LatLng> {
        val latCol = type.latitudeColumn
        val lonCol = type.longitudeColumn

        // --- NEW: Safety check to prevent SQLiteException ---
        if (!columnExists(tableName, latCol) || !columnExists(tableName, lonCol)) {
            Log.w("TrackOnMapAftermath", "Skipping $type: Columns $latCol/$lonCol not found in $tableName")
            return emptyList()
        }

        val path = mutableListOf<LatLng>()
        samplesDb.database.query(tableName, arrayOf(latCol, lonCol), "$latCol IS NOT NULL", null, null, null, null).use { cursor ->
            val latIdx = cursor.getColumnIndex(latCol)
            val lonIdx = cursor.getColumnIndex(lonCol)

            while (cursor.moveToNext()) {
                path.add(LatLng(cursor.getDouble(latIdx), cursor.getDouble(lonIdx)))
            }
        }
        return path
    }

    /**
     * Helper to verify if a column exists in a specific table
     */
    private fun columnExists(tableName: String, columnName: String): Boolean {
        samplesDb.database.rawQuery("PRAGMA table_info($tableName)", null).use { cursor ->
            val nameIdx = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) {
                if (cursor.getString(nameIdx).equals(columnName, ignoreCase = true)) return true
            }
        }
        return false
    }

    private fun getExtremaPos(id: Long, file: String, type: ExtremaType): LatLng? {
        val lat = summariesDb.getExtremaValue(id, SensorType.LATITUDE, type)
            ?: samplesDb.calcExtremaValue(summariesDb, file, type, SensorType.LATITUDE)
        val lon = summariesDb.getExtremaValue(id, SensorType.LONGITUDE, type)
            ?: samplesDb.calcExtremaValue(summariesDb, file, type, SensorType.LONGITUDE)

        return if (lat != null && lon != null) LatLng(lat, lon) else null
    }
}