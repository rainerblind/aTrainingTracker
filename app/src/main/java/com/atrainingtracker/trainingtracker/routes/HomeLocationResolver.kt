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
 * Resolves the athlete's primary Home destination from KnownLocationsDatabaseManager.
 *
 * Evaluation Strategy:
 * 1. Explicit name match: Any location whose name contains "haus", "home", or "zuhause" (case-insensitive).
 * 2. Start Frequency: In the absence of an explicitly named location, the location with the highest hit count.
 */
object HomeLocationResolver {

    private val HOME_KEYWORDS = listOf("haus", "home", "zuhause")

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

        // 1. Explicit keyword match in name
        val namedMatch = allLocations.firstOrNull { loc ->
            val name = loc.name?.trim()?.lowercase() ?: ""
            HOME_KEYWORDS.any { keyword -> name.contains(keyword) }
        }
        if (namedMatch != null) {
            return toHomeDestination(namedMatch)
        }

        // 2. Highest hitCount among all locations (primary start base)
        val highestHitLoc = allLocations.maxByOrNull { it.hitCount }
        if (highestHitLoc != null && highestHitLoc.hitCount > 0) {
            return toHomeDestination(highestHitLoc)
        }

        // Fallback to the first location if any exists
        return toHomeDestination(allLocations.first())
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
