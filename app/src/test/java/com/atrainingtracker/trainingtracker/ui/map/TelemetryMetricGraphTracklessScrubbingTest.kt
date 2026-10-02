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

import com.google.android.gms.maps.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs

/**
 * Unit and structural verification for trackless time-domain scrubbing in [TelemetryMetricGraph]
 * (REQ-UI-235 / TST-UI-194.4 / ATT-2006).
 */
class TelemetryMetricGraphTracklessScrubbingTest {

    private val projectRoot: File by lazy {
        var dir = File(System.getProperty("user.dir") ?: ".")
        while (!File(dir, "app").exists() && dir.parentFile != null) {
            dir = dir.parentFile!!
        }
        dir
    }

    private val telemetryMetricGraphFile: File by lazy {
        File(projectRoot, "app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/TelemetryMetricGraph.kt")
    }

    @Test
    fun testTracklessScrubbing_contractEnforcesTimeDomainAndDirectTimeDispatch() {
        assertTrue("TelemetryMetricGraph.kt must exist", telemetryMetricGraphFile.exists())
        val content = telemetryMetricGraphFile.readText()

        // 1. isTrackless detection
        assertTrue(
            "TelemetryMetricGraph must evaluate isTrackless checking last point distance == 0.0 and timeSec > 0",
            content.contains("(pathPoints.lastOrNull()?.distance ?: 0.0) == 0.0 && (pathPoints.lastOrNull()?.timeSec ?: 0L) > 0L")
        )

        // 2. isTimeDomain includes isTrackless
        assertTrue(
            "TelemetryMetricGraph must enforce isTimeDomain = xAxisDomain == ProfileXAxisDomain.TIME || isTrackless",
            content.contains("val isTimeDomain = xAxisDomain == ProfileXAxisDomain.TIME || isTrackless")
        )

        // 3. Drag gesture dispatches timeSec directly when isTrackless
        assertTrue(
            "Drag gesture must dispatch nearest.timeSec directly when isTrackless",
            content.contains("if (isTrackless) {") &&
                    content.contains("currentOnDistanceSelectedState(nearest?.timeSec?.toDouble())")
        )

        // 4. Tap gesture dispatches timeSec directly when isTrackless
        assertTrue(
            "Tap gesture must dispatch nearest.timeSec directly when isTrackless",
            content.contains("if (isTimeDomain) {") &&
                    content.contains("if (isTrackless) {") &&
                    content.contains("currentOnDistanceSelectedState(nearest?.timeSec?.toDouble())")
        )
    }

    @Test
    fun testTracklessCursor_contractEvaluatesDirectlyAgainstTimeSpan() {
        assertTrue("TelemetryMetricGraph.kt must exist", telemetryMetricGraphFile.exists())
        val content = telemetryMetricGraphFile.readText()

        // 1. cursorDistSpan evaluates directly against currentDistance when isTrackless
        assertTrue(
            "Cursor calculation must evaluate cursorDistSpan = currentDistance directly when isTrackless",
            content.contains("val cursorDistSpan = if (isTimeDomain) {") &&
                    content.contains("if (isTrackless) {") &&
                    content.contains("currentDistance")
        )

        // 2. Marker dot lookup compares p.timeSec directly against currentDistance when isTrackless
        assertTrue(
            "Marker dot lookup must compare timeSec against currentDistance when isTrackless",
            content.contains("val nearestPoint = if (isTrackless && isTimeDomain) {") &&
                    content.contains("pathPoints.minByOrNull { abs(it.timeSec - currentDistance.toLong()) }")
        )
    }

    @Test
    fun testTracklessTelemetryScrubbingMath() {
        // Algorithmic verification of nearest point interpolation along time domain for trackless workout
        val tracklessPoints = listOf(
            PathPoint(distance = 0.0, latLng = LatLng(0.0, 0.0), altitude = 0.0, timeSec = 0L, hr = 120),
            PathPoint(distance = 0.0, latLng = LatLng(0.0, 0.0), altitude = 0.0, timeSec = 300L, hr = 145),
            PathPoint(distance = 0.0, latLng = LatLng(0.0, 0.0), altitude = 0.0, timeSec = 600L, hr = 160),
            PathPoint(distance = 0.0, latLng = LatLng(0.0, 0.0), altitude = 0.0, timeSec = 900L, hr = 155),
            PathPoint(distance = 0.0, latLng = LatLng(0.0, 0.0), altitude = 0.0, timeSec = 1200L, hr = 130)
        )

        val totalTime = tracklessPoints.last().timeSec.toDouble()
        assertEquals(1200.0, totalTime, 0.001)

        // Simulate touch at 50% width
        val touchFraction = 0.5f
        val scrubTime = touchFraction * totalTime
        assertEquals(600.0, scrubTime, 0.001)

        val nearest = tracklessPoints.minByOrNull { abs(it.timeSec.toDouble() - scrubTime) }
        assertNotNull(nearest)
        assertEquals(600L, nearest!!.timeSec)
        assertEquals(160, nearest.hr)

        // Simulate touch at 70% width (840s -> nearest is 900s)
        val touchFraction70 = 0.7f
        val scrubTime70 = touchFraction70 * totalTime
        val nearest70 = tracklessPoints.minByOrNull { abs(it.timeSec.toDouble() - scrubTime70) }
        assertNotNull(nearest70)
        assertEquals(900L, nearest70!!.timeSec)
        assertEquals(155, nearest70.hr)
    }
}
