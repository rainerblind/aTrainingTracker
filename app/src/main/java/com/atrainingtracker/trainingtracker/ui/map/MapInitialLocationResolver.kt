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

package com.atrainingtracker.trainingtracker.ui.map

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.atrainingtracker.trainingtracker.database.KnownLocationsDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.repositories.KnownLocationsRepository
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository
import com.google.android.gms.maps.model.LatLng

/**
 * Resolves initial map camera coordinates through a prioritized defensive fallback cascade (REQ-MAP-059.1).
 *
 * Cascade Priority:
 * 1. Live GPS fix ([liveLocation])
 * 2. System LocationManager last known location (GPS, Network, Passive) with permission checks
 * 3. Designated Home Base known location
 * 4. Most visited known location (argmax hitCount)
 * 5. Most recent workout start location
 * 6. Default fallback coordinates (Munich, 48.137154, 11.576124)
 */
object MapInitialLocationResolver {

    val DEFAULT_FALLBACK_LOCATION = LatLng(48.137154, 11.576124)
    const val DEFAULT_INITIAL_ZOOM = 14.0f

    fun resolveInitialLocation(
        context: Context,
        liveLocation: LatLng? = null,
        knownLocationsDb: KnownLocationsDatabaseManager? = null,
        workoutSummariesDb: WorkoutSummariesDatabaseManager? = null
    ): LatLng {
        // 1. Live GPS fix
        if (liveLocation != null && isValidCoordinate(liveLocation)) {
            return liveLocation
        }

        // 2. System LocationManager last known location
        val systemLocation = getSystemLastKnownLocation(context)
        if (systemLocation != null && isValidCoordinate(systemLocation)) {
            return systemLocation
        }

        // 3. Designated Home Base known location
        try {
            val home = if (knownLocationsDb != null) {
                knownLocationsDb.homeLocation
            } else {
                KnownLocationsRepository.getInstance(context).homeLocation
            }
            if (home != null && isValidCoordinate(home.latLng)) {
                return home.latLng
            }
        } catch (_: Throwable) {
        }

        // 4. Most visited known location
        try {
            val allLocations = if (knownLocationsDb != null) {
                knownLocationsDb.allLocations
            } else {
                KnownLocationsRepository.getInstance(context).allLocations
            }
            val mostVisited = allLocations.filter { isValidCoordinate(it.latLng) }.maxByOrNull { it.hitCount }
            if (mostVisited != null) {
                return mostVisited.latLng
            }
        } catch (_: Throwable) {
        }

        // 5. Most recent workout start location
        try {
            val starts = if (workoutSummariesDb != null) {
                workoutSummariesDb.allWorkoutStartLocations
            } else {
                WorkoutRepository.getInstance(context).allWorkoutStartLocations
            }
            val lastStart = starts.lastOrNull { isValidCoordinate(it) }
            if (lastStart != null) {
                return lastStart
            }
        } catch (_: Throwable) {
        }

        // 6. Default fallback
        return DEFAULT_FALLBACK_LOCATION
    }

    fun isValidCoordinate(latitude: Double, longitude: Double): Boolean {
        if (latitude.isNaN() || longitude.isNaN()) return false
        if (latitude == 0.0 && longitude == 0.0) return false
        return latitude in -90.0..90.0 && longitude in -180.0..180.0
    }

    fun isValidCoordinate(latLng: LatLng?): Boolean {
        if (latLng == null) return false
        return isValidCoordinate(latLng.latitude, latLng.longitude)
    }

    private fun getSystemLastKnownLocation(context: Context): LatLng? {
        try {
            val fineGranted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            val coarseGranted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            if (!fineGranted && !coarseGranted) return null

            val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
            val providers = listOf(
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER,
                LocationManager.PASSIVE_PROVIDER
            )

            for (provider in providers) {
                try {
                    if (lm.isProviderEnabled(provider)) {
                        val loc = lm.getLastKnownLocation(provider)
                        if (loc != null && (loc.latitude != 0.0 || loc.longitude != 0.0)) {
                            return LatLng(loc.latitude, loc.longitude)
                        }
                    }
                } catch (_: SecurityException) {
                } catch (_: Exception) {
                }
            }
        } catch (_: Throwable) {
        }
        return null
    }
}
