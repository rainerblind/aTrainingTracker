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
 */

package com.atrainingtracker.trainingtracker.ui.knownlocations

import androidx.annotation.StringRes
import com.atrainingtracker.R

/**
 * Defines the sorting dimensions available for Known Start Locations (Lieblingsorte).
 * Order of entries dictates the display order in the UI sort dropdown (Name is last).
 */
enum class KnownLocationSortOrder(@StringRes val labelResId: Int) {
    STARTS(R.string.known_locations_sort_starts),
    DISTANCE_TO_USER(R.string.sort_closest),
    ALTITUDE(R.string.sort_altitude),
    NAME(R.string.sort_name)
}
