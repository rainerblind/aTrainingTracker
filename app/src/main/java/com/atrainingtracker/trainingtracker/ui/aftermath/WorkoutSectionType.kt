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

import androidx.annotation.StringRes
import com.atrainingtracker.R

/**
 * Context for rendering workout sections (REQ-UI-255 / ATT-2176).
 */
enum class WorkoutDisplayContext {
    LIST_CARD,
    FULL_DETAIL
}

/**
 * Enumeration of customizable workout sections displayed in workout summary cards
 * and detailed aftermath views (REQ-UI-255 / ATT-2176).
 */
enum class WorkoutSectionType(@StringRes val titleRes: Int) {
    DESCRIPTION(R.string.settings_workout_card_description),
    EXTREMA(R.string.settings_workout_card_extrema),
    LAPS(R.string.settings_workout_card_laps),
    STRAVA(R.string.settings_workout_card_strava),
    MAP(R.string.settings_workout_card_map),
    ELEVATION(R.string.settings_workout_card_elevation),
    CHARTS(R.string.settings_workout_card_charts),
    ZONES(R.string.settings_workout_card_zones);

    companion object {
        val DEFAULT_ORDER: List<WorkoutSectionType> = listOf(
            DESCRIPTION,
            EXTREMA,
            LAPS,
            STRAVA,
            MAP,
            ELEVATION,
            CHARTS,
            ZONES
        )

        /**
         * Deserializes a comma-separated string into a list of [WorkoutSectionType]s.
         * Falls back safely to [DEFAULT_ORDER] if the string is blank or invalid.
         * Appends any missing enum values to ensure self-healing completeness.
         */
        fun fromSerializedString(serialized: String?): List<WorkoutSectionType> {
            if (serialized.isNullOrBlank()) return DEFAULT_ORDER
            val parsed = serialized.split(",")
                .mapNotNull { name ->
                    runCatching { valueOf(name.trim()) }.getOrNull()
                }
                .distinct()
            if (parsed.isEmpty()) return DEFAULT_ORDER

            // Append any missing enum values to maintain self-healing complete list
            val missing = values().filter { !parsed.contains(it) }
            return parsed + missing
        }

        /**
         * Serializes a list of [WorkoutSectionType]s into a comma-separated string.
         */
        fun toSerializedString(order: List<WorkoutSectionType>): String {
            return order.joinToString(",") { it.name }
        }
    }
}
