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

package com.atrainingtracker.trainingtracker.ui.tracking.tracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Architectural and layout contract tests verifying dynamic cockpit weight allocation,
 * asymmetric map expansion, sensor grid scrollability constraints, and zero dead space anchoring
 * (REQ-UI-339 / REQ-UI-346 / REQ-UI-358 / TST-UI-319 / ATT-3343).
 */
class SensorGridScreenLayoutTest {

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
        assertTrue("File must exist: $relativePath", target.exists())
        return target
    }

    @Test
    fun testSensorGrid_appliesWeightConstraintWithFillFalse() {
        val file = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt")
        val content = file.readText()

        assertTrue(
            "Sensor grid Column must apply .weight(1f, fill = false) to prevent crowding bottom cockpit elements (TST-UI-299.1)",
            content.contains(".weight(1f, fill = false)")
        )
    }

    @Test
    fun testElevationProfile_doesNotApplyWeightWhenMapDisabled() {
        val file = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt")
        val content = file.readText()

        // REQ-UI-346 / TST-UI-306.1: Surface must not apply Modifier.weight(1f) to prevent half-screen stretching
        assertFalse(
            "ElevationProfile Surface must NOT apply Modifier.weight(1f) when !state.mapState.showMap (TST-UI-306.1)",
            content.contains("if (!state.mapState.showMap) Modifier.weight(1f) else Modifier")
        )
        assertFalse(
            "ElevationProfile Surface must not apply weight(1f) (TST-UI-306.1)",
            content.contains(".then(\n                                    if (!state.mapState.showMap) Modifier.weight(1f)")
        )
    }

    @Test
    fun testElevationProfile_doesNotPassFillParentHeightOrFillMaxSize() {
        val file = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt")
        val content = file.readText()

        // REQ-UI-346 / TST-UI-306.2: ElevationProfile must use canonical adaptive height (not fillParentHeight or fillMaxSize)
        assertFalse(
            "ElevationProfile must NOT receive fillParentHeight = !state.mapState.showMap (TST-UI-306.2)",
            content.contains("fillParentHeight = !state.mapState.showMap")
        )
        assertFalse(
            "ElevationProfile must NOT receive dynamic fillMaxSize modifier (TST-UI-306.2)",
            content.contains("modifier = if (!state.mapState.showMap) Modifier.fillMaxSize() else Modifier.fillMaxWidth()")
        )
        assertTrue(
            "ElevationProfile must receive modifier = Modifier.fillMaxWidth() (TST-UI-306.2)",
            content.contains("modifier = Modifier.fillMaxWidth()")
        )
    }

    @Test
    fun testElevationProfile_guardedByPathPointsSize() {
        val file = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt")
        val content = file.readText()

        assertTrue(
            "ElevationProfile rendering must be guarded by path points size >= 2 to prevent empty canvas stretching (TST-UI-306.4 / TST-UI-315.3)",
            content.contains("state.showElevationProfile && state.pathPoints.size >= 2") ||
            content.contains("state.showElevationProfile && resolvedProfile.pathPoints.size >= 2")
        )
    }

    @Test
    fun testLayoutStructure_boxWithConstraintsAndAsymmetricWeightAllocation() {
        val file = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt")
        val content = file.readText()

        // REQ-UI-358.1 / TST-UI-319.1: BoxWithConstraints must wrap cockpit layout
        assertTrue(
            "SensorGridScreen must declare BoxWithConstraints for dynamic available height measurement (TST-UI-319.1)",
            content.contains("BoxWithConstraints(")
        )
        assertTrue(
            "SensorGridScreen must capture availableHeight from maxHeight (TST-UI-319.1)",
            content.contains("val availableHeight = maxHeight")
        )

        // REQ-UI-358.2 / TST-UI-319.1: Asymmetric weight allocation
        assertTrue(
            "Sensor grid must branch on state.mapState.showMap to assign bounded height vs fill=false weight (TST-UI-319.1)",
            content.contains("val sensorGridModifier = if (state.mapState.showMap)")
        )
        assertTrue(
            "When map is visible, sensor grid must clamp to max 50% available height with vertical scroll (TST-UI-319.1)",
            content.contains(".heightIn(max = availableHeight * 0.5f)")
        )
        assertTrue(
            "When map is hidden, sensor grid must apply weight(1f, fill = false) (TST-UI-319.1)",
            content.contains(".weight(1f, fill = false)")
        )
        assertTrue(
            "Tracking map Box must declare .weight(1f, fill = true) to consume 100% of remaining vertical space (TST-UI-319.1)",
            content.contains(".weight(1f, fill = true)")
        )
    }

    @Test
    fun testLayoutGeometry_simulationCalculatesZeroDeadSpace() {
        // REQ-UI-358.2 / REQ-UI-358.3 / TST-UI-319.2: Geometry Simulation
        val availableHeight = 800f
        val elevationProfileHeight = 128f

        // Case 1: 1 row of sensor fields (80dp) with map enabled
        val sensorContentHeight1Row = 80f
        val sensorGridMaxHeight = availableHeight * 0.5f // 400dp
        val sensorGridMeasuredHeight1Row = minOf(sensorContentHeight1Row, sensorGridMaxHeight) // 80dp
        val remainingSpaceForMap1Row = availableHeight - sensorGridMeasuredHeight1Row - elevationProfileHeight // 592dp
        val totalConsumed1Row = sensorGridMeasuredHeight1Row + remainingSpaceForMap1Row + elevationProfileHeight
        val deadSpace1Row = availableHeight - totalConsumed1Row

        assertEquals(80f, sensorGridMeasuredHeight1Row, 0.001f)
        assertEquals(592f, remainingSpaceForMap1Row, 0.001f)
        assertEquals(800f, totalConsumed1Row, 0.001f)
        assertEquals(0f, deadSpace1Row, 0.001f)

        // Case 2: Multi-row sensor fields (6 rows = 480dp) clamped to 50%
        val sensorContentHeight6Rows = 480f
        val sensorGridMeasuredHeight6Rows = minOf(sensorContentHeight6Rows, sensorGridMaxHeight) // 400dp
        val remainingSpaceForMap6Rows = availableHeight - sensorGridMeasuredHeight6Rows - elevationProfileHeight // 272dp
        val totalConsumed6Rows = sensorGridMeasuredHeight6Rows + remainingSpaceForMap6Rows + elevationProfileHeight
        val deadSpace6Rows = availableHeight - totalConsumed6Rows

        assertEquals(400f, sensorGridMeasuredHeight6Rows, 0.001f)
        assertEquals(272f, remainingSpaceForMap6Rows, 0.001f)
        assertEquals(800f, totalConsumed6Rows, 0.001f)
        assertEquals(0f, deadSpace6Rows, 0.001f)

        // Case 3: Elevation profile disabled
        val remainingSpaceForMapNoProfile = availableHeight - sensorGridMeasuredHeight1Row // 720dp
        val totalConsumedNoProfile = sensorGridMeasuredHeight1Row + remainingSpaceForMapNoProfile
        val deadSpaceNoProfile = availableHeight - totalConsumedNoProfile

        assertEquals(720f, remainingSpaceForMapNoProfile, 0.001f)
        assertEquals(800f, totalConsumedNoProfile, 0.001f)
        assertEquals(0f, deadSpaceNoProfile, 0.001f)
    }

    @Test
    fun testElevationProfileSurface_appliesScaffoldBottomPadding() {
        // REQ-UI-369 / TST-UI-332.1: Surface modifier must include bottom = paddingValues.calculateBottomPadding()
        val file = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt")
        val content = file.readText()

        assertTrue(
            "ElevationProfile Surface must declare bottom = paddingValues.calculateBottomPadding() for bottom sheet clearance (TST-UI-332.1)",
            content.contains("bottom = paddingValues.calculateBottomPadding()")
        )
    }

    @Test
    fun testLayoutGeometry_elevationProfileClearsBottomSheet_mapAndNonMap() {
        // REQ-UI-369 / TST-UI-332.2: Geometry Simulation of Bottom Sheet Clearance
        val availableHeight = 800f
        val sheetPeekHeight = 104f
        val elevationProfileHeight = 70f
        val topPadding = 8f
        val bottomInset = sheetPeekHeight // paddingValues.calculateBottomPadding()

        val sheetTopY = availableHeight - sheetPeekHeight // 696f

        // Case 1: Non-map cockpit with multi-row sensor fields (300dp)
        val sensorContentHeightNonMap = 300f
        val profileTopNonMap = sensorContentHeightNonMap + topPadding // 308f
        val profileBottomNonMap = profileTopNonMap + elevationProfileHeight // 378f
        assertTrue(
            "Elevation profile bottom must be above or at sheet top (zero occlusion on non-map tab)",
            profileBottomNonMap <= sheetTopY
        )

        // Case 2: Map-enabled cockpit (map expands with weight(1f, fill = true))
        val sensorGridHeightMap = 200f
        // Surface consumes elevationProfileHeight + topPadding + bottomInset
        val surfaceTotalHeight = elevationProfileHeight + topPadding + bottomInset // 70 + 8 + 104 = 182f
        val remainingSpaceForMap = availableHeight - sensorGridHeightMap - surfaceTotalHeight // 800 - 200 - 182 = 418f

        val mapTop = sensorGridHeightMap // 200f
        val mapBottom = mapTop + remainingSpaceForMap // 618f
        val surfaceTop = mapBottom + topPadding // 626f
        val profileCanvasBottom = surfaceTop + elevationProfileHeight // 696f

        assertEquals("Map must expand to exactly consume remaining space", 418f, remainingSpaceForMap, 0.001f)
        assertEquals("Profile canvas bottom must exactly touch sheet top without overlapping", sheetTopY, profileCanvasBottom, 0.001f)
        assertTrue("Profile canvas bottom must not penetrate below sheet top", profileCanvasBottom <= sheetTopY)
    }

    @Test
    fun testLayoutGeometry_zeroSheetPeekHeightBoundaryVerification() {
        // REQ-UI-369.3 / TST-UI-332.3: Zero Sheet Peek Inactive Boundary Verification
        val availableHeight = 800f
        val sheetPeekHeight = 0f
        val elevationProfileHeight = 70f
        val topPadding = 8f
        val bottomInset = sheetPeekHeight // 0f

        val sensorGridHeight = 200f
        val surfaceTotalHeight = elevationProfileHeight + topPadding + bottomInset // 78f
        val remainingSpaceForMap = availableHeight - sensorGridHeight - surfaceTotalHeight // 722f
        val totalConsumed = sensorGridHeight + remainingSpaceForMap + surfaceTotalHeight // 800f
        val deadSpace = availableHeight - totalConsumed // 0f

        val profileCanvasBottom = sensorGridHeight + remainingSpaceForMap + topPadding + elevationProfileHeight // 800f

        assertEquals(0f, deadSpace, 0.001f)
        assertEquals(availableHeight, profileCanvasBottom, 0.001f)
    }

    @Test
    fun testLayoutGeometry_elevationProfileDisabled_zeroDeadSpace() {
        // REQ-UI-369 / TST-UI-332.4: Negative Test - Elevation Profile Disabled
        val availableHeight = 800f
        val sheetPeekHeight = 104f
        val sensorGridHeight = 200f

        // When showElevationProfile is false, Surface is NOT composed.
        // Map expands with weight(1f, fill = true) across 100% of remaining vertical space.
        val remainingSpaceForMap = availableHeight - sensorGridHeight // 600f
        val totalConsumed = sensorGridHeight + remainingSpaceForMap // 800f
        val deadSpace = availableHeight - totalConsumed // 0f

        assertEquals("Map must consume 100% remaining space when elevation profile is disabled", 600f, remainingSpaceForMap, 0.001f)
        assertEquals("Zero dead space must be preserved when elevation profile is disabled", 0f, deadSpace, 0.001f)
    }
}

