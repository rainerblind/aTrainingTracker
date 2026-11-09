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
            content.contains("// Altitude Metric")
        )

        // Verify dedicated starts section exists
        assertTrue(
            "KnownLocationCard must contain dedicated starts section",
            content.contains("// Number of Starts")
        )

        // Verify altitude metric appears BEFORE the starts metric
        val altitudeIndex = content.indexOf("// Altitude Metric")
        val startsIndex = content.indexOf("// Number of Starts")
        assertTrue(
            "Altitude metric must appear before starts metric",
            altitudeIndex in 0 until startsIndex
        )

        // REQ-UI-465.2: Verify semantic Terrain icon and elimination of ic_ascent
        assertTrue(
            "KnownLocationCard must use Icons.Default.Terrain for altitude metric",
            content.contains("imageVector = Icons.Default.Terrain")
        )
        assertFalse(
            "KnownLocationCard must eliminate misleading ic_ascent arrow",
            content.contains("R.drawable.ic_ascent")
        )
    }

    /**
     * REQ-UI-469.1 / TST-UI-469.1: Harmonized bottom-anchored column architecture with IntrinsicSize.Min.
     */
    @Test
    fun testHarmonizedBottomAnchoredColumnArchitecture() {
        val content = loadScreenSource()

        // Row container must enforce height(IntrinsicSize.Min) and bottom alignment
        assertTrue(
            "KnownLocationCard row must enforce height(IntrinsicSize.Min)",
            content.contains(".height(IntrinsicSize.Min)")
        )
        assertTrue(
            "KnownLocationCard row must align to bottom",
            content.contains("verticalAlignment = Alignment.Bottom")
        )

        // Left metadata column must use fillMaxHeight() and vertical spring spacer
        assertTrue(
            "Metadata column must use fillMaxHeight()",
            content.contains(".fillMaxHeight()")
        )
        assertTrue(
            "Metadata column must contain vertical spring Spacer(modifier = Modifier.weight(1f))",
            content.contains("Spacer(modifier = Modifier.weight(1f))")
        )
    }

    /**
     * REQ-UI-469.2 / TST-UI-469.1: Unboxed primary starts metric with DirectionsRun icon and bold primary typography.
     */
    @Test
    fun testUnboxedPrimaryStartsMetric() {
        val content = loadScreenSource()

        // Starts metric must use DirectionsRun icon
        assertTrue(
            "Starts metric must use Icons.AutoMirrored.Filled.DirectionsRun",
            content.contains("Icons.AutoMirrored.Filled.DirectionsRun")
        )

        // Starts metric must use bold primary blue typography
        assertTrue(
            "Starts metric must use bodyMedium typography",
            content.contains("style = MaterialTheme.typography.bodyMedium")
        )
        assertTrue(
            "Starts metric must use FontWeight.Bold",
            content.contains("fontWeight = FontWeight.Bold")
        )
        assertTrue(
            "Starts metric must use colorScheme.primary",
            content.contains("color = MaterialTheme.colorScheme.primary")
        )

        // Starts metric click target tag must be preserved
        assertTrue(
            "Starts test tag must be preserved",
            content.contains("location_starts_badge_\${item.id}")
        )
    }

    /**
     * REQ-UI-470.6 / TST-UI-470.7b: Dynamic sport breakdown in canonical order [Bike, Run, Other]
     * using authentic multi-color vector drawables with Color.Unspecified.
     */
    @Test
    fun testSportTypeStartsBreakdownInCanonicalOrder() {
        val content = loadScreenSource()

        // Must define SportStartsItem composable with Color.Unspecified to protect multi-color vectors
        assertTrue(
            "Must define SportStartsItem composable",
            content.contains("private fun SportStartsItem(")
        )
        assertTrue(
            "SportStartsItem must render with tint = Color.Unspecified",
            content.contains("tint = Color.Unspecified")
        )

        // Must reference authentic sport vector drawables
        assertTrue(
            "Starts breakdown must reference R.drawable.bsport_bike",
            content.contains("R.drawable.bsport_bike")
        )
        assertTrue(
            "Starts breakdown must reference R.drawable.bsport_run",
            content.contains("R.drawable.bsport_run")
        )
        assertTrue(
            "Starts breakdown must reference R.drawable.bsport_other",
            content.contains("R.drawable.bsport_other")
        )

        // Canonical ordering [Bike -> Run -> Other] in layout source
        val bikeIdx = content.indexOf("R.drawable.bsport_bike")
        val runIdx = content.indexOf("R.drawable.bsport_run")
        val otherIdx = content.indexOf("R.drawable.bsport_other")
        assertTrue("Bike must precede Run in canonical layout order", bikeIdx in 0 until runIdx)
        assertTrue("Run must precede Other in canonical layout order", runIdx in 0 until otherIdx)
    }

    /**
     * REQ-UI-469.3 / TST-UI-469.1: Unboxed secondary routes cross-link with ic_favorite_route.
     */
    @Test
    fun testUnboxedSecondaryRoutesCrossLink() {
        val content = loadScreenSource()

        // Routes cross-link must use ic_favorite_route
        assertTrue(
            "Routes cross-link must use ic_favorite_route",
            content.contains("R.drawable.ic_favorite_route")
        )

        // Routes cross-link must use bodySmall and primary color
        assertTrue(
            "Routes cross-link must use bodySmall typography",
            content.contains("style = MaterialTheme.typography.bodySmall")
        )
        assertTrue(
            "Routes cross-link must use colorScheme.primary",
            content.contains("color = MaterialTheme.colorScheme.primary")
        )

        // Routes test tag must be preserved
        assertTrue(
            "Routes test tag must be preserved",
            content.contains("location_routes_badge_\${item.id}")
        )
    }

    /**
     * REQ-UI-469 / TST-UI-469.1: Boxed/bordered Surface pill badges must be eliminated from metadata column.
     */
    @Test
    fun testEliminationOfBoxedPillContainersInMetadataColumn() {
        val content = loadScreenSource()

        val cardIndex = content.indexOf("private fun KnownLocationCard(")
        val cardContent = if (cardIndex != -1) content.substring(cardIndex) else content

        // Saturated or tinted pill backgrounds must be eliminated
        assertFalse(
            "Card must eliminate secondaryContainer pill background",
            cardContent.contains("secondaryContainer")
        )
        assertFalse(
            "Card must eliminate surfaceVariant pill background",
            cardContent.contains("surfaceVariant.copy(alpha = 0.35f)")
        )

        // BorderStroke on badges must be eliminated
        assertFalse(
            "Card must eliminate BorderStroke pill borders",
            cardContent.contains("BorderStroke(")
        )

        // Arrow chevrons on badges must be eliminated
        assertFalse(
            "Card must eliminate ArrowForward chevrons on badges",
            cardContent.contains("Icons.AutoMirrored.Filled.ArrowForward")
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
     * REQ-UI-217 / REQ-UI-244 / REQ-UI-465.3: Verify that KnownLocationCard contains a compact
     * 116x80dp landscape map preview thumbnail in Google Maps lite mode on the right side,
     * wired to onShowOnMap with offline inspection safety.
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

        // Map preview thumbnail must use 116x80dp landscape dimensions (REQ-UI-465.3)
        assertTrue(
            "Map preview thumbnail must be 116.dp width by 80.dp height",
            content.contains(".size(width = 116.dp, height = 80.dp)")
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

    /**
     * REQ-UI-224 (TST-UI-178.2): Verify that KnownLocationThumbnailMap integrates calibrated
     * zoom calculation from KnownLocationZoomMath and eliminates the hardcoded 14.5f zoom literal.
     */
    @Test
    fun testMapPreviewThumbnail_usesCalibratedZoomMath() {
        val content = loadScreenSource()

        // Must compute targetZoom via KnownLocationZoomMath.calculateThumbnailZoom
        assertTrue(
            "KnownLocationsScreen must calculate targetZoom via KnownLocationZoomMath.calculateThumbnailZoom (REQ-UI-224)",
            content.contains("KnownLocationZoomMath.calculateThumbnailZoom(item.radius, item.latLng.latitude)")
        )

        // Must wire targetZoom into CameraPosition.fromLatLngZoom
        assertTrue(
            "KnownLocationsScreen must wire targetZoom into CameraPosition.fromLatLngZoom",
            content.contains("CameraPosition.fromLatLngZoom(item.latLng, targetZoom)")
        )

        // Must wire targetZoom into cameraPositionState.move
        assertTrue(
            "KnownLocationsScreen must wire targetZoom into cameraPositionState.move",
            content.contains("CameraUpdateFactory.newLatLngZoom(item.latLng, targetZoom)")
        )
    }

    @Test
    fun testMapPreviewThumbnail_eliminatesHardcoded14_5Literal() {
        val content = loadScreenSource()

        // Hardcoded 14.5f zoom literal must be eliminated
        assertFalse(
            "KnownLocationsScreen must eliminate hardcoded 14.5f zoom literal (REQ-UI-224)",
            content.contains("14.5f")
        )
    }
}

