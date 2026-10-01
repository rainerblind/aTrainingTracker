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

package com.atrainingtracker.trainingtracker.ui.knownlocations

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Structural contract test verifying that [KnownLocationCard] isolates the altitude metric
 * and places Starts and Strecken badges on their own dedicated row with compact sizing (REQ-UI-195, TST-UI-149.1).
 */
class KnownLocationCardLayoutTest {

    private fun loadScreenSource(): String {
        val candidates = listOf(
            File("app/src/main/java/com/atrainingtracker/trainingtracker/ui/knownlocations/KnownLocationsScreen.kt"),
            File("src/main/java/com/atrainingtracker/trainingtracker/ui/knownlocations/KnownLocationsScreen.kt"),
            File("../app/src/main/java/com/atrainingtracker/trainingtracker/ui/knownlocations/KnownLocationsScreen.kt")
        )
        val file = candidates.firstOrNull { it.exists() }
            ?: error("KnownLocationsScreen.kt not found in candidates: $candidates")
        return file.readText()
    }

    @Test
    fun testAltitudeDecoupledFromBadgesRow() {
        val content = loadScreenSource()

        // Verify altitude metric comment / structure exists
        assertTrue(
            "KnownLocationCard must contain dedicated altitude metric section",
            content.contains("// Prominent Altitude Metric")
        )

        // Verify dedicated badges row comment exists
        assertTrue(
            "KnownLocationCard must contain dedicated badges row",
            content.contains("// Dedicated Badges Row (Starts and Routes, REQ-UI-195)")
        )

        // Verify altitude metric appears BEFORE the dedicated badges row
        val altitudeIndex = content.indexOf("// Prominent Altitude Metric")
        val badgesIndex = content.indexOf("// Dedicated Badges Row (Starts and Routes, REQ-UI-195)")
        assertTrue(
            "Altitude metric must appear before dedicated badges row",
            altitudeIndex in 0 until badgesIndex
        )
    }

    @Test
    fun testCompactBadgeDimensionsAndStyling() {
        val content = loadScreenSource()

        // Badges must use RoundedCornerShape(8.dp) rather than 12.dp
        assertTrue(
            "Badges must use compact RoundedCornerShape(8.dp)",
            content.contains("shape = RoundedCornerShape(8.dp)")
        )

        // Badges must use compact internal padding (8.dp horizontal, 4.dp vertical)
        assertTrue(
            "Badges must use compact padding (horizontal = 8.dp, vertical = 4.dp)",
            content.contains("padding(horizontal = 8.dp, vertical = 4.dp)")
        )

        // Badges must NOT enforce defaultMinSize(minHeight = 48.dp) on their visual surface container
        assertFalse(
            "Badges must not force 48dp minimum container height",
            content.contains("defaultMinSize(minHeight = 48.dp)")
        )

        // Both test tags must be preserved
        assertTrue(
            "Starts badge test tag must be preserved",
            content.contains("location_starts_badge_\${item.id}")
        )
        assertTrue(
            "Routes badge test tag must be preserved",
            content.contains("location_routes_badge_\${item.id}")
        )
    }

    @Test
    fun testSubtleGhostBadgeStylingAndTokens() {
        val content = loadScreenSource()

        // Saturated primaryContainer pill background must be eliminated (REQ-UI-207)
        assertFalse(
            "Badges must not use saturated primaryContainer background",
            content.contains("primaryContainer.copy(alpha = 0.5f)")
        )

        // Badges must use subtle ghost container background
        assertTrue(
            "Badges must use subtle surfaceVariant background",
            content.contains("color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)")
        )

        // Badges must use onSurfaceVariant for contentColor
        assertTrue(
            "Badges must use onSurfaceVariant for contentColor",
            content.contains("contentColor = MaterialTheme.colorScheme.onSurfaceVariant")
        )

        // Badges must use subtle outline border
        assertTrue(
            "Badges must use subtle outlineVariant border stroke",
            content.contains("border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))")
        )

        // Badges must use labelMedium typography and medium font weight
        assertTrue(
            "Badges must use labelMedium typography",
            content.contains("style = MaterialTheme.typography.labelMedium")
        )
        assertTrue(
            "Badges must use FontWeight.Medium",
            content.contains("fontWeight = FontWeight.Medium")
        )
    }

    /**
     * REQ-UI-217 (Item 1): Verify that KnownLocationCard heading typography is standardized
     * to MaterialTheme.typography.titleLarge with FontWeight.Bold.
     */
    @Test
    fun testLocationCardStandardizedTitleLargeTypography() {
        val content = loadScreenSource()

        // Location card heading must use titleLarge
        assertTrue(
            "KnownLocationCard heading must use MaterialTheme.typography.titleLarge",
            content.contains("style = MaterialTheme.typography.titleLarge")
        )

        // Location card heading must use FontWeight.Bold
        assertTrue(
            "KnownLocationCard heading must use FontWeight.Bold",
            content.contains("fontWeight = FontWeight.Bold")
        )

        // titleMedium must no longer be used for the heading in KnownLocationCard
        val cardIndex = content.indexOf("private fun KnownLocationCard(")
        val cardContent = if (cardIndex != -1) content.substring(cardIndex) else content
        assertFalse(
            "KnownLocationCard must no longer use titleMedium for location title",
            cardContent.contains("style = MaterialTheme.typography.titleMedium")
        )
    }

    /**
     * REQ-UI-217 (Items 2 & 3): Verify that KnownLocationCard contains a compact 80dp map preview
     * thumbnail in Google Maps lite mode on the right side, wired to onShowOnMap with offline inspection safety.
     */
    @Test
    fun testMapPreviewThumbnailLayoutAndLiteMode() {
        val content = loadScreenSource()

        // Map preview thumbnail composable must exist
        assertTrue(
            "KnownLocationThumbnailMap composable must exist",
            content.contains("private fun KnownLocationThumbnailMap(")
        )

        // Map preview thumbnail must define test tag
        assertTrue(
            "Map preview thumbnail must define location_map_preview_ test tag",
            content.contains("location_map_preview_\${item.id}")
        )

        // Map preview thumbnail must use 80dp square dimensions
        assertTrue(
            "Map preview thumbnail must be 80.dp",
            content.contains(".size(80.dp)")
        )

        // Map preview thumbnail must use 12dp rounded corner shape
        assertTrue(
            "Map preview thumbnail must use RoundedCornerShape(12.dp)",
            content.contains("RoundedCornerShape(12.dp)")
        )

        // Map preview must enforce Google Maps lite mode
        assertTrue(
            "Map preview must run in liteMode(true)",
            content.contains("GoogleMapOptions().liteMode(true)")
        )

        // Map preview must wire onShowOnMap click callback
        assertTrue(
            "Map preview must wire onShowOnMap callback",
            content.contains("onMapClick = { onShowOnMap() }") || content.contains("onShowOnMap = onShowOnMap")
        )

        // Map preview must handle LocalInspectionMode.current for offline testing and Compose Previews
        assertTrue(
            "Map preview must handle LocalInspectionMode.current",
            content.contains("LocalInspectionMode.current")
        )
    }
}

