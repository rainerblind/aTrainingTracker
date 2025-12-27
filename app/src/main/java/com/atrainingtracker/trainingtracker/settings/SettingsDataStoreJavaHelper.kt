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
     * @param zoneType The ZoneType (0=Run, 1=Bike, 2=Power)
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