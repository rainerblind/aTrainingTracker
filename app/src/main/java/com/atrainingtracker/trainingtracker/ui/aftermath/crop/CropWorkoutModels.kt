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

package com.atrainingtracker.trainingtracker.ui.aftermath.crop

import androidx.compose.runtime.Immutable
import com.google.android.gms.maps.model.LatLng

/**
 * Lightweight point model containing minimal telemetry needed for crop operations and visualization.
 */
@Immutable
data class CropSamplePoint(
    val rowId: Long,
    val index: Int,
    val timeStr: String?,
    val timeTotalS: Long,
    val timeActiveS: Long,
    val distanceTotalM: Double,
    val latLng: LatLng?,
    val altitude: Double?,
    val lapNr: Long
)

/**
 * UI State for the Crop Workout subview / screen.
 */
@Immutable
data class CropWorkoutUiState(
    val isLoading: Boolean = true,
    val isCropping: Boolean = false,
    val errorMessageResId: Int? = null,
    val workoutName: String = "",
    val totalPoints: Int = 0,
    val startIndex: Int = 0,
    val endIndex: Int = 0,
    val originalDistanceM: Double = 0.0,
    val originalDurationS: Long = 0L,
    val croppedDistanceM: Double = 0.0,
    val croppedDurationS: Long = 0L,
    val croppedAvgSpeedMps: Double = 0.0,
    val points: List<CropSamplePoint> = emptyList(),
    val isValid: Boolean = false
)
