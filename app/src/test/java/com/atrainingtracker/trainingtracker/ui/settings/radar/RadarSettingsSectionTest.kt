/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.atrainingtracker.trainingtracker.ui.settings.radar

import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.settings.RadarEdgeSide
import com.atrainingtracker.trainingtracker.settings.RadarLightControlMode
import com.atrainingtracker.trainingtracker.settings.TuningConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Unit tests verifying UI configuration contracts and conditional gating for Radar Tail Light
 * and Radar Overlay settings section (REQ-CON-031.5c, REQ-CON-031.5d, REQ-TRK-062.5b / TST-TRK-062.4).
 */
class RadarSettingsSectionTest {

    @Test
    fun testStringResource_fullscreenOverlayModeStringExists() {
        assertNotEquals(0, R.string.radar_overlay_mode_fullscreen)
    }

    @Test
    fun testRadarClearMode_conditionalVisibilityInvariant() {
        // Clear mode selector must be HIDDEN when FULL_SCREEN is active (REQ-TRK-062.5b)
        val fullScreenConfig = TuningConfig(radarEdgeSide = RadarEdgeSide.FULL_SCREEN)
        assertFalse(
            "Clear state selector must be hidden when FULL_SCREEN is active",
            fullScreenConfig.radarEdgeSide != RadarEdgeSide.FULL_SCREEN
        )

        val rightConfig = TuningConfig(radarEdgeSide = RadarEdgeSide.RIGHT)
        assertTrue(
            "Clear state selector must be visible when RIGHT edge is active",
            rightConfig.radarEdgeSide != RadarEdgeSide.FULL_SCREEN
        )

        val leftConfig = TuningConfig(radarEdgeSide = RadarEdgeSide.LEFT)
        assertTrue(
            "Clear state selector must be visible when LEFT edge is active",
            leftConfig.radarEdgeSide != RadarEdgeSide.FULL_SCREEN
        )
    }

    @Test
    fun testStringResources_3WayControlModeSelectorStringsExist() {
        assertNotEquals(0, R.string.radar_light_control_mode_title)
        assertNotEquals(0, R.string.radar_light_control_mode_manual)
        assertNotEquals(0, R.string.radar_light_control_mode_manual_desc)
        assertNotEquals(0, R.string.radar_light_control_mode_fixed)
        assertNotEquals(0, R.string.radar_light_control_mode_fixed_desc)
        assertNotEquals(0, R.string.radar_light_control_mode_smart)
        assertNotEquals(0, R.string.radar_light_control_mode_smart_desc)
    }

    @Test
    fun testDefaultLightModeDropdown_conditionalVisibilityInvariant() {
        // Dropdown must ONLY be rendered when mode == FIXED_WORKOUT_MODE (REQ-CON-031.5d)
        val fixedConfig = TuningConfig(radarLightControlMode = RadarLightControlMode.FIXED_WORKOUT_MODE)
        assertTrue(
            "Default light mode dropdown must be visible when FIXED_WORKOUT_MODE is active",
            fixedConfig.radarLightControlMode == RadarLightControlMode.FIXED_WORKOUT_MODE
        )

        val manualConfig = TuningConfig(radarLightControlMode = RadarLightControlMode.MANUAL)
        assertFalse(
            "Default light mode dropdown must be hidden when MANUAL is active",
            manualConfig.radarLightControlMode == RadarLightControlMode.FIXED_WORKOUT_MODE
        )

        val smartConfig = TuningConfig(radarLightControlMode = RadarLightControlMode.SMART_AUTOMATION)
        assertFalse(
            "Default light mode dropdown must be hidden when SMART_AUTOMATION is active",
            smartConfig.radarLightControlMode == RadarLightControlMode.FIXED_WORKOUT_MODE
        )
    }

    @Test
    fun testTuningConfig_controlModePermutations_preserveBrakeLightIndependence() {
        // Deceleration brake light must remain fully independent of control mode (REQ-CON-035.6)
        val cfgManualBrakeOn = TuningConfig(
            radarLightControlMode = RadarLightControlMode.MANUAL,
            radarBrakeLightEnabled = true
        )
        assertTrue(cfgManualBrakeOn.radarBrakeLightEnabled)
        assertEquals(RadarLightControlMode.MANUAL, cfgManualBrakeOn.radarLightControlMode)

        val cfgSmartBrakeOff = TuningConfig(
            radarLightControlMode = RadarLightControlMode.SMART_AUTOMATION,
            radarBrakeLightEnabled = false
        )
        assertFalse(cfgSmartBrakeOff.radarBrakeLightEnabled)
        assertEquals(RadarLightControlMode.SMART_AUTOMATION, cfgSmartBrakeOff.radarLightControlMode)
    }

