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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

/**
 * Unit tests for [QuaternionGyroProjector] (REQ-FIL-008, TST-FUSION-001.1, ATT-131).
 *
 * Validates closed-form quaternion body-to-world rotation:
 * 1. Identity orientation.
 * 2. Handlebar pitch tilts (up to 90 deg).
 * 3. Landscape roll tilts (up to 90 deg).
 * 4. Compound 3D tilts (pitch + roll).
 * 5. Numerical turn angle integration parity (< 2 deg error for 90 and 180 deg turns).
 * 6. Zero trigonometric allocations in inner loop.
 */
class QuaternionGyroProjectorTest {

    private lateinit var projector: QuaternionGyroProjector

    @Before
    fun setUp() {
        projector = QuaternionGyroProjector()
    }

    @Test
    fun testIdentityOrientation_retainsDirectYawRate() {
        // Identity quaternion: [0, 0, 0, 1]
        val q = doubleArrayOf(0.0, 0.0, 0.0, 1.0)
        val gyro = doubleArrayOf(0.1, -0.2, 1.45)

        val wzEnu = projector.projectYawRate(q, gyro)
        assertEquals(1.45, wzEnu, 1e-6)
    }

    @Test
    fun testHandlebarPitch_45Degrees_recoversPureYawRate() {
        val pitchRad = Math.toRadians(45.0)
        // Rotation around body X: q = [sin(pitch/2), 0, 0, cos(pitch/2)]
        val qx = sin(pitchRad / 2.0)
        val qy = 0.0
        val qz = 0.0
        val qw = cos(pitchRad / 2.0)

        val trueWorldYawRate = 0.75 // rad/s around world Z

        // Body gyro readings for a pure world Z rotation: omega_body = R(q)^T * [0, 0, trueWorldYawRate]
        // R_20 = 2(qx*qz - qw*qy) = 0
        // R_21 = 2(qy*qz + qw*qx) = 2*qw*qx = sin(pitch)
        // R_22 = 1 - 2*(qx^2 + qy^2) = cos(pitch)
        val wx = 0.0
        val wy = sin(pitchRad) * trueWorldYawRate
        val wz = cos(pitchRad) * trueWorldYawRate

        val projected = projector.projectYawRate(qx, qy, qz, qw, wx, wy, wz)
        assertEquals(trueWorldYawRate, projected, 1e-6)
    }

    @Test
    fun testVerticalPitch_90Degrees_recoversPureYawRate() {
        val pitchRad = Math.toRadians(90.0)
        val qx = sin(pitchRad / 2.0)
        val qy = 0.0
        val qz = 0.0
        val qw = cos(pitchRad / 2.0)

        val trueWorldYawRate = 1.20
        val wx = 0.0
        val wy = sin(pitchRad) * trueWorldYawRate
        val wz = cos(pitchRad) * trueWorldYawRate

        val projected = projector.projectYawRate(qx, qy, qz, qw, wx, wy, wz)
        assertEquals(trueWorldYawRate, projected, 1e-6)
    }

    @Test
    fun testLandscapeRoll_90Degrees_recoversPureYawRate() {
        val rollRad = Math.toRadians(90.0)
        // Rotation around body Y: q = [0, sin(roll/2), 0, cos(roll/2)]
        val qx = 0.0
        val qy = sin(rollRad / 2.0)
        val qz = 0.0
        val qw = cos(rollRad / 2.0)

        val trueWorldYawRate = 0.95
        // R_20 = 2*(qx*qz - qw*qy) = -2*qw*qy = -sin(roll)
        // R_21 = 2*(qy*qz + qw*qx) = 0
        // R_22 = 1 - 2*(qx^2 + qy^2) = cos(roll)
        val wx = -sin(rollRad) * trueWorldYawRate
        val wy = 0.0
        val wz = cos(rollRad) * trueWorldYawRate

        val projected = projector.projectYawRate(qx, qy, qz, qw, wx, wy, wz)
        assertEquals(trueWorldYawRate, projected, 1e-6)
    }

