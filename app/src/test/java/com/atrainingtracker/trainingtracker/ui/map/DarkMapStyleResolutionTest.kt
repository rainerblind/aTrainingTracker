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

import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.MapType
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

import android.util.Log
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Before

/**
 * Unit tests and static architectural audits for universal dark map styling
 * and anti-flash container background protection.
 *
 * Traceability: REQ-MAP-021, TST-MAP-023.1, TST-MAP-023.2
 */
class DarkMapStyleResolutionTest {

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>(), any()) } returns 0
        DarkMapStyle.clearCache()
    }

    @After
    fun tearDown() {
        DarkMapStyle.clearCache()
        unmockkAll()
    }

    @Test
    fun testResolveMapPropertiesInDarkMode() {
        val mockDarkOptions = mockk<MapStyleOptions>(relaxed = true)
        val properties = DarkMapStyle.resolveMapProperties(
            isDark = true,
            darkMapStyleOptions = mockDarkOptions,
            isMyLocationEnabled = true
        )

        assertEquals("Dark mode must select MapType.NORMAL", MapType.NORMAL, properties.mapType)
        assertSame("Dark mode must attach DarkMapStyle options", mockDarkOptions, properties.mapStyleOptions)
        assertTrue("isMyLocationEnabled must be preserved", properties.isMyLocationEnabled)
    }

    @Test
    fun testResolveMapPropertiesInLightModePreservesTerrainDefault() {
        val mockDarkOptions = mockk<MapStyleOptions>(relaxed = true)
        val properties = DarkMapStyle.resolveMapProperties(
            isDark = false,
            darkMapStyleOptions = mockDarkOptions,
            isMyLocationEnabled = false
        )

        assertEquals("Light mode must default to MapType.TERRAIN", MapType.TERRAIN, properties.mapType)
        assertNull("Light mode must have null style options", properties.mapStyleOptions)
        assertFalse("isMyLocationEnabled must be preserved", properties.isMyLocationEnabled)
    }

    @Test
    fun testResolveMapPropertiesInLightModeCustomType() {
        val mockDarkOptions = mockk<MapStyleOptions>(relaxed = true)
        val properties = DarkMapStyle.resolveMapProperties(
            isDark = false,
            darkMapStyleOptions = mockDarkOptions,
            isMyLocationEnabled = true,
            lightMapType = MapType.HYBRID
        )

        assertEquals("Light mode must respect custom lightMapType", MapType.HYBRID, properties.mapType)
        assertNull("Light mode must have null style options", properties.mapStyleOptions)
        assertTrue("isMyLocationEnabled must be preserved", properties.isMyLocationEnabled)
    }

    @Test
    fun testDarkMapStyleJsonParsingValid() {
        val json = """[{"elementType":"geometry","stylers":[{"color":"#242f3e"}]}]"""
        val options = DarkMapStyle.parseStyleJson(json)
        assertNotNull("Valid JSON should parse successfully into MapStyleOptions", options)
    }

    @Test
    fun testDarkMapStyleJsonParsingInvalidFallback() {
        val corruptedJson = """{ invalid: json ]"""
        val options = DarkMapStyle.parseStyleJson(corruptedJson)
        assertNull("Corrupted JSON must safely return null without throwing", options)
    }

    @Test
    fun testStaticAuditMapFilesEnforceDynamicStylingAndAntiFlash() {
        val javaDir = sequenceOf(
            File("src/main/java"),
            File("app/src/main/java"),
            File("../app/src/main/java")
        ).firstOrNull { it.exists() } ?: File("src/main/java")

        val targetMapFiles = listOf(
            "com/atrainingtracker/trainingtracker/ui/map/ATrainingTrackerMap.kt",
            "com/atrainingtracker/trainingtracker/ui/map/PathPreviewMap.kt",
            "com/atrainingtracker/trainingtracker/ui/knownlocations/EditKnownLocationDialog.kt",
            "com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClusterComponents.kt",
            "com/atrainingtracker/trainingtracker/ui/clusters/ManualClusterScreen.kt",
            "com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodSummaryCard.kt",
            "com/atrainingtracker/trainingtracker/ui/components/workoutlaps/LapEditBottomSheet.kt",
            "com/atrainingtracker/trainingtracker/migration/ImportBackupTabsScreen.kt"
        )

        for (relPath in targetMapFiles) {
            val file = File(javaDir, relPath)
            assertTrue("Map file must exist: ${file.path}", file.exists())
            val content = file.readText()

            // 1. Ensure no hardcoded unstyled MapType.TERRAIN remains as direct properties
            assertFalse(
                "File ${file.name} must not contain hardcoded MapProperties(mapType = MapType.TERRAIN)",
                content.contains("properties = MapProperties(mapType = MapType.TERRAIN)")
            )

            // 2. Ensure dynamic DarkMapStyle resolution is utilized
            assertTrue(
                "File ${file.name} must reference DarkMapStyle or resolveMapProperties",
                content.contains("DarkMapStyle") || content.contains("resolveMapProperties")
            )

            // 3. Ensure anti-flash background protection is present
            assertTrue(
                "File ${file.name} must define anti-flash background protection (0xFF121212)",
                content.contains("0xFF121212")
            )
        }
    }
}
