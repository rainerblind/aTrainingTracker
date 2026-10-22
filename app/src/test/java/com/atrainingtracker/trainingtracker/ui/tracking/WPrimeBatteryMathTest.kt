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

package com.atrainingtracker.trainingtracker.ui.tracking

import com.atrainingtracker.trainingtracker.ui.theme.TTColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [WPrimeBatteryMath] verifying mathematical normalization,
 * discrete warning levels, clamping bounds, stale transitions, and denominator stability
 * conforming to REQ-UI-416.2 and TST-UI-379.1.
 */
class WPrimeBatteryMathTest {

    @Test
    fun testBatteryNormalizationAcrossLevels() {
        val now = 10_000L

        // 1. Normal (> 30%): 18.0 kJ / 20.0 kJ -> 90%
        val stateNormal = WPrimeBatteryMath.calculateBatteryState(
            remainingKj = 18.0,
            totalKj = 20.0,
            timestampMs = now,
            nowMs = now
        )
        assertEquals(18.0, stateNormal.remainingKj!!, 1e-4)
        assertEquals(20.0, stateNormal.totalKj, 1e-4)
        assertEquals(90, stateNormal.percentage)
        assertEquals(0.90f, stateNormal.batteryFraction!!, 1e-4f)
        assertEquals(WPrimeWarningLevel.NORMAL, stateNormal.warningLevel)
        assertEquals(TTColor.WPrimeBalGreen, stateNormal.batteryColor)
        assertFalse(stateNormal.isWarning)
        assertFalse(stateNormal.isStale)

        // 2. Low (20% .. 30%): 5.0 kJ / 20.0 kJ -> 25%
        val stateLow = WPrimeBatteryMath.calculateBatteryState(
            remainingKj = 5.0,
            totalKj = 20.0,
            timestampMs = now,
            nowMs = now
        )
        assertEquals(5.0, stateLow.remainingKj!!, 1e-4)
        assertEquals(25, stateLow.percentage)
        assertEquals(0.25f, stateLow.batteryFraction!!, 1e-4f)
        assertEquals(WPrimeWarningLevel.LOW, stateLow.warningLevel)
        assertEquals(TTColor.Zone3, stateLow.batteryColor)
        assertFalse(stateLow.isWarning)
        assertFalse(stateLow.isStale)

        // 3. Critical (> 0% .. < 20%): 3.0 kJ / 20.0 kJ -> 15%
        val stateCritical = WPrimeBatteryMath.calculateBatteryState(
            remainingKj = 3.0,
            totalKj = 20.0,
            timestampMs = now,
            nowMs = now
        )
        assertEquals(3.0, stateCritical.remainingKj!!, 1e-4)
        assertEquals(15, stateCritical.percentage)
        assertEquals(0.15f, stateCritical.batteryFraction!!, 1e-4f)
        assertEquals(WPrimeWarningLevel.CRITICAL, stateCritical.warningLevel)
        assertEquals(TTColor.Zone5, stateCritical.batteryColor)
        assertTrue(stateCritical.isWarning)
        assertFalse(stateCritical.isStale)

        // 4. Depleted (<= 0%): 0.0 kJ / 20.0 kJ -> 0%
        val stateDepleted = WPrimeBatteryMath.calculateBatteryState(
            remainingKj = 0.0,
            totalKj = 20.0,
            timestampMs = now,
            nowMs = now
        )
        assertEquals(0.0, stateDepleted.remainingKj!!, 1e-4)
        assertEquals(0, stateDepleted.percentage)
        assertEquals(0.0f, stateDepleted.batteryFraction!!, 1e-4f)
        assertEquals(WPrimeWarningLevel.DEPLETED, stateDepleted.warningLevel)
        assertEquals(TTColor.Zone5, stateDepleted.batteryColor)
        assertTrue(stateDepleted.isWarning)
        assertFalse(stateDepleted.isStale)
    }

    @Test
    fun testClampingBounds() {
        val now = 10_000L

        // Remaining > Total: 25.0 kJ / 20.0 kJ -> 100%, fraction 1.0f
        val stateOver = WPrimeBatteryMath.calculateBatteryState(
            remainingKj = 25.0,
            totalKj = 20.0,
            timestampMs = now,
            nowMs = now
        )
        assertEquals(20.0, stateOver.remainingKj!!, 1e-4)
        assertEquals(100, stateOver.percentage)
        assertEquals(1.0f, stateOver.batteryFraction!!, 1e-4f)
        assertEquals(WPrimeWarningLevel.NORMAL, stateOver.warningLevel)

        // Remaining < 0: -5.0 kJ / 20.0 kJ -> 0%, fraction 0.0f
        val stateNegative = WPrimeBatteryMath.calculateBatteryState(
            remainingKj = -5.0,
            totalKj = 20.0,
            timestampMs = now,
            nowMs = now
        )
        assertEquals(0.0, stateNegative.remainingKj!!, 1e-4)
        assertEquals(0, stateNegative.percentage)
        assertEquals(0.0f, stateNegative.batteryFraction!!, 1e-4f)
        assertEquals(WPrimeWarningLevel.DEPLETED, stateNegative.warningLevel)
        assertTrue(stateNegative.isWarning)
    }

