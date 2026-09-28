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

import android.content.Context
import android.util.Log
import androidx.annotation.RawRes
import com.atrainingtracker.R
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType

/**
 * Singleton cache and loader for Google Maps dark vector tile styling.
 *
 * Requirements: REQ-MAP-021
 * System Invariant: Singleton-cached (<10 KB RAM, <3ms parse), thread-safe,
 * graceful fallback to unstyled MapType.NORMAL upon invalid JSON or loading failure.
 */
object DarkMapStyle {
    private const val TAG = "DarkMapStyle"

    @Volatile
    private var cachedStyleOptions: MapStyleOptions? = null

    /**
     * Retrieves the cached [MapStyleOptions] or loads and caches it from [resId].
     *
     * In the event of any exception (e.g. missing raw resource or corrupted JSON),
     * a warning is logged and null is returned, enabling a safe fallback to standard MapType.NORMAL.
     */
    fun getMapStyleOptions(
        context: Context,
        @RawRes resId: Int = R.raw.map_style_dark
    ): MapStyleOptions? {
        cachedStyleOptions?.let { return it }

        return synchronized(this) {
            cachedStyleOptions ?: try {
                val options = MapStyleOptions.loadRawResourceStyle(context, resId)
                cachedStyleOptions = options
                options
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load dark map style from raw resource, falling back to unstyled MapType.NORMAL", e)
                null
            }
        }
    }

    /**
     * Parses a raw JSON string into [MapStyleOptions] with exception handling.
     */
    fun parseStyleJson(json: String): MapStyleOptions? {
        return try {
            org.json.JSONArray(json)
            MapStyleOptions(json)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse dark map style JSON string", e)
            null
        }
    }

    /**
     * Clears the cached style options. Used primarily in unit tests.
     */
    fun clearCache() {
        synchronized(this) {
            cachedStyleOptions = null
        }
    }

    /**
     * Resolves [MapProperties] dynamically based on dark mode state.
     *
     * Requirements: REQ-MAP-021
     * - Dark mode (isDark == true): MapType.NORMAL + [darkMapStyleOptions]
     * - Light mode (isDark == false): [lightMapType] (defaults to MapType.TERRAIN) + null style options
     */
    fun resolveMapProperties(
        isDark: Boolean,
        darkMapStyleOptions: MapStyleOptions?,
        isMyLocationEnabled: Boolean = false,
        lightMapType: MapType = MapType.TERRAIN
    ): MapProperties {
        return if (isDark) {
            MapProperties(
                mapType = MapType.NORMAL,
                mapStyleOptions = darkMapStyleOptions,
                isMyLocationEnabled = isMyLocationEnabled
            )
        } else {
            MapProperties(
                mapType = lightMapType,
                mapStyleOptions = null,
                isMyLocationEnabled = isMyLocationEnabled
            )
        }
    }

    /**
     * Resolves [MapProperties] dynamically based on dark mode state using Android [Context]
     * to lazily obtain singleton-cached [MapStyleOptions].
     *
     * Requirements: REQ-MAP-021
     */
    fun resolveMapProperties(
        isDark: Boolean,
        context: Context,
        isMyLocationEnabled: Boolean = false,
        lightMapType: MapType = MapType.TERRAIN
    ): MapProperties {
        val options = if (isDark) getMapStyleOptions(context) else null
        return resolveMapProperties(
            isDark = isDark,
            darkMapStyleOptions = options,
            isMyLocationEnabled = isMyLocationEnabled,
            lightMapType = lightMapType
        )
    }
}
