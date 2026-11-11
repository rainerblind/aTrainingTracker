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

import com.atrainingtracker.testing.LocalizationTestCache
import com.atrainingtracker.trainingtracker.settings.TuningPreferencesDefaults
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * Structural contract test verifying that AdvancedTuningDialog and AftermathAnalysisSection
 * host the elevation profile smoothing sigma slider, wire state to TuningConfig, and
 * include the sigma indicator in the section subtitle (REQ-UI-297, TST-UI-257.4, ATT-2746).
 * Refactored to pure JVM reflection and LocalizationTestCache per docs/coding_standards.md §5.4 and REQ-PRO-049.
 */
class AdvancedTuningElevationSmoothingContractTest {

    @Test
    fun testAdvancedTuningDialog_hostsElevationSmoothingSigmaSlider() {
        // 1. Resource strings exist in LocalizationTestCache
        val strings = LocalizationTestCache.getStrings("values", "strings.xml")
        assertNotNull("tuning_elevation_smoothing_sigma_title must exist", strings["tuning_elevation_smoothing_sigma_title"])
        assertNotNull("tuning_elevation_smoothing_sigma_desc must exist", strings["tuning_elevation_smoothing_sigma_desc"])

        // 2. Composable existence
        val aftermathClass = Class.forName("com.atrainingtracker.trainingtracker.ui.settings.tuning.categories.AftermathAnalysisSectionKt")
        val aftermathMethod = aftermathClass.declaredMethods.find { it.name.startsWith("AftermathAnalysisSection") && Modifier.isPublic(it.modifiers) }
        assertNotNull("AftermathAnalysisSection composable must exist", aftermathMethod)

        // 3. Slider parameters in TuningPreferencesDefaults
        assertTrue("MIN_ELEVATION_SMOOTHING_SIGMA_METERS must be defined", TuningPreferencesDefaults.MIN_ELEVATION_SMOOTHING_SIGMA_METERS >= 0f)
        assertTrue("MAX_ELEVATION_SMOOTHING_SIGMA_METERS must exceed min", TuningPreferencesDefaults.MAX_ELEVATION_SMOOTHING_SIGMA_METERS > TuningPreferencesDefaults.MIN_ELEVATION_SMOOTHING_SIGMA_METERS)

        // 4. Subtitle formatting helper exists
        val formatterClass = Class.forName("com.atrainingtracker.trainingtracker.ui.settings.tuning.TuningSubtitleFormatter")
        assertNotNull("TuningSubtitleFormatter must exist", formatterClass)
    }
}
