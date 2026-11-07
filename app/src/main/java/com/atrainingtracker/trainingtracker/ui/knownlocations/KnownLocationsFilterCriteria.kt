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

import androidx.compose.runtime.Immutable
import com.atrainingtracker.trainingtracker.repositories.KnownLocationItem

/**
 * Immutable domain model representing multi-dimensional filter criteria for Known Locations (Lieblingsorte) (REQ-UI-466).
 *
 * Encapsulates filter dimensions:
 * - [query]: Case-insensitive substring match against location name or coordinates.
 * - [minStarts]: Minimum effective start count threshold (defaults to 5 on screen entry, null when cleared).
 */
@Immutable
data class KnownLocationsFilterCriteria(
    val query: String = "",
    val minStarts: Int? = 5
) {
    /**
     * Total count of distinct active filter dimensions.
     */
    val activeFilterCount: Int
        get() {
            var count = 0
            if (query.isNotBlank()) count++
            if (minStarts != null) count++
            return count
        }

    /**
     * Returns true if no filter dimensions are actively constrained.
     */
    val isEmpty: Boolean
        get() = query.isBlank() && minStarts == null

    /**
     * Returns true if at least one filter dimension is active.
     */
    val isNotEmpty: Boolean
        get() = !isEmpty

    /**
     * Predicate evaluating whether a [KnownLocationItem] matches this filter criteria
     * against its effective start count.
     */
    fun matches(item: KnownLocationItem, effectiveStarts: Int): Boolean {
        if (minStarts != null && effectiveStarts < minStarts) {
            return false
        }
        val trimmed = query.trim().lowercase()
        if (trimmed.isEmpty()) return true
        return item.name.lowercase().contains(trimmed) ||
            item.latLng.latitude.toString().contains(trimmed) ||
            item.latLng.longitude.toString().contains(trimmed)
    }
}