    @Test
    fun testStaleTransition() {
        val timestamp = 10_000L

        // Fresh telemetry: exactly 3000 ms delta -> NOT stale
        val stateFresh = WPrimeBatteryMath.calculateBatteryState(
            remainingKj = 15.0,
            totalKj = 20.0,
            timestampMs = timestamp,
            nowMs = timestamp + 3000L
        )
        assertFalse(stateFresh.isStale)
        assertEquals(0.75f, stateFresh.batteryFraction!!, 1e-4f)

        // Expired telemetry: 3001 ms delta -> STALE
        val stateExpired = WPrimeBatteryMath.calculateBatteryState(
            remainingKj = 15.0,
            totalKj = 20.0,
            timestampMs = timestamp,
            nowMs = timestamp + 3001L
        )
        assertTrue(stateExpired.isStale)
        assertNull(stateExpired.batteryFraction)
        assertEquals(0, stateExpired.percentage)
        assertFalse("Stale state must not trigger warning alert", stateExpired.isWarning)

        // Null telemetry
        val stateNull = WPrimeBatteryMath.calculateBatteryState(
            remainingKj = null,
            totalKj = 20.0,
            timestampMs = timestamp,
            nowMs = timestamp
        )
        assertTrue(stateNull.isStale)
        assertNull(stateNull.batteryFraction)

        // Non-finite telemetry (NaN / Infinity)
        val stateNaN = WPrimeBatteryMath.calculateBatteryState(
            remainingKj = Double.NaN,
            totalKj = 20.0,
            timestampMs = timestamp,
            nowMs = timestamp
        )
        assertTrue(stateNaN.isStale)

        val stateInf = WPrimeBatteryMath.calculateBatteryState(
            remainingKj = Double.POSITIVE_INFINITY,
            totalKj = 20.0,
            timestampMs = timestamp,
            nowMs = timestamp
        )
        assertTrue(stateInf.isStale)
    }

    @Test
    fun testMidSessionParameterChangeClamping() {
        val now = 10_000L
        val remaining = 10.0 // 10 kJ remaining

        // Baseline 20 kJ -> 50%
        val state20 = WPrimeBatteryMath.calculateBatteryState(remaining, 20.0, now, now)
        assertEquals(0.50f, state20.batteryFraction!!, 1e-4f)
        assertEquals(50, state20.percentage)

        // Baseline changed to 25 kJ -> 40%
        val state25 = WPrimeBatteryMath.calculateBatteryState(remaining, 25.0, now, now)
        assertEquals(0.40f, state25.batteryFraction!!, 1e-4f)
        assertEquals(40, state25.percentage)

        // Baseline changed to 15 kJ -> 67%
        val state15 = WPrimeBatteryMath.calculateBatteryState(remaining, 15.0, now, now)
        assertEquals(10.0f / 15.0f, state15.batteryFraction!!, 1e-4f)
        assertEquals(67, state15.percentage)

        // Baseline reduced below remaining (e.g. 8 kJ) -> clamped to 8 kJ (100%)
        val state8 = WPrimeBatteryMath.calculateBatteryState(remaining, 8.0, now, now)
        assertEquals(8.0, state8.remainingKj!!, 1e-4)
        assertEquals(1.0f, state8.batteryFraction!!, 1e-4f)
        assertEquals(100, state8.percentage)
    }

    @Test
    fun testDenominatorChangeStability() {
        val now = 10_000L
        val remaining = 10.0

        // As capacity changes 20 -> 25 -> 15 kJ, batteryFraction smoothly updates proportionally
        val f1 = WPrimeBatteryMath.calculateBatteryState(remaining, 20.0, now, now).batteryFraction!!
        val f2 = WPrimeBatteryMath.calculateBatteryState(remaining, 25.0, now, now).batteryFraction!!
        val f3 = WPrimeBatteryMath.calculateBatteryState(remaining, 15.0, now, now).batteryFraction!!

        assertEquals(0.50f, f1, 1e-4f)
        assertEquals(0.40f, f2, 1e-4f)
        assertEquals(0.6667f, f3, 1e-4f)
        assertTrue("Fraction must be between 0 and 1 without reset", f1 in 0f..1f && f2 in 0f..1f && f3 in 0f..1f)
    }

    @Test
    fun testZeroOrNegativeTotalKj() {
        val now = 10_000L
        val stateZero = WPrimeBatteryMath.calculateBatteryState(10.0, 0.0, now, now)
        assertTrue(stateZero.isStale)
        assertNull(stateZero.batteryFraction)

        val stateNegative = WPrimeBatteryMath.calculateBatteryState(10.0, -10.0, now, now)
        assertTrue(stateNegative.isStale)
        assertNull(stateNegative.batteryFraction)
    }
}
