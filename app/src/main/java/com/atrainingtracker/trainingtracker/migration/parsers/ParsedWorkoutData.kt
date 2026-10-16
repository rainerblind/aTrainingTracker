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

package com.atrainingtracker.trainingtracker.migration.parsers

import android.content.ContentValues
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.migration.ParsedLap
import com.google.android.gms.maps.model.LatLng

/**
 * Data transfer object encapsulating all extracted data from a parsed workout file
 * (TCX, GPX, or FIT) before persistence into SQLite databases.
 *
 * Enforces REQ-MIG-038.
 */
data class ParsedWorkoutData(
    val workoutName: String? = null,
    val workoutNotes: String? = null,
    val sportName: String? = null,
    val bSportType: BSportType = BSportType.UNKNOWN,
    val resolvedSportId: Long = -1L,
    val firstTime: String? = null,
    val lastTime: String? = null,
    val points: List<LatLng> = emptyList(),
    val altitudes: List<Double> = emptyList(),
    val distances: List<Double> = emptyList(),
    val minAltVal: Double = Double.MAX_VALUE,
    val minAltPos: LatLng? = null,
    val maxAltVal: Double = -Double.MAX_VALUE,
    val maxAltPos: LatLng? = null,
    val parsedLaps: List<ParsedLap> = emptyList(),
    val bufferedSamples: List<ContentValues> = emptyList(),
    val foundSensors: Set<SensorType> = emptySet()
)
