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

package com.atrainingtracker.trainingtracker.database

/**
 * Enumeration representing the origin source provenance of a workout session (ATT-2186).
 *
 * Distinguishes whether an activity was recorded live on the Android device or imported
 * via an external file format.
 */
enum class WorkoutSource {
    /** Live sensor tracking recorded on the device via TrackerService. */
    TRACKED,

    /** Imported Garmin Training Center XML activity file. */
    TCX,

    /** Imported GPS Exchange Format activity file. */
    GPX,

    /** Imported Garmin Flexible and Interoperable Data Transfer binary activity file. */
    FIT;

    companion object {
        /**
         * Safely parses a string into a [WorkoutSource] instance.
         *
         * @param value The raw string value from database or file metadata.
         * @return The corresponding [WorkoutSource], or [TRACKED] if null, blank, or unrecognized.
         */
        @JvmStatic
        fun fromString(value: String?): WorkoutSource {
            if (value.isNullOrBlank()) {
                return TRACKED
            }
            return try {
                valueOf(value.trim().uppercase())
            } catch (_: IllegalArgumentException) {
                TRACKED
            }
        }
    }
}
