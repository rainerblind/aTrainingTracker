/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.atrainingtracker.trainingtracker.batterysaver

import android.content.Context
import com.atrainingtracker.trainingtracker.settings.SettingsDataStore
import com.atrainingtracker.trainingtracker.settings.SettingsDataStoreJavaHelper

enum class DimmingLevel(val brightness: Float) {
    FULL_DIM(0.15f),
    MEDIUM_DIM(0.50f),
    NO_DIM(1.0f);

    companion object {
        const val SAFETY_FLOOR = 0.05f
        const val WAKEUP_DURATION_MS = 15_000L
        const val DOWNWARD_HYSTERESIS_MS = 3_000L
    }
}

data class TelemetrySnapshot(
    val slopePercent: Float? = null,
    val hrZone: Int? = null,
    val powerZone: Int? = null,
    val isCycling: Boolean = true
)

fun calculateZoneIndex(context: Context, zoneType: SettingsDataStore.ZoneType, value: Double): Int {
    val z1Max = SettingsDataStoreJavaHelper.getZoneMax(context, zoneType, 1)
    val z2Max = SettingsDataStoreJavaHelper.getZoneMax(context, zoneType, 2)
    val z3Max = SettingsDataStoreJavaHelper.getZoneMax(context, zoneType, 3)
    val z4Max = SettingsDataStoreJavaHelper.getZoneMax(context, zoneType, 4)
    return when {
        value <= z1Max -> 1
        value <= z2Max -> 2
        value <= z3Max -> 3
        value <= z4Max -> 4
        else -> 5
    }
}
