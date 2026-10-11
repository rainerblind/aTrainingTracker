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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Architectural contract and resilience test for AppNavigationDrawer header asset resolution.
 * Verifies that the app logo is guarded against Resources$NotFoundException crashes and available
 * across all screen density splits.
 *
 * Fulfills verification requirement TST-UI-272 (ATT-3164 / REQ-UI-312).
 */
class AppNavigationDrawerAssetResilienceTest {

    @Test
    fun testDrawerHeaderUsesSafePainterResource() {
        val candidates = listOf(
            File("app/src/main/java/com/atrainingtracker/trainingtracker/ui/navigation/AppNavigationDrawer.kt"),
            File("src/main/java/com/atrainingtracker/trainingtracker/ui/navigation/AppNavigationDrawer.kt")
        )
        val sourceFile = candidates.firstOrNull { it.exists() }
            ?: throw IllegalStateException("AppNavigationDrawer.kt not found")

        val content = sourceFile.readText()

        assertTrue(
            "AppNavigationDrawer.kt must import safePainterResource",
            content.contains("import com.atrainingtracker.trainingtracker.ui.theme.safePainterResource")
        )
        assertTrue(
            "AppNavigationDrawer.kt must call safePainterResource for logo_512",
            content.contains("safePainterResource") && content.contains("id = R.drawable.logo_512")
        )
        assertTrue(
            "AppNavigationDrawer.kt must supply DirectionsRun fallback",
            content.contains("fallback = Icons.AutoMirrored.Filled.DirectionsRun")
        )
        assertFalse(
            "AppNavigationDrawer.kt must not use unguarded painterResource(id = R.drawable.logo_512)",
            content.contains("painterResource(id = R.drawable.logo_512)")
        )
        assertFalse(
            "AppNavigationDrawer.kt must not import unguarded painterResource",
            content.contains("import androidx.compose.ui.res.painterResource")
        )
    }

    @Test
    fun testLogo512PresentInRootAndDensityBuckets() {
        val rootDirs = listOf(
            File("app/src/main/res"),
            File("src/main/res")
        )
        val resDir = rootDirs.firstOrNull { it.exists() }
            ?: throw IllegalStateException("Resource directory not found")

        val targetFolders = listOf(
            "drawable",
            "drawable-mdpi",
            "drawable-hdpi",
            "drawable-xhdpi",
            "drawable-xxhdpi",
            "drawable-xxxhdpi",
            "drawable-nodpi"
        )

        for (folder in targetFolders) {
            val assetFile = File(resDir, "$folder/logo_512.png")
            assertTrue(
                "logo_512.png must exist in $folder/ for complete density-split APK parity",
                assetFile.exists()
            )
            assertTrue(
                "logo_512.png in $folder/ must not be empty",
                assetFile.length() > 0
            )
        }
    }

    @Test
    fun testLogo512ResourceIdIsValid() {
        val resourceId = R.drawable.logo_512
        assertTrue("R.drawable.logo_512 must be a positive non-zero resource ID", resourceId > 0)
    }
}
