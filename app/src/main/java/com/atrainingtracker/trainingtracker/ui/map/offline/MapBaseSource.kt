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

/**
 * Binary base-map tile source orthogonal to `WorkoutBaseMapType` (`REQ-MAP-071.3`).
 *
 * Switching between [GOOGLE] and [OFFLINE_OSM] preserves the athlete's preferred Google base-map
 * style (`NORMAL` / `TERRAIN` / `SATELLITE` / `HYBRID`) without overwriting it.
 */
enum class MapBaseSource {
    /** Standard Google Maps tile rendering (default). */
    GOOGLE,

    /** On-device Mapsforge OpenStreetMap vector tiles rendered under `MapType.NONE`. */
    OFFLINE_OSM;

    companion object {
        /** Canonical default base-map source for fresh installs and upgrades. */
        val DEFAULT: MapBaseSource = GOOGLE

        /**
         * Defensively resolves a persisted token into [MapBaseSource], falling back to [DEFAULT]
         * for null, blank, or unrecognized future tokens.
         */
        @JvmStatic
        fun safeValueOf(raw: String?): MapBaseSource {
            if (raw.isNullOrBlank()) return DEFAULT
            return entries.firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) } ?: DEFAULT
        }
    }
}
