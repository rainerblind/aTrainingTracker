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

package com.atrainingtracker.trainingtracker.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Automated test suite verifying edit action icon consistency, vector asset establishment,
 * design token tinting uniformity, and complete absence of legacy drawables (TST-UI-134.1, TST-UI-134.4).
 */
class EditIconConsistencyTest {

    private val resDir: File = sequenceOf(
        File("src/main/res"),
        File("app/src/main/res"),
        File("../app/src/main/res")
    ).firstOrNull { it.exists() } ?: File("src/main/res")

    private val javaDir: File = sequenceOf(
        File("src/main/java"),
        File("app/src/main/java"),
        File("../app/src/main/java")
    ).firstOrNull { it.exists() } ?: File("src/main/java")

    @Test
    fun testTableEditDrawableReservedForDrawerLayouts() {
        val tableEditDrawable = File(resDir, "drawable/ic_table_edit.xml")
        assertTrue(
            "drawable/ic_table_edit.xml must exist for drawer tracking table configuration",
            tableEditDrawable.exists()
        )
        val content = tableEditDrawable.readText()
        assertTrue("ic_table_edit.xml must be a vector drawable", content.contains("<vector"))
    }

    @Test
    fun testCanonicalEditVectorDrawableExistsAndIsValid() {
        val editVector = File(resDir, "drawable/ic_edit.xml")
        assertTrue("drawable/ic_edit.xml must exist", editVector.exists())
        val content = editVector.readText()
        assertTrue("ic_edit.xml must be a vector drawable", content.contains("<vector"))
        assertTrue("ic_edit.xml must define pathData", content.contains("pathData"))
    }

    @Test
    fun testNoLegacyMenuEditInMenuResources() {
        val menuDir = File(resDir, "menu")
        assertTrue("Menu directory must exist", menuDir.exists() && menuDir.isDirectory)

        menuDir.listFiles { file -> file.extension == "xml" }?.forEach { menuFile ->
            val content = menuFile.readText()
            assertFalse(
                "${menuFile.name} must not contain reference to legacy @android:drawable/ic_menu_edit",
                content.contains("@android:drawable/ic_menu_edit")
            )
        }

        val deviceListMenu = File(menuDir, "device_list_context_menu.xml")
        assertTrue("device_list_context_menu.xml must exist", deviceListMenu.exists())
        val deviceMenuContent = deviceListMenu.readText()
        assertTrue(
            "device_list_context_menu.xml must reference @drawable/ic_edit for editDevice",
            deviceMenuContent.contains("@drawable/ic_edit")
        )
    }

    @Test
    fun testNoLegacyTableEditReferencesInEntityHeaders() {
        val uiDir = File(javaDir, "com/atrainingtracker/trainingtracker/ui")
        assertTrue("UI directory must exist: ${uiDir.absolutePath}", uiDir.exists() && uiDir.isDirectory)

        val filesToCheck = listOf(
            File(uiDir, "components/workoutheader/WorkoutHeader.kt"),
            File(uiDir, "clusters/WorkoutClusterHeatmapScreen.kt"),
            File(uiDir, "knownlocations/EditKnownLocationDialog.kt"),
            File(uiDir, "settings/trackingtabs/ActivityTypeSelectionDialog.kt"),
            File(uiDir, "tracking/trackingtabs/TrackingTabPreviewHeader.kt"),
            File(uiDir, "map/MapScreenWithTrack.kt")
        )

        for (file in filesToCheck) {
            assertTrue("${file.name} must exist at ${file.absolutePath}", file.exists())
            val text = file.readText()
            assertFalse(
                "${file.name} must not reference ic_table_edit for entity editing",
                text.contains("ic_table_edit")
            )
        }

        // Verify AppNavigationDrawer explicitly uses ic_table_edit for drawer_tracking_layouts
        val drawerFile = File(uiDir, "navigation/AppNavigationDrawer.kt")
        assertTrue("AppNavigationDrawer.kt must exist", drawerFile.exists())
        val drawerText = drawerFile.readText()
        assertTrue(
            "AppNavigationDrawer.kt must use R.drawable.ic_table_edit for drawer_tracking_layouts",
            drawerText.contains("DrawerItemConfig(R.id.drawer_tracking_layouts, R.drawable.ic_table_edit")
        )
    }

    @Test
    fun testEditActionTintsArePrimary() {
        val uiDir = File(javaDir, "com/atrainingtracker/trainingtracker/ui")
        val trackingHeader = File(uiDir, "tracking/trackingtabs/TrackingTabPreviewHeader.kt")
        val trackingContent = trackingHeader.readText()
        assertTrue(
            "TrackingTabPreviewHeader edit button must use MaterialTheme.colorScheme.primary",
            trackingContent.contains("tint = MaterialTheme.colorScheme.primary")
        )

        val mapScreen = File(uiDir, "map/MapScreenWithTrack.kt")
        val mapContent = mapScreen.readText()
        assertTrue(
            "MapScreenWithTrack edit button must use MaterialTheme.colorScheme.primary",
            mapContent.contains("tint = MaterialTheme.colorScheme.primary")
        )
    }
}
