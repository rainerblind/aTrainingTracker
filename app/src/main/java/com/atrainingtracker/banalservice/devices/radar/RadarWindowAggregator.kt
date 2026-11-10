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

package com.atrainingtracker.banalservice.devices.radar

/**
 * Snapshot of 1 Hz aggregated radar telemetry suitable for scalar sensor publishing and DB sampling (REQ-CON-028.5).
 */
data class RadarLatchedSnapshot(
    val threatCount: Int,
    val closestDistanceMeters: Double?,
    val approachSpeedMps: Double,
    val vehicleCount: Int
)

/**
 * 1 Hz window aggregator and vehicle overtaking counter with hysteresis (REQ-CON-028.5).
 *
 * Aggregates high-frequency (4–8 Hz) target frames into 1 Hz peak threat count, max approach speed,
 * and minimum distance latches to ensure passing vehicles are not missed between 1 Hz DB poll ticks.
 *
 * Adheres to Rule 1.8: Constructor state initialization parity with [resetSession].
 */
class RadarWindowAggregator {

    private val lock = Any()

    private var currentWindowMaxThreats: Int = 0
    private var currentWindowMinDistance: Double? = null
    private var currentWindowMaxSpeed: Double = 0.0

    private var cumulativeVehicleCount: Int = 0

    // Target tracking for vehicle passing hysteresis
    // Maps targetId -> minimum distance observed so far
    private val activeTargetMinDistances = mutableMapOf<Int, Float>()

    var hasReceivedData: Boolean = false
        private set

    init {
        // Enforce Rule 1.8 constructor state initialization parity
        resetSession()
    }

    /**
     * Resets all accumulators, vehicle count, and tracking hysteresis to baseline state.
     */
    fun resetSession() {
        synchronized(lock) {
            currentWindowMaxThreats = 0
            currentWindowMinDistance = null
            currentWindowMaxSpeed = 0.0
            cumulativeVehicleCount = 0
            activeTargetMinDistances.clear()
            hasReceivedData = false
        }
    }

    /**
     * Consumes an incoming high-rate radar target frame (4–8 Hz).
     */
    fun onTargetsUpdated(targets: List<RadarTarget>) {
        synchronized(lock) {
            hasReceivedData = true

            val approachingTargets = targets.filter { it.threatLevel != ThreatLevel.NONE }
            val count = approachingTargets.size
            if (count > currentWindowMaxThreats) {
                currentWindowMaxThreats = count
            }

            for (target in approachingTargets) {
                val dist = target.rangeMeters.toDouble()
                if (currentWindowMinDistance == null || dist < currentWindowMinDistance!!) {
                    currentWindowMinDistance = dist
                }
                val speed = target.approachSpeedMps.toDouble()
                if (speed > currentWindowMaxSpeed) {
                    currentWindowMaxSpeed = speed
                }

                // Update hysteresis tracking for vehicle overtaking
                val prevMin = activeTargetMinDistances[target.targetId]
                if (prevMin == null || target.rangeMeters < prevMin) {
                    activeTargetMinDistances[target.targetId] = target.rangeMeters
                }
            }

            // Detect vehicles that completed passing:
            // A target was tracked closing to within PASSING_THRESHOLD_METERS (e.g. <= 15m)
            // and is no longer present in the active targets list
            val currentTargetIds = targets.map { it.targetId }.toSet()
            val disappearedTargetIds = activeTargetMinDistances.keys.filterNot { currentTargetIds.contains(it) }

            for (id in disappearedTargetIds) {
                val minDistance = activeTargetMinDistances[id] ?: Float.MAX_VALUE
                if (minDistance <= PASSING_THRESHOLD_METERS) {
                    cumulativeVehicleCount++
                }
                activeTargetMinDistances.remove(id)
            }
        }
    }

    /**
     * Samples and latches the current 1 Hz window metrics, then resets the window accumulator for the next tick.
     */
    fun sampleAndResetWindow(): RadarLatchedSnapshot {
        synchronized(lock) {
            val snapshot = RadarLatchedSnapshot(
                threatCount = currentWindowMaxThreats,
                closestDistanceMeters = currentWindowMinDistance,
                approachSpeedMps = currentWindowMaxSpeed,
                vehicleCount = cumulativeVehicleCount
            )

            // Reset 1 Hz latches for next window
            currentWindowMaxThreats = 0
            currentWindowMinDistance = null
            currentWindowMaxSpeed = 0.0

            return snapshot
        }
    }

    fun getVehicleCount(): Int {
        synchronized(lock) {
            return cumulativeVehicleCount
        }
    }

    companion object {
        /** Distance threshold under which a closing vehicle is classified as an overtaken vehicle upon exit. */
        const val PASSING_THRESHOLD_METERS = 18.0f
    }
}
