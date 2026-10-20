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
 * (`REQ-UI-415.1`, `REQ-UI-424.3`, `TST-UI-378.1`, `TST-UI-388.1`).
 * Exclusively represents Coggan Power Metrics.
 */
enum class PowerMetricSlot {
    NORMALIZED_POWER,
    INTENSITY_FACTOR,
    TSS
}

/**
 * Pure mapping function resolving power metric slots into at most one horizontal row (1-3 items)
 * containing exclusively Coggan Power Metrics (`REQ-UI-415.1`, `REQ-UI-424.3`, `TST-UI-388.1`).
 *
 * Gating Rules:
 * 1. If Coggan is enabled, Coggan summary is enabled, and NP is present:
 *    - Returns NORMALIZED_POWER, plus INTENSITY_FACTOR (if present), plus TSS (if present).
 * 2. If Coggan is disabled, Coggan summary is disabled, or NP is absent:
 *    - Returns empty list (Power row is omitted).
 */
fun resolvePowerMetricSlots(
    data: WorkoutDetailsData,
    enableCogganPowerMetrics: Boolean = true,
    showCogganPowerInWorkoutSummary: Boolean = true,
    enableCogganPower: Boolean = enableCogganPowerMetrics,
    showCogganPower: Boolean = showCogganPowerInWorkoutSummary
): List<PowerMetricSlot> {
    val effEnableCoggan = if (enableCogganPower != enableCogganPowerMetrics) enableCogganPower else enableCogganPowerMetrics
    val effShowCoggan = if (showCogganPower != showCogganPowerInWorkoutSummary) showCogganPower else showCogganPowerInWorkoutSummary

    val showCoggan = effEnableCoggan && effShowCoggan && data.normalizedPowerWatts != null

    return if (showCoggan) {
        buildList {
            add(PowerMetricSlot.NORMALIZED_POWER)
            data.intensityFactor?.let { add(PowerMetricSlot.INTENSITY_FACTOR) }
            data.tss?.let { add(PowerMetricSlot.TSS) }
        }
    } else {
        emptyList()
    }
}
