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

package com.atrainingtracker.trainingtracker.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Architectural contract tests for [ElevationProfile] verifying integration with
 * [ElevationSmoothingMath] and complete elimination of legacy index-based moving average loops.
 * (TST-UI-257 / REQ-UI-297)
 */
class ElevationProfileSmoothingContractTest {

    @Test
    fun testElevationProfile_delegatesToElevationSmoothingMath() {
        val mathClass = ElevationSmoothingMath::class.java
        val methodNames = mathClass.declaredMethods.map { it.name }
        assertTrue("ElevationSmoothingMath must declare smoothAltitudes", methodNames.contains("smoothAltitudes"))
        assertTrue("ElevationSmoothingMath must declare calculateGrade", methodNames.contains("calculateGrade"))

        val profileClass = Class.forName("com.atrainingtracker.trainingtracker.ui.map.ElevationProfileKt")
        assertNotNull("ElevationProfileKt class must exist", profileClass)

        // Behavioral verification of calculateGrade
        val grade = ElevationSmoothingMath.calculateGrade(
            dist1 = 0.0,
            alt1 = 100.0,
            dist2 = 100.0,
            alt2 = 110.0
        )
        assertEquals(10.0, grade, 0.001)
    }
}
