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

import androidx.compose.runtime.Immutable

/**
 * Immutable domain model representing an individual lap / split within a workout session.
 *
 * @property id The SQLite primary key (_id) in the Laps table.
 * @property workoutId The associated workout ID.
 * @property lapNr The sequential lap number assigned during recording or import.
 * @property timeStart The ISO start timestamp of the lap, if available.
 * @property timeTotalS The active duration of the lap in seconds.
 * @property distanceTotalM The total distance traversed during the lap in meters.
 * @property speedAverageMps The average speed of the lap in meters per second.
 * @property name An optional custom name for the lap (e.g. "Warmup", "Hill Sprint").
 * @property description An optional short description or personal notes for the lap.
 */
@Immutable
data class LapData(
    val id: Long = 0L,
    val workoutId: Long,
    val lapNr: Long,
    val timeStart: String? = null,
    val timeTotalS: Int,
    val distanceTotalM: Double,
    val speedAverageMps: Double,
    val name: String? = null,
    val description: String? = null
) {
    /**
     * Resolves the user-facing title for the lap:
     * Returns the custom name if non-blank; otherwise falls back to "Lap $fallbackIndex".
     */
    fun getDisplayName(fallbackIndex: Int): String {
        return if (!name.isNullOrBlank()) {
            name
        } else {
            "Lap $fallbackIndex"
        }
    }
}
