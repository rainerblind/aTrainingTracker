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

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Architectural and visual contract tests for MapScreenWithTrack, ATrainingTrackerMap,
 * navigation bar insets, and top-start navigation button (REQ-MAP-059.2, REQ-MAP-059.3, REQ-MAP-059.4 / TST-MAP-061.4).
 */
class MapScreenWithTrackTest {

    private fun findProjectRoot(): File {
        var dir: File = File(".").canonicalFile
        while (dir.parentFile != null) {
            if (File(dir, "gradlew").exists() && File(dir, "app").exists()) {
                return dir
            }
            dir = dir.parentFile!!
        }
        return File(".").canonicalFile
    }

    private fun resolveSourceFile(relativePath: String): File {
        val root = findProjectRoot()
        val target = File(root, relativePath)
        assertTrue("Source file must exist: $relativePath", target.exists())
        return target
    }

    @Test
    fun testMapScreenWithTrack_forwardsNavigationBarInsetsToATrainingTrackerMap() {
        val content = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapScreenWithTrack.kt").readText()

        assertTrue(
            "MapScreenWithTrack must pass WindowInsets.navigationBars.asPaddingValues() to ATrainingTrackerMap (REQ-MAP-059.3)",
            content.contains("contentPadding = WindowInsets.navigationBars.asPaddingValues()")
        )
    }

    @Test
    fun testMapScreenWithTrack_definesTopStartNavigationButton() {
        val content = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapScreenWithTrack.kt").readText()

        assertTrue(
            "MapScreenWithTrack must accept onMenuClick parameter (REQ-MAP-059.4)",
            content.contains("onMenuClick: (() -> Unit)? = null")
        )
        assertTrue(
            "MapScreenWithTrack must accept onNavigateBack parameter (REQ-MAP-059.4)",
            content.contains("onNavigateBack: (() -> Unit)? = null")
        )
        assertTrue(
            "MapScreenWithTrack must define navigation button with testTag map_navigation_button",
            content.contains(".testTag(\"map_navigation_button\")")
        )
        assertTrue(
            "Navigation button must be anchored at Alignment.TopStart with statusBarsPadding()",
            content.contains(".align(Alignment.TopStart)") && content.contains(".statusBarsPadding()")
        )
        assertTrue(
            "Navigation button must be 44dp circular Surface",
            content.contains(".size(44.dp)") && content.contains("shape = CircleShape")
        )
        assertTrue(
            "Navigation button must toggle between Menu and ArrowBack icons based on navigation context",
            content.contains("Icons.AutoMirrored.Filled.ArrowBack") && content.contains("Icons.Default.Menu")
        )
    }

    @Test
    fun testATrainingTrackerMap_declaresAndForwardsContentPadding() {
        val content = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/ATrainingTrackerMap.kt").readText()

        assertTrue(
            "ATrainingTrackerMap must accept contentPadding parameter with default empty PaddingValues",
            content.contains("contentPadding: PaddingValues = PaddingValues()")
        )
        assertTrue(
            "ATrainingTrackerMap must forward contentPadding to GoogleMap",
            content.contains("contentPadding = contentPadding")
        )
        assertTrue(
            "ATrainingTrackerMap must preserve topPadding = 0.dp under Rule 33",
            content.contains("val topPadding = 0.dp")
        )
    }

    @Test
    fun testATrainingTrackerMap_initializesCameraWithFallbackResolverAtZoom14() {
        val content = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/ATrainingTrackerMap.kt").readText()

        assertTrue(
            "ATrainingTrackerMap must resolve initial camera coordinates via MapInitialLocationResolver (REQ-MAP-059.2)",
            content.contains("MapInitialLocationResolver.resolveInitialLocation(context, currentLocationFlow.value)")
        )
        assertTrue(
            "ATrainingTrackerMap must initialize CameraPosition at DEFAULT_INITIAL_ZOOM when initialBounds is null",
            content.contains("CameraPosition.fromLatLngZoom(") &&
            content.contains("MapInitialLocationResolver.DEFAULT_INITIAL_ZOOM")
        )
    }

    @Test
    fun testATrainingTrackerMap_forwardsInitialLocationToMapBoundsController_TST_MAP_062_4() {
        val content = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/ATrainingTrackerMap.kt").readText()

        assertTrue(
            "ATrainingTrackerMap must pass initialLocation to MapBoundsController (REQ-MAP-060.2)",
            content.contains("initialLocation = initialLocation")
        )
    }

