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
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.atrainingtracker.trainingtracker.location

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.annotation.WorkerThread
import com.atrainingtracker.trainingtracker.database.KnownLocationsDatabaseManager
import com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.SpatialRegionEngine
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

/**
 * Resolves human-readable place names for geographic activity region centers during training periods (ATT-4934).
 *
 * Implements a deterministic 3-tier fallback hierarchy (REQ-PER-026.1):
 * 1. Priority 1: Nearest non-placeholder Known Location within 10 km.
 * 2. Priority 2: Android Geocoder macro-locality (locality ?: subLocality ?: subAdminArea).
 * 3. Priority 3: Localized fallback "Region n".
 *
 * Employs two-tier caching (REQ-PER-026.3) to prevent main-thread I/O and repeated geocoding during list scroll:
 * - Level 1: In-memory [ConcurrentHashMap] keyed by quantized coordinate cell (~4.4 km).
 * - Level 2: Persistent [android.content.SharedPreferences] cache keyed by quantized coordinate cell.
 */
object RegionNameResolver {
    private const val TAG = "RegionNameResolver"
    const val PREFS_FILE = "period_region_names"
    const val KNOWN_LOCATION_MAX_DISTANCE_METERS = 10_000.0 // 10 km
    private const val QUANTIZATION_DEGREE_STEP = 0.04 // ~4.4 km cell

    private val memoryCache = ConcurrentHashMap<String, String>()

    @VisibleForTesting
    var knownLocationOverride: ((Context, LatLng) -> String?)? = null

    @VisibleForTesting
    var geocoderOverride: ((Context, LatLng, Locale) -> String?)? = null

    /**
     * Quantizes a latitude or longitude coordinate into a discrete integer grid index (~4.4 km).
     */
    fun quantizeCoordinate(coord: Double): Int = (coord / QUANTIZATION_DEGREE_STEP).roundToInt()

    /**
     * Builds a spatial cache key for the given coordinate pair.
     */
    fun cacheKey(lat: Double, lng: Double): String =
        "${quantizeCoordinate(lat)}_${quantizeCoordinate(lng)}"

