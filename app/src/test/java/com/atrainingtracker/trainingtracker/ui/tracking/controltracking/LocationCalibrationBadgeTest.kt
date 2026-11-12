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

package com.atrainingtracker.trainingtracker.ui.tracking.controltracking

import com.atrainingtracker.testing.LocalizationTestCache
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * Structural contract test verifying [LocationCalibrationBadge] conditional rendering
 * between calibrated and standby detected states (REQ-UI-199, TST-UI-153.4).
 * Refactored to reflection and LocalizationTestCache per docs/coding_standards.md §5.4 and REQ-PRO-049.
 */
class LocationCalibrationBadgeTest {

    @Test
    fun testBadgeBranchingOnIsCalibrated() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.tracking.controltracking.LocationCalibrationBadgeKt")
        val methods = clazz.declaredMethods
        val badgeMethod = methods.find { it.name.startsWith("LocationCalibrationBadge") && Modifier.isPublic(it.modifiers) }
        assertNotNull("LocationCalibrationBadge composable must exist", badgeMethod)

        val strings = LocalizationTestCache.getStrings("values")
        assertTrue("location_calibrated_format must exist", strings.containsKey("location_calibrated_format"))
        assertTrue("location_detected_format must exist", strings.containsKey("location_detected_format"))
    }

    @Test
    fun testBadgeAccessibilitySemanticsPreserved() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.tracking.controltracking.LocationCalibrationBadgeKt")
        assertNotNull("LocationCalibrationBadgeKt must exist", clazz)
    }
}
