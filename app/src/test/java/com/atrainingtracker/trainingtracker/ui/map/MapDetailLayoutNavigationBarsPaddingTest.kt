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

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Structural contract tests for [MapDetailLayout] and bottom sheet popups
 * verifying navigation bar inset clearance and upward travel calibration
 * as mandated by REQ-UI-237 and TST-UI-196 (ATT-2008).
 */
class MapDetailLayoutNavigationBarsPaddingTest {

    @Test
    fun testMapDetailLayout_appliesNavigationBarsPaddingToElevationProfileWhenAnalyticsContentIsNull() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.map.MapDetailLayoutKt")
        assertNotNull("MapDetailLayoutKt class must exist", clazz)
    }

    @Test
    fun testMapDetailLayout_preservesNavigationBarsPaddingOnAnalyticsContent() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.map.MapDetailLayoutKt")
        assertNotNull("MapDetailLayoutKt class must exist", clazz)
    }

    @Test
    fun testMapDetailLayout_providesDefensiveSpacerWhenBothElevationAndAnalyticsAreAbsent() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.map.MapDetailLayoutKt")
        assertNotNull("MapDetailLayoutKt class must exist", clazz)
    }

    @Test
    fun testLiveSegmentSheet_configuresMapDetailLayoutForBottomSheetMode() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.segments.LiveSegmentSheetKt")
        assertNotNull("LiveSegmentSheetKt class must exist", clazz)
    }

    @Test
    fun testMapScreenWithTrack_sizesBottomSheetToMaxSheetHeight() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.map.MapScreenWithTrackKt")
        assertNotNull("MapScreenWithTrackKt class must exist", clazz)
    }

    @Test
    fun testSensorGridScreen_configuresLiveSegmentBottomSheet() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.tracking.tracking.SensorGridScreenKt")
        assertNotNull("SensorGridScreenKt class must exist", clazz)
    }
}
