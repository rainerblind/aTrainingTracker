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

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Architectural contract tests for [ElevationProfile] verifying integration with
 * [ElevationSmoothingMath] and complete elimination of legacy index-based moving average loops.
 * (TST-UI-257 / REQ-UI-297)
 */
class ElevationProfileSmoothingContractTest {

    private val projectRoot: File by lazy {
        var dir = File(System.getProperty("user.dir") ?: ".")
        while (!File(dir, "app").exists() && dir.parentFile != null) {
            dir = dir.parentFile!!
        }
        dir
    }

    private val elevationProfileFile: File by lazy {
        File(projectRoot, "app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/ElevationProfile.kt")
    }

    @Test
    fun testElevationProfile_delegatesToElevationSmoothingMath() {
        assertTrue("ElevationProfile.kt must exist", elevationProfileFile.exists())
        val content = elevationProfileFile.readText()

        // 1. Must invoke ElevationSmoothingMath.smoothAltitudes with sigma = effectiveSigma
        assertTrue(
            "ElevationProfile must invoke ElevationSmoothingMath.smoothAltitudes with sigma",
            content.contains("ElevationSmoothingMath.smoothAltitudes(pathPointsDownsampled, sigma = effectiveSigma)")
        )

        // 2. Must wire TuningPreferencesDataStore and allow smoothingSigma override
        assertTrue(
            "ElevationProfile must observe TuningPreferencesDataStore tuningConfigFlow",
            content.contains("tuningDataStore.tuningConfigFlow")
        )
        assertTrue(
            "ElevationProfile must resolve effectiveSigma with fallback to elevationSmoothingSigmaMeters",
            content.contains("val effectiveSigma = smoothingSigma ?: tuningConfig.elevationSmoothingSigmaMeters.toDouble()")
        )

        // 3. Must invoke ElevationSmoothingMath.calculateGrade
        assertTrue(
            "ElevationProfile must invoke ElevationSmoothingMath.calculateGrade",
            content.contains("ElevationSmoothingMath.calculateGrade")
        )

        // 3. Must NOT contain legacy index-based window calculations
        assertFalse(
            "ElevationProfile must not contain targetWindowMeters",
            content.contains("targetWindowMeters")
        )
        assertFalse(
            "ElevationProfile must not contain calculatedWindow",
            content.contains("calculatedWindow")
        )
        assertFalse(
            "ElevationProfile must not contain legacy index loop",
            content.contains("for (j in start..end)")
        )
    }
}
