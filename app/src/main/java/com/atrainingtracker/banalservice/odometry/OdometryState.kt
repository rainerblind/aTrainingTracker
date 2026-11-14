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
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0/>.
 */

package com.atrainingtracker.banalservice.odometry

import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Immutable container representing the 5-state unicycle odometry estimate.
 *
 * State vector:
 * x = [eastMeters, northMeters, speedMps, headingRad, gyroBiasRadPerSec]^T
 *
 * @property eastMeters Local East position relative to local tangent origin (meters).
 * @property northMeters Local North position relative to local tangent origin (meters).
 * @property speedMps Forward linear ground speed (m/s).
 * @property headingRad World yaw angle in radians (ENU convention: 0 = East, pi/2 = North, counter-clockwise).
 * @property gyroBiasRadPerSec Estimated vertical gyro bias b_{wz} (rad/s).
 * @property covariance 5x5 error covariance matrix P.
 * @property timestampMs Timestamp of the state estimate in milliseconds.
 */
data class OdometryState(
    val eastMeters: Double,
    val northMeters: Double,
    val speedMps: Double,
    val headingRad: Double,
    val gyroBiasRadPerSec: Double,
    val covariance: Array<DoubleArray>,
    val timestampMs: Long
) {
    /**
     * Heading converted to geographic compass bearing in degrees [0, 360) where 0 = North, 90 = East.
     */
    val bearingDeg: Double
        get() {
            val deg = Math.toDegrees(PI / 2.0 - headingRad)
            val normalized = deg % 360.0
            return if (normalized < 0.0) normalized + 360.0 else normalized
        }

    /**
     * Standard deviation of 2D position (meters): sqrt(P_EE + P_NN).
     */
    val positionStdDevMeters: Double
        get() = sqrt(max(0.0, covariance[0][0] + covariance[1][1]))

    /**
     * Standard deviation of forward speed (m/s): sqrt(P_vv).
     */
    val speedStdDevMps: Double
        get() = sqrt(max(0.0, covariance[2][2]))

    /**
     * Standard deviation of heading (rad): sqrt(P_psi_psi).
     */
    val headingStdDevRad: Double
        get() = sqrt(max(0.0, covariance[3][3]))

    /**
     * Standard deviation of gyro bias (rad/s): sqrt(P_bb).
     */
    val gyroBiasStdDevRadPerSec: Double
        get() = sqrt(max(0.0, covariance[4][4]))

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as OdometryState
        if (eastMeters != other.eastMeters) return false
        if (northMeters != other.northMeters) return false
        if (speedMps != other.speedMps) return false
        if (headingRad != other.headingRad) return false
        if (gyroBiasRadPerSec != other.gyroBiasRadPerSec) return false
        if (timestampMs != other.timestampMs) return false
        if (!covariance.contentDeepEquals(other.covariance)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = eastMeters.hashCode()
        result = 31 * result + northMeters.hashCode()
        result = 31 * result + speedMps.hashCode()
        result = 31 * result + headingRad.hashCode()
        result = 31 * result + gyroBiasRadPerSec.hashCode()
        result = 31 * result + timestampMs.hashCode()
        result = 31 * result + covariance.contentDeepHashCode()
        return result
    }
}
