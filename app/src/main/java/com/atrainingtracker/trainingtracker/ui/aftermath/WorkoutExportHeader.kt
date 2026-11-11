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

package com.atrainingtracker.trainingtracker.ui.aftermath

import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.database.DatabaseConstants

/**
 * Encapsulated domain DTO for workout export headers (REQ-DAT-042.3).
 */
data class WorkoutExportHeader(
    val workoutId: Long = DatabaseConstants.INVALID_ID,
    val fileBaseName: String = "",
    val startTime: String = "",
    val totalTime: String = "0",
    val data: String = "--------",
    val goal: String = "",
    val method: String = "",
    val totalDistance: String = "0",
    val description: String = "",
    val workoutName: String = "",
    val sportTypeId: Long = DatabaseConstants.INVALID_ID,
    val indoorTrainerSession: Boolean = false,
    val calories: Int? = null,
    val totalAscent: Int? = null,
    val totalDescent: Int? = null,
    val avgSpeedMps: Double? = null
)

/**
 * Domain record representing an individual sample during workout stream export (REQ-DAT-042.2).
 */
data class WorkoutSampleRecord(
    val time: String? = null,
    val lapNr: Int = 0,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val altitude: Double? = null,
    val distanceM: Double? = null,
    val speedMps: Double? = null,
    val hr: Int? = null,
    val cadence: Double? = null,
    val power: Double? = null,
    val temperature: Double? = null
)

/**
 * Functional streaming consumer for sample iteration (REQ-DAT-042.2).
 */
fun interface SampleConsumer {
    fun accept(sample: WorkoutSampleRecord)
}

/**
 * Encapsulated metadata DTO for workout summary updates (REQ-DAT-042.3).
 */
data class WorkoutSummaryUpdateData(
    val sportId: Long,
    val workoutName: String,
    val description: String,
    val trainer: Boolean,
    val commute: Boolean,
    val race: Boolean,
    val bSportString: String,
    val bSportType: BSportType,
    val equipmentId: Long?
)

/**
 * Encapsulated metadata DTO for workout duplicate checks (REQ-DAT-042.3).
 */
data class WorkoutDuplicateMetadata(
    val timeStartStr: String?,
    val startEpochSeconds: Long?,
    val bSportType: BSportType?,
    val durationSeconds: Long?
)

/**
 * Encapsulated sample data collections for physiology computation (REQ-DAT-042.4).
 */
data class PowerSpeedHrSamples(
    val powerSamples: List<Double?>,
    val speedSamples: List<Double?>,
    val hrSamples: List<Double?>
)
