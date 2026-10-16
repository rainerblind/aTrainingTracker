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

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * TST-UI-357 / REQ-UI-394 (ATT-3779):
 * Verifies the ElevationProfile empty-state rendering contract on tracking tabs when enabled
 * before tracking starts or without an active route, canonical minimum height sizing (70.dp),
 * and 9-locale localization parity for `elevation_profile_empty_state`.
 */
class ElevationProfileEmptyStateContractTest {

    private fun resolveProjectRoot(): File {
        val userDir = File(System.getProperty("user.dir") ?: ".")
        return if (File(userDir, "app/src/main").exists()) {
            userDir
        } else {
            userDir.parentFile ?: userDir
        }
    }

    @Test
    fun `canonical minimum elevation profile height for zero range is 70dp`() {
        val height = calculateElevationProfileHeight(0.0)
        assertEquals(70.dp, height)
    }

    @Test
    fun `ElevationProfile exposes showEmptyState parameter and delegates to ElevationProfileEmptyState`() {
        val root = resolveProjectRoot()
        val file = File(root, "app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/ElevationProfile.kt")
        assertTrue("ElevationProfile.kt must exist", file.exists())
        val content = file.readText()

        assertTrue(
            "ElevationProfile must declare showEmptyState: Boolean = false",
            content.contains("showEmptyState: Boolean = false")
        )
        assertTrue(
            "ElevationProfile must invoke ElevationProfileEmptyState when pathPoints.size < 2 && showEmptyState",
            content.contains("if (showEmptyState)") && content.contains("ElevationProfileEmptyState(")
        )
        assertTrue(
            "ElevationProfileEmptyState must use canonical calculateElevationProfileHeight(0.0)",
            content.contains("val targetHeight = fixedHeight ?: calculateElevationProfileHeight(0.0)")
        )
        assertTrue(
            "ElevationProfileEmptyState must reference R.string.elevation_profile_empty_state",
            content.contains("stringResource(R.string.elevation_profile_empty_state)")
        )
    }

    @Test
    fun `SensorGridScreen renders ElevationProfile whenever showElevationProfile is true with showEmptyState enabled`() {
        val root = resolveProjectRoot()
        val file = File(root, "app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt")
        assertTrue("SensorGridScreen.kt must exist", file.exists())
        val content = file.readText()

        assertFalse(
            "SensorGridScreen must not suppress ElevationProfile behind resolvedProfile.pathPoints.size >= 2",
            content.contains("if (state.showElevationProfile && resolvedProfile.pathPoints.size >= 2)")
        )
        assertTrue(
            "SensorGridScreen must gate ElevationProfile solely on if (state.showElevationProfile)",
            content.contains("if (state.showElevationProfile)")
        )
        assertTrue(
            "SensorGridScreen must pass showEmptyState = true to ElevationProfile",
            content.contains("showEmptyState = true")
        )
    }

    @Test
    fun `elevation_profile_empty_state is defined across all 9 supported locales`() {
        val root = resolveProjectRoot()
        val locales = listOf(
            "values",
            "values-de",
            "values-es",
            "values-fr",
            "values-it",
            "values-ja",
            "values-nl",
            "values-pl",
            "values-pt"
        )

        for (locale in locales) {
            val stringsFile = File(root, "app/src/main/res/$locale/strings.xml")
            assertTrue("Missing strings.xml for $locale", stringsFile.exists())
            val xml = stringsFile.readText()
            assertTrue(
                "Missing elevation_profile_empty_state in $locale/strings.xml",
                xml.contains("<string name=\"elevation_profile_empty_state\">")
            )
        }
    }
}
