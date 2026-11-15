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

@file:Suppress("PackageNaming")

package com.atrainingtracker.banalservice.devices.bluetooth_le.polar

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.VisibleForTesting
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Lightweight store remembering BLE devices confirmed to advertise or initialize
 * Polar PFTP offline recording capabilities (`FEATURE_POLAR_OFFLINE_RECORDING`)
 * so user-renamed Polar optical sensors remain recognized without a SQLite schema bump (ATT-7923).
 */
object PolarOfflineCapabilityStore {

    private const val PREFS_NAME = "polar_offline_capabilities"
    private const val KEY_PREFIX = "offline_rec_"

    private val memoryCache = ConcurrentHashMap<String, Boolean>()

    /**
     * Marks [macAddress] as capable of Polar PFTP offline HR recording.
     */
    @JvmStatic
    fun markOfflineRecordingCapable(context: Context?, macAddress: String?) {
        val normalized = normalizeMac(macAddress) ?: return
        memoryCache[normalized] = true
        getPrefs(context)?.edit()?.putBoolean(KEY_PREFIX + normalized, true)?.apply()
    }

    /**
     * Returns true if [macAddress] was previously confirmed to support Polar PFTP offline recording.
     */
    @JvmStatic
    fun hasOfflineRecordingCapability(context: Context?, macAddress: String?): Boolean {
        val normalized = normalizeMac(macAddress) ?: return false
        return memoryCache[normalized] == true || readStoredCapability(context, normalized)
    }

    private fun readStoredCapability(context: Context?, normalizedMac: String): Boolean {
        val stored = getPrefs(context)?.getBoolean(KEY_PREFIX + normalizedMac, false) ?: false
        if (stored) {
            memoryCache[normalizedMac] = true
        }
        return stored
    }

    private fun getPrefs(context: Context?): SharedPreferences? {
        val appContext = context?.applicationContext ?: return null
        return appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    @VisibleForTesting
    fun resetForTesting() {
        memoryCache.clear()
    }

    private fun normalizeMac(macAddress: String?): String? {
        val trimmed = macAddress?.trim()?.uppercase(Locale.ROOT)
        return if (trimmed.isNullOrEmpty()) null else trimmed
    }
}