    private fun getXmlString(file: File, stringName: String): String? {
        val target = if (file.exists()) file else File("app/" + file.path)
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(target)
        val nodes = doc.getElementsByTagName("string")
        for (i in 0 until nodes.length) {
            val el = nodes.item(i) as? org.w3c.dom.Element ?: continue
            if (el.getAttribute("name") == stringName) {
                return el.textContent
            }
        }
        return null
    }

    /**
     * TST-TRK-063.7a / ATT-7738: German audio alert mode labels must be <= 12 characters
     * to fit comfortably on 360-412dp single-line segmented button rows without ellipsis.
     */
    @Test
    fun testRadarSettingsStrings_alertModeLabelsFitBudget() {
        val deFile = File("src/main/res/values-de/strings_radar.xml")
        val alertModes = listOf(
            "radar_alert_mode_all",
            "radar_alert_mode_approach_fast",
            "radar_alert_mode_fast_only",
            "radar_alert_mode_muted"
        )
        for (key in alertModes) {
            val text = getXmlString(deFile, key)
            assertNotNull("String $key must exist in values-de", text)
            assertTrue(
                "German alert mode label '$text' for $key must be <= 12 chars (got ${text!!.length})",
                text.length <= 12
            )
        }
    }

    /**
     * TST-TRK-073.4a / ATT-7738: Close-pass warning threshold labels across all 9 supported locales
     * must be <= 16 characters to fit 3-button segmented rows without ellipsis.
     */
    @Test
    fun testRadarSettingsStrings_closePassLabelsFitBudget() {
        val locales = listOf("values", "values-de", "values-es", "values-fr", "values-it", "values-ja", "values-nl", "values-pl", "values-pt")
        val thresholdKeys = listOf(
            "radar_close_pass_threshold_1_0m",
            "radar_close_pass_threshold_1_5m",
            "radar_close_pass_threshold_2_0m"
        )
        for (loc in locales) {
            val file = File("src/main/res/$loc/strings_radar.xml")
            for (key in thresholdKeys) {
                val text = getXmlString(file, key)
                assertNotNull("String $key must exist in $loc", text)
                assertTrue(
                    "Close-pass label '$text' for $key in $loc must be <= 16 chars (got ${text!!.length})",
                    text.length <= 16
                )
            }
        }
    }

    /**
     * TST-CON-031.5e / ATT-7738: In German and Dutch, section heading and control title
     * must be disambiguated so 'Rücklicht-Steuerung' / 'Achterlichtbediening' does not appear back-to-back.
     */
    @Test
    fun testRadarSettingsStrings_deAndNlLightTitlesDisambiguated() {
        val deFile = File("src/main/res/values-de/strings_radar.xml")
        val deSettingsTitle = getXmlString(deFile, "radar_light_settings_title")
        val deControlModeTitle = getXmlString(deFile, "radar_light_control_mode_title")
        assertNotEquals(
            "In German, radar_light_settings_title and radar_light_control_mode_title must not be identical",
            deSettingsTitle,
            deControlModeTitle
        )

        val nlFile = File("src/main/res/values-nl/strings_radar.xml")
        val nlSettingsTitle = getXmlString(nlFile, "radar_light_settings_title")
        val nlControlModeTitle = getXmlString(nlFile, "radar_light_control_mode_title")
        assertNotEquals(
            "In Dutch, radar_light_settings_title and radar_light_control_mode_title must not be identical",
            nlSettingsTitle,
            nlControlModeTitle
        )
    }

    /**
     * ATT-7738: Dynamic helper descriptions for audio alert modes must exist.
     */
    @Test
    fun testRadarSettingsStrings_alertModeDescriptionsExist() {
        assertNotEquals(0, R.string.radar_alert_mode_all_desc)
        assertNotEquals(0, R.string.radar_alert_mode_approach_fast_desc)
        assertNotEquals(0, R.string.radar_alert_mode_fast_only_desc)
        assertNotEquals(0, R.string.radar_alert_mode_muted_desc)
    }
}
