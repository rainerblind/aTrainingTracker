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

package com.atrainingtracker.trainingtracker.batterysaver

/**
 * Display brightness control modes for active workout tracking.
 * - [SYSTEM]: Uses standard Android system brightness without application override.
 * - [AUTO]: Dynamic display dimming based on slope and heart rate/power zones with touch and event wakeup.
 * - [CUSTOM]: Constant user-selected brightness level (5% - 100%) without event wakeup.
 */
enum class DisplayBrightnessMode(val id: String) {
    SYSTEM("system"),
    AUTO("auto"),
    CUSTOM("custom");

    companion object {
        fun fromId(id: String?): DisplayBrightnessMode =
            entries.find { it.id == id } ?: AUTO
    }
}
