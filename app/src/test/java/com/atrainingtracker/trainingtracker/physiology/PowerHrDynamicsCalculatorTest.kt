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

package com.atrainingtracker.trainingtracker.physiology

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.exp

class PowerHrDynamicsCalculatorTest {

    @Test
    fun testSyntheticStepResponseIdentification() {
        val n = 3600 // 1 hour at 1 Hz
        val trueTau = 35.0
        val trueHr0 = 85.0
        val trueK = 0.20 // 5.0 W/bpm

        val power = ArrayList<Double?>(n)
        val hr = ArrayList<Double?>(n)

        var x = 150.0
        val alpha = exp(-1.0 / trueTau)

        for (i in 0 until n) {
            // Power alternating between 150W and 250W
            val p = if ((i / 300) % 2 == 0) 150.0 else 250.0
            x = alpha * x + (1.0 - alpha) * p
            val h = trueHr0 + trueK * x

            power.add(p)
            hr.add(h)
        }

        val result = PowerHrDynamicsCalculator.calculate(power, hr, workoutId = 42L)
        assertNotNull("Identification result must not be null for valid 1h workout", result)
        val r = checkNotNull(result)

        assertEquals(42L, r.workoutId)
        assertEquals(trueTau, r.tauStart, 2.0)
        assertEquals(trueHr0, r.hr0Start, 2.0)
        assertEquals(trueK, r.kStart, 0.02)
        assertEquals(trueHr0 + 200.0 * trueK, r.hr200Start, 2.0)
        assertTrue("R^2 must be >= 0.98 for clean synthetic signal", r.r2Start >= 0.98)
        assertTrue("RMSE must be < 1.0 bpm for clean signal", r.rmseStart < 1.0)

        // End window should match start window since no fatigue was injected
        assertNotNull(r.tauEnd)
        assertNotNull(r.hr200End)
        assertEquals(r.hr200Start, checkNotNull(r.hr200End), 2.5)
        assertEquals(0.0, checkNotNull(r.deltaHr200), 2.5)
    }

    @Test
    fun testCardiacDriftIdentification() {
        val n = 3600 // 1 hour
        val startTau = 30.0
        val startHr0 = 80.0
        val startK = 0.20

        val power = ArrayList<Double?>(n)
        val hr = ArrayList<Double?>(n)

        var x = 180.0
        val alpha = exp(-1.0 / startTau)

        for (i in 0 until n) {
            val p = if ((i / 200) % 2 == 0) 160.0 else 240.0
            x = alpha * x + (1.0 - alpha) * p

            // Inject 18 bpm cardiac drift ramping linearly over the final 30 minutes
            val drift = if (i > 1800) ((i - 1800).toDouble() / (n - 1800)) * 18.0 else 0.0
            val h = startHr0 + startK * x + drift

            power.add(p)
            hr.add(h)
        }

        val result = PowerHrDynamicsCalculator.calculate(power, hr)
        assertNotNull(result)
        val r = checkNotNull(result)

        assertTrue("HR@200W end should be higher than start due to cardiac drift", (r.hr200End ?: 0.0) > r.hr200Start)
        assertTrue("Delta HR@200W must be positive", (r.deltaHr200 ?: 0.0) > 5.0)
    }

    @Test
    fun testInsufficientDurationRejection() {
        // Less than 20 minutes (1,199 samples)
        val shortP = List(1199) { 200.0 }
        val shortHr = List(1199) { 140.0 }
        assertNull("Must reject workouts shorter than 20 min", PowerHrDynamicsCalculator.calculate(shortP, shortHr))
    }

    @Test
    fun testNullOrEmptyInputHandling() {
        assertNull(PowerHrDynamicsCalculator.calculate(null, emptyList()))
        assertNull(PowerHrDynamicsCalculator.calculate(emptyList(), null))
        assertNull(PowerHrDynamicsCalculator.calculate(null, null))
    }

    @Test
    fun testZeroPowerVarianceRejection() {
        // Completely constant power (zero variance)
        val constP = List(1800) { 200.0 }
        val constHr = List(1800) { 140.0 }
        assertNull("Must reject zero power variance", PowerHrDynamicsCalculator.calculate(constP, constHr))
    }

    @Test
    fun testTrajectorySimulation() {
        val power = doubleArrayOf(100.0, 200.0, 200.0, 200.0, 100.0)
        val trajectory = PowerHrDynamicsCalculator.simulateTrajectory(
            power = power,
            tau = 10.0,
            hr0 = 60.0,
            k = 0.5
        )

        assertEquals(5, trajectory.size)
        // t=0: initial state x_0 = 100, HR = 60 + 0.5*100 = 110.0
        assertEquals(110.0, trajectory[0], 0.001)
        // t=1: x_1 = exp(-0.1)*100 + (1-exp(-0.1))*200 > 100
        assertTrue("HR must rise toward target", trajectory[1] > 110.0)
        assertTrue("HR must rise further at steady 200W", trajectory[2] > trajectory[1])
    }
}
