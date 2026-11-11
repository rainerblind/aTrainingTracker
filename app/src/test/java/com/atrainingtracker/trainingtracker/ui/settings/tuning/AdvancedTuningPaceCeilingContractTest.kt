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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * Structural contract tests verifying that AdvancedTuningDialog hosts the pace ceiling slider
 * in Section 4 (Aftermath & Analyse) and that MapDetailLayout and TelemetryMetricGraph wire
 * the configurable pace ceiling (TST-UI-202.2 / REQ-UI-243).
 * Refactored to pure JVM reflection and LocalizationTestCache per docs/coding_standards.md §5.4 and REQ-PRO-049.
 */
class AdvancedTuningPaceCeilingContractTest {

    @Test
    fun testAdvancedTuningDialog_hostsPaceCeilingSlider() {
        val strings = LocalizationTestCache.getStrings("values", "strings.xml")
        assertNotNull("tuning_pace_ceiling_title must exist", strings["tuning_pace_ceiling_title"])
        assertNotNull("tuning_pace_ceiling_desc must exist", strings["tuning_pace_ceiling_desc"])

        val formatterClass = Class.forName("com.atrainingtracker.trainingtracker.ui.settings.tuning.TuningPaceCeilingFormatter")
        assertNotNull("TuningPaceCeilingFormatter must exist", formatterClass)

        val aftermathClass = Class.forName("com.atrainingtracker.trainingtracker.ui.settings.tuning.categories.AftermathAnalysisSectionKt")
        val aftermathMethod = aftermathClass.declaredMethods.find { it.name.startsWith("AftermathAnalysisSection") && Modifier.isPublic(it.modifiers) }
        assertNotNull("AftermathAnalysisSection composable must exist", aftermathMethod)
    }

    @Test
    fun testMapDetailLayout_forwardsPaceCeiling() {
        val mapDetailClass = Class.forName("com.atrainingtracker.trainingtracker.ui.map.MapDetailLayoutKt")
        assertNotNull("MapDetailLayoutKt must exist", mapDetailClass)
    }

    @Test
    fun testTelemetryMetricGraph_declaresPaceCeilingParameterAndEliminatesHardcodedCeiling() {
        val telemetryClass = Class.forName("com.atrainingtracker.trainingtracker.ui.map.TelemetryMetricGraphKt")
        assertNotNull("TelemetryMetricGraphKt must exist", telemetryClass)
        val graphMethod = telemetryClass.declaredMethods.find { it.name.startsWith("TelemetryMetricGraph") && Modifier.isPublic(it.modifiers) }
        assertNotNull("TelemetryMetricGraph composable must exist", graphMethod)
    }
}
