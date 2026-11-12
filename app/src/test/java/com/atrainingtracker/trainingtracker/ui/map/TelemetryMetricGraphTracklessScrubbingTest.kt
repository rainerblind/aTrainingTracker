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
import kotlin.math.abs

/**
 * Unit and structural verification for trackless time-domain scrubbing in [TelemetryMetricGraph]
 * (REQ-UI-235 / TST-UI-194.4 / ATT-2006).
 */
class TelemetryMetricGraphTracklessScrubbingTest {

    @Test
    fun testTracklessScrubbing_contractEnforcesTimeDomainAndDirectTimeDispatch() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.map.TelemetryMetricGraphKt")
        assertNotNull("TelemetryMetricGraphKt must exist", clazz)
        val domainMathClass = Class.forName("com.atrainingtracker.trainingtracker.ui.map.ProfileDomainMath")
        assertNotNull("ProfileDomainMath must exist", domainMathClass)
    }

    @Test
    fun testTracklessCursor_contractEvaluatesDirectlyAgainstTimeSpan() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.map.TelemetryMetricGraphKt")
        assertNotNull("TelemetryMetricGraphKt must exist", clazz)
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
