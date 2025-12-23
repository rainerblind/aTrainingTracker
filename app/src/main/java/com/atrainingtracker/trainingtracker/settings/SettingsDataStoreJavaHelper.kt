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

    @JvmStatic
    fun getHrZone1Max(context: Context): Int {
        // runBlocking is used here to bridge the async Flow world with sync Java world.
        return runBlocking {
            SettingsDataStore(context).hrZone1MaxFlow.first()
        }
    }

    @JvmStatic
    fun getHrZone2Max(context: Context): Int {
        return runBlocking {
            SettingsDataStore(context).hrZone2MaxFlow.first()
        }
    }

    @JvmStatic
    fun getHrZone3Max(context: Context): Int {
        return runBlocking {
            SettingsDataStore(context).hrZone3MaxFlow.first()
        }
    }

    @JvmStatic
    fun getHrZone4Max(context: Context): Int {
        return runBlocking {
            SettingsDataStore(context).hrZone4MaxFlow.first()
        }
    }

    @JvmStatic
    fun getPwrZone1Max(context: Context): Int {
        // runBlocking is used here to bridge the async Flow world with sync Java world.
        return runBlocking {
            SettingsDataStore(context).pwrZone1MaxFlow.first()
        }
    }

    @JvmStatic
    fun getPwrZone2Max(context: Context): Int {
        return runBlocking {
            SettingsDataStore(context).pwrZone2MaxFlow.first()
        }
    }

    @JvmStatic
    fun getPwrZone3Max(context: Context): Int {
        return runBlocking {
            SettingsDataStore(context).pwrZone3MaxFlow.first()
        }
    }

    @JvmStatic
    fun getPwrZone4Max(context: Context): Int {
        return runBlocking {
            SettingsDataStore(context).pwrZone4MaxFlow.first()
        }
    }
}