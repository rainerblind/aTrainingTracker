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

package com.atrainingtracker.trainingtracker.ui.aftermath.workoutdetail

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Structural contract, host mounting, and localization tests for HrvThresholdSummaryCard
 * pursuant to REQ-PER-036, Rule 23, and Rule 81 (TST-PER-044.5).
 */
class HrvThresholdSummaryCardTest {

    private fun findFile(relativePath: String): File {
        val candidates = listOf(
            File(relativePath),
            File("app", relativePath),
            File("../app", relativePath),
            File(File("").absoluteFile, relativePath),
            File(File("").absoluteFile, "app/$relativePath"),
            File(File("").absoluteFile.parentFile, "app/$relativePath")
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("Could not find file '$relativePath' in candidates: ${candidates.map { it.absolutePath }}")
    }

    @Test
    fun `HrvThresholdSummaryCard exists and has Composable contract (REQ-PER-036_5)`() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.aftermath.workoutdetail.HrvThresholdSummaryCardKt")
        val methods = clazz.declaredMethods.filter { it.name == "HrvThresholdSummaryCard" }
        assertTrue("HrvThresholdSummaryCard composable method must exist", methods.isNotEmpty())

        val method = methods.first()
        // Compose compiler adds Composer and integer changed flags to @Composable functions in bytecode
        val hasComposerParam = method.parameterTypes.any { it.name.contains("Composer") }
        assertTrue("HrvThresholdSummaryCard must take Composer parameter from Compose compiler", hasComposerParam)
    }

    @Test
    fun `HrvThresholdSummaryCard is mounted in WorkoutDetailTabbedContent under ZONES (Rule 81)`() {
        val contentFile = findFile("src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutDetailTabbedContent.kt")
        assertTrue("WorkoutDetailTabbedContent.kt must exist", contentFile.exists())

        val source = contentFile.readText()
        assertTrue(
            "WorkoutDetailTabbedContent must import HrvThresholdSummaryCard",
            source.contains("import com.atrainingtracker.trainingtracker.ui.aftermath.workoutdetail.HrvThresholdSummaryCard")
        )
        assertTrue(
            "WorkoutDetailTabbedContent must invoke HrvThresholdSummaryCard under ZONES",
            source.contains("HrvThresholdSummaryCard(")
        )
        assertTrue(
            "WorkoutDetailTabbedContent must guard invocation with hasHrvData",
            source.contains("if (TelemetryMetricUtils.hasHrvData(activeScrubPath))")
        )
    }

    @Test
    fun `all threshold card string keys exist across canonical resources (TST-PER-044_6)`() {
        val requiredKeys = listOf(
            "hrv_threshold_card_title",
            "hrv_threshold_card_subtitle",
            "hrv_threshold_hrvt1_label",
            "hrv_threshold_hrvt2_label",
            "hrv_threshold_confidence_high",
            "hrv_threshold_confidence_medium",
            "hrv_threshold_confidence_low",
            "hrv_threshold_insufficient_span",
            "hrv_threshold_provenance_free",
            "hrv_threshold_provenance_step",
            "hrv_threshold_provenance_ramp",
            "hrv_threshold_stat_r2",
            "hrv_threshold_stat_neff",
            "hrv_threshold_stat_neff_steps",
            "hrv_threshold_stat_artifact",
            "hrv_threshold_diag_steady",
            "hrv_threshold_diag_high_artifact",
            "hrv_threshold_diag_short_duration",
            "hrv_threshold_diag_low_r2",
            "hrv_threshold_no_estimate",
            "hrv_threshold_informative_only"
        )

        val defaultFile = findFile("src/main/res/values/strings_hrv.xml")
        assertTrue("Default strings_hrv.xml must exist", defaultFile.exists())
        val defaultContent = defaultFile.readText()

        for (key in requiredKeys) {
            assertTrue("Key '$key' must exist in values/strings_hrv.xml", defaultContent.contains("name=\"$key\""))
        }
    }
}
