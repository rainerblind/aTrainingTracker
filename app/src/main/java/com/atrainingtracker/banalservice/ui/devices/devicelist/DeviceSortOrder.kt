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

package com.atrainingtracker.banalservice.ui.devices.devicelist

import androidx.annotation.StringRes
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.ui.devices.devicedata.DeviceUiData

/**
 * Defines the user-selectable sorting dimensions available for sensors in the "My Sensors" list.
 *
 * Order of entries dictates display order in the UI sort dropdown:
 * Alphabetical (default), First Seen, Last Usage, Battery Level.
 */
enum class DeviceSortOrder(@StringRes val labelResId: Int) {
    ALPHABETICAL(R.string.devices_sort_alphabetical),
    FIRST_SEEN(R.string.devices_sort_first_seen),
    LAST_USAGE(R.string.devices_sort_last_usage),
    BATTERY_LEVEL(R.string.devices_sort_battery_level);

    companion object {
        /**
         * Parses a lastSeen timestamp string into epoch milliseconds.
         * Returns 0L if the string is null, blank, or cannot be parsed.
         */
        fun parseLastSeenTimestamp(lastSeen: String?): Long {
            return com.atrainingtracker.banalservice.database.LastActiveDateFormatter
                .parseToEpochMillis(lastSeen) ?: 0L
        }

        /**
         * Creates a Comparator for [DeviceUiData] honoring [sortOrder] for non-newly-discovered devices.
         */
        fun getComparator(sortOrder: DeviceSortOrder): Comparator<DeviceUiData> {
            return when (sortOrder) {
                ALPHABETICAL -> compareBy<DeviceUiData, String>(String.CASE_INSENSITIVE_ORDER) { it.deviceName }
                    .thenBy { it.id }

                FIRST_SEEN -> compareByDescending<DeviceUiData> { it.id }

                LAST_USAGE -> compareByDescending<DeviceUiData> { it.isConnected }
                    .thenByDescending { parseLastSeenTimestamp(it.lastSeen) }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.deviceName }
                    .thenBy { it.id }

                BATTERY_LEVEL -> compareBy<DeviceUiData> { if (it.batteryPercentage >= 0) 0 else 1 }
                    .thenBy { if (it.batteryPercentage >= 0) it.batteryPercentage else Int.MAX_VALUE }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.deviceName }
                    .thenBy { it.id }
            }
        }
    }
}
