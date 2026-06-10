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

package com.atrainingtracker.trainingtracker.ui.components.workoutextrema

import com.google.android.gms.maps.model.LatLng

/**
 * Represents the complete data model for the extrema values section.
 *
 * @param isCalculating True if the background worker is still processing values.
 * @param dataRows The list of individual sensor data rows to display.
 */
data class ExtremaData(
    val workoutId: Long,
    val isCalculating: Boolean,
    val calculationMessage: String? = null,
    val dataRows: List<ExtremaDataRow>
)

/**
 * Represents a single row of extrema data for one sensor.
 *
 * @param sensorLabel The display name of the sensor (e.g., "HR").
 * @param unitLabel The unit of measurement (e.g., "bpm").
 * @param minValue The formatted minimum value, or null if not available.
 * @param avgValue The formatted average value, or null if not available.
 * @param maxValue The formatted maximum value, or null if not available.
 */
data class ExtremaDataRow(
    val sensorLabel: String,
    val unitLabel: String,
    val minValue: String?,
    val minLatLng: LatLng? = null,
    val avgValue: String?,
    val maxValue: String?,
    val maxLatLng: LatLng? = null
) {
    /**
     * Helper to check if any value is present for this sensor.
     * @return true if at least one of min, avg, or max is not null.
     */
    fun hasAnyData(): Boolean {
        return minValue != null || avgValue != null || maxValue != null
    }
}
