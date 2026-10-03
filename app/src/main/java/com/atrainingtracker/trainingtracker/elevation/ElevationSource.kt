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

package com.atrainingtracker.trainingtracker.elevation

/**
 * Enumeration representing the provenance or calibration origin of a location's altitude.
 *
 * Traceability: REQ-DAT-014 (Internet Digital Elevation Model Reference Altitude Retrieval).
 */
enum class ElevationSource {
    /**
     * Learned automatically via the historical moving average algorithm.
     */
    AUTO_LEARNED,

    /**
     * Authoritative ground-truth elevation retrieved from an internet Digital Elevation Model (Open-Meteo).
     */
    INTERNET_DEM,

    /**
     * Manually configured and calibrated by the user (ATT-919).
     */
    MANUAL_USER,

    /**
     * Fallback altitude derived from GPS fix when offline.
     */
    GPS_FALLBACK,

    /**
     * Legacy records stored prior to Schema V5 requiring healing.
     */
    LEGACY_RAW;

    companion object {
        @JvmStatic
        fun fromString(value: String?): ElevationSource {
            if (value == null) return LEGACY_RAW
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: LEGACY_RAW
        }
    }
}
