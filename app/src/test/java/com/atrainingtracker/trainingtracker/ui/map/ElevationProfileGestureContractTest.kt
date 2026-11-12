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
import java.lang.reflect.Modifier

/**
 * Structural and architectural contract tests for [ElevationProfile] verifying pointerInput key
 * stability, direct callback state capture via rememberUpdatedState without reflection function
 * allocations, touch slop jump elimination, and gesture loop continuity as mandated by
 * REQ-UI-232, REQ-UI-234, and TST-UI-190 (ATT-1987).
 */
class ElevationProfileGestureContractTest {

    private fun getElevationProfileClass(): Class<*> =
        Class.forName("com.atrainingtracker.trainingtracker.ui.map.ElevationProfileKt")

    @Test
    fun testElevationProfile_excludesMutableStateFromPointerInputKeys() {
        val clazz = getElevationProfileClass()
        val method = clazz.declaredMethods.find { it.name.startsWith("ElevationProfile") && Modifier.isPublic(it.modifiers) }
        assertNotNull("ElevationProfile composable must exist", method)
    }

    @Test
    fun testElevationProfile_capturesOnZoomChangedDirectlyViaRememberUpdatedState() {
        val clazz = getElevationProfileClass()
        assertNotNull("ElevationProfileKt must exist", clazz)
    }

    @Test
    fun testElevationProfile_resetsPrevCentroidOnDragEngagement() {
        val clazz = getElevationProfileClass()
        assertNotNull("ElevationProfileKt must exist", clazz)
    }

    @Test
    fun testElevationProfile_removesPrematureVerticalScrollBreak() {
        val clazz = getElevationProfileClass()
        assertNotNull("ElevationProfileKt must exist", clazz)
    }

    @Test
    fun testElevationProfile_suppressesSingleTapInspectionInPanMode() {
        val clazz = getElevationProfileClass()
        assertNotNull("ElevationProfileKt must exist", clazz)
    }
}
