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

package com.atrainingtracker.banalservice.filters

import com.atrainingtracker.banalservice.sensor.SensorType

/**
 * Encapsulates the recommended default filter configuration for a sensor type.
 */
data class DefaultFilterConfig(
    val filterType: FilterType,
    val filterConstant: Double,
    val unit: String = "sec"
)

/**
 * Centralized domain-specific default filter configuration presets for sensor types
 * (ATT-1624, REQ-UI-198, TST-UI-152).
 */
object SensorFilterDefaults {

    /**
     * Resolves the domain-optimized default filter configuration for the given [sensorType].
     *
     * - [SensorType.POWER]: 3-second moving average (dampens pedal stroke dead-spots)
     * - [SensorType.PACE_spm]: 5-second moving average (dampens GPS step jitter)
     * - [SensorType.VERTICAL_SPEED]: 15-second moving average (dampens 1Hz barometric quantization noise)
     * - [SensorType.SLOPE]: 5-second moving average (prevents divide-by-small-delta spikes)
     * - Accumulators & other metrics: Instantaneous (1.0s direct)
     */
    @JvmStatic
    fun getDefaultFilterConfig(sensorType: SensorType?): DefaultFilterConfig {
        if (sensorType == null) {
            return DefaultFilterConfig(FilterType.INSTANTANEOUS, 1.0, "sec")
        }
        return when (sensorType) {
            SensorType.POWER -> DefaultFilterConfig(FilterType.MOVING_AVERAGE_TIME, 3.0, "sec")
            SensorType.PACE_spm -> DefaultFilterConfig(FilterType.MOVING_AVERAGE_TIME, 5.0, "sec")
            SensorType.VERTICAL_SPEED -> DefaultFilterConfig(FilterType.MOVING_AVERAGE_TIME, 15.0, "sec")
            SensorType.SLOPE -> DefaultFilterConfig(FilterType.MOVING_AVERAGE_TIME, 5.0, "sec")
            else -> DefaultFilterConfig(FilterType.INSTANTANEOUS, 1.0, "sec")
        }
    }

    @JvmStatic
    fun getDefaultFilterType(sensorType: SensorType?): FilterType =
        getDefaultFilterConfig(sensorType).filterType

    @JvmStatic
    fun getDefaultFilterConstant(sensorType: SensorType?): Double =
        getDefaultFilterConfig(sensorType).filterConstant

    @JvmStatic
    fun getDefaultUnit(sensorType: SensorType?): String =
        getDefaultFilterConfig(sensorType).unit
}

/**
 * Extension method to resolve the recommended default filter configuration directly on [SensorType].
 */
fun SensorType.getDefaultFilterConfig(): DefaultFilterConfig =
    SensorFilterDefaults.getDefaultFilterConfig(this)
