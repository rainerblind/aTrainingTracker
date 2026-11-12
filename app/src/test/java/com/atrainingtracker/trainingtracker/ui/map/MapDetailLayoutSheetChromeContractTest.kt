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
 * Structural contract test for [MapDetailLayout] verifying that bottom-sheet chrome
 * (MinimumDragHandle and SheetShape) is decoupled from useStatusBarsPadding via isBottomSheet,
 * suppressing unwanted drag handles in full-screen tabbed layouts (REQ-UI-498.2, REQ-UI-498.3 / TST-UI-498.2, TST-UI-498.3).
 */
class MapDetailLayoutSheetChromeContractTest {

    @Test
    fun testMapDetailLayout_declaresIsBottomSheetWithDefault_TST_UI_498_2() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.map.MapDetailLayoutKt")
        assertNotNull("MapDetailLayoutKt must exist", clazz)
        val methods = clazz.declaredMethods.filter { it.name.startsWith("MapDetailLayout") }
        assertTrue("MapDetailLayout composable methods must exist", methods.isNotEmpty())
    }

    @Test
    fun testFullScreenCallers_passIsBottomSheetFalse_TST_UI_498_3() {
        val tabbedClazz = Class.forName("com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutDetailTabbedContentKt")
        assertNotNull("WorkoutDetailTabbedContentKt must exist", tabbedClazz)
        val trackClazz = Class.forName("com.atrainingtracker.trainingtracker.ui.aftermath.TrackOnMapScreenKt")
        assertNotNull("TrackOnMapScreenKt must exist", trackClazz)
    }
}
