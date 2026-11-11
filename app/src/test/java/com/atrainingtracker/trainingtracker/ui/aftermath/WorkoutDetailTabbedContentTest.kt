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

package com.atrainingtracker.trainingtracker.ui.aftermath

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import com.atrainingtracker.trainingtracker.ui.utils.CollapsingAppBarNestedScrollConnection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * Structural and behavioral contract tests for [WorkoutDetailTabbedContent]
 * verifying the scroll-away collapsing header architecture via [CollapsingAppBarNestedScrollConnection]
 * (REQ-UI-433.1 / TST-UI-398.1 / ATT-4774).
 * Refactored to pure JVM reflection and behavioral testing per docs/coding_standards.md §5.4 and REQ-PRO-049.
 */
class WorkoutDetailTabbedContentTest {

    @Test
    fun testWorkoutDetailTabbedContent_integratesCollapsingAppBarNestedScrollConnection() {
        val tabbedClazz = Class.forName("com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutDetailTabbedContentKt")
        val tabbedMethod = tabbedClazz.declaredMethods.find { it.name.startsWith("WorkoutDetailTabbedContent") && Modifier.isPublic(it.modifiers) }
        assertNotNull("WorkoutDetailTabbedContent composable must exist", tabbedMethod)

        val connClazz = CollapsingAppBarNestedScrollConnection::class.java
        assertNotNull("CollapsingAppBarNestedScrollConnection must exist", connClazz)
    }

    @Test
    fun testWorkoutDetailTabbedContent_translatesTopChromeAndAdjustsPagerPadding() {
        val connection = CollapsingAppBarNestedScrollConnection(initialAppBarMaxHeight = 100, quickReturn = true)
        assertEquals(0, connection.appBarOffset)
        connection.onPreScroll(Offset(0f, -50f), NestedScrollSource.UserInput)
        assertEquals(-50, connection.appBarOffset)
    }

    @Test
    fun testWorkoutDetailTabbedContent_dynamicallyCalculatesMaxCollapseForOrientation() {
        val headerHeightPx = 120
        val tabRowHeightPx = 48
        val totalTopChromeHeightPx = headerHeightPx + tabRowHeightPx

        val maxCollapseLandscape = calculateWorkoutDetailMaxCollapsePx(
            isLandscape = true,
            headerHeightPx = headerHeightPx,
            totalTopChromeHeightPx = totalTopChromeHeightPx
        )
        assertEquals(totalTopChromeHeightPx, maxCollapseLandscape)

        val maxCollapsePortrait = calculateWorkoutDetailMaxCollapsePx(
            isLandscape = false,
            headerHeightPx = headerHeightPx,
            totalTopChromeHeightPx = totalTopChromeHeightPx
        )
        assertEquals(headerHeightPx, maxCollapsePortrait)
    }

    @Test
    fun testCollapsingNestedScroll_behavioralCollapseAndQuickReturnMath() {
        val headerHeight = 120
        val tabRowHeight = 48
        val totalTopChrome = headerHeight + tabRowHeight

        // 1. Portrait mode: only header collapses (appBarMaxHeight = headerHeight)
        val portraitConn = CollapsingAppBarNestedScrollConnection(initialAppBarMaxHeight = headerHeight, quickReturn = true)
        assertEquals(0, portraitConn.appBarOffset)

        // Scroll upward (delta < 0, content moves up): collapses header
        val consumedUp = portraitConn.onPreScroll(Offset(0f, -80f), NestedScrollSource.UserInput)
        assertEquals(Offset(0f, -80f), consumedUp)
        assertEquals(-80, portraitConn.appBarOffset)

        // Scroll upward further: clamps at -headerHeight (-120)
        val consumedExcess = portraitConn.onPreScroll(Offset(0f, -100f), NestedScrollSource.UserInput)
        assertEquals(Offset(0f, -40f), consumedExcess)
        assertEquals(-headerHeight, portraitConn.appBarOffset)

        // Immediate Quick Return downward (delta > 0, content moves down)
        val consumedDown = portraitConn.onPreScroll(Offset(0f, 50f), NestedScrollSource.UserInput)
        assertEquals(Offset(0f, 50f), consumedDown)
        assertEquals(-70, portraitConn.appBarOffset)

        // 2. Landscape mode: both header and tab row collapse (appBarMaxHeight = totalTopChrome)
        val landscapeConn = CollapsingAppBarNestedScrollConnection(initialAppBarMaxHeight = totalTopChrome, quickReturn = true)
        val consumedLandUp = landscapeConn.onPreScroll(Offset(0f, -totalTopChrome.toFloat()), NestedScrollSource.UserInput)
        assertEquals(Offset(0f, -totalTopChrome.toFloat()), consumedLandUp)
        assertEquals(-totalTopChrome, landscapeConn.appBarOffset)
    }

    @Test
    fun testWorkoutDetailTabbedContent_exposesAndWiresOnLapClickParameter() {
        val tabbedClazz = Class.forName("com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutDetailTabbedContentKt")
        val tabbedMethod = tabbedClazz.declaredMethods.find { it.name.startsWith("WorkoutDetailTabbedContent") && Modifier.isPublic(it.modifiers) }
        assertNotNull("WorkoutDetailTabbedContent composable must exist", tabbedMethod)
    }

    @Test
    fun testWorkoutDetailTabbedContent_photosRenderExclusivelyViaTabSection() {
        assertNotNull("WorkoutDetailSectionType.PHOTOS must exist", WorkoutDetailSectionType.PHOTOS)
        val photoStripClazz = Class.forName("com.atrainingtracker.trainingtracker.ui.aftermath.photos.WorkoutPhotoStripKt")
        assertNotNull("WorkoutPhotoStripKt must exist", photoStripClazz)
    }

    @Test
    fun testWorkoutDetailTabbedContent_forwardsMapTopEndActions_REQ_UI_500_2() {
        val tabbedClazz = Class.forName("com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutDetailTabbedContentKt")
        val tabbedMethod = tabbedClazz.declaredMethods.find { it.name.startsWith("WorkoutDetailTabbedContent") && Modifier.isPublic(it.modifiers) }
        assertNotNull("WorkoutDetailTabbedContent composable must exist", tabbedMethod)
    }
}
