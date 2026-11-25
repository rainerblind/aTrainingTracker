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

package com.atrainingtracker.trainingtracker.ui.map.offline

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Isolated preference store for the orthogonal binary [MapBaseSource] setting (`REQ-MAP-071.3`).
 *
 * Persists strictly in `"offline_map_prefs"` without touching `SettingsDataStore` or
 * `MyPreferenceManager` (preserving `WorkoutBaseMapType` orthogonally and avoiding background
 * DataStore coroutine launches on mocked/JVM test contexts per `Coding Standards §5.5`).
 */
object OfflineMapPreferences {
    const val PREFS_NAME = "offline_map_prefs"
    const val KEY_MAP_BASE_SOURCE = "map_base_source"

    private val _baseSourceFlow = MutableStateFlow(MapBaseSource.DEFAULT)

    @Volatile
    private var hydratedFromDisk = false

    /**
     * Returns the reactive [StateFlow] of the persisted [MapBaseSource], lazily hydrating from
     * [SharedPreferences] once on first access.
     */
    fun observeBaseSource(context: Context?): StateFlow<MapBaseSource> {
        ensureHydrated(context)
        return _baseSourceFlow.asStateFlow()
    }

    /** Reads the currently persisted [MapBaseSource], defaulting safely to [MapBaseSource.GOOGLE]. */
    @JvmStatic
    fun getBaseSource(context: Context?): MapBaseSource {
        ensureHydrated(context)
        return _baseSourceFlow.value
    }

    /** Persists [source] immediately and notifies all reactive observers. */
    @JvmStatic
    fun setBaseSource(context: Context?, source: MapBaseSource) {
        synchronized(this) {
            safePrefs(context)?.let { prefs ->
                try {
                    prefs.edit().putString(KEY_MAP_BASE_SOURCE, source.name).apply()
                } catch (_: Throwable) {
                    // Defensive guard for mocked/JVM test contexts
                }
            }
            hydratedFromDisk = true
            _baseSourceFlow.value = source
        }
    }

    private fun ensureHydrated(context: Context?) {
        if (hydratedFromDisk || context == null) return
        synchronized(this) {
            if (hydratedFromDisk) return
            val prefs = safePrefs(context) ?: return
            val resolved = try {
                MapBaseSource.safeValueOf(prefs.getString(KEY_MAP_BASE_SOURCE, null))
            } catch (_: Throwable) {
                MapBaseSource.DEFAULT
            }
            _baseSourceFlow.value = resolved
            hydratedFromDisk = true
        }
    }

    private fun safePrefs(context: Context?): SharedPreferences? {
        if (context == null) return null
        return try {
            val appCtx = context.applicationContext ?: context
            appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        } catch (_: Throwable) {
            null
        }
    }

    @VisibleForTesting
    internal fun resetForTesting(initialSource: MapBaseSource = MapBaseSource.DEFAULT) {
        synchronized(this) {
            hydratedFromDisk = false
            _baseSourceFlow.value = initialSource
        }
    }
}
