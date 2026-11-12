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

package com.atrainingtracker.trainingtracker.ui.sportingjourney

import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Pure mathematical computation engine for the Sporting Journey / Trophy Vault (REQ-SPT-002, REQ-SPT-003).
 *
 * Performs deterministic conversions, planetary comparisons, biological calculations, and badge criteria evaluations.
 */
object SportingJourneyCalculator {

    const val EARTH_EQUATOR_KM = 40_075.0
    const val MOUNT_EVEREST_METERS = 8_848.0
    const val EBIKE_WH_PER_KM = 10.0 // 10 Wh/km -> 100 km per kWh
    const val ESPRESSO_KWH = 0.025 // 25 Wh per espresso shot -> 40 espressos per kWh
    const val SMARTPHONE_CHARGE_KWH = 0.015 // 15 Wh per phone charge -> ~66.7 charges per kWh

    /**
     * Calculates percentage of Earth's Equator covered by [distanceKm].
     */
    fun calculateEquatorPercentage(distanceKm: Double): Double {
        if (distanceKm <= 0.0) return 0.0
        return (distanceKm / EARTH_EQUATOR_KM) * 100.0
    }

    /**
     * Calculates the Mount Everest vertical multiple from [elevationMeters].
     */
    fun calculateEverestMultiplier(elevationMeters: Double): Double {
        if (elevationMeters <= 0.0) return 0.0
        return elevationMeters / MOUNT_EVEREST_METERS
    }

    /**
     * Converts active training hours to continuous 24-hour days.
     */
    fun calculateNonstopDays(activeHours: Double): Double {
        if (activeHours <= 0.0) return 0.0
        return activeHours / 24.0
    }

    /**
     * Calculates pedal revolutions from average cadence in RPM and active time in seconds.
     */
    fun calculateRevolutions(avgCadenceRpm: Double, activeTimeSeconds: Long): Long {
        if (avgCadenceRpm <= 0.0 || activeTimeSeconds <= 0) return 0L
        return ((avgCadenceRpm * activeTimeSeconds) / 60.0).roundToLong()
    }

    /**
     * Calculates estimated heartbeats from average HR in BPM and active time in seconds.
     */
    fun calculateHeartbeats(avgHeartRateBpm: Double, activeTimeSeconds: Long): Long {
        if (avgHeartRateBpm <= 0.0 || activeTimeSeconds <= 0) return 0L
        return ((avgHeartRateBpm * activeTimeSeconds) / 60.0).roundToLong()
    }

    /**
     * Converts mechanical work in kilojoules (kJ) to kilowatt-hours (kWh).
     */
    fun calculateKwhFromKj(workKj: Double): Double {
        if (workKj <= 0.0) return 0.0
        return workKj / 3600.0
    }

    /**
     * Converts average power in Watts and active seconds into kilowatt-hours (kWh).
     */
    fun calculateKwhFromPower(avgPowerWatts: Double, activeTimeSeconds: Long): Double {
        if (avgPowerWatts <= 0.0 || activeTimeSeconds <= 0) return 0.0
        return (avgPowerWatts * activeTimeSeconds) / 3_600_000.0
    }

    /**
     * Calculates e-bike travel equivalent in kilometers for [kwh].
     */
    fun calculateEbikeKm(kwh: Double): Double {
        if (kwh <= 0.0) return 0.0
        return (kwh * 1000.0) / EBIKE_WH_PER_KM
    }

    /**
     * Calculates the number of espresso shots brewable with [kwh].
     */
    fun calculateEspressoCount(kwh: Double): Int {
        if (kwh <= 0.0) return 0
        return (kwh / ESPRESSO_KWH).roundToInt()
    }

    /**
     * Calculates the number of full smartphone battery charges equivalent to [kwh].
     */
    fun calculateSmartphoneCharges(kwh: Double): Int {
        if (kwh <= 0.0) return 0
        return (kwh / SMARTPHONE_CHARGE_KWH).roundToInt()
    }

    /**
     * Computes the percentage of ritual workouts (repeated cluster loops) vs total workouts.
     */
    fun calculateRitualPercentage(ritualWorkoutsCount: Int, totalWorkouts: Int): Int {
        if (totalWorkouts <= 0 || ritualWorkoutsCount <= 0) return 0
        val clampedRitual = ritualWorkoutsCount.coerceAtMost(totalWorkouts)
        return ((clampedRitual.toDouble() / totalWorkouts.toDouble()) * 100.0).toInt().coerceIn(0, 100)
    }
}
