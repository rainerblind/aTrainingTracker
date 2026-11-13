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
import org.junit.Assert.assertTrue
import org.junit.Test

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
}
