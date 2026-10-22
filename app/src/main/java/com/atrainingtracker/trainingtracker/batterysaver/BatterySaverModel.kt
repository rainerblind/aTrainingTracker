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

enum class DimmingLevel(val factor: Float) {
    FULL_DIM(0.25f),
    MEDIUM_DIM(0.50f),
    NO_DIM(1.0f);

    val brightness: Float
        get() = factor

    companion object {
        const val SAFETY_FLOOR = 0.05f
        const val WAKEUP_DURATION_MS = 15_000L
        const val DOWNWARD_HYSTERESIS_MS = 3_000L
    }
}

/**
 * Dynamic configuration for AMOLED Battery Saver thresholds and brightness factors.
 * Configured via Advanced Tuning Preferences (REQ-SET-073).
 */
data class BatterySaverTuningConfig(
    val fullDimFactor: Float = DimmingLevel.FULL_DIM.factor,
    val mediumDimFactor: Float = DimmingLevel.MEDIUM_DIM.factor,
    val slopeFlatThreshold: Float = 2.0f,
    val slopeSteepThreshold: Float = 5.0f,
    val wakeupDurationMs: Long = DimmingLevel.WAKEUP_DURATION_MS,
    val downwardHysteresisMs: Long = DimmingLevel.DOWNWARD_HYSTERESIS_MS
)

data class TelemetrySnapshot(
    val slopePercent: Float? = null,
    val hrZone: Int? = null,
    val powerZone: Int? = null,
    val isCycling: Boolean = true
)

/**
 * Pure function to map a numeric telemetry value to a 1-based zone index based on an ascending list of upper cutoffs.
 * (REQ-SET-079 / ATT-3999).
 * Zero dependency on Context or DataStore.
 */
fun calculateZoneIndex(value: Double, cutoffs: List<Int>): Int {
    for (i in cutoffs.indices) {
        if (value <= cutoffs[i]) return i + 1
    }
    return cutoffs.size + 1
}

/**
 * Backward-compatible overload delegating to non-blocking helper and pure calculation.
 */
fun calculateZoneIndex(context: Context, zoneType: SettingsDataStore.ZoneType, value: Double): Int {
    val z1Max = SettingsDataStoreJavaHelper.getZoneMax(context, zoneType, 1)
    val z2Max = SettingsDataStoreJavaHelper.getZoneMax(context, zoneType, 2)
    val z3Max = SettingsDataStoreJavaHelper.getZoneMax(context, zoneType, 3)
    val z4Max = SettingsDataStoreJavaHelper.getZoneMax(context, zoneType, 4)
    val cutoffs = if (zoneType == SettingsDataStore.ZoneType.PWR_BIKE) {
        val z5Max = SettingsDataStoreJavaHelper.getZoneMax(context, zoneType, 5)
        val z6Max = SettingsDataStoreJavaHelper.getZoneMax(context, zoneType, 6)
        listOf(z1Max, z2Max, z3Max, z4Max, z5Max, z6Max)
    } else {
        listOf(z1Max, z2Max, z3Max, z4Max)
    }
    return calculateZoneIndex(value, cutoffs)
}
