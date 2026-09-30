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

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for ElevationProfileZoomMath time domain calculations and tick formatting (REQ-UI-201, TST-UI-155.3).
 */
class ElevationProfileZoomMathTimeTest {

    @Test
    fun testCalculateAdaptiveTimeStepThresholds() {
        // > 4h (14400s) -> 1h ticks (3600s)
        assertEquals(3600L, ElevationProfileZoomMath.calculateAdaptiveTimeStep(18000.0))

        // > 2h (7200s) -> 30m ticks (1800s)
        assertEquals(1800L, ElevationProfileZoomMath.calculateAdaptiveTimeStep(10000.0))

        // > 1h (3600s) -> 15m ticks (900s)
        assertEquals(900L, ElevationProfileZoomMath.calculateAdaptiveTimeStep(5400.0))

        // > 30m (1800s) -> 5m ticks (300s)
        assertEquals(300L, ElevationProfileZoomMath.calculateAdaptiveTimeStep(2400.0))

        // > 10m (600s) -> 2m ticks (120s)
        assertEquals(120L, ElevationProfileZoomMath.calculateAdaptiveTimeStep(900.0))

        // > 4m (240s) -> 1m ticks (60s)
        assertEquals(60L, ElevationProfileZoomMath.calculateAdaptiveTimeStep(300.0))

        // <= 4m -> 30s ticks
        assertEquals(30L, ElevationProfileZoomMath.calculateAdaptiveTimeStep(180.0))
        assertEquals(30L, ElevationProfileZoomMath.calculateAdaptiveTimeStep(30.0))
    }

    @Test
    fun testFormatTimeTick() {
        assertEquals("0:00", ElevationProfileZoomMath.formatTimeTick(0L))
        assertEquals("0:45", ElevationProfileZoomMath.formatTimeTick(45L))
        assertEquals("1:05", ElevationProfileZoomMath.formatTimeTick(65L))
        assertEquals("59:59", ElevationProfileZoomMath.formatTimeTick(3599L))
        assertEquals("1:00:00", ElevationProfileZoomMath.formatTimeTick(3600L))
        assertEquals("1:01:05", ElevationProfileZoomMath.formatTimeTick(3665L))
        assertEquals("2:02:05", ElevationProfileZoomMath.formatTimeTick(7325L))
    }
}
