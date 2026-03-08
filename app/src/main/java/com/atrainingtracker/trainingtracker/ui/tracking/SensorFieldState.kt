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

package com.atrainingtracker.trainingtracker.ui.tracking

import androidx.compose.ui.graphics.Color

/**
 * A simple, plain data class that represents the complete state of a single sensor field on the UI.
 * This is the "model" that the Composables will be fed. It contains no business logic.
 */
data class SensorFieldState(
    // A unique, stable identifier for this specific field configuration.
    // Used to efficiently find and update this state from new sensor data.
    val configHash: Int,

    // The configuration info, needed for actions like long-press to edit.
    val sensorFieldId: Long,
    val rowNr: Int,
    val colNr: Int,

    val viewSize: ViewSize,

    // The main label for the field (e.g., "Pace", "Heart Rate").
    val label: String,

    // The description of the filter (e.g., "5s avg.", "α=0.9").
    val filterDescription: String,

    // The main value, pre-formatted as a String (e.g., "5:30").
    val value: String,

    // The units for the value, also a String (e.g., "/km", "bpm").
    val units: String,

    // The background color, calculated from the training zone.
    val zoneColor: Color
)