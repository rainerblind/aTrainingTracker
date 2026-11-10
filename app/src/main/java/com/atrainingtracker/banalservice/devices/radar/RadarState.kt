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
 * Threat level classifications for approaching radar targets (REQ-CON-028.4).
 */
enum class ThreatLevel {
    /** Clear or no approaching threat. */
    NONE,
    /** Approaching vehicle at normal closing speed (amber alert). */
    APPROACHING,
    /** Approaching vehicle at high closing speed (red alert). */
    FAST_APPROACHING
}

/**
 * Connection states for radar hardware peripherals (REQ-CON-028.4).
 */
enum class RadarConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    DISCONNECTED_ERROR
}

/**
 * Vehicle size classifications parsed from BLE V2 radar telemetry (REQ-CON-030.3).
 */
enum class VehicleSizeCategory {
    UNKNOWN_OR_LIGHT,
    CAR,
    TRUCK_OR_BUS
}

/**
 * Spatial lane trajectory classification relative to rider's travel path (REQ-CON-030.3).
 */
enum class LaneState {
    UNKNOWN,
    IN_RIDER_LANE,
    CHANGING_LANE,
    PASSING_LANE
}

/**
 * Immutable domain representation of an individual radar target (REQ-CON-028.4, REQ-CON-030.3).
 *
 * @property targetId Unique target tracking identifier (0..7 typically).
 * @property rangeMeters Distance to the target in meters (0..175m).
 * @property approachSpeedMps Closing speed in meters per second.
 * @property threatLevel Semantic threat level (NONE, APPROACHING, FAST_APPROACHING).
 * @property lateralOffsetMeters Signed lateral distance from rider's longitudinal travel axis (negative = left, positive = right).
 * @property longitudinalSpeedMps Relative longitudinal velocity in meters per second.
 * @property lateralSpeedMps Relative lateral velocity in meters per second.
 * @property vehicleWidthMeters Estimated vehicle width in meters.
 * @property vehicleLengthMeters Estimated vehicle length in meters.
 * @property vehicleSizeCategory Classified vehicle size category.
 * @property laneState Spatial lane position relative to rider path.
 */
data class RadarTarget @JvmOverloads constructor(
    val targetId: Int,
    val rangeMeters: Float,
    val approachSpeedMps: Float,
    val threatLevel: ThreatLevel = ThreatLevel.NONE,
    val lateralOffsetMeters: Float? = null,
    val longitudinalSpeedMps: Float? = null,
    val lateralSpeedMps: Float? = null,
    val vehicleWidthMeters: Float? = null,
    val vehicleLengthMeters: Float? = null,
    val vehicleSizeCategory: VehicleSizeCategory = VehicleSizeCategory.UNKNOWN_OR_LIGHT,
    val laneState: LaneState = LaneState.UNKNOWN
)

/**
 * Immutable snapshot of cycling radar system state published at 4–8 Hz (REQ-CON-028.4).
 *
 * @property connectionState Current hardware connection state.
 * @property overallThreatLevel Aggregate threat level across all active targets.
 * @property targets List of currently tracked radar targets.
 * @property lastUpdateRealtimeMs SystemClock.elapsedRealtime() when this snapshot was published.
 */
data class RadarState(
    val connectionState: RadarConnectionState = RadarConnectionState.DISCONNECTED,
    val overallThreatLevel: ThreatLevel = ThreatLevel.NONE,
    val targets: List<RadarTarget> = emptyList(),
    val lastUpdateRealtimeMs: Long = 0L
)