    @Test
    fun testATrainingTrackerApp_wiresMapNavigationCallbacks() {
        val content = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/navigation/ATrainingTrackerApp.kt").readText()

        assertTrue(
            "ATrainingTrackerApp must wire onMenuClick to drawerController.openDrawer()",
            content.contains("drawerController.openDrawer()")
        )
        assertTrue(
            "ATrainingTrackerApp must wire onNavigateBack to navController.popBackStack()",
            content.contains("navController.popBackStack()")
        )
    }

    @Test
    fun testMapContentScope_usesCenteredHeartGlyphMarkerWithConcentricAnchor_TST_MAP_065_2() {
        val content = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapContentScope.kt").readText()

        assertTrue(
            "MapContentScope must use createCenteredHeartGlyphMarker for known locations layer (REQ-MAP-063.2)",
            content.contains("createCenteredHeartGlyphMarker(context, primaryColor, Color.White, alpha = 0.85f)")
        )
        assertTrue(
            "MapContentScope must specify concentric anchor Offset(0.5f, 0.5f) for known location markers (REQ-MAP-063.2)",
            content.contains("anchor = Offset(0.5f, 0.5f)")
        )
    }

    @Test
    fun testEditKnownLocationDialog_usesCenteredHeartGlyphMarkerWithConcentricAnchor_TST_MAP_065_1() {
        val content = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/knownlocations/EditKnownLocationDialog.kt").readText()

        assertTrue(
            "EditKnownLocationDialog must use createCenteredHeartGlyphMarker for LocationMiniMap (REQ-MAP-063.1)",
            content.contains("createCenteredHeartGlyphMarker(")
        )
        assertTrue(
            "EditKnownLocationDialog must specify anchor = Offset(0.5f, 0.5f) (REQ-MAP-063.1)",
            content.contains("anchor = Offset(0.5f, 0.5f)")
        )
        assertTrue(
            "EditKnownLocationDialog must render Icons.Default.Favorite in preview mode (REQ-MAP-063.1)",
            content.contains("imageVector = Icons.Default.Favorite")
        )
    }

    @Test
    fun testCrossSurfaceMarkerParity_thumbnailAndEditAndGlobalMap_TST_MAP_065_3() {
        val thumbnailContent = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/knownlocations/KnownLocationsScreen.kt").readText()
        val editContent = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/knownlocations/EditKnownLocationDialog.kt").readText()
        val mapContent = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapContentScope.kt").readText()

        assertTrue("Thumbnail map must use createCenteredHeartGlyphMarker", thumbnailContent.contains("createCenteredHeartGlyphMarker("))
        assertTrue("Edit dialog map must use createCenteredHeartGlyphMarker", editContent.contains("createCenteredHeartGlyphMarker("))
        assertTrue("Global map must use createCenteredHeartGlyphMarker", mapContent.contains("createCenteredHeartGlyphMarker("))

        assertTrue("Thumbnail map must have anchor = Offset(0.5f, 0.5f)", thumbnailContent.contains("anchor = Offset(0.5f, 0.5f)"))
        assertTrue("Edit dialog map must have anchor = Offset(0.5f, 0.5f)", editContent.contains("anchor = Offset(0.5f, 0.5f)"))
        assertTrue("Global map must have anchor = Offset(0.5f, 0.5f)", mapContent.contains("anchor = Offset(0.5f, 0.5f)"))
    }

    @Test
    fun testLegacyHeartPinMarker_isAnnotatedDeprecated_TST_MAP_065_4() {
        val content = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapUtils.kt").readText()

        assertTrue(
            "createHeartPinMarker must be annotated with @Deprecated (REQ-MAP-063.3)",
            content.contains("@Deprecated") && content.contains("fun createHeartPinMarker(")
        )
    }

    @Test
    fun testMapScreenWithTrack_climbsLayerAndClimbDetailSheetIntegration_TST_MAP_066_4() {
        val content = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapScreenWithTrack.kt").readText()

        assertTrue(
            "MapScreenWithTrack must gate climbs layer with GeneralMapLayer.CLIMBS in enabledLayers (REQ-MAP-064.4)",
            content.contains("if (GeneralMapLayer.CLIMBS in enabledLayers)")
        )
        assertTrue(
            "MapScreenWithTrack must render climbs polylines with onClimbClick callback",
            content.contains("climbs(uiState.climbs, onClimbClick =")
        )
        assertTrue(
            "MapScreenWithTrack must display ClimbDetailSheet when climb is selected",
            content.contains("ClimbDetailSheet(")
        )
        assertTrue(
            "MapScreenWithTrack must handle GeneralMapLayer.CLIMBS in layer selection menu with map_layer_climbs string",
            content.contains("GeneralMapLayer.CLIMBS -> Pair(") && content.contains("stringResource(R.string.map_layer_climbs)")
        )
    }
}
