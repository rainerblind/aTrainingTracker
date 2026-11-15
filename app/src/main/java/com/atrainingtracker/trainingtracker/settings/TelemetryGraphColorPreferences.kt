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
import android.content.SharedPreferences
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.preference.PreferenceManager
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.ui.map.TelemetryMetricType
import com.atrainingtracker.trainingtracker.ui.theme.TelemetryGraphColor
import com.atrainingtracker.util.safeEnumValueOf

/**
 * Global per-metric and sensor-family graph color preference store backed by [SharedPreferences]
 * (REQ-UI-530.2, REQ-UI-530.3, ATT-7759).
 *
 * Unifies color resolution across post-workout [TelemetryMetricType] charts and live cockpit
 * [SensorType] sparklines with downgrade-safe enum deserialization (`REQ-ARC-001`).
 */
object TelemetryGraphColorPreferences {

    private const val TAG = "TelemetryGraphColorPrefs"
    const val KEY_PREFIX = "telemetry_graph_color_"

    /**
     * Resolves default [SharedPreferences] defensively so Compose previews and JVM unit tests
     * without a full Android preference context safely receive `null`.
     */
    fun resolveSharedPreferences(context: Context): SharedPreferences? = runCatching {
        PreferenceManager.getDefaultSharedPreferences(context)
    }.getOrNull()

    /**
     * Returns the canonical preference key for a post-workout [TelemetryMetricType].
     */
    fun preferenceKey(metricType: TelemetryMetricType): String = when (metricType) {
        TelemetryMetricType.HEART_RATE -> "${KEY_PREFIX}HEART_RATE"
        TelemetryMetricType.SPEED -> "${KEY_PREFIX}SPEED"
        TelemetryMetricType.PACE -> "${KEY_PREFIX}PACE"
        TelemetryMetricType.POWER -> "${KEY_PREFIX}POWER"
        TelemetryMetricType.W_PRIME_BAL -> "${KEY_PREFIX}W_PRIME_BAL"
        TelemetryMetricType.HRV_DFA_A1 -> "${KEY_PREFIX}HRV_DFA_A1"
        TelemetryMetricType.HRV_RMSSD -> "${KEY_PREFIX}HRV_RMSSD"
    }

    /**
     * Maps a live cockpit [SensorType] to its shared metric-family preference key so global
     * overrides apply consistently across Workout Detail and Cockpit sparklines.
     */
    fun preferenceKey(sensorType: SensorType): String = when (sensorType) {
        SensorType.HR -> "${KEY_PREFIX}HEART_RATE"
        SensorType.SPEED_mps -> "${KEY_PREFIX}SPEED"
        SensorType.PACE_spm -> "${KEY_PREFIX}PACE"
        SensorType.POWER,
        SensorType.NORMALIZED_POWER,
        SensorType.POWER_5S,
        SensorType.POWER_1M,
        SensorType.POWER_5M,
        SensorType.POWER_20M,
        SensorType.POWER_PEAK_5S,
        SensorType.POWER_PEAK_1M,
        SensorType.POWER_PEAK_5M,
        SensorType.POWER_PEAK_20M,
        SensorType.POWER_TO_WEIGHT -> "${KEY_PREFIX}POWER"
        SensorType.W_PRIME_BAL -> "${KEY_PREFIX}W_PRIME_BAL"
        SensorType.HRV_DFA_A1 -> "${KEY_PREFIX}HRV_DFA_A1"
        SensorType.HRV_RMSSD -> "${KEY_PREFIX}HRV_RMSSD"
        else -> "${KEY_PREFIX}SENSOR_${sensorType.name}"
    }

    /**
     * Returns the canonical default [TelemetryGraphColor] for [metricType] (REQ-UI-530.2).
     */
    fun getDefaultColor(metricType: TelemetryMetricType): TelemetryGraphColor = when (metricType) {
        TelemetryMetricType.HEART_RATE -> TelemetryGraphColor.CRIMSON
        TelemetryMetricType.POWER -> TelemetryGraphColor.VIOLET
        TelemetryMetricType.W_PRIME_BAL -> TelemetryGraphColor.EMERALD
        TelemetryMetricType.HRV_DFA_A1 -> TelemetryGraphColor.AMBER
        TelemetryMetricType.HRV_RMSSD -> TelemetryGraphColor.TEAL
        TelemetryMetricType.SPEED,
        TelemetryMetricType.PACE -> TelemetryGraphColor.THEME_DEFAULT
    }

    /**
     * Returns the canonical default [TelemetryGraphColor] for [sensorType] (REQ-UI-530.2).
     */
    fun getDefaultColor(sensorType: SensorType): TelemetryGraphColor = when (sensorType) {
        SensorType.HR -> TelemetryGraphColor.CRIMSON
        SensorType.POWER,
        SensorType.NORMALIZED_POWER,
        SensorType.POWER_5S,
        SensorType.POWER_1M,
        SensorType.POWER_5M,
        SensorType.POWER_20M,
        SensorType.POWER_PEAK_5S,
        SensorType.POWER_PEAK_1M,
        SensorType.POWER_PEAK_5M,
        SensorType.POWER_PEAK_20M,
        SensorType.POWER_TO_WEIGHT -> TelemetryGraphColor.VIOLET
        SensorType.W_PRIME_BAL -> TelemetryGraphColor.EMERALD
        SensorType.HRV_DFA_A1 -> TelemetryGraphColor.AMBER
        SensorType.HRV_RMSSD -> TelemetryGraphColor.TEAL
        else -> TelemetryGraphColor.THEME_DEFAULT
    }

