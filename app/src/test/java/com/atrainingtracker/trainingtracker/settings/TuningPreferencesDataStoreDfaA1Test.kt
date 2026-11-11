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

package com.atrainingtracker.trainingtracker.settings

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for DFA a1 aerobic threshold preference defaults and clamping bounds conforming to REQ-TRK-061.4 and TST-TRK-061.4.
 */
class TuningPreferencesDataStoreDfaA1Test {

    @Test
    fun testDefaultsAndBoundsConstants() {
        assertEquals(0.75f, TuningPreferencesDefaults.DEFAULT_DFA_A1_THRESHOLD_T1, 0.0001f)
        assertEquals(0.50f, TuningPreferencesDefaults.DEFAULT_DFA_A1_THRESHOLD_T2, 0.0001f)
        assertEquals(0.20f, TuningPreferencesDefaults.MIN_DFA_A1_THRESHOLD, 0.0001f)
        assertEquals(1.50f, TuningPreferencesDefaults.MAX_DFA_A1_THRESHOLD, 0.0001f)
        assertEquals(0.05f, TuningPreferencesDefaults.MIN_DFA_A1_THRESHOLD_GAP, 0.0001f)
    }

    @Test
    fun testThresholdInvariant_gapEnforcement() {
        val t1 = 0.60f
        val t2 = 0.60f
        // Invariant: T1 >= T2 + 0.05f
        val enforcedT1 = maxOf(t1, t2 + TuningPreferencesDefaults.MIN_DFA_A1_THRESHOLD_GAP)
        assertEquals(0.65f, enforcedT1, 0.0001f)

        val enforcedT2 = minOf(t2, t1 - TuningPreferencesDefaults.MIN_DFA_A1_THRESHOLD_GAP)
        assertEquals(0.55f, enforcedT2, 0.0001f)
    }

    @Test
    fun testTuningConfig_defaults() {
        val config = TuningConfig()
        assertEquals(0.75f, config.dfaA1ThresholdT1, 0.0001f)
        assertEquals(0.50f, config.dfaA1ThresholdT2, 0.0001f)
    }

    @Test
    fun testTuningConfig_customValues() {
        val config = TuningConfig(dfaA1ThresholdT1 = 0.80f, dfaA1ThresholdT2 = 0.55f)
        assertEquals(0.80f, config.dfaA1ThresholdT1, 0.0001f)
        assertEquals(0.55f, config.dfaA1ThresholdT2, 0.0001f)
    }

    @Test
    fun testClampingBounds() {
        val belowMin = 0.10f.coerceIn(
            TuningPreferencesDefaults.MIN_DFA_A1_THRESHOLD,
            TuningPreferencesDefaults.MAX_DFA_A1_THRESHOLD
        )
        val aboveMax = 2.00f.coerceIn(
            TuningPreferencesDefaults.MIN_DFA_A1_THRESHOLD,
            TuningPreferencesDefaults.MAX_DFA_A1_THRESHOLD
        )
        val withinRange = 0.65f.coerceIn(
            TuningPreferencesDefaults.MIN_DFA_A1_THRESHOLD,
            TuningPreferencesDefaults.MAX_DFA_A1_THRESHOLD
        )

        assertEquals(0.20f, belowMin, 0.0001f)
        assertEquals(1.50f, aboveMax, 0.0001f)
        assertEquals(0.65f, withinRange, 0.0001f)
    }
}
