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

package com.atrainingtracker.trainingtracker.routes

import com.atrainingtracker.trainingtracker.database.KnownLocationsDatabaseManager
import com.atrainingtracker.trainingtracker.database.KnownLocationsDatabaseManager.MyLocation
import com.google.android.gms.maps.model.LatLng

/**
 * Data class representing a resolved Home destination for return navigation (REQ-MAP-029 / ATT-1953).
 */
data class HomeDestination(
    val id: Long,
    val name: String,
    val latLng: LatLng,
    val altitude: Double
)

/**
 * Resolves the athlete's primary Home destination from KnownLocationsDatabaseManager (REQ-MAP-034, REQ-MAP-036).
 *
 * Evaluation Strategy:
 * 1. Explicit user designation: Location where isHome == true.
 * 2. Start Frequency: In the absence of an explicitly designated location, the location with the highest hit count (> 0).
 * 3. Fallback: First location if any exists.
 */
object HomeLocationResolver {

    /**
     * Resolves the effective home location ID from a list of known locations (REQ-MAP-036).
     *
     * Evaluation order:
     * 1. Explicit user designation: first location where [MyLocation.isHome] is true.
     * 2. Highest start frequency: location with maximum [MyLocation.hitCount] where hitCount > 0.
     * 3. Fallback: first location if any exists.
     * Returns null if [locations] is empty.
     */
    @JvmStatic
    fun resolveHomeLocationId(locations: List<MyLocation>): Long? {
        if (locations.isEmpty()) {
            return null
        }
        val designatedHome = locations.firstOrNull { it.isHome }
        if (designatedHome != null) {
            return designatedHome.id
        }
        val highestHitLoc = locations.filter { it.hitCount > 0 }.maxByOrNull { it.hitCount }
        if (highestHitLoc != null) {
            return highestHitLoc.id
        }
        return locations.first().id
    }

    @JvmStatic
    fun resolveHomeLocation(knownLocationsManager: KnownLocationsDatabaseManager): HomeDestination? {
        val allLocations: List<MyLocation> = try {
            knownLocationsManager.allLocations ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }

        if (allLocations.isEmpty()) {
            return null
        }

        val effectiveHomeId = resolveHomeLocationId(allLocations) ?: return null
        val resolvedLoc = allLocations.firstOrNull { it.id == effectiveHomeId } ?: return null
        return toHomeDestination(resolvedLoc)
    }

    private fun toHomeDestination(loc: MyLocation): HomeDestination {
        return HomeDestination(
            id = loc.id,
            name = loc.name ?: "Home",
            latLng = loc.latLng,
            altitude = loc.altitude
        )
    }
}
