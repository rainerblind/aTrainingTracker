/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.atrainingtracker.trainingtracker.ui.settings.tuning

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Structural contract test verifying that AdvancedTuningDialog and AftermathAnalysisSection
 * host the elevation profile smoothing sigma slider, wire state to TuningConfig, and
 * include the sigma indicator in the section subtitle (REQ-UI-297, TST-UI-257.4, ATT-2746).
 */
class AdvancedTuningElevationSmoothingContractTest {

    private fun findSourceContent(): String {
        val candidates = listOf(
            File("app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning"),
            File("src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning"),
            File("../app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning")
        )
        val dir = candidates.firstOrNull { it.exists() && it.isDirectory }
            ?: return ""
        val dialogFile = File(dir, "AdvancedTuningDialog.kt")
        val aftermathFile = File(dir, "categories/AftermathAnalysisSection.kt")
        val accordionFile = File(dir, "AdvancedTuningAccordion.kt")
        return (if (dialogFile.exists()) dialogFile.readText() else "") + "\n" +
                (if (aftermathFile.exists()) aftermathFile.readText() else "") + "\n" +
                (if (accordionFile.exists()) accordionFile.readText() else "")
    }

    @Test
    fun testAdvancedTuningDialog_hostsElevationSmoothingSigmaSlider() {
        val content = findSourceContent()

        // 1. Resource strings referenced
        assertTrue(
            "AftermathAnalysisSection must reference tuning_elevation_smoothing_sigma_title",
            content.contains("R.string.tuning_elevation_smoothing_sigma_title")
        )
        assertTrue(
            "AftermathAnalysisSection must reference tuning_elevation_smoothing_sigma_desc",
            content.contains("R.string.tuning_elevation_smoothing_sigma_desc")
        )

        // 2. State management in dialog
        assertTrue(
            "AdvancedTuningDialog must manage elevationSmoothingSigmaMeters state",
            content.contains("elevationSmoothingSigmaMeters")
        )

        // 3. Slider parameters in AftermathAnalysisSection
        assertTrue(
            "AftermathAnalysisSection must specify MIN_ELEVATION_SMOOTHING_SIGMA_METERS",
            content.contains("MIN_ELEVATION_SMOOTHING_SIGMA_METERS")
        )
        assertTrue(
            "AftermathAnalysisSection must specify MAX_ELEVATION_SMOOTHING_SIGMA_METERS",
            content.contains("MAX_ELEVATION_SMOOTHING_SIGMA_METERS")
        )

        // 4. Subtitle formatting includes sigma symbol
        assertTrue(
            "TuningSubtitleFormatter must format smoothing sigma with \\u03c3",
            content.contains("\\u03c3:") || content.contains("σ:")
        )
    }
}
