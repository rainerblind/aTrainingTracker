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

package com.atrainingtracker.trainingtracker.repositories

import android.content.Context
import androidx.annotation.VisibleForTesting
import com.atrainingtracker.trainingtracker.database.KnownLocationsDatabaseManager
import com.atrainingtracker.trainingtracker.elevation.ElevationResult
import com.atrainingtracker.trainingtracker.elevation.ElevationService
import com.atrainingtracker.trainingtracker.elevation.ElevationSource
import com.atrainingtracker.trainingtracker.location.LocationNameResolver
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

/**
 * Immutable domain representation of a known workout start location.
 *
 * @param id Database primary key identifier.
 * @param name User-editable or reverse-geocoded location name.
 * @param altitude Reference altitude in meters.
 * @param radius Spatial geofence radius in meters (default 200m).
 * @param latLng Geodetic coordinate (latitude, longitude).
 * @param hitCount Visit frequency count.
 * @param isLocked True if altitude is protected from automated overwrite.
 * @param source Provenance of the reference altitude.
 */
data class KnownLocationItem(
    val id: Long,
    val name: String,
    val altitude: Double,
    val radius: Int,
    val latLng: LatLng,
    val hitCount: Int,
    val isLocked: Boolean,
    val source: ElevationSource
)

/**
 * Repository orchestrating access to known workout start locations.
 *
 * Invariants:
 * - Thread Confinement: All SQLite operations are strictly dispatched onto [KnownLocationsDB-Thread]
 *   to eliminate coroutine thread hopping and avoid deadlocks with Java synchronized blocks.
 * - Auto-Locking: Saving a manual altitude edit automatically persists [ElevationSource.MANUAL_USER]
 *   and sets is_locked = 1, ensuring the user is never burdened with manual lock toggling.
 * - Reactive State: Exposes reactive StateFlow of locations for Compose UI screens.
 *
 * Traceability: REQ-UI-165, REQ-DAT-007, REQ-DAT-014, TST-UI-117.
 */
open class KnownLocationsRepository @VisibleForTesting constructor(
    private val context: Context,
    private val databaseManager: KnownLocationsDatabaseManager,
    private val elevationService: ElevationService = ElevationService.getInstance(),
    private val dbDispatcher: CoroutineDispatcher = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "KnownLocationsDB-Thread").apply { isDaemon = true }
    }.asCoroutineDispatcher()
) {
    private val repositoryScope = CoroutineScope(SupervisorJob() + dbDispatcher)

    private val _locations = MutableStateFlow<List<KnownLocationItem>>(emptyList())
    open val locationsFlow: StateFlow<List<KnownLocationItem>> = _locations.asStateFlow()

    init {
        repositoryScope.launch {
            loadLocations()
            healLegacyNames()
        }
    }

    /**
     * Reads all locations from the database on [dbDispatcher] and updates [locationsFlow].
     */
    open suspend fun loadLocations(): List<KnownLocationItem> = withContext(dbDispatcher) {
        val rawLocations = databaseManager.allLocations
        val items = rawLocations.map { loc ->
            KnownLocationItem(
                id = loc.id,
                name = loc.name ?: LocationNameResolver.formatFallback(context, loc.latLng.latitude, loc.latLng.longitude),
                altitude = loc.altitude,
                radius = loc.radius,
                latLng = loc.latLng,
                hitCount = loc.hitCount,
                isLocked = loc.isLocked,
                source = loc.source
            )
        }
        _locations.value = items
        items
    }

    /**
     * Atomically updates a location's name, altitude, and source.
     * When [source] is [ElevationSource.MANUAL_USER], [isLocked] is automatically set to true.
     */
    open suspend fun updateLocation(
        id: Long,
        name: String,
        altitude: Double,
        source: ElevationSource
    ) = withContext(dbDispatcher) {
        val isLocked = (source == ElevationSource.MANUAL_USER)
        databaseManager.updateLocation(id, name, altitude, source, isLocked)
        loadLocations()
    }

    /**
     * Deletes a location by id and refreshes reactive state.
     */
    open suspend fun deleteLocation(id: Long) = withContext(dbDispatcher) {
        databaseManager.deleteId(id)
        loadLocations()
    }

    /**
     * Queries Open-Meteo DEM elevation for the specified location coordinates.
     * On success, persists the DEM elevation with [ElevationSource.INTERNET_DEM] and [isLocked] = false.
     */
    open suspend fun refreshDem(id: Long, latLng: LatLng): ElevationResult = withContext(dbDispatcher) {
        val result = elevationService.fetchElevation(latLng.latitude, latLng.longitude)
        if (result is ElevationResult.Success) {
            val existing = databaseManager.getMyLocation(id)
            val name = if (existing != null && !LocationNameResolver.isPlaceholderName(existing.name)) {
                existing.name
            } else {
                LocationNameResolver.resolveLocationName(context, latLng.latitude, latLng.longitude)
            }
            databaseManager.updateLocation(id, name, result.elevationMeters, ElevationSource.INTERNET_DEM, false)
            loadLocations()
        }
        result
    }

    /**
     * Upgrades any placeholder location names (e.g. "Auto-learned start", "Internet DEM start")
     * to human-readable names resolved via [LocationNameResolver].
     * Custom names (edited by user) are strictly preserved.
     */
    open suspend fun healLegacyNames() = withContext(dbDispatcher) {
        val rawLocations = databaseManager.allLocations
        var changed = false
        for (loc in rawLocations) {
            if (LocationNameResolver.isPlaceholderName(loc.name)) {
                val resolvedName = LocationNameResolver.resolveLocationName(context, loc.latLng.latitude, loc.latLng.longitude)
                if (!LocationNameResolver.isPlaceholderName(resolvedName) && resolvedName != loc.name) {
                    databaseManager.updateLocation(loc.id, resolvedName, loc.altitude, loc.source, loc.isLocked)
                    changed = true
                }
            }
        }
        if (changed) {
            loadLocations()
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: KnownLocationsRepository? = null

        @JvmStatic
        fun getInstance(context: Context): KnownLocationsRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: KnownLocationsRepository(
                    context = context.applicationContext,
                    databaseManager = KnownLocationsDatabaseManager.getInstance(context.applicationContext)
                ).also { INSTANCE = it }
            }
        }

        @VisibleForTesting
        fun resetForTesting(repository: KnownLocationsRepository? = null) {
            INSTANCE = repository
        }
    }
}
