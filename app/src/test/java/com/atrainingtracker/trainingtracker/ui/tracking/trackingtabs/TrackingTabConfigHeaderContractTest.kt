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

package com.atrainingtracker.trainingtracker.ui.tracking.trackingtabs

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Contract tests for TrackingTabConfigHeader and CopyTrackingTabDialog (REQ-UI-439.1, REQ-UI-439.2 / TST-UI-439.1 / ATT-5078).
 */
class TrackingTabConfigHeaderContractTest {

    @Test
    fun testTrackingTabConfigHeader_hasCopyButtonContract() {
        val headerFile = File("src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/trackingtabs/TrackingTabConfigHeader.kt")
        assertTrue("TrackingTabConfigHeader.kt must exist", headerFile.exists())
        val content = headerFile.readText()

        assertTrue("TrackingTabConfigHeader must import ContentCopy", content.contains("import androidx.compose.material.icons.filled.ContentCopy"))
        assertTrue("TrackingTabConfigHeader must declare onCopyTab callback", content.contains("onCopyTab: (Long) -> Unit"))
        assertTrue("TrackingTabConfigHeader must use Icons.Default.ContentCopy", content.contains("Icons.Default.ContentCopy"))
        assertTrue("TrackingTabConfigHeader must reference R.string.cd_copy_tab", content.contains("R.string.cd_copy_tab"))
        assertTrue("TrackingTabConfigHeader must invoke onCopyTab with tabViewId", content.contains("onCopyTab(viewInfo.tabViewId)"))
    }

    @Test
    fun testCopyTrackingTabDialog_existsAndDeclaresActivityTypeMultiSelect() {
        val dialogFile = File("src/main/java/com/atrainingtracker/trainingtracker/ui/settings/trackingtabs/CopyTrackingTabDialog.kt")
        assertTrue("CopyTrackingTabDialog.kt must exist", dialogFile.exists())
        val content = dialogFile.readText()

        assertTrue("CopyTrackingTabDialog must declare CopyTrackingTabDialog composable", content.contains("fun CopyTrackingTabDialog("))
        assertTrue("CopyTrackingTabDialog must declare CopyTrackingTabContent composable", content.contains("fun CopyTrackingTabContent("))
        assertTrue("CopyTrackingTabDialog must reference copy_tab_title", content.contains("R.string.copy_tab_title"))
        assertTrue("CopyTrackingTabDialog must reference copy_tab_action", content.contains("R.string.copy_tab_action"))
        assertTrue("CopyTrackingTabDialog must iterate over ActivityType.values()", content.contains("ActivityType.values().forEach"))
    }

    @Test
    fun testTrackingTabConfigHeader_hasMoveButtonsAndBoundaryContracts() {
        val headerFile = File("src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/trackingtabs/TrackingTabConfigHeader.kt")
        assertTrue("TrackingTabConfigHeader.kt must exist", headerFile.exists())
        val content = headerFile.readText()

        assertTrue("TrackingTabConfigHeader must import ArrowBack", content.contains("ArrowBack"))
        assertTrue("TrackingTabConfigHeader must import ArrowForward", content.contains("ArrowForward"))
        assertTrue("TrackingTabConfigHeader must declare tabIndex parameter", content.contains("tabIndex: Int"))
        assertTrue("TrackingTabConfigHeader must declare tabCount parameter", content.contains("tabCount: Int"))
        assertTrue("TrackingTabConfigHeader must declare onMoveTab callback", content.contains("onMoveTab: (Long, Boolean) -> Unit"))
        assertTrue("TrackingTabConfigHeader must reference R.string.cd_move_tab_left", content.contains("R.string.cd_move_tab_left"))
        assertTrue("TrackingTabConfigHeader must reference R.string.cd_move_tab_right", content.contains("R.string.cd_move_tab_right"))
        assertTrue("TrackingTabConfigHeader must gate Move Left with tabIndex > 0", content.contains("enabled = tabIndex > 0"))
        assertTrue("TrackingTabConfigHeader must gate Move Right with tabIndex < tabCount - 1", content.contains("enabled = tabIndex < tabCount - 1"))
    }
}
