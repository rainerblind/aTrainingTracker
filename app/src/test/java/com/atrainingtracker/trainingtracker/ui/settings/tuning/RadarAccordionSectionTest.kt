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

package com.atrainingtracker.trainingtracker.ui.settings.tuning

import android.content.Context
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.devices.radar.RadarLightMode
import com.atrainingtracker.banalservice.devices.radar.RadarScenario
import com.atrainingtracker.trainingtracker.settings.RadarAudioAlertMode
import com.atrainingtracker.trainingtracker.settings.RadarEdgeSide
import com.atrainingtracker.trainingtracker.settings.TuningConfig
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying TuningSection.CYCLING_RADAR and TuningSubtitleFormatter.formatCyclingRadarSubtitle (REQ-UI-512, TST-UI-512).
 */
class RadarAccordionSectionTest {

    @Test
    fun testTuningSection_containsCyclingRadar() {
        val sections = TuningSection.values()
        assertTrue("TuningSection must contain CYCLING_RADAR", sections.contains(TuningSection.CYCLING_RADAR))
        val radarIdx = sections.indexOf(TuningSection.CYCLING_RADAR)
        val sensorsGpsIdx = sections.indexOf(TuningSection.SENSORS_GPS)
        assertTrue("CYCLING_RADAR must follow SENSORS_GPS in Block 2 sequence", radarIdx > sensorsGpsIdx)
    }

    @Test
    fun testFormatCyclingRadarSubtitle_idleState_displaysEdgeAlertAndLight() {
        val mockContext = mockk<Context>()
        every { mockContext.getString(R.string.radar_overlay_edge_right) } returns "Right Edge"
        every { mockContext.getString(R.string.radar_alert_mode_all) } returns "All Alerts"
        every { mockContext.getString(R.string.radar_light_mode_day_flash) } returns "Day Flash"

        val subtitle = TuningSubtitleFormatter.formatCyclingRadarSubtitle(
            edgeSide = RadarEdgeSide.RIGHT,
            alertMode = RadarAudioAlertMode.ALL_ALERTS,
            lightMode = RadarLightMode.DAY_FLASH,
            isRunning = false,
            scenario = null,
            context = mockContext
        )

        assertEquals("Right Edge · All Alerts · Day Flash", subtitle)
    }

    @Test
    fun testFormatCyclingRadarSubtitle_idleState_leftEdgeMutedSolid() {
        val mockContext = mockk<Context>()
        every { mockContext.getString(R.string.radar_overlay_edge_left) } returns "Left Edge"
        every { mockContext.getString(R.string.radar_alert_mode_muted) } returns "Muted"
        every { mockContext.getString(R.string.radar_light_mode_solid) } returns "Solid"

        val subtitle = TuningSubtitleFormatter.formatCyclingRadarSubtitle(
            edgeSide = RadarEdgeSide.LEFT,
            alertMode = RadarAudioAlertMode.MUTED,
            lightMode = RadarLightMode.SOLID,
            isRunning = false,
            scenario = null,
            context = mockContext
        )

        assertEquals("Left Edge · Muted · Solid", subtitle)
    }

    @Test
    fun testFormatCyclingRadarSubtitle_runningSimulationState_displaysActiveScenario() {
        val mockContext = mockk<Context>()
        every { mockContext.getString(R.string.radar_simulator_running) } returns "Simulation active"
        every { mockContext.getString(R.string.radar_scenario_single_car) } returns "Single Car"

        val subtitle = TuningSubtitleFormatter.formatCyclingRadarSubtitle(
            edgeSide = RadarEdgeSide.RIGHT,
            alertMode = RadarAudioAlertMode.ALL_ALERTS,
            lightMode = RadarLightMode.DAY_FLASH,
            isRunning = true,
            scenario = RadarScenario.SINGLE_CAR,
            context = mockContext
        )

        assertEquals("Simulation active • Single Car", subtitle)
    }

    @Test
    fun testFormatCyclingRadarSubtitle_withTuningConfig() {
        val mockContext = mockk<Context>()
        every { mockContext.getString(R.string.radar_overlay_edge_right) } returns "Right Edge"
        every { mockContext.getString(R.string.radar_alert_mode_all) } returns "All Alerts"
        every { mockContext.getString(R.string.radar_light_mode_day_flash) } returns "Day Flash"

        val config = TuningConfig(
            radarEdgeSide = RadarEdgeSide.RIGHT,
            radarAudioAlertMode = RadarAudioAlertMode.ALL_ALERTS,
            radarLightDefaultMode = RadarLightMode.DAY_FLASH
        )

        val subtitle = TuningSubtitleFormatter.formatCyclingRadarSubtitle(
            edgeSide = config.radarEdgeSide,
            alertMode = config.radarAudioAlertMode,
            lightMode = config.radarLightDefaultMode,
            isRunning = false,
            scenario = null,
            context = mockContext
        )

        assertEquals("Right Edge · All Alerts · Day Flash", subtitle)
    }

    @Test
    fun testStringResourceTuningCatCyclingRadarExists() {
        assertNotEquals(0, R.string.tuning_cat_cycling_radar)
    }
}