    /**
     * Clears in-memory and persistent caches (primarily for unit test verification).
     */
    @VisibleForTesting
    fun clearCacheForTesting(context: Context? = null) {
        memoryCache.clear()
        knownLocationOverride = null
        geocoderOverride = null
        context?.let {
            try {
                it.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE).edit().clear().apply()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to clear SharedPreferences cache: ${e.message}")
            }
        }
    }

    /**
     * Asynchronously resolves a semantic place name for the given region center coordinates.
     *
     * @param context Android application context for DB and Geocoder access.
     * @param center Geographic center of the spatial region.
     * @param locale Desired locale for reverse geocoding (defaults to system default).
     * @return Resolved place name (e.g. "Schönaich"), or null if no name could be resolved.
     */
    suspend fun resolvePlaceName(
        context: Context,
        center: LatLng,
        locale: Locale = Locale.getDefault(),
        dispatcher: CoroutineDispatcher = Dispatchers.IO
    ): String? = withContext(dispatcher) {
        val key = cacheKey(center.latitude, center.longitude)

        // 1. Check memory cache (Level 1)
        memoryCache[key]?.let { return@withContext it }

        // 2. Check persistent SharedPreferences cache (Level 2)
        getFromPreferences(context, key)?.let {
            memoryCache[key] = it
            return@withContext it
        }

        // 3. Priority 1: Known Location within 10 km
        val knownName = findNearestKnownLocationName(context, center)
        if (!knownName.isNullOrBlank()) {
            memoryCache[key] = knownName
            putToPreferences(context, key, knownName)
            return@withContext knownName
        }

        // 4. Priority 2: Android Geocoder macro-locality
        val geocodedName = reverseGeocodeMacroLocality(context, center, locale)
        if (!geocodedName.isNullOrBlank()) {
            memoryCache[key] = geocodedName
            putToPreferences(context, key, geocodedName)
            return@withContext geocodedName
        }

        // Priority 3: Fallback (returns null so UI formats generic Region n)
        null
    }

    /**
     * Resolves a region display label, returning the resolved place name or falling back to "Region $index".
     */
    suspend fun resolveRegionName(
        context: Context,
        center: LatLng,
        index: Int,
        locale: Locale = Locale.getDefault(),
        dispatcher: CoroutineDispatcher = Dispatchers.IO
    ): String {
        return resolvePlaceName(context, center, locale, dispatcher) ?: "Region $index"
    }

    /**
     * Queries [KnownLocationsDatabaseManager] for the nearest non-placeholder location within 10 km.
     */
    @WorkerThread
    fun findNearestKnownLocationName(
        context: Context,
        center: LatLng,
        maxDistanceMeters: Double = KNOWN_LOCATION_MAX_DISTANCE_METERS
    ): String? {
        knownLocationOverride?.let { return it(context, center) }

        return try {
            val manager = KnownLocationsDatabaseManager.getInstance(context)
            val locations = manager.allLocations
            var bestName: String? = null
            var minDistance = maxDistanceMeters

            for (loc in locations) {
                val name = loc.name?.trim() ?: continue
                if (LocationNameResolver.isPlaceholderName(name)) continue
                val dist = SpatialRegionEngine.distanceBetween(center, loc.latLng)
                if (dist <= minDistance) {
                    minDistance = dist
                    bestName = name
                }
            }
            bestName
        } catch (e: Throwable) {
            Log.w(TAG, "Error querying KnownLocationsDatabaseManager: ${e.message}")
            null
        }
    }

    /**
     * Reverse-geocodes coordinates via [Geocoder] to extract macro-locality.
     */
    @WorkerThread
    fun reverseGeocodeMacroLocality(
        context: Context,
        center: LatLng,
        locale: Locale = Locale.getDefault()
    ): String? {
        geocoderOverride?.let { return it(context, center, locale) }

        return try {
            if (!Geocoder.isPresent()) return null
            val geocoder = Geocoder(context, locale)
            val addresses = getFromLocationCompat(geocoder, center.latitude, center.longitude, 1)
            if (!addresses.isNullOrEmpty()) {
                extractMacroLocality(addresses[0])
            } else {
                null
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to reverse geocode region center (${center.latitude}, ${center.longitude}): ${e.message}")
            null
        }
    }

    /**
     * Extracts a concise macro-locality (city / town / district) from an [Address].
     */
    fun extractMacroLocality(address: Address): String? {
        val locality = address.locality?.trim()
        if (!locality.isNullOrEmpty()) return locality

        val subLocality = address.subLocality?.trim()
        if (!subLocality.isNullOrEmpty()) return subLocality

        val subAdminArea = address.subAdminArea?.trim()
        if (!subAdminArea.isNullOrEmpty()) return subAdminArea

        val adminArea = address.adminArea?.trim()
        if (!adminArea.isNullOrEmpty()) return adminArea

        return null
    }

    @Suppress("DEPRECATION")
    private fun getFromLocationCompat(
        geocoder: Geocoder,
        latitude: Double,
        longitude: Double,
        maxResults: Int
    ): List<Address>? {
        return try {
            geocoder.getFromLocation(latitude, longitude, maxResults)
        } catch (e: Throwable) {
            Log.w(TAG, "Geocoder I/O exception: ${e.message}")
            null
        }
    }

    private fun getFromPreferences(context: Context, key: String): String? {
        return try {
            val prefs = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE) ?: return null
            prefs.getString(key, null)
        } catch (_: Throwable) {
            null
        }
    }

    private fun putToPreferences(context: Context, key: String, name: String) {
        try {
            val prefs = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE) ?: return
            prefs.edit().putString(key, name).apply()
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to persist region name: ${e.message}")
        }
    }
}
