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
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.settings.TuningConfig
import com.atrainingtracker.trainingtracker.settings.ZoneDisplayOptions

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
    val zoneColor: Color,
    // The presentation cues for training zone accenting.
    val zoneDisplayOptions: ZoneDisplayOptions = ZoneDisplayOptions(),

    // The optional state of the horizontal training zone gauge bar (REQ-UI-376).
    val zoneGaugeState: ZoneGaugeState? = null,

    // The optional state of the visual pedal power balance indicator bar (REQ-UI-377).
    val pedalBalanceState: PedalBalanceState? = null,

    // The canonical sensor type for dynamic tuning visibility gating (REQ-UI-409.3).
    val sensorType: SensorType? = null
)

/**
 * Evaluates whether a given [sensorType] is visible under the active [tuningConfig] (REQ-UI-409.3).
 *
 * - Coggan metrics ([SensorType.NORMALIZED_POWER], [SensorType.INTENSITY_FACTOR], [SensorType.TSS])
 *   are visible only when [TuningConfig.enableCogganPowerMetrics] is `true`.
 * - Skiba Critical Power / W'bal ([SensorType.W_PRIME_BAL]) is visible only when
 *   [TuningConfig.enableCriticalPowerModel] is `true`.
 * - When [tuningConfig] is `null` (e.g. during cold app boot), falls back to [TuningConfig] defaults.
 */
fun isSensorTypeVisibleForTuning(
    sensorType: SensorType?,
    tuningConfig: TuningConfig? = null
): Boolean {
    if (sensorType == null) return true
    val effectiveConfig = tuningConfig ?: TuningConfig()
    return when (sensorType) {
        SensorType.NORMALIZED_POWER,
        SensorType.INTENSITY_FACTOR,
        SensorType.TSS -> effectiveConfig.enableCogganPowerMetrics
        SensorType.W_PRIME_BAL -> effectiveConfig.enableCriticalPowerModel
        else -> true
    }
}

/**
 * Pure view-only filter for [fields] based on [tuningConfig] and [screenMode] (REQ-UI-409.3).
 *
 * In [ScreenMode.CONFIGURATION], all fields remain visible so layout editing never hides persisted
 * database rows or corrupts row/column indices.
 */
fun filterVisibleFieldsForTuning(
    fields: List<SensorFieldState>,
    tuningConfig: TuningConfig? = null,
    screenMode: ScreenMode = ScreenMode.TRACKING
): List<SensorFieldState> {
    if (screenMode == ScreenMode.CONFIGURATION) {
        return fields
    }
    return fields.filter { isSensorTypeVisibleForTuning(it.sensorType, tuningConfig) }
}

/**
 * Pure view-only row-packing projection for cockpit rendering (REQ-UI-409.3).
 *
 * Groups visible fields by their persisted [SensorFieldState.rowNr] in ascending order and sorts
 * each non-empty row's fields by [SensorFieldState.colNr] ascending.
 *
 * Strict View-Only Invariant: This function never mutates [SensorFieldState.rowNr] or
 * [SensorFieldState.colNr] and never writes to SQLite, guaranteeing 100% round-trip restoration
 * when a physiological model toggle is switched OFF and back ON.
 */
fun packVisibleRowsForTuning(
    fields: List<SensorFieldState>,
    tuningConfig: TuningConfig? = null,
    screenMode: ScreenMode = ScreenMode.TRACKING
): List<List<SensorFieldState>> {
    val visible = filterVisibleFieldsForTuning(fields, tuningConfig, screenMode)
    if (visible.isEmpty()) return emptyList()
    return visible
        .groupBy { it.rowNr }
        .toSortedMap()
        .values
        .map { rowFields -> rowFields.sortedBy { it.colNr } }
}