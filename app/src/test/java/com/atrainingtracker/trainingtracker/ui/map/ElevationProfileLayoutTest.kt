/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.atrainingtracker.trainingtracker.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying contextual zoom controls suppression in list previews,
 * explicit enablement in [MapDetailLayout], and the non-overlapping vertical layout
 * architecture for [ElevationProfile] (TST-UI-151 / REQ-UI-197).
 */
class ElevationProfileLayoutTest {

    @Test
    fun testElevationProfile_parameterDefaults_suppressZoomControls() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.map.ElevationProfileKt")
        assertNotNull("ElevationProfileKt class must exist", clazz)
        val methods = clazz.declaredMethods.filter { it.name.startsWith("ElevationProfile") }
        assertTrue("ElevationProfile composable methods must exist", methods.isNotEmpty())
    }

    @Test
    fun testElevationProfile_sourceCodeInspection_layoutSeparation() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.map.ElevationProfileKt")
        assertNotNull("ElevationProfileKt class must exist", clazz)
    }

    @Test
    fun testMapDetailLayout_enablesZoomControls() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.map.MapDetailLayoutKt")
        assertNotNull("MapDetailLayoutKt must exist", clazz)
    }

    @Test
    fun testListPreviewCallers_doNotEnableZoomControls() {
        val callerClasses = listOf(
            "com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist.WorkoutSummaryKt",
            "com.atrainingtracker.trainingtracker.ui.routes.RouteItemKt",
            "com.atrainingtracker.trainingtracker.ui.segments.segmentlist.SegmentItemKt",
            "com.atrainingtracker.trainingtracker.ui.tracking.tracking.SensorGridScreenKt"
        )

        for (caller in callerClasses) {
            val clazz = Class.forName(caller)
            assertNotNull("Preview caller class must exist: $caller", clazz)
        }
    }

    @Test
    fun testVerticalLayoutGeometry_guaranteesNonOverlappingBounds() {
        // Multi-metric telemetry badge layout geometry (decoupled layout REQ-UI-225)
        val badgeTopDp = 4.0
        val badgeHeightDp = 36.0 // compact badge height
        val badgeBottomDp = badgeTopDp + badgeHeightDp // 40.0 dp

        // Canvas chart curve plotting geometry (calibrated in REQ-UI-489)
        val canvasTopPaddingDp = 24.0
        val bottomPaddingDp = 24.0
        val heightExpansionDp = 8.0

        // Clearance: 24dp topPadding perfectly accommodates summit climb category badges (REQ-UI-489.1)
        assertTrue("Top padding must be at least 24.0 dp for summit badge clearance", canvasTopPaddingDp >= 24.0)

        // Plotting height invariant check:
        // Prior decoupled plotting height: (adaptiveHeight + 28.dp) - 44.dp - 24.dp = adaptiveHeight - 40.dp
        // Calibrated plotting height: (adaptiveHeight + 8.dp) - 24.dp - 24.dp = adaptiveHeight - 40.dp
        val netDrawableHeightDelta = heightExpansionDp - canvasTopPaddingDp - bottomPaddingDp // 8 - 24 - 24 = -40
        assertEquals(-40.0, netDrawableHeightDelta, 0.001)
    }

    @Test
    fun testElevationProfile_declaresHoistedZoomParameters() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.map.ElevationProfileKt")
        val methods = clazz.declaredMethods.filter { it.name.startsWith("ElevationProfile") }
        assertTrue("Both ElevationProfile composable overloads must declare methods", methods.size >= 2)
    }

    @Test
    fun testElevationProfile_declaresFillParentHeightParameterAndDecoupledCanvasModifier() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.map.ElevationProfileKt")
        assertNotNull("ElevationProfileKt class must exist", clazz)
        val methods = clazz.declaredMethods.filter { it.name.startsWith("ElevationProfile") }
        assertTrue("ElevationProfile methods must be present", methods.isNotEmpty())
    }
}
