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

import kotlin.math.sqrt

/**
 * Magnetometer-Free Quaternion Body-to-World Gyro Yaw-Rate Projector (REQ-FIL-008, TST-FUSION-001.1, ATT-131).
 *
 * Projects 3D smartphone body angular rates [wx, wy, wz] into geographic East-North-Up (ENU) world frame
 * via closed-form quaternion algebra:
 * \vec{\omega}_{ENU} = R(q) \vec{\omega}_{body} = q \otimes \vec{\omega}_{body} \otimes q^*
 *
 * Extracts vertical world yaw-rate:
 * \omega_{ENU,z} = 2(q_x q_z - q_w q_y)\omega_x + 2(q_y q_z + q_w q_x)\omega_y + (1 - 2(q_x^2 + q_y^2))\omega_z
 *
 * Key Invariants:
 * 1. Zero heap allocations in inner loop; zero trigonometric evaluations.
 * 2. Invariant to phone mounting orientation (pitch/roll up to 90 degrees).
 * 3. Linear accelerometer double-integration is strictly prohibited (angular rates only).
 */
class QuaternionGyroProjector {

    /**
     * Projects 3D body angular velocities into vertical world yaw-rate \omega_{ENU,z} (rad/s).
     *
     * @param qx Quaternion x component (vector).
     * @param qy Quaternion y component (vector).
     * @param qz Quaternion z component (vector).
     * @param qw Quaternion w component (scalar, cos(theta/2)).
     * @param wx Gyroscope angular rate around body X axis (rad/s).
     * @param wy Gyroscope angular rate around body Y axis (rad/s).
     * @param wz Gyroscope angular rate around body Z axis (rad/s).
     * @return World vertical yaw-rate in radians per second (rad/s). Positive = counter-clockwise (turning left).
     */
    fun projectYawRate(
        qx: Double,
        qy: Double,
        qz: Double,
        qw: Double,
        wx: Double,
        wy: Double,
        wz: Double
    ): Double {
        val normSq = qx * qx + qy * qy + qz * qz + qw * qw
        if (normSq <= 1e-12) {
            return wz
        }
        val invNorm = 1.0 / sqrt(normSq)
        val nx = qx * invNorm
        val ny = qy * invNorm
        val nz = qz * invNorm
        val nw = qw * invNorm

        val r20 = 2.0 * (nx * nz - nw * ny)
        val r21 = 2.0 * (ny * nz + nw * nx)
        val r22 = 1.0 - 2.0 * (nx * nx + ny * ny)

        return r20 * wx + r21 * wy + r22 * wz
    }

    /**
     * Projects FloatArray inputs from Android SensorEvent (Sensor.TYPE_GAME_ROTATION_VECTOR and Sensor.TYPE_GYROSCOPE).
     */
    fun projectYawRate(gameRotationVector: FloatArray, gyroRadPerSec: FloatArray): Double {
        if (gameRotationVector.size < 4 || gyroRadPerSec.size < 3) {
            return if (gyroRadPerSec.size >= 3) gyroRadPerSec[2].toDouble() else 0.0
        }
        return projectYawRate(
            qx = gameRotationVector[0].toDouble(),
            qy = gameRotationVector[1].toDouble(),
            qz = gameRotationVector[2].toDouble(),
            qw = gameRotationVector[3].toDouble(),
            wx = gyroRadPerSec[0].toDouble(),
            wy = gyroRadPerSec[1].toDouble(),
            wz = gyroRadPerSec[2].toDouble()
        )
    }

    /**
     * Projects DoubleArray inputs from pre-parsed or simulated telemetry.
     */
    fun projectYawRate(gameRotationVector: DoubleArray, gyroRadPerSec: DoubleArray): Double {
        if (gameRotationVector.size < 4 || gyroRadPerSec.size < 3) {
            return if (gyroRadPerSec.size >= 3) gyroRadPerSec[2] else 0.0
        }
        return projectYawRate(
            qx = gameRotationVector[0],
            qy = gameRotationVector[1],
            qz = gameRotationVector[2],
            qw = gameRotationVector[3],
            wx = gyroRadPerSec[0],
            wy = gyroRadPerSec[1],
            wz = gyroRadPerSec[2]
        )
    }

    /**
     * Computes the complete 3D ENU angular velocity vector into a pre-allocated [outEnu] array [East, North, Up].
     */
    fun project3dAngularVelocity(
        qx: Double,
        qy: Double,
        qz: Double,
        qw: Double,
        wx: Double,
        wy: Double,
        wz: Double,
        outEnu: DoubleArray
    ) {
        val normSq = qx * qx + qy * qy + qz * qz + qw * qw
        if (normSq <= 1e-12) {
            outEnu[0] = wx
            outEnu[1] = wy
            outEnu[2] = wz
            return
        }
        val invNorm = 1.0 / sqrt(normSq)
        val nx = qx * invNorm
        val ny = qy * invNorm
        val nz = qz * invNorm
        val nw = qw * invNorm

        // Rotation matrix from body to ENU
        val r00 = 1.0 - 2.0 * (ny * ny + nz * nz)
        val r01 = 2.0 * (nx * ny - nw * nz)
        val r02 = 2.0 * (nx * nz + nw * ny)

        val r10 = 2.0 * (nx * ny + nw * nz)
        val r11 = 1.0 - 2.0 * (nx * nx + nz * nz)
        val r12 = 2.0 * (ny * nz - nw * nx)

        val r20 = 2.0 * (nx * nz - nw * ny)
        val r21 = 2.0 * (ny * nz + nw * nx)
        val r22 = 1.0 - 2.0 * (nx * nx + ny * ny)

        outEnu[0] = r00 * wx + r01 * wy + r02 * wz
        outEnu[1] = r10 * wx + r11 * wy + r12 * wz
        outEnu[2] = r20 * wx + r21 * wy + r22 * wz
    }
}
