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
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Structural and behavioral contract tests for [WorkoutDetailTabbedContent]
 * verifying the scroll-away collapsing header architecture via [CollapsingAppBarNestedScrollConnection]
 * (REQ-UI-433.1 / TST-UI-398.1 / ATT-4774).
 */
class WorkoutDetailTabbedContentTest {

    private val sourceFile: File by lazy {
        listOf(
            File("src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutDetailTabbedContent.kt"),
            File("app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutDetailTabbedContent.kt"),
            File("../app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutDetailTabbedContent.kt")
        ).firstOrNull { it.exists() } ?: File("app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutDetailTabbedContent.kt")
    }

    @Test
    fun testWorkoutDetailTabbedContent_integratesCollapsingAppBarNestedScrollConnection() {
        assertTrue("WorkoutDetailTabbedContent.kt must exist", sourceFile.exists())
        val content = sourceFile.readText()

        assertTrue(
            "WorkoutDetailTabbedContent must remember CollapsingAppBarNestedScrollConnection",
            content.contains("val connection = remember { CollapsingAppBarNestedScrollConnection(0) }") ||
                    content.contains("CollapsingAppBarNestedScrollConnection")
        )
        assertTrue(
            "WorkoutDetailTabbedContent root container must attach .nestedScroll(connection)",
            content.contains(".nestedScroll(connection)")
        )
    }

    @Test
    fun testWorkoutDetailTabbedContent_translatesTopChromeAndAdjustsPagerPadding() {
        assertTrue("WorkoutDetailTabbedContent.kt must exist", sourceFile.exists())
        val content = sourceFile.readText()

        assertTrue(
            "Top chrome container must apply offset with connection.appBarOffset",
            content.contains("IntOffset(0, connection.appBarOffset)")
        )
        assertTrue(
            "HorizontalPager must apply dynamic top padding currentTopPaddingDp",
            content.contains(".padding(top = currentTopPaddingDp)")
        )
        assertTrue(
            "currentTopPaddingPx must calculate from totalTopChromeHeightPx and connection.appBarOffset",
            content.contains("totalTopChromeHeightPx + connection.appBarOffset")
        )
    }

    @Test
    fun testWorkoutDetailTabbedContent_dynamicallyCalculatesMaxCollapseForOrientation() {
        assertTrue("WorkoutDetailTabbedContent.kt must exist", sourceFile.exists())
        val content = sourceFile.readText()

        assertTrue(
            "WorkoutDetailTabbedContent must evaluate landscape orientation for collapse bounding",
            content.contains("configuration.orientation == Configuration.ORIENTATION_LANDSCAPE")
        )
        assertTrue(
            "In landscape, maxCollapse must equal totalTopChromeHeightPx; in portrait, headerHeightPx",
            content.contains("val maxCollapse = if (isLandscape) totalTopChromeHeightPx else headerHeightPx") ||
                    content.contains("if (isLandscape) totalTopChromeHeightPx else headerHeightPx")
        )
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
        assertTrue("WorkoutDetailTabbedContent.kt must exist", sourceFile.exists())
        val content = sourceFile.readText()

        assertTrue(
            "WorkoutDetailTabbedContent must declare onLapClick parameter (REQ-UI-142 / REQ-UI-408 / ATT-5483)",
            content.contains("onLapClick: ((LapData) -> Unit)? = null")
        )

        assertTrue(
            "WorkoutDetailTabbedContent must invoke onLapClick?.invoke(lap) when a lap is clicked in LAPS section",
            content.contains("onLapClick?.invoke(lap)")
        )
    }
}
