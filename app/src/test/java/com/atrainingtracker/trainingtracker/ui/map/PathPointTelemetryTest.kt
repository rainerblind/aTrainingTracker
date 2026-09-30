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
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit test for PathPoint telemetry extension and backward compatibility (REQ-UI-201, TST-UI-155.1).
 */
class PathPointTelemetryTest {

    @Test
    fun testPathPointDefaultArgumentsBackwardCompatibility() {
        val point = PathPoint(
            distance = 1200.0,
            altitude = 450.0,
            latLng = LatLng(48.137, 11.575)
        )

        assertEquals(1200.0, point.distance, 0.001)
        assertEquals(450.0, point.altitude, 0.001)
        assertEquals(48.137, point.latLng.latitude, 0.0001)
        assertEquals(11.575, point.latLng.longitude, 0.0001)

        // Telemetry defaults
        assertEquals(0L, point.timeSec)
        assertNull(point.hr)
        assertNull(point.power)
        assertNull(point.speedMps)
        assertNull(point.slope)
    }

    @Test
    fun testPathPointFullTelemetryInitialization() {
        val point = PathPoint(
            distance = 5000.0,
            altitude = 620.5,
            latLng = LatLng(47.5, 10.2),
            timeSec = 750L,
            hr = 152,
            power = 245,
            speedMps = 7.8,
            slope = 3.5
        )

        assertEquals(5000.0, point.distance, 0.001)
        assertEquals(620.5, point.altitude, 0.001)
        assertEquals(47.5, point.latLng.latitude, 0.0001)
        assertEquals(10.2, point.latLng.longitude, 0.0001)
        assertEquals(750L, point.timeSec)
        assertEquals(152, point.hr)
        assertEquals(245, point.power)
        assertEquals(7.8, point.speedMps!!, 0.001)
        assertEquals(3.5, point.slope!!, 0.001)
    }
}
