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

package com.atrainingtracker.trainingtracker.settings

import android.content.Context
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * A helper object to allow synchronous access to DataStore values from Java code.
 * NOTE: This uses runBlocking and should be used carefully to avoid blocking the UI thread.
 * It is acceptable for reading small, cached preference values.
 */
object SettingsDataStoreJavaHelper {

    /**
     * Generic helper to get a max zone value for a specific profile (Run, Bike, Power).
     * @param context The application context
     * @param zoneType The ZoneType
     * @param zoneIndex The zone number (1-4). Zone 5 is usually > Zone 4.
     * @return The max value for that zone.
     */
    @JvmStatic
    fun getZoneMax(context: Context, zoneType: SettingsDataStore.ZoneType, zoneIndex: Int): Int {
        val dataStore = SettingsDataStore(context)

        return runBlocking {
            when (zoneIndex) {
                1 -> dataStore.getZone1MaxFlow(zoneType).first()
                2 -> dataStore.getZone2MaxFlow(zoneType).first()
                3 -> dataStore.getZone3MaxFlow(zoneType).first()
                4 -> dataStore.getZone4MaxFlow(zoneType).first()
                else -> 0 // Fallback
            }
        }
    }
}