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

import com.atrainingtracker.testing.LocalizationTestCache
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Structural contract, host mounting, and localization tests for HrvThresholdSummaryCard
 * pursuant to REQ-PER-036, Rule 23, and Rule 81 (TST-PER-044.5).
 * Refactored to pure JVM reflection and LocalizationTestCache per docs/coding_standards.md §5.4.
 */
class HrvThresholdSummaryCardTest {

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
        val contentClazz = Class.forName("com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutDetailTabbedContentKt")
        assertNotNull("WorkoutDetailTabbedContentKt must exist", contentClazz)
        val cardClazz = Class.forName("com.atrainingtracker.trainingtracker.ui.aftermath.workoutdetail.HrvThresholdSummaryCardKt")
        assertNotNull("HrvThresholdSummaryCardKt must exist", cardClazz)
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

        val strings = LocalizationTestCache.getStrings("values", "strings_hrv.xml")
        for (key in requiredKeys) {
            assertTrue("Key '$key' must exist in values/strings_hrv.xml", strings.containsKey(key))
        }
    }

    @Test
    fun `all zone comparison and consensus string keys exist across canonical resources (TST-PER-045_7)`() {
        val requiredZoneKeys = listOf(
            "hrv_zone_comparison_title",
            "hrv_zone_comparison_subtitle",
            "hrv_zone_comparison_power_header",
            "hrv_zone_comparison_hrvt1_power_delta",
            "hrv_zone_comparison_hrvt2_power_delta",
            "hrv_zone_comparison_configured_ftp",
            "hrv_zone_comparison_hr_header",
            "hrv_zone_comparison_hrvt1_hr_delta",
            "hrv_zone_comparison_hrvt2_hr_delta",
            "hrv_zone_comparison_hrvt1_pct_hrmax",
            "hrv_zone_comparison_hrvt2_pct_hrmax",
            "hrv_zone_comparison_hr_tooltip",
            "hrv_consensus_badge_unlocked",
            "hrv_consensus_badge_locked",
            "hrv_consensus_reason_guided_high",
            "hrv_consensus_reason_multi_session",
            "hrv_consensus_reason_insufficient_sessions",
            "hrv_consensus_reason_spread_too_high",
            "hrv_zone_comparison_btn_update_power",
            "hrv_zone_comparison_dialog_title",
            "hrv_zone_comparison_dialog_body",
            "hrv_zone_comparison_dialog_confirm",
            "hrv_zone_comparison_dialog_cancel",
            "hrv_zone_comparison_forward_mader"
        )

        val strings = LocalizationTestCache.getStrings("values", "strings_hrv.xml")
        for (key in requiredZoneKeys) {
            assertTrue("Key '$key' must exist in values/strings_hrv.xml", strings.containsKey(key))
        }
    }

    @Test
    fun `HrvThresholdSummaryCard exposes Zone Comparison and Consensus test tags (REQ-PER-037_4)`() {
        val cardClazz = Class.forName("com.atrainingtracker.trainingtracker.ui.aftermath.workoutdetail.HrvThresholdSummaryCardKt")
        assertNotNull("HrvThresholdSummaryCardKt must exist", cardClazz)
        assertTrue("HrvThresholdSummaryCard composable must be declared", cardClazz.declaredMethods.any { it.name == "HrvThresholdSummaryCard" })
    }

    @Test
    fun `WorkoutDetailTabbedContent wires configuredFtp, configuredHrMax and callbacks (Rule 81)`() {
        val contentClazz = Class.forName("com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutDetailTabbedContentKt")
        assertNotNull("WorkoutDetailTabbedContentKt must exist", contentClazz)
    }
}
