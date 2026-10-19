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

package com.atrainingtracker.trainingtracker.ui.components.workoutdetails

/**
 * Power metric slot types for deterministic priority resolution in [PowerTrainingMetricsSection]
 * (`REQ-UI-415.1`, `TST-UI-378.1`).
 */
enum class PowerMetricSlot {
    NORMALIZED_POWER,
    INTENSITY_FACTOR,
    TSS,
    WORK_KJ,
    POWER_TO_WEIGHT
}

/**
 * Pure mapping function resolving power metric slots into at most one horizontal row (2-3 items)
 * following priority fallback rules (`REQ-UI-415.1`, `TST-UI-378.1`).
 *
 * Priority Rules:
 * 1. If Coggan is enabled and NP is present:
 *    - If both IF and TSS are present: [NORMALIZED_POWER, INTENSITY_FACTOR, TSS] (Coggan Trio).
 *    - If IF or TSS is absent (e.g. unconfigured athlete FTP): start with NP, append IF/TSS if present,
 *      then fill available slots up to 3 from WORK_KJ and POWER_TO_WEIGHT.
 * 2. If Coggan is disabled or NP is absent:
 *    - Render available basic physics metrics: WORK_KJ and POWER_TO_WEIGHT (up to 2 items).
 * 3. If no power metrics are present: returns empty list.
 */
fun resolvePowerMetricSlots(
    data: WorkoutDetailsData,
    enableCogganPowerMetrics: Boolean
): List<PowerMetricSlot> {
    val showCoggan = enableCogganPowerMetrics && data.normalizedPowerWatts != null
    return when {
        showCoggan && data.intensityFactor != null && data.tss != null ->
            listOf(PowerMetricSlot.NORMALIZED_POWER, PowerMetricSlot.INTENSITY_FACTOR, PowerMetricSlot.TSS)
        showCoggan -> buildList {
            add(PowerMetricSlot.NORMALIZED_POWER)
            data.intensityFactor?.let { add(PowerMetricSlot.INTENSITY_FACTOR) }
            data.tss?.let { add(PowerMetricSlot.TSS) }
            if (size < 3 && data.workKj != null) add(PowerMetricSlot.WORK_KJ)
            if (size < 3 && data.avgPowerToWeight != null) add(PowerMetricSlot.POWER_TO_WEIGHT)
        }
        data.workKj != null || data.avgPowerToWeight != null -> buildList {
            data.workKj?.let { add(PowerMetricSlot.WORK_KJ) }
            data.avgPowerToWeight?.let { add(PowerMetricSlot.POWER_TO_WEIGHT) }
        }
        else -> emptyList()
    }
}
