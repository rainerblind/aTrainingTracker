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

package com.atrainingtracker.trainingtracker.ui.navigation

import com.atrainingtracker.R
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Unit test verifying resource availability and density-split immunity for the navigation drawer header.
 *
 * Fulfills verification requirement TST-STB-006 (ATT-1036 / REQ-STB-006).
 */
class DrawerHeaderResourceTest {

    @Test
    fun testMenuHeaderBackgroundResourceIdIsValid() {
        val resourceId = R.drawable.menu_header_background
        assertTrue("R.drawable.menu_header_background must be a positive non-zero resource ID", resourceId > 0)
    }

    @Test
    fun testUniversalFallbackExistsInDrawableNodpi() {
        val nodpiFile = File("src/main/res/drawable-nodpi/menu_header_background.jpg")
        val altPath = File("app/src/main/res/drawable-nodpi/menu_header_background.jpg")
        val file = if (nodpiFile.exists()) nodpiFile else altPath

        assertTrue(
            "menu_header_background.jpg must exist in drawable-nodpi/ to ensure inclusion in base APK during bundle splitting",
            file.exists()
        )
        assertTrue("Fallback menu_header_background.jpg in drawable-nodpi/ must not be empty", file.length() > 0)
    }

    @Test
    fun testDensityBucketsContainMenuHeaderBackground() {
        val densities = listOf("drawable-mdpi", "drawable-hdpi", "drawable-xhdpi", "drawable-xxhdpi", "drawable-xxxhdpi")
        for (density in densities) {
            val localPath = File("src/main/res/$density/menu_header_background.jpg")
            val altPath = File("app/src/main/res/$density/menu_header_background.jpg")
            val file = if (localPath.exists()) localPath else altPath

            assertTrue("menu_header_background.jpg must exist in $density", file.exists())
            assertTrue("menu_header_background.jpg in $density must not be empty", file.length() > 0)
        }
    }

    @Test
    fun testLogo512AndHeaderBackgroundBothInNodpi() {
        val nodpiDir = File("src/main/res/drawable-nodpi").takeIf { it.exists() }
            ?: File("app/src/main/res/drawable-nodpi")

        val logoFile = File(nodpiDir, "logo_512.png")
        val backgroundFile = File(nodpiDir, "menu_header_background.jpg")

        assertTrue("logo_512.png must exist in drawable-nodpi", logoFile.exists() && logoFile.length() > 0)
        assertTrue("menu_header_background.jpg must exist in drawable-nodpi", backgroundFile.exists() && backgroundFile.length() > 0)
    }
}