    private fun readColor(
        prefs: SharedPreferences?,
        key: String,
        defaultColor: TelemetryGraphColor
    ): TelemetryGraphColor {
        val raw = prefs?.getString(key, null) ?: return defaultColor
        return safeEnumValueOf(raw, defaultColor, TAG)
    }

    /**
     * Reads the effective [TelemetryGraphColor] for [metricType], falling back to its canonical
     * default when unset or corrupted.
     */
    fun getGraphColor(context: Context, metricType: TelemetryMetricType): TelemetryGraphColor =
        getGraphColor(resolveSharedPreferences(context), metricType)

    /**
     * Reads the effective [TelemetryGraphColor] for [metricType] from [prefs], falling back to its
     * canonical default when unset or corrupted.
     */
    fun getGraphColor(prefs: SharedPreferences?, metricType: TelemetryMetricType): TelemetryGraphColor =
        readColor(prefs, preferenceKey(metricType), getDefaultColor(metricType))

    /**
     * Reads the effective [TelemetryGraphColor] for [sensorType], falling back to its canonical
     * default when unset or corrupted.
     */
    fun getGraphColor(context: Context, sensorType: SensorType): TelemetryGraphColor =
        getGraphColor(resolveSharedPreferences(context), sensorType)

    /**
     * Reads the effective [TelemetryGraphColor] for [sensorType] from [prefs], falling back to its
     * canonical default when unset or corrupted.
     */
    fun getGraphColor(prefs: SharedPreferences?, sensorType: SensorType): TelemetryGraphColor =
        readColor(prefs, preferenceKey(sensorType), getDefaultColor(sensorType))

    /**
     * Persists [color] globally for [metricType].
     */
    fun setGraphColor(context: Context, metricType: TelemetryMetricType, color: TelemetryGraphColor) {
        resolveSharedPreferences(context)?.let { setGraphColor(it, metricType, color) }
    }

    /**
     * Persists [color] globally for [metricType] in [prefs].
     */
    fun setGraphColor(prefs: SharedPreferences, metricType: TelemetryMetricType, color: TelemetryGraphColor) {
        prefs.edit().putString(preferenceKey(metricType), color.name).apply()
    }

    /**
     * Persists [color] globally for [sensorType].
     */
    fun setGraphColor(context: Context, sensorType: SensorType, color: TelemetryGraphColor) {
        resolveSharedPreferences(context)?.let { setGraphColor(it, sensorType, color) }
    }

    /**
     * Persists [color] globally for [sensorType] in [prefs].
     */
    fun setGraphColor(prefs: SharedPreferences, sensorType: SensorType, color: TelemetryGraphColor) {
        prefs.edit().putString(preferenceKey(sensorType), color.name).apply()
    }

    /**
     * Resets the color override for [metricType] back to its canonical default.
     */
    fun resetGraphColor(context: Context, metricType: TelemetryMetricType) {
        resolveSharedPreferences(context)?.let { resetGraphColor(it, metricType) }
    }

    /**
     * Resets the color override for [metricType] in [prefs] back to its canonical default.
     */
    fun resetGraphColor(prefs: SharedPreferences, metricType: TelemetryMetricType) {
        prefs.edit().remove(preferenceKey(metricType)).apply()
    }

    /**
     * Resets the color override for [sensorType] back to its canonical default.
     */
    fun resetGraphColor(context: Context, sensorType: SensorType) {
        resolveSharedPreferences(context)?.let { resetGraphColor(it, sensorType) }
    }

    /**
     * Resets the color override for [sensorType] in [prefs] back to its canonical default.
     */
    fun resetGraphColor(prefs: SharedPreferences, sensorType: SensorType) {
        prefs.edit().remove(preferenceKey(sensorType)).apply()
    }

    /**
     * Clears all custom graph color overrides across all metrics and sensors.
     */
    fun resetAllGraphColors(context: Context) {
        resolveSharedPreferences(context)?.let { resetAllGraphColors(it) }
    }

    /**
     * Clears all custom graph color overrides in [prefs] across all metrics and sensors.
     */
    fun resetAllGraphColors(prefs: SharedPreferences) {
        val keysToRemove = prefs.all.keys.filter { it.startsWith(KEY_PREFIX) }
        if (keysToRemove.isEmpty()) return
        prefs.edit().apply {
            keysToRemove.forEach { remove(it) }
        }.apply()
    }

    /**
     * Resolves the theme-aware [Color] for [metricType] using [colorScheme]'s surface luminance.
     */
    fun resolveAccentColor(
        context: Context,
        metricType: TelemetryMetricType,
        colorScheme: ColorScheme,
        prefs: SharedPreferences? = resolveSharedPreferences(context)
    ): Color = resolveAccentColor(prefs, metricType, colorScheme)

    /**
     * Resolves the theme-aware [Color] for [metricType] from [prefs] and [colorScheme]'s surface luminance.
     */
    fun resolveAccentColor(
        prefs: SharedPreferences?,
        metricType: TelemetryMetricType,
        colorScheme: ColorScheme
    ): Color {
        val graphColor = getGraphColor(prefs, metricType)
        val isDarkTheme = TelemetryGraphColor.isDarkSurface(colorScheme)
        return graphColor.resolveColor(colorScheme, isDarkTheme)
    }

    /**
     * Resolves the theme-aware [Color] for [sensorType] from [prefs] and [colorScheme]'s surface luminance.
     */
    fun resolveAccentColor(
        prefs: SharedPreferences?,
        sensorType: SensorType,
        colorScheme: ColorScheme
    ): Color {
        val graphColor = getGraphColor(prefs, sensorType)
        val isDarkTheme = TelemetryGraphColor.isDarkSurface(colorScheme)
        return graphColor.resolveColor(colorScheme, isDarkTheme)
    }
}
