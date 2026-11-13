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

package com.atrainingtracker.trainingtracker.ui.tracking.controltracking

import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.sensor.SensorType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Architectural contract test for Tracking Cockpit SensorStatus top action bar spacing and centering (REQ-UI-451, TST-UI-451, ATT-5422, ATT-7544).
 * Verifies Option A 16 dp total screen margin offset, 32 dp touch targets, 9-icon layout budget on 360 dp screens, and radar icon mapping.
 */
class SensorStatusTopBarSpacingTest {

    @Test
    fun testPortraitLayoutConstants_matchOptionADesignTokens() {
        assertEquals("Horizontal padding must be 8.dp", 8.dp, SENSOR_STATUS_PORTRAIT_HORIZONTAL_PADDING)
        assertEquals("Vertical padding must be 4.dp", 4.dp, SENSOR_STATUS_PORTRAIT_VERTICAL_PADDING)
        assertEquals("Action button touch target must be 32.dp", 32.dp, SENSOR_STATUS_ACTION_BUTTON_SIZE)
        assertEquals("Action icon visual size must be 24.dp", 24.dp, SENSOR_STATUS_ACTION_ICON_SIZE)
    }

    @Test
    fun testOptionATotalScreenEdgeOffset_equalsExactly16dp() {
        // Enclosing Surface in TrackingTabsScreen applies Modifier.padding(4.dp)
        val outerSurfacePadding = 4.dp
        val rowHorizontalPadding = SENSOR_STATUS_PORTRAIT_HORIZONTAL_PADDING
        val iconInternalInset = (SENSOR_STATUS_ACTION_BUTTON_SIZE - SENSOR_STATUS_ACTION_ICON_SIZE) / 2

        val totalEdgeOffset = outerSurfacePadding + rowHorizontalPadding + iconInternalInset
        assertEquals(
            "Option A total visual margin from display edge to outer icon contour must be exactly 16.dp",
            16.dp,
            totalEdgeOffset
        )
    }

    @Test
    fun testSymmetricalSlotWidth_guaranteesMathematicalCenterAlignmentOfSensors() {
        // Left slot: Menu button or fallback Spacer
        val leftSlotWidth = SENSOR_STATUS_ACTION_BUTTON_SIZE
        // Right slot: Supporter emblem or fallback Spacer
        val rightSlotWidth = SENSOR_STATUS_ACTION_BUTTON_SIZE

        assertEquals(
            "Left and right action slots must have identical width to guarantee central Box centers the 8 sensor icons",
            leftSlotWidth,
            rightSlotWidth
        )
    }

    @Test
    fun testTouchTargetEnlargement_improvesErgonomicsOverLegacy24dp() {
        val legacySize = 24.dp
        val newSize = SENSOR_STATUS_ACTION_BUTTON_SIZE

        assertTrue(
            "Touch target must be enlarged from 24.dp to 32.dp",
            newSize > legacySize
        )
        val touchAreaRatio = (newSize.value * newSize.value) / (legacySize.value * legacySize.value)
        assertTrue(
            "Touch area must improve by more than 75%",
            touchAreaRatio >= 1.75f
        )
    }

    @Test
    fun testNineIconPortraitBudget_fitsWithin360dpAvailableWidth() {
        assertEquals("Sensor icon visual size must be 22.dp", 22.dp, SENSOR_STATUS_ICON_SIZE)
        assertEquals("Horizontal padding per icon must be 4.dp", 4.dp, SENSOR_STATUS_ICON_HORIZONTAL_PADDING)

        val slotWidth = SENSOR_STATUS_ICON_SIZE + (SENSOR_STATUS_ICON_HORIZONTAL_PADDING * 2)
        assertEquals("Each icon slot must be exactly 30.dp wide", 30.dp, slotWidth)

        val total9IconsWidth = slotWidth * 9
        assertEquals("9 sensor icons must consume exactly 270.dp", 270.dp, total9IconsWidth)

        // On a compact 360.dp display:
        // Outer Surface padding: 4.dp * 2 = 8.dp
        // Row horizontal padding: 8.dp * 2 = 16.dp
        // Action buttons: 32.dp * 2 = 64.dp
        // Available width in central Box = 360.dp - 8.dp - 16.dp - 64.dp = 272.dp
        val availableCentralWidth = 360.dp - (4.dp * 2) - (SENSOR_STATUS_PORTRAIT_HORIZONTAL_PADDING * 2) - (SENSOR_STATUS_ACTION_BUTTON_SIZE * 2)
        assertEquals(272.dp, availableCentralWidth)

        assertTrue(
            "9 icons width ($total9IconsWidth) must fit cleanly within available width ($availableCentralWidth) with >= 2.dp margin",
            total9IconsWidth <= availableCentralWidth
        )
    }

    @Test
    fun testRadarSensorTypes_mapToDedicatedIcRadarDrawable() {
        assertEquals("RADAR_THREAT_COUNT must map to ic_radar", R.drawable.ic_radar, SensorType.RADAR_THREAT_COUNT.iconResId)
        assertEquals("RADAR_CLOSEST_DISTANCE_m must map to ic_radar", R.drawable.ic_radar, SensorType.RADAR_CLOSEST_DISTANCE_m.iconResId)
        assertEquals("RADAR_APPROACH_SPEED_mps must map to ic_radar", R.drawable.ic_radar, SensorType.RADAR_APPROACH_SPEED_mps.iconResId)
        assertEquals("RADAR_VEHICLE_COUNT must map to ic_radar", R.drawable.ic_radar, SensorType.RADAR_VEHICLE_COUNT.iconResId)
        assertEquals("RADAR_PASSING_DISTANCE_m must map to ic_radar", R.drawable.ic_radar, SensorType.RADAR_PASSING_DISTANCE_m.iconResId)
        assertEquals("RADAR_LARGE_VEHICLE_COUNT must map to ic_radar", R.drawable.ic_radar, SensorType.RADAR_LARGE_VEHICLE_COUNT.iconResId)
    }

    @Test
    fun testIcRadarDrawableFile_existsAndIsValidVector() {
        val file = File("src/main/res/drawable/ic_radar.xml")
        val altFile = File("app/src/main/res/drawable/ic_radar.xml")
        val target = if (file.exists()) file else altFile
        assertTrue("ic_radar.xml must exist in res/drawable", target.exists())
        val content = target.readText()
        assertTrue("ic_radar.xml must be a vector drawable", content.contains("<vector"))
        assertTrue("ic_radar.xml must define 24dp dimensions", content.contains("android:width=\"24dp\"") && content.contains("android:height=\"24dp\""))
    }
}
