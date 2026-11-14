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
 * REQ-SEN-029.1: Enum representing the mounting position of a wheel speed sensor on a bike.
 */
enum class WheelPosition {
    FRONT,
    REAR,
    UNSPECIFIED;

    companion object {
        @JvmStatic
        fun fromString(value: String?): WheelPosition {
            if (value == null) return UNSPECIFIED
            return try {
                valueOf(value.trim().uppercase())
            } catch (_: IllegalArgumentException) {
                UNSPECIFIED
            }
        }
    }
}
