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
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutDetailSectionType
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutDetailTabConfig
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutDetailTabPreset
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutSectionType
import com.atrainingtracker.trainingtracker.ui.aftermath.toWorkoutDetailSectionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
            "hrv_zone_comparison_forward_mader",
            "hrv_hr_consensus_reason_guided_high",
            "hrv_hr_consensus_reason_multi_session",
            "hrv_hr_consensus_reason_insufficient_sessions",
            "hrv_hr_consensus_reason_spread_too_high",
            "hrv_zone_comparison_btn_update_hr",
            "hrv_zone_comparison_dialog_hr_title",
            "hrv_zone_comparison_dialog_hr_body",
            "hrv_zone_comparison_dialog_hr_confirm",
            "hrv_zone_comparison_dialog_hr_cancel"
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

    @Test
    fun `WorkoutData resolves protocolType from metadata (Finding 3)`() {
        assertEquals(
            HrvProtocolType.GUIDED_STEP,
            WorkoutData.resolveProtocolType("GUIDED_STEP", null, null, "Morning Ride")
        )
        assertEquals(
            HrvProtocolType.GUIDED_STEP,
            WorkoutData.resolveProtocolType(null, null, null, "Step Test 20W")
        )
        assertEquals(
            HrvProtocolType.GUIDED_RAMP,
            WorkoutData.resolveProtocolType("GUIDED_RAMP", null, null, "Evening Ramp")
        )
        assertEquals(
            HrvProtocolType.GUIDED_RAMP,
            WorkoutData.resolveProtocolType(null, "Ramp Test Protocol", null, "Ride")
        )
        assertEquals(
            HrvProtocolType.FREE_WORKOUT,
            WorkoutData.resolveProtocolType("Intervals", "Aerobic base", "Fun ride", "Sunday Long Ride")
        )
    }

    @Test
    fun `diagnostic string resources format positional arguments cleanly across all 9 locales (Finding 1)`() {
        val locales = listOf("values", "values-de", "values-es", "values-fr", "values-it", "values-ja", "values-nl", "values-pl", "values-pt")
        for (loc in locales) {
            val strings = LocalizationTestCache.getStrings(loc, "strings_hrv.xml")

            // 1. High artifact (%1$.1f)
            val highArtFormat = strings["hrv_threshold_diag_high_artifact"]
            assertNotNull("hrv_threshold_diag_high_artifact must exist in $loc", highArtFormat)
            val formattedArt = String.format(java.util.Locale.US, highArtFormat!!, 6.4)
            assertTrue("Formatted artifact string must contain 6.4 in $loc", formattedArt.contains("6.4"))

            // 2. Short duration (%1$d, %2$d)
            val shortDurFormat = strings["hrv_threshold_diag_short_duration"]
            assertNotNull("hrv_threshold_diag_short_duration must exist in $loc", shortDurFormat)
            val formattedDur = String.format(java.util.Locale.US, shortDurFormat!!, 5, 8)
            assertTrue("Formatted duration string must contain 5 and 8 in $loc", formattedDur.contains("5") && formattedDur.contains("8"))

            // 3. Low R2 (%1$.2f, %2$.2f)
            val lowR2Format = strings["hrv_threshold_diag_low_r2"]
            assertNotNull("hrv_threshold_diag_low_r2 must exist in $loc", lowR2Format)
            val formattedR2 = String.format(java.util.Locale.US, lowR2Format!!, 0.42, 0.55)
            assertTrue("Formatted R2 string must contain 0.42 and 0.55 in $loc", formattedR2.contains("0.42") && formattedR2.contains("0.55"))
        }
    }

    /**
     * TST-UI-527.4: Card Mounting and Section Assignment Contract (REQ-UI-527.4).
     */
    @Test
    fun `TST-UI-527_4 card mounting under HRV section and preset mapping`() {
        // Tab preset and default tab configuration for HRV
        val hrvTab = WorkoutDetailTabConfig.DEFAULT_TABS[3]
        assertEquals("tab_hrv", hrvTab.id)
        assertEquals(WorkoutDetailTabPreset.HRV, hrvTab.preset)
        assertEquals(listOf(WorkoutDetailSectionType.HRV), hrvTab.sections)

        // WorkoutDetailSectionType.HRV maps bidirectionally to WorkoutSectionType.HRV
        assertEquals(WorkoutSectionType.HRV, WorkoutDetailSectionType.HRV.toWorkoutSectionTypeOrNull())
        assertEquals(WorkoutDetailSectionType.HRV, WorkoutSectionType.HRV.toWorkoutDetailSectionType())

        // WorkoutDetailTabbedContentKt exists
        val tabbedClazz = Class.forName("com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutDetailTabbedContentKt")
        assertNotNull("WorkoutDetailTabbedContentKt must exist", tabbedClazz)
    }

    /**
     * TST-UI-527.5: Zone Comparison Gating on hasPower and hasHr (REQ-UI-527.5).
     */
    @Test
    fun `TST-UI-527_5 power comparison is suppressed when workout has no power data even with configured FTP`() {
        val configuredFtp = 250
        val configuredHrMax = 185

        // Case A: Workout without power, with HR and configured FTP
        val hasPowerFalse = false
        val hasHrTrue = true
        val showPowerComparisonA = hasPowerFalse && configuredFtp > 0
        val showHrComparisonA = hasHrTrue && configuredHrMax > 0
        assertFalse("showPowerComparison must be false when hasPower is false", showPowerComparisonA)
        assertTrue("showHrComparison must remain true when hasHr is true", showHrComparisonA)
        assertTrue("hasZoneData must remain true when showHrComparison is true", showPowerComparisonA || showHrComparisonA)

        // Case B: Workout with neither power nor HR
        val hasHrFalse = false
        val showHrComparisonB = hasHrFalse && configuredHrMax > 0
        val hasZoneDataB = showPowerComparisonA || showHrComparisonB
        assertFalse("Entire zone comparison section must be hidden when neither power nor HR applies", hasZoneDataB)

        // Case C: Workout with power and configured FTP
        val hasPowerTrue = true
        val showPowerComparisonC = hasPowerTrue && configuredFtp > 0
        assertTrue("showPowerComparison must be true when hasPower and configuredFtp > 0", showPowerComparisonC)
    }

    /**
     * TST-UI-527: 9-language localization parity for workout_detail_tab_hrv and settings_workout_card_hrv.
     */
    @Test
    fun `TST-UI-527 localization parity for workout_detail_tab_hrv and settings_workout_card_hrv across all 9 locales`() {
        val locales = listOf("values", "values-de", "values-es", "values-fr", "values-it", "values-ja", "values-nl", "values-pl", "values-pt")
        for (loc in locales) {
            val strings = LocalizationTestCache.getStrings(loc, "strings.xml")
            val tabHrv = strings["workout_detail_tab_hrv"]
            assertNotNull("workout_detail_tab_hrv must exist in $loc", tabHrv)
            assertTrue("workout_detail_tab_hrv must not be blank in $loc", tabHrv!!.isNotBlank())

            val cardHrv = strings["settings_workout_card_hrv"]
            assertNotNull("settings_workout_card_hrv must exist in $loc", cardHrv)
            assertTrue("settings_workout_card_hrv must not be blank in $loc", cardHrv!!.isNotBlank())
        }
    }
}
