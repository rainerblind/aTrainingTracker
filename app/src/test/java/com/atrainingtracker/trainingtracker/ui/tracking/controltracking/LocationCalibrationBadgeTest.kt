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

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Structural contract test verifying [LocationCalibrationBadge] conditional rendering
 * between calibrated and standby detected states (REQ-UI-199, TST-UI-153.4).
 */
class LocationCalibrationBadgeTest {

    private fun loadSource(): String {
        val candidates = listOf(
            File("app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/LocationCalibrationBadge.kt"),
            File("src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/LocationCalibrationBadge.kt"),
            File("../app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/LocationCalibrationBadge.kt")
        )
        val file = candidates.firstOrNull { it.exists() }
            ?: error("LocationCalibrationBadge.kt not found in candidates: $candidates")
        return file.readText()
    }

    @Test
    fun testBadgeBranchingOnIsCalibrated() {
        val content = loadSource()

        // Verify status.isCalibrated condition exists
        assertTrue(
            "LocationCalibrationBadge must branch on status.isCalibrated",
            content.contains("if (status.isCalibrated)")
        )

        // Verify calibrated format is used when true
        assertTrue(
            "LocationCalibrationBadge must reference R.string.location_calibrated_format when calibrated",
            content.contains("R.string.location_calibrated_format")
        )

        // Verify standby detected format is used when false
        assertTrue(
            "LocationCalibrationBadge must reference R.string.location_detected_format when uncalibrated/standby",
            content.contains("R.string.location_detected_format")
        )
    }

    @Test
    fun testBadgeAccessibilitySemanticsPreserved() {
        val content = loadSource()

        // Verify semantics or contentDescription is preserved
        assertTrue(
            "LocationCalibrationBadge must maintain accessibility contentDescription",
            content.contains("contentDescription")
        )
    }
}
