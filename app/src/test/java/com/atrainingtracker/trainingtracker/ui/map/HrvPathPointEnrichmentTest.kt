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
 * Unit tests verifying PathPoint HRV field enrichment and @JvmOverloads constructor backward compatibility
 * conforming to REQ-PER-035.1 and TST-PER-043.1.
 */
class HrvPathPointEnrichmentTest {

    @Test
    fun testPathPoint_hrvEnrichment_andJvmOverloadsParity() {
        // 1. Verify default null parameters for backwards compatibility
        val defaultPoint = PathPoint(
            distance = 1200.0,
            latLng = LatLng(48.137, 11.575)
        )
        assertEquals(1200.0, defaultPoint.distance, 1e-4)
        assertEquals(48.137, defaultPoint.latLng.latitude, 1e-4)
        assertEquals(11.575, defaultPoint.latLng.longitude, 1e-4)
        assertNull(defaultPoint.altitude)
        assertEquals(0L, defaultPoint.timeSec)
        assertNull(defaultPoint.hr)
        assertNull(defaultPoint.power)
        assertNull(defaultPoint.speedMps)
        assertNull(defaultPoint.slope)
        assertNull(defaultPoint.wPrimeBalKj)
        assertNull(defaultPoint.hrvDfaA1)
        assertNull(defaultPoint.hrvRmssd)
        assertNull(defaultPoint.hrvArtifactRate)

        // 2. Verify full constructor with HRV telemetry enrichment
        val enrichedPoint = PathPoint(
            distance = 2500.0,
            latLng = LatLng(48.140, 11.580),
            altitude = 520.0,
            timeSec = 600L,
            hr = 155,
            power = 240,
            speedMps = 7.5,
            slope = 2.1,
            wPrimeBalKj = 18.5,
            hrvDfaA1 = 0.78,
            hrvRmssd = 38.4,
            hrvArtifactRate = 1.2
        )
        assertEquals(2500.0, enrichedPoint.distance, 1e-4)
        assertEquals(520.0, enrichedPoint.altitude!!, 1e-4)
        assertEquals(600L, enrichedPoint.timeSec)
        assertEquals(155, enrichedPoint.hr)
        assertEquals(240, enrichedPoint.power)
        assertEquals(0.78, enrichedPoint.hrvDfaA1!!, 1e-4)
        assertEquals(38.4, enrichedPoint.hrvRmssd!!, 1e-4)
        assertEquals(1.2, enrichedPoint.hrvArtifactRate!!, 1e-4)

        // 3. Verify copy operation preserves HRV fields
        val copiedPoint = enrichedPoint.copy(hrvDfaA1 = 0.72)
        assertEquals(0.72, copiedPoint.hrvDfaA1!!, 1e-4)
        assertEquals(38.4, copiedPoint.hrvRmssd!!, 1e-4)
        assertEquals(1.2, copiedPoint.hrvArtifactRate!!, 1e-4)
    }
}
