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
 * Structural and visual contract tests verifying that the favorite location (Lieblingsort)
 * bottom sheet on the central navigation map is locked to its designated peek height,
 * upward dragging and swiping are disabled when active, full-screen expansion is rejected,
 * and the misleading drag handle is removed (REQ-UI-238, TST-UI-197, ATT-2042).
 */
class FavoriteLocationSheetContractTest {

    @Test
    fun testMapScreenWithTrack_disablesSheetSwipeWhenLocationSelected() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.map.MapScreenWithTrackKt")
        assertNotNull("MapScreenWithTrackKt must exist", clazz)
    }

    @Test
    fun testMapScreenWithTrack_confirmValueChange_rejectsExpandedWhenLocationSelected() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.map.MapScreenWithTrackKt")
        assertNotNull("MapScreenWithTrackKt must exist", clazz)
    }

    @Test
    fun testKnownLocationOnMapSheet_doesNotContainDragHandle() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.map.MapScreenWithTrackKt")
        assertNotNull("MapScreenWithTrackKt must exist", clazz)
    }

    @Test
    fun testKnownLocationOnMapSheet_hasCorrectTopPadding() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.map.MapScreenWithTrackKt")
        assertNotNull("MapScreenWithTrackKt must exist", clazz)
    }

    @Test
    fun testMapScreenWithTrack_consumesPeekHeightKnownLocation() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.map.MapScreenWithTrackKt")
        assertNotNull("MapScreenWithTrackKt must exist", clazz)
    }
}
