/*
 * aTrainingTracker
 * Copyright (C) 2013-2026 Rainer Blind <rainer.blind@gmail.com>
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
import com.atrainingtracker.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Locale

/**
 * ATT-919 / REQ-UI-165: Resolves human-readable location names for known workout start
 * coordinates via Android [Geocoder], providing clean address formatting with localized
 * coordinate fallbacks.
 */
object LocationNameResolver {
    private const val TAG = "LocationNameResolver"

    /**
     * Resolves a human-readable location name for given coordinates via [Geocoder].
     * Falls back to localized coordinate formatting if offline, unavailable, or empty.
     */
    suspend fun resolveLocationName(
        context: Context,
        latitude: Double,
        longitude: Double,
        locale: Locale = Locale.getDefault()
    ): String = withContext(Dispatchers.IO) {
        try {
            if (!Geocoder.isPresent()) {
                return@withContext formatFallback(context, latitude, longitude)
            }
            val geocoder = Geocoder(context, locale)
            val addresses = getFromLocationCompat(geocoder, latitude, longitude, 1)
            if (!addresses.isNullOrEmpty()) {
                val address = addresses[0]
                val name = formatAddressName(address)
                if (!name.isNullOrBlank()) {
                    return@withContext name
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to reverse geocode ($latitude, $longitude): ${e.message}")
        }
        formatFallback(context, latitude, longitude)
    }

    /**
     * Formats an [Address] into a concise, human-readable location name.
     */
    @VisibleForTesting
    fun formatAddressName(address: Address): String? {
        val locality = address.locality?.trim()
        val subLocality = address.subLocality?.trim()
        val thoroughfare = address.thoroughfare?.trim()
        val featureName = address.featureName?.trim()

        // 1. If featureName is meaningful (not purely street number / not identical to thoroughfare)
        if (!featureName.isNullOrEmpty() && featureName != thoroughfare && !featureName.matches(Regex("^\\d+[a-zA-Z]?$"))) {
            return if (!locality.isNullOrEmpty() && !featureName.contains(locality, ignoreCase = true)) {
                "$featureName, $locality"
            } else {
                featureName
            }
        }

        // 2. Thoroughfare + SubLocality or Locality
        if (!thoroughfare.isNullOrEmpty()) {
            val place = subLocality ?: locality
            return if (!place.isNullOrEmpty() && !thoroughfare.contains(place, ignoreCase = true)) {
                "$thoroughfare, $place"
            } else {
                thoroughfare
            }
        }

        // 3. SubLocality + Locality
        if (!subLocality.isNullOrEmpty() && !locality.isNullOrEmpty() && !subLocality.equals(locality, ignoreCase = true)) {
            return "$subLocality, $locality"
        }

        // 4. Locality or Administrative fallbacks
        if (!locality.isNullOrEmpty()) return locality
        if (!subLocality.isNullOrEmpty()) return subLocality
        if (!address.subAdminArea.isNullOrBlank()) return address.subAdminArea
        if (!address.adminArea.isNullOrBlank()) return address.adminArea

        return null
    }

    /**
     * Formats a fallback name using geodetic coordinates.
     */
    fun formatFallback(context: Context, latitude: Double, longitude: Double): String {
        return context.getString(R.string.known_location_unnamed_format, latitude, longitude)
    }

    /**
     * Synchronous blocking resolution for Java caller threads (e.g. background executors).
     */
    @JvmStatic
    @JvmOverloads
    fun resolveLocationNameBlocking(
        context: Context,
        latitude: Double,
        longitude: Double,
        locale: Locale = Locale.getDefault()
    ): String = kotlinx.coroutines.runBlocking(Dispatchers.IO) {
        resolveLocationName(context, latitude, longitude, locale)
    }

    /**
     * Checks if a location name is an uninformative placeholder that should be upgraded
     * to a resolved human-readable name during legacy healing.
     */
    @JvmStatic
    fun isPlaceholderName(name: String?): Boolean {
        if (name.isNullOrBlank()) return true
        val lower = name.trim().lowercase(Locale.ROOT)
        return lower.startsWith("auto-learned") ||
                lower.startsWith("internet dem") ||
                lower.startsWith("startort") ||
                lower.startsWith("start location") ||
                lower == "default" ||
                lower.matches(Regex("^[a-z_]+_start$"))
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
        } catch (e: IOException) {
            Log.w(TAG, "Geocoder I/O exception: ${e.message}")
            null
        }
    }
}
