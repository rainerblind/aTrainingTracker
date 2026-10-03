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

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Structural contract tests verifying that AdvancedTuningDialog hosts the pace ceiling slider
 * in Section 4 (Aftermath & Analyse) and that MapDetailLayout and TelemetryMetricGraph wire
 * the configurable pace ceiling (TST-UI-202.2 / REQ-UI-243).
 */
class AdvancedTuningPaceCeilingContractTest {

    private fun findFile(relativePath: String): File {
        val candidates = listOf(
            File(relativePath),
            File("app/$relativePath"),
            File("../$relativePath")
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("$relativePath not found in candidates: $candidates")
    }

    @Test
    fun testAdvancedTuningDialog_hostsPaceCeilingSlider() {
        val dialogFile = findFile("src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningDialog.kt")
        val aftermathFileCandidates = listOf(
            File("src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/categories/AftermathAnalysisSection.kt"),
            File("app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/categories/AftermathAnalysisSection.kt"),
            File("../app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/categories/AftermathAnalysisSection.kt")
        )
        val aftermathFile = aftermathFileCandidates.firstOrNull { it.exists() }
        val content = dialogFile.readText() + "\n" + (aftermathFile?.readText() ?: "")

        assertTrue(
            "AdvancedTuningDialog must reference tuning_pace_ceiling_title",
            content.contains("R.string.tuning_pace_ceiling_title")
        )
        assertTrue(
            "AdvancedTuningDialog must reference tuning_pace_ceiling_desc",
            content.contains("R.string.tuning_pace_ceiling_desc")
        )
        assertTrue(
            "AdvancedTuningDialog must reference TuningPaceCeilingFormatter",
            content.contains("TuningPaceCeilingFormatter.formatPaceCeiling")
        )
        assertTrue(
            "AdvancedTuningDialog must manage paceCeilingMinKm state",
            content.contains("paceCeilingMinKm")
        )
        assertTrue(
            "AftermathAnalysisSection must configure slider with 15 steps",
            content.contains("steps = 15")
        )
    }

    @Test
    fun testMapDetailLayout_forwardsPaceCeiling() {
        val file = findFile("src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayout.kt")
        val content = file.readText()

        assertTrue(
            "MapDetailLayout must forward tuningConfig.paceCeilingMinKm to TelemetryMetricGraph",
            content.contains("paceCeilingMinKm = tuningConfig.paceCeilingMinKm")
        )
    }

    @Test
    fun testTelemetryMetricGraph_declaresPaceCeilingParameterAndEliminatesHardcodedCeiling() {
        val file = findFile("src/main/java/com/atrainingtracker/trainingtracker/ui/map/TelemetryMetricGraph.kt")
        val content = file.readText()

        assertTrue(
            "TelemetryMetricGraph must declare paceCeilingMinKm parameter",
            content.contains("paceCeilingMinKm: Float = TuningPreferencesDefaults.DEFAULT_PACE_CEILING_MIN_KM")
        )
        assertTrue(
            "extractMetricValue must declare paceCeilingMinKm parameter",
            content.contains("paceCeilingMinKm: Float = TuningPreferencesDefaults.DEFAULT_PACE_CEILING_MIN_KM")
        )
        assertFalse(
            "TelemetryMetricGraph must NOT contain hardcoded coerceIn(1.5, 20.0)",
            content.contains("coerceIn(1.5, 20.0)")
        )
        assertFalse(
            "TelemetryMetricGraph must NOT contain hardcoded coerceAtLeast(1.5)",
            content.contains("coerceAtLeast(1.5)")
        )
    }
}
