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
 * Snapshot of 1 Hz aggregated radar telemetry suitable for scalar sensor publishing and DB sampling (REQ-CON-028.5, REQ-CON-030.4).
 */
data class RadarLatchedSnapshot @JvmOverloads constructor(
    val threatCount: Int,
    val closestDistanceMeters: Double?,
    val approachSpeedMps: Double,
    val vehicleCount: Int,
    val passingDistanceMeters: Double? = null,
    val largeVehicleCount: Int = 0
)

/**
 * 1 Hz window aggregator and vehicle overtaking counter with hysteresis (REQ-CON-028.5, REQ-CON-030.4).
 *
 * Aggregates high-frequency (4–8 Hz) target frames into 1 Hz peak threat count, max approach speed,
 * minimum distance latches, minimum lateral passing clearance, and large vehicle count to ensure
 * passing vehicles are not missed between 1 Hz DB poll ticks.
 *
 * Adheres to Rule 1.8: Constructor state initialization parity with [resetSession].
 */
class RadarWindowAggregator {

    private val lock = Any()

    private var currentWindowMaxThreats: Int = 0
    private var currentWindowMinDistance: Double? = null
    private var currentWindowMaxSpeed: Double = 0.0
    private var currentWindowMinPassingDistance: Double? = null

    private var cumulativeVehicleCount: Int = 0
    private var cumulativeLargeVehicleCount: Int = 0

    // Target tracking for vehicle passing hysteresis and metadata
    private data class TrackedTargetInfo(
        var minRange: Float,
        var minPassingLateral: Double? = null,
        var vehicleSizeCategory: VehicleSizeCategory = VehicleSizeCategory.UNKNOWN_OR_LIGHT
    )
    private val activeTargetInfo = mutableMapOf<Int, TrackedTargetInfo>()

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
            currentWindowMinPassingDistance = null
            cumulativeVehicleCount = 0
            cumulativeLargeVehicleCount = 0
            activeTargetInfo.clear()
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

                // Update hysteresis tracking for vehicle overtaking and lateral passing clearance
                val info = activeTargetInfo.getOrPut(target.targetId) {
                    TrackedTargetInfo(
                        minRange = target.rangeMeters,
                        minPassingLateral = if (target.rangeMeters <= CLOSE_PASS_LONGITUDINAL_THRESHOLD_METERS && target.lateralOffsetMeters != null) {
                            Math.abs(target.lateralOffsetMeters.toDouble())
                        } else null,
                        vehicleSizeCategory = target.vehicleSizeCategory
                    )
                }
                if (target.rangeMeters < info.minRange) {
                    info.minRange = target.rangeMeters
                }
                if (target.vehicleSizeCategory != VehicleSizeCategory.UNKNOWN_OR_LIGHT) {
                    info.vehicleSizeCategory = target.vehicleSizeCategory
                }

                // Track lateral passing clearance during close passing window (y <= 3.0m)
                if (target.rangeMeters <= CLOSE_PASS_LONGITUDINAL_THRESHOLD_METERS && target.lateralOffsetMeters != null) {
                    val lat = Math.abs(target.lateralOffsetMeters.toDouble())
                    if (info.minPassingLateral == null || lat < info.minPassingLateral!!) {
                        info.minPassingLateral = lat
                    }
                    if (currentWindowMinPassingDistance == null || lat < currentWindowMinPassingDistance!!) {
                        currentWindowMinPassingDistance = lat
                    }
                }
            }

            // Detect vehicles that completed passing:
            // A target was tracked closing to within PASSING_THRESHOLD_METERS (e.g. <= 18m)
            // and is no longer present in the active targets list
            val currentTargetIds = targets.map { it.targetId }.toSet()
            val disappearedTargetIds = activeTargetInfo.keys.filterNot { currentTargetIds.contains(it) }

            for (id in disappearedTargetIds) {
                val info = activeTargetInfo[id]
                if (info != null && info.minRange <= PASSING_THRESHOLD_METERS) {
                    cumulativeVehicleCount++
                    if (info.vehicleSizeCategory == VehicleSizeCategory.TRUCK_OR_BUS) {
                        cumulativeLargeVehicleCount++
                    }
                }
                activeTargetInfo.remove(id)
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
                vehicleCount = cumulativeVehicleCount,
                passingDistanceMeters = currentWindowMinPassingDistance,
                largeVehicleCount = cumulativeLargeVehicleCount
            )

            // Reset 1 Hz latches for next window
            currentWindowMaxThreats = 0
            currentWindowMinDistance = null
            currentWindowMaxSpeed = 0.0
            currentWindowMinPassingDistance = null

            return snapshot
        }
    }

    fun getVehicleCount(): Int {
        synchronized(lock) {
            return cumulativeVehicleCount
        }
    }

    fun getLargeVehicleCount(): Int {
        synchronized(lock) {
            return cumulativeLargeVehicleCount
        }
    }

    companion object {
        /** Distance threshold under which a closing vehicle is classified as an overtaken vehicle upon exit. */
        const val PASSING_THRESHOLD_METERS = 18.0f

        /** Longitudinal distance threshold under which lateral offset is latched as passing clearance. */
        const val CLOSE_PASS_LONGITUDINAL_THRESHOLD_METERS = 3.0f
    }
}
