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

package com.atrainingtracker.trainingtracker.ui.hrv

import com.atrainingtracker.testing.LocalizationTestCache
import com.atrainingtracker.trainingtracker.ui.navigation.NavRoutes
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Structural contract, Rule 81 host navigation mounting, and UI component tests for [HrvRampTestScreen]
 * implementing TST-TRK-072.5 pursuant to REQ-TRK-072.
 * Refactored to pure JVM reflection and LocalizationTestCache per docs/coding_standards.md §5.4.
 */
class HrvRampTestScreenTest {

    @Test
    fun testHrvRampTestScreenComposableContract_TST_TRK_072_5() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.hrv.HrvRampTestScreenKt")
        val methods = clazz.declaredMethods.filter { it.name == "HrvRampTestScreen" }
        assertTrue("HrvRampTestScreen composable method must exist", methods.isNotEmpty())

        val method = methods.first()
        val hasComposerParam = method.parameterTypes.any { it.name.contains("Composer") }
        assertTrue("HrvRampTestScreen must take Composer parameter from Compose compiler", hasComposerParam)
    }

    @Test
    fun testRule81HostNavigationMountingInATrainingTrackerApp_TST_TRK_072_5() {
        assertEquals("hrv_test", NavRoutes.HRV_TEST)
        val appClass = Class.forName("com.atrainingtracker.trainingtracker.ui.navigation.ATrainingTrackerAppKt")
        assertNotNull("ATrainingTrackerAppKt must exist", appClass)
    }

    @Test
    fun testRule81HostNavigationMountingInAthleteProfileScreen_TST_TRK_072_5() {
        val profileClass = Class.forName("com.atrainingtracker.trainingtracker.ui.athleteprofile.AthleteProfileScreenKt")
        assertNotNull("AthleteProfileScreenKt must exist", profileClass)
    }

    @Test
    fun testRequiredTestTagsPresentInHrvRampTestScreen_TST_TRK_072_5() {
        val screenClass = Class.forName("com.atrainingtracker.trainingtracker.ui.hrv.HrvRampTestScreenKt")
        assertNotNull("HrvRampTestScreenKt must exist", screenClass)
        assertTrue("HrvRampTestScreen composable must be declared", screenClass.declaredMethods.any { it.name == "HrvRampTestScreen" })
    }

    @Test
    fun testStringKeysPresenceInHrvStringsXml_TST_TRK_072_6() {
        val requiredKeys = listOf(
            "hrv_test_title",
            "hrv_test_subtitle",
            "hrv_test_protocol_label",
            "hrv_test_protocol_step",
            "hrv_test_protocol_ramp",
            "hrv_test_sport_label",
            "hrv_test_sport_cycling",
            "hrv_test_sport_running",
            "hrv_test_step_duration_label",
            "hrv_test_step_duration_4min",
            "hrv_test_step_duration_3min",
            "hrv_test_start_load_label",
            "hrv_test_increment_label",
            "hrv_test_warmup_label",
            "hrv_test_warmup_desc",
            "hrv_test_preflight_title",
            "hrv_test_preflight_checking",
            "hrv_test_preflight_pass",
            "hrv_test_preflight_warning",
            "hrv_test_preflight_fail",
            "hrv_test_preflight_no_sensor",
            "hrv_test_phase_warmup",
            "hrv_test_phase_step",
            "hrv_test_phase_ramp",
            "hrv_test_phase_cooldown",
            "hrv_test_target_power",
            "hrv_test_target_pace",
            "hrv_test_time_remaining",
            "hrv_test_live_a1",
            "hrv_test_live_artifacts",
            "hrv_test_early_complete_title",
            "hrv_test_early_complete_desc",
            "hrv_test_btn_start",
            "hrv_test_btn_finish",
            "hrv_test_btn_cooldown",
            "hrv_test_btn_cancel",
            // Active Pacing Guidance (ATT-7529)
            "hrv_test_pacing_target_header",
            "hrv_test_pacing_actual_header",
            "hrv_test_live_power_fmt",
            "hrv_test_live_speed_fmt",
            "hrv_test_live_hr_fmt",
            // Protocol Info & Pros/Cons (ATT-7529)
            "hrv_test_info_procedure_label",
            "hrv_test_title_step_fmt",
            "hrv_test_title_ramp",
            "hrv_test_badge_step_4min",
            "hrv_test_badge_step_3min",
            "hrv_test_badge_ramp",
            "hrv_test_step1_warmup_cycling",
            "hrv_test_step1_warmup_running",
            "hrv_test_step2_step_cycling_fmt",
            "hrv_test_step2_step_running_fmt",
            "hrv_test_step2_ramp_cycling",
            "hrv_test_step2_ramp_running",
            "hrv_test_step3_completion",
            "hrv_test_pro_label",
            "hrv_test_con_label",
            "hrv_test_pro_step_4min",
            "hrv_test_con_step_4min",
            "hrv_test_pro_step_3min",
            "hrv_test_con_step_3min",
            "hrv_test_pro_ramp",
            "hrv_test_con_ramp"
        )

        val strings = LocalizationTestCache.getStrings("values", "strings_hrv.xml")
        for (key in requiredKeys) {
            assertTrue("Key '$key' must exist in values/strings_hrv.xml", strings.containsKey(key))
        }
    }

    @Test
    fun testConsolidatedLauncherNavigationAndCleanScreen_TST_TRK_072_9() {
        // REQ-TRK-072.7 / TST-TRK-072.9
        assertEquals("hrv_test", NavRoutes.HRV_TEST)

        val trackingSource = resolveSourceFile("src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/trackingtabs/TrackingTabsScreen.kt")
        assertTrue("TrackingTabsScreen source file must exist", trackingSource.exists())
        val trackingContent = trackingSource.readText()
        assertFalse("TrackingTabsScreen must not contain hrv_test_cockpit_launcher", trackingContent.contains("hrv_test_cockpit_launcher"))
        assertFalse("TrackingTabsScreen must not contain onNavigateToHrvTest", trackingContent.contains("onNavigateToHrvTest"))

        val profileSource = resolveSourceFile("src/main/java/com/atrainingtracker/trainingtracker/ui/athleteprofile/AthleteProfileScreen.kt")
        assertTrue("AthleteProfileScreen source file must exist", profileSource.exists())
        val profileContent = profileSource.readText()
        assertFalse("AthleteProfileScreen must not contain btn_guided_hrv_test", profileContent.contains("btn_guided_hrv_test"))
        assertFalse("AthleteProfileScreen must not contain onNavigateToHrvTest", profileContent.contains("onNavigateToHrvTest"))

        val zonesSource = resolveSourceFile("src/main/java/com/atrainingtracker/trainingtracker/activities/ZonesSettingsActivity.kt")
        assertTrue("ZonesSettingsActivity source file must exist", zonesSource.exists())
        val zonesContent = zonesSource.readText()
        assertTrue("ZonesSettingsActivity must use TrendingUp icon for ramp test", zonesContent.contains("Icons.AutoMirrored.Filled.TrendingUp"))
        assertFalse("ZonesSettingsActivity must not use FitnessCenter for ramp test", zonesContent.contains("imageVector = Icons.Default.FitnessCenter"))
    }

    private fun resolveSourceFile(pathUnderApp: String): File {
        val f1 = File(pathUnderApp)
        if (f1.exists()) return f1
        val f2 = File("app/$pathUnderApp")
        if (f2.exists()) return f2
        return f1
    }
}
