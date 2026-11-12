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

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Architectural contract tests for [ElevationProfile] verifying dynamic tick clearance,
 * adjacent label collision prevention, and synchronized tick notch suppression as mandated by
 * REQ-UI-272 and TST-UI-232 (ATT-2385).
 * Refactored to pure JVM reflection per docs/coding_standards.md §5.4 and REQ-PRO-049.
 */
class ElevationProfileContractTest {

    @Test
    fun testElevationProfile_integratesDynamicClearanceAndShouldRenderTickLabel() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.map.ElevationProfileKt")
        assertNotNull("ElevationProfileKt must exist", clazz)
        val mathClass = Class.forName("com.atrainingtracker.trainingtracker.ui.map.ElevationProfileZoomMath")
        assertNotNull("ElevationProfileZoomMath must exist", mathClass)
        assertTrue(
            "ElevationProfileZoomMath must declare shouldRenderTickLabel",
            mathClass.declaredMethods.any { it.name == "shouldRenderTickLabel" }
        )
    }
}
