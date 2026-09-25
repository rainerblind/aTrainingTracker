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

package com.atrainingtracker.trainingtracker.ui.knownlocations

import java.util.Locale
import kotlin.math.round

/**
 * Unit conversion and formatting utilities for known start locations.
 *
 * Standards:
 * - Metric Display: 0.1m resolution when decimal, integer meters when whole.
 * - Imperial Display: Nearest whole foot ($1\text{m} = \frac{1}{0.3048}\text{ft}$).
 * - Imperial to Metric Save: $h_{\text{m}} = \text{round}(h_{\text{ft}} \times 0.3048 \times 10.0) / 10.0$.
 *
 * Traceability: REQ-UI-165, TST-UI-117.5.
 */
object KnownLocationsUnitConversions {
    const val FEET_TO_METERS = 0.3048

    /**
     * Converts meters to feet rounded to the nearest whole foot.
     */
    fun metersToFeet(meters: Double): Long {
        return round(meters / FEET_TO_METERS).toLong()
    }

    /**
     * Converts feet to meters rounded to 0.1m precision for persistent storage.
     */
    fun feetToMeters(feet: Double): Double {
        return round(feet * FEET_TO_METERS * 10.0) / 10.0
    }

    /**
     * Formats altitude for UI cards and markers based on metric/imperial preference.
     */
    fun formatAltitude(meters: Double, isMetric: Boolean): String {
        return if (isMetric) {
            val rounded = round(meters * 10.0) / 10.0
            if (rounded % 1.0 == 0.0) {
                "${rounded.toInt()} m"
            } else {
                String.format(Locale.US, "%.1f m", rounded)
            }
        } else {
            val feet = metersToFeet(meters)
            "$feet ft"
        }
    }

    /**
     * Formats altitude for editable text input (without unit suffix).
     */
    fun formatAltitudeForEdit(meters: Double, isMetric: Boolean): String {
        return if (isMetric) {
            val rounded = round(meters * 10.0) / 10.0
            if (rounded % 1.0 == 0.0) {
                "${rounded.toInt()}"
            } else {
                String.format(Locale.US, "%.1f", rounded)
            }
        } else {
            "${metersToFeet(meters)}"
        }
    }

    /**
     * Parses user input text into meters, handling decimal commas and points.
     * Returns null if string is not a valid positive number.
     */
    fun parseInputToMeters(input: String, isMetric: Boolean): Double? {
        val sanitized = input.trim().replace(',', '.')
        val parsed = sanitized.toDoubleOrNull() ?: return null
        if (parsed.isNaN() || parsed.isInfinite() || parsed < -500.0 || parsed > 9000.0) {
            return null
        }
        return if (isMetric) {
            round(parsed * 10.0) / 10.0
        } else {
            feetToMeters(parsed)
        }
    }
}
