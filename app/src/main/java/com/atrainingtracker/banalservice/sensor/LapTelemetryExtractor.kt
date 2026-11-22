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

package com.atrainingtracker.banalservice.sensor

/**
 * Immutable snapshot of lap telemetry extracted safely from [SensorData] readings
 * (REQ-TRK-094.1, TST-TRK-094.1, ATT-6034).
 */
data class LapTelemetrySnapshot(
    @JvmField val lapNr: Int,
    @JvmField val lapTimeSeconds: Int,
    @JvmField val lapTimeDisplay: String,
    @JvmField val lapDistanceMeters: Double,
    @JvmField val lapDistanceDisplay: String,
    @JvmField val lapSpeedMps: Double
)

/**
 * Type-safe, finite-guarded lap telemetry extractor shared by `BANALService.newLap()`,
 * `TrackerService.createNewLap()`, and `TrackerService.BanalFinalizationSnapshot.capture()`
 * (REQ-TRK-094.1, ATT-6034).
 */
object LapTelemetryExtractor {
    const val DEFAULT_LAP_TIME_DISPLAY = "??:??:??"
    const val DEFAULT_LAP_DISTANCE_DISPLAY = "0"

    @JvmStatic
    @JvmOverloads
    fun extract(
        lapNrData: SensorData<*>?,
        lapTimeData: SensorData<*>?,
        lapDistanceData: SensorData<*>?,
        includeDisplayStrings: Boolean = true
    ): LapTelemetrySnapshot {
        val lapNr = extractFiniteInt(lapNrData?.value)

        val lapTimeValue = lapTimeData?.value
        val lapTimeFinite = isFiniteNumber(lapTimeValue)
        val lapTimeSeconds = if (lapTimeFinite) (lapTimeValue as Number).toInt() else 0
        val lapTimeDisplay = if (includeDisplayStrings && lapTimeFinite) {
            lapTimeData?.stringValue ?: DEFAULT_LAP_TIME_DISPLAY
        } else {
            DEFAULT_LAP_TIME_DISPLAY
        }

        val lapDistanceValue = lapDistanceData?.value
        val lapDistanceFinite = isFiniteNumber(lapDistanceValue)
        val lapDistanceMeters = if (lapDistanceFinite) (lapDistanceValue as Number).toDouble() else 0.0
        val lapDistanceDisplay = if (includeDisplayStrings && lapDistanceFinite) {
            lapDistanceData?.stringValue ?: DEFAULT_LAP_DISTANCE_DISPLAY
        } else {
            DEFAULT_LAP_DISTANCE_DISPLAY
        }

        val rawSpeed = if (lapTimeSeconds > 0) lapDistanceMeters / lapTimeSeconds else 0.0
        val lapSpeedMps = if (rawSpeed.isFinite()) rawSpeed else 0.0

        return LapTelemetrySnapshot(
            lapNr = lapNr,
            lapTimeSeconds = lapTimeSeconds,
            lapTimeDisplay = lapTimeDisplay,
            lapDistanceMeters = lapDistanceMeters,
            lapDistanceDisplay = lapDistanceDisplay,
            lapSpeedMps = lapSpeedMps
        )
    }

    private fun isFiniteNumber(value: Any?): Boolean {
        val number = value as? Number ?: return false
        return number.toDouble().isFinite()
    }

    private fun extractFiniteInt(value: Any?): Int {
        val number = value as? Number ?: return 0
        if (!number.toDouble().isFinite()) return 0
        return number.toInt()
    }
}
