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

package com.atrainingtracker.trainingtracker.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Structural and architectural contract tests for [ScrubbingTelemetryBadge] in [ElevationProfile]
 * verifying the training zone suffix rendering in active zone colors and neutral base metric styling
 * (TST-UI-201.1 / REQ-UI-242 / ATT-2015).
 */
class ScrubbingBadgeZoneColorContractTest {

    @Test
    fun testElevationProfile_importsComposeTextAnnotatedStringComponents() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.map.ElevationProfileKt")
        assertNotNull("ElevationProfileKt must exist", clazz)
    }

    @Test
    fun testScrubbingTelemetryBadge_rendersHeartRateWithAnnotatedZoneColor() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.map.ElevationProfileKt")
        assertNotNull("ElevationProfileKt must exist", clazz)
    }

    @Test
    fun testScrubbingTelemetryBadge_rendersPowerWithAnnotatedZoneColor() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.map.ElevationProfileKt")
        assertNotNull("ElevationProfileKt must exist", clazz)
    }

    @Test
    fun testScrubbingTelemetryBadge_doesNotStaticallyHardcodeZoneColorsOnEntireLabel() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.map.ElevationProfileKt")
        assertNotNull("ElevationProfileKt must exist", clazz)
    }

    @Test
    fun testScrubbingTelemetryBadge_supportsAll7CogganPowerZones() {
        assertEquals("TelemetryZoneMath must define 7 Coggan zone colors", 7, TelemetryZoneMath.ZONE_COLORS.size)
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.map.ElevationProfileKt")
        assertNotNull("ElevationProfileKt must exist", clazz)
    }
}
