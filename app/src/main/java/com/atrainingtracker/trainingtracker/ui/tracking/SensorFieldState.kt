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
    val viewId: Int,
    val rowNr: Int,
    val colNr: Int,

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