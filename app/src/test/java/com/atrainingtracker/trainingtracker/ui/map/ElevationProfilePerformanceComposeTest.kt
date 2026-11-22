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

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Paint
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import java.io.File
import java.io.FileOutputStream
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.MyUnits
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.climbs.Climb
import com.atrainingtracker.trainingtracker.climbs.ClimbCategory
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.atrainingtracker.trainingtracker.ui.theme.TTColor
import com.google.android.gms.maps.model.LatLng
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Unit & behavioral Robolectric Compose UI verification suite for `ATT-6037` (`REQ-UI-2793`, `TST-UI-2793`):
 * - Precomputed climb summit & span cache (`precomputeClimbDrawData`).
 * - Hoisted `ClimbCategory` Paint cache (`buildClimbBadgePaintCache`).
 * - Contiguous color-run fill coalescing reusing a single hoisted [Path] (`drawElevationSegmentsCoalesced`).
 * - Live `ElevationProfile` rendering with climb badges, zoom controls, scrubbing badge, and legend toggle.
 * - Direct `viewModel.currentLocation` `StateFlow` forwarding in `MapScreenWithTrack` without top-level recomposition.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w412dp-h915dp-xxhdpi")
class ElevationProfilePerformanceComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testPrecomputeClimbDrawData_extractsExactSummitAndSpanBounds_tstUi2793_1() {
        val climbWithPeak = Climb(
            id = 1L,
            name = "Alpine Pass",
            startLat = 47.0,
            startLng = 11.0,
            endLat = 47.1,
            endLng = 11.1,
            distanceMeters = 4000.0,
            elevationGainMeters = 420.0,
            avgGradePercent = 10.5,
            maxGradePercent = 14.0,
            category = ClimbCategory.HC,
            pathPoints = listOf(
                PathPoint(distance = 1000.0, latLng = LatLng(47.0, 11.0), altitude = 600.0),
                PathPoint(distance = 3200.0, latLng = LatLng(47.05, 11.05), altitude = 1020.0),
                PathPoint(distance = 5000.0, latLng = LatLng(47.1, 11.1), altitude = 980.0)
            )
        )
        val climbNullAlts = Climb(
            id = 2L,
            name = "Flat Ridge",
            startLat = 47.2,
            startLng = 11.2,
            endLat = 47.25,
            endLng = 11.25,
            distanceMeters = 500.0,
            elevationGainMeters = 20.0,
            avgGradePercent = 4.0,
            maxGradePercent = 5.0,
            category = ClimbCategory.CAT_4,
            pathPoints = listOf(
                PathPoint(distance = 6000.0, latLng = LatLng(47.2, 11.2), altitude = null),
                PathPoint(distance = 6500.0, latLng = LatLng(47.25, 11.25), altitude = null)
            )
        )

        val cached = precomputeClimbDrawData(listOf(climbWithPeak, climbNullAlts))
        assertEquals(2, cached.size)

        // Peak at 3200m / 1020m, span 1000m..5000m
        assertEquals(ClimbCategory.HC, cached[0].category)
        assertEquals(3200.0, cached[0].summitDistance!!, 0.001)
        assertEquals(1020.0, cached[0].summitAltitude!!, 0.001)
        assertEquals(1000.0, cached[0].startDistance!!, 0.001)
        assertEquals(5000.0, cached[0].endDistance!!, 0.001)