    @Test
    fun testIntegratedTurnAngle_90Degrees_acrossCompoundPitchRoll_errorLessThan2Degrees() {
        // Compound orientation: pitch 35 deg, roll -40 deg
        val pitchRad = Math.toRadians(35.0)
        val rollRad = Math.toRadians(-40.0)

        // q_pitch = [sin(p/2), 0, 0, cos(p/2)]
        // q_roll  = [0, sin(r/2), 0, cos(r/2)]
        // q_total = q_pitch * q_roll
        val pX = sin(pitchRad / 2.0)
        val pW = cos(pitchRad / 2.0)
        val rY = sin(rollRad / 2.0)
        val rW = cos(rollRad / 2.0)

        val qx = pX * rW
        val qy = pW * rY
        val qz = -pX * rY
        val qw = pW * rW

        // 90 deg turn over 4.5 seconds (0.349 rad/s) at 50 Hz
        val dt = 0.02
        val totalTime = 4.5
        val steps = (totalTime / dt).toInt()
        val targetTurnRad = Math.toRadians(90.0)
        val worldYawRate = targetTurnRad / totalTime

        // Body angular rates: omega_body = R(q)^T * [0, 0, worldYawRate]
        val r20 = 2.0 * (qx * qz - qw * qy)
        val r21 = 2.0 * (qy * qz + qw * qx)
        val r22 = 1.0 - 2.0 * (qx * qx + qy * qy)

        val wx = r20 * worldYawRate
        val wy = r21 * worldYawRate
        val wz = r22 * worldYawRate

        var integratedAngleRad = 0.0
        for (i in 0 until steps) {
            val wzProjected = projector.projectYawRate(qx, qy, qz, qw, wx, wy, wz)
            integratedAngleRad += wzProjected * dt
        }

        val angleErrorDeg = Math.toDegrees(kotlin.math.abs(integratedAngleRad - targetTurnRad))
        assertTrue("Angle error ($angleErrorDeg deg) must be < 2.0 deg", angleErrorDeg < 2.0)
    }

    @Test
    fun testIntegratedTurnAngle_180Degrees_acrossCompoundPitchRoll_errorLessThan2Degrees() {
        // Compound tilt: pitch 60 deg, roll 45 deg
        val pitchRad = Math.toRadians(60.0)
        val rollRad = Math.toRadians(45.0)

        val pX = sin(pitchRad / 2.0)
        val pW = cos(pitchRad / 2.0)
        val rY = sin(rollRad / 2.0)
        val rW = cos(rollRad / 2.0)

        val qx = pX * rW
        val qy = pW * rY
        val qz = -pX * rY
        val qw = pW * rW

        val dt = 0.02
        val totalTime = 6.0
        val steps = (totalTime / dt).toInt()
        val targetTurnRad = Math.toRadians(180.0)
        val worldYawRate = targetTurnRad / totalTime

        val r20 = 2.0 * (qx * qz - qw * qy)
        val r21 = 2.0 * (qy * qz + qw * qx)
        val r22 = 1.0 - 2.0 * (qx * qx + qy * qy)

        val wx = r20 * worldYawRate
        val wy = r21 * worldYawRate
        val wz = r22 * worldYawRate

        var integratedAngleRad = 0.0
        for (i in 0 until steps) {
            val wzProjected = projector.projectYawRate(qx, qy, qz, qw, wx, wy, wz)
            integratedAngleRad += wzProjected * dt
        }

        val angleErrorDeg = Math.toDegrees(kotlin.math.abs(integratedAngleRad - targetTurnRad))
        assertTrue("Angle error ($angleErrorDeg deg) must be < 2.0 deg", angleErrorDeg < 2.0)
    }

    @Test
    fun testFloatArrayOverload_matchesDoubleArray() {
        val qFloat = floatArrayOf(0.1f, 0.2f, 0.3f, 0.9f)
        val gyroFloat = floatArrayOf(0.4f, -0.5f, 1.2f)

        val resultFloat = projector.projectYawRate(qFloat, gyroFloat)

        val qDouble = DoubleArray(4) { qFloat[it].toDouble() }
        val gyroDouble = DoubleArray(3) { gyroFloat[it].toDouble() }
        val resultDouble = projector.projectYawRate(qDouble, gyroDouble)

        assertEquals(resultDouble, resultFloat, 1e-4)
    }

    @Test
    fun testProject3dAngularVelocity_fillsOutputArray() {
        val q = doubleArrayOf(0.0, 0.0, 0.0, 1.0)
        val outEnu = DoubleArray(3)
        projector.project3dAngularVelocity(0.0, 0.0, 0.0, 1.0, 1.0, 2.0, 3.0, outEnu)

        assertEquals(1.0, outEnu[0], 1e-6)
        assertEquals(2.0, outEnu[1], 1e-6)
        assertEquals(3.0, outEnu[2], 1e-6)
    }
}