        // All-null altitudes fall back cleanly to first/last distance without crashing
        assertEquals(ClimbCategory.CAT_4, cached[1].category)
        assertEquals(6000.0, cached[1].startDistance!!, 0.001)
        assertEquals(6500.0, cached[1].endDistance!!, 0.001)
        assertNull(cached[1].summitAltitude)
    }

    @Test
    fun testBuildClimbBadgePaintCache_populatesAllCategoriesWithScaledDensity_tstUi2793_1() {
        val density = Density(density = 3.0f, fontScale = 1.0f)
        val cache = buildClimbBadgePaintCache(density)

        assertEquals(ClimbCategory.entries.size, cache.size)
        ClimbCategory.entries.forEach { category ->
            val paints = cache[category]
            assertTrue("Missing cached paints for $category", paints != null)
            assertEquals(Paint.Align.CENTER, paints!!.badgePaint.textAlign)
            assertTrue(paints.badgePaint.isAntiAlias)
            assertTrue(paints.badgePaint.isFakeBoldText)
            assertEquals(30f, paints.badgePaint.textSize, 0.01f)
            assertEquals(Paint.Style.FILL, paints.bgPaint.style)
            assertEquals(4.5f, paints.stemPaint.strokeWidth, 0.01f)
        }
    }

    @Test
    fun testDrawElevationSegmentsCoalesced_mergesContiguousColorRunsReusingSinglePath_tstUi2793_1() {
        // 600 contiguous segments forming exactly 3 color runs: 200 Zone1, 200 Zone3, 200 Zone4
        val segments = (0 until 600).map { i ->
            val color = when {
                i < 200 -> TTColor.Zone1
                i < 400 -> TTColor.Zone3
                else -> TTColor.Zone4
            }
            ElevationSegment(
                dist1 = i * 10.0,
                timeSec1 = i.toLong(),
                altNorm1 = (i / 600f),
                dist2 = (i + 1) * 10.0,
                timeSec2 = (i + 1).toLong(),
                altNorm2 = ((i + 1) / 600f),
                color = color
            )
        }

        val bitmap = ImageBitmap(width = 400, height = 200)
        val drawScope = CanvasDrawScope()
        val singlePath = Path()
        var fullRunCount = -1
        var zoomedRunCount = -1
        var timeDomainRunCount = -1

        drawScope.draw(
            density = Density(2f),
            layoutDirection = LayoutDirection.Ltr,
            canvas = Canvas(bitmap),
            size = Size(400f, 200f)
        ) {
            fullRunCount = drawElevationSegmentsCoalesced(
                segments = segments,
                isTimeDomain = false,
                currentStartDist = 0.0,
                visibleSpan = 6000.0,
                width = 400f,
                height = 200f,
                strokeWidthPx = 4f,
                reusableFillPath = singlePath
            )
            // Viewport strictly within Run 2 (2100m..3900m) -> only 1 coalesced fill run
            zoomedRunCount = drawElevationSegmentsCoalesced(
                segments = segments,
                isTimeDomain = false,
                currentStartDist = 2100.0,
                visibleSpan = 1800.0,
                width = 400f,
                height = 200f,
                strokeWidthPx = 4f,
                reusableFillPath = singlePath
            )
            // Time domain across all 600s -> 3 coalesced fill runs
            timeDomainRunCount = drawElevationSegmentsCoalesced(
                segments = segments,
                isTimeDomain = true,
                currentStartDist = 0.0,
                visibleSpan = 600.0,
                width = 400f,
                height = 200f,
                strokeWidthPx = 4f,
                reusableFillPath = singlePath
            )
        }

        assertEquals("600 contiguous segments with 3 colors must coalesce into 3 fill runs", 3, fullRunCount)
        assertEquals("Viewport inside a single color band must emit 1 fill run", 1, zoomedRunCount)
        assertEquals("Time-domain segments must coalesce identically into 3 fill runs", 3, timeDomainRunCount)
    }

    @Test
    fun testElevationProfile_rendersWithClimbsZoomControlsAndLiveScrubbingRedraws_tstUi2793_1() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val legendDesc = context.getString(R.string.legend)
        val pathPoints = (0..120).map { i ->
            val dist = i * 50.0
            val alt = 400.0 + if (i <= 60) i * 8.0 else (120 - i) * 6.0
            PathPoint(
                distance = dist,
                latLng = LatLng(47.5 + i * 0.0005, 11.2 + i * 0.0005),
                altitude = alt,
                timeSec = i * 10L
            )
        }
        val hcClimb = Climb(
            id = 10L,
            name = "Summit Pass",
            startLat = 47.505,
            startLng = 11.205,
            endLat = 47.530,
            endLng = 11.230,
            distanceMeters = 2500.0,
            elevationGainMeters = 400.0,
            avgGradePercent = 16.0,
            maxGradePercent = 21.0,
            category = ClimbCategory.HC,
            pathPoints = pathPoints.subList(10, 61)
        )

        var selectedDist by mutableStateOf<Double?>(1500.0)
        var zoomScale by mutableStateOf(1.0f)

        composeRule.setContent {
            ATrainingTrackerTheme {
                Surface {
                    ElevationProfile(
                        pathPoints = pathPoints,
                        currentDistance = selectedDist,
                        showZoomControls = true,
                        zoomScale = zoomScale,
                        startDist = 0.0,
                        onZoomChanged = { z, _ -> zoomScale = z },
                        climbs = listOf(hcClimb),
                        smoothingSigma = 0.0
                    )
                }
            }
        }
        composeRule.waitForIdle()

        // Scrubbing badge and legend icon render cleanly
        composeRule.onNodeWithTag("scrubbing_telemetry_badge").assertIsDisplayed()
        composeRule.onNodeWithContentDescription(legendDesc).assertIsDisplayed()

        // Trigger multiple live redraws (scrubbing + zoom changes) exercising hoisted Paint/Path reuse
        composeRule.runOnIdle {
            selectedDist = 2750.0
            zoomScale = 2.0f
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("scrubbing_telemetry_badge").assertIsDisplayed()

        // Toggle legend popover
        composeRule.onNodeWithContentDescription(legendDesc).performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun testCaptureElevationProfileLightAndDarkComparisonPng_tstUi2793_1() {
        val pathPoints = (0..160).map { i ->
            val dist = i * 50.0
            val alt = when {
                i < 25 -> 420.0 + i * 0.4 // flat (<2%)
                i < 55 -> 430.0 + (i - 25) * 2.0 // gentle 4%
                i < 85 -> 490.0 + (i - 55) * 4.2 // moderate 8.4%
                i < 110 -> 616.0 + (i - 85) * 6.8 // steep 13.6%
                else -> (786.0 - (i - 110) * 5.2).coerceAtLeast(450.0) // descent
            }
            PathPoint(
                distance = dist,
                latLng = LatLng(47.5 + i * 0.0004, 11.2 + i * 0.0004),
                altitude = alt,
                timeSec = i * 12L
            )
        }
        val hcClimb = Climb(
            id = 21L,
            name = "Alpine Summit",
            startLat = 47.51,
            startLng = 11.21,
            endLat = 47.544,
            endLng = 11.244,
            distanceMeters = 4250.0,
            elevationGainMeters = 356.0,
            avgGradePercent = 8.4,
            maxGradePercent = 14.0,
            category = ClimbCategory.CAT_1,
            pathPoints = pathPoints.subList(25, 111)
        )

        composeRule.setContent {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("elevation_light_dark_root"),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ATrainingTrackerTheme(darkTheme = false) {
                    Surface(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                        ElevationProfile(
                            pathPoints = pathPoints,
                            currentDistance = 4500.0,
                            showZoomControls = true,
                            climbs = listOf(hcClimb),
                            smoothingSigma = 0.0
                        )
                    }
                }
                ATrainingTrackerTheme(darkTheme = true) {
                    Surface(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                        ElevationProfile(
                            pathPoints = pathPoints,
                            currentDistance = 4500.0,
                            showZoomControls = true,
                            climbs = listOf(hcClimb),
                            smoothingSigma = 0.0
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()

        val outFile = File(System.getProperty("att6037.elevationPngOut") ?: "/tmp/att6037_elevation_light_dark.png")
        outFile.parentFile?.mkdirs()
        val bitmap = composeRule.onNodeWithTag("elevation_light_dark_root").captureToImage().asAndroidBitmap()
        FileOutputStream(outFile).use { fos ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
        }
        assertTrue("Rendered ElevationProfile Light/Dark PNG must be non-empty", outFile.length() > 1_000L)
    }

    @Test
    fun testMapScreenWithTrack_forwardsCurrentLocationFlowWithoutParentRecomposition_tstUi2793_1() {
        mockkStatic(TrainingApplication::class)
        every { TrainingApplication.getUnit() } returns MyUnits.METRIC

        val uiStateFlow = MutableStateFlow(MapFragmentUIState(bSportType = BSportType.BIKE))
        val locationFlow = MutableStateFlow<LatLng?>(LatLng(48.137, 11.575))
        val vm = mockk<MapFragmentWithTrackViewModel>(relaxed = true)
        every { vm.uiState } returns uiStateFlow
        every { vm.currentLocation } returns locationFlow
        every { vm.liveSegments } returns MutableStateFlow(emptyList())
        every { vm.allRoutes } returns MutableStateFlow(emptyList())
        every { vm.allClusters } returns MutableStateFlow(emptyList())
        every { vm.enabledLayers } returns MutableStateFlow(setOf(GeneralMapLayer.TRACK))

        var hostRecompositions = 0
        composeRule.setContent {
            ATrainingTrackerTheme {
                CompositionLocalProvider(LocalInspectionMode provides true) {
                    SideEffect { hostRecompositions++ }
                    MapScreenWithTrack(
                        viewModel = vm,
                        onMenuClick = {}
                    )
                }
            }
        }
        composeRule.waitForIdle()
        val baselineCount = hostRecompositions

        // Emit 10 GPS location updates; MapScreenWithTrack forwards viewModel.currentLocation directly
        repeat(10) { idx ->
            composeRule.runOnIdle {
                locationFlow.value = LatLng(48.137 + idx * 0.001, 11.575 + idx * 0.001)
            }
            composeRule.waitForIdle()
        }

        assertEquals(
            "GPS currentLocation emissions must not force MapScreenWithTrack host recompositions",
            baselineCount,
            hostRecompositions
        )
        composeRule.onNodeWithTag("map_navigation_button").assertIsDisplayed()
    }

    @Test
    fun testMapContentScopeImpl_renderUpdatesOnDynamicLayerTogglesAndTrackGrowthAtConstantZoom_tstUi2793_1() {
        // Non-LocalInspectionMode Compose Strong Skipping regression test (Stage 6 Bounce 1):
        // Verifies that keeping a remembered MapContentScopeImpl instance at constant currentZoom
        // still recomposes Render(...) immediately whenever collect() increments contentRevision,
        // while skipping when contentRevision is unchanged and avoiding infinite recompositions.
        val context = ApplicationProvider.getApplicationContext<Context>()
        val scope = MapContentScopeImpl(
            zoomFocus = MapZoomFocus.LOCAL_SEGMENTS,
            primaryColor = TTColor.RouteSelected,
            context = context,
            directionIcons = Triple(null, null, null),
            bSportType = BSportType.BIKE
        )
        val observedRevisions = mutableListOf<Long>()
        scope.onRenderPass = { rev -> observedRevisions.add(rev) }

        var layerToggleTick by mutableIntStateOf(0)
        var unrelatedHostTick by mutableIntStateOf(0)
        var currentRev by mutableStateOf(0L)

        composeRule.setContent {
            ATrainingTrackerTheme {
                // Read unrelatedHostTick so we can force a host recomposition without changing layerToggleTick
                val hostTick = unrelatedHostTick
                val activeRev = remember(layerToggleTick) {
                    scope.collect {
                        // Layer DSL updates collected synchronously; lists remain empty during UiApplier
                        // pass while exercising the exact Strong Skipping contract on Render(zoom, rev)
                    }.also { currentRev = it }
                }
                // Constant scope identity (===) and constant zoom (14f), varying only contentRevision
                scope.Render(currentZoom = 14f, contentRevision = activeRev)
                if (hostTick < 0) Unit
            }
        }
        composeRule.waitForIdle()
        assertEquals("Initial collect must produce revision 1", 1L, currentRev)
        assertEquals(listOf(1L), observedRevisions)

        // 1. Dynamic layer toggle / live track update at constant zoom & constant scope identity
        composeRule.runOnIdle { layerToggleTick = 1 }
        composeRule.waitForIdle()
        assertEquals(2L, currentRev)
        assertEquals(
            "Strong Skipping MUST NOT skip Render() when contentRevision advances at constant zoom",
            listOf(1L, 2L),
            observedRevisions
        )

        // 2. Second dynamic update at constant zoom & constant scope identity
        composeRule.runOnIdle { layerToggleTick = 2 }
        composeRule.waitForIdle()
        assertEquals(3L, currentRev)
        assertEquals(listOf(1L, 2L, 3L), observedRevisions)

        // 3. Unrelated parent recomposition with identical contentRevision skips Render() cleanly (0 churn)
        composeRule.runOnIdle { unrelatedHostTick = 1 }
        composeRule.waitForIdle()
        assertEquals(
            "Strong Skipping should skip Render() when contentRevision and zoom are both unchanged",
            listOf(1L, 2L, 3L),
            observedRevisions
        )

        // 4. Verify collect() clears & populates markers/currentTracks cleanly across dynamic updates
        val revOn = scope.collect {
            liveTrack(listOf(LatLng(48.137, 11.575), LatLng(48.138, 11.576)))
            markers(listOf(LocationMarker(LatLng(48.137, 11.575), R.drawable.start_logo_map, "Start")))
        }
        assertEquals(4L, revOn)
        assertEquals(1, scope.currentTracks.size)
        assertEquals(2, scope.currentTracks.first().size)
        assertEquals(1, scope.markers.size)

        val revOff = scope.collect {
            // Toggling layers OFF clears previous collections and advances revision monotonically
        }
        assertEquals(5L, revOff)
        assertTrue(scope.currentTracks.isEmpty())
        assertTrue(scope.markers.isEmpty())
    }
}
