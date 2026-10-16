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

package com.atrainingtracker.trainingtracker.ui.aftermath.zones

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests verifying Tier 3 Domain Fallback Guard for ZoneCardDisplayMode (REQ-UI-372.8, TST-UI-335.10).
 * Enforces that when HISTOGRAM is requested on a workout lacking fine-grained telemetry data,
 * the mode gracefully resolves to COLUMNS.
 */
class ZoneCardDisplayModeFallbackTest {

    @Test
    fun histogramWithoutTelemetryData_fallsBackToColumns() {
        val resolved = ZoneCardDisplayMode.HISTOGRAM.resolveWithHistogramFallback(hasHistogram = false)
        assertEquals(ZoneCardDisplayMode.COLUMNS, resolved)
    }

    @Test
    fun histogramWithTelemetryData_preservesHistogramMode() {
        val resolved = ZoneCardDisplayMode.HISTOGRAM.resolveWithHistogramFallback(hasHistogram = true)
        assertEquals(ZoneCardDisplayMode.HISTOGRAM, resolved)
    }

    @Test
    fun columnsMode_unaffectedByHistogramPresenceOrAbsence() {
        assertEquals(ZoneCardDisplayMode.COLUMNS, ZoneCardDisplayMode.COLUMNS.resolveWithHistogramFallback(hasHistogram = false))
        assertEquals(ZoneCardDisplayMode.COLUMNS, ZoneCardDisplayMode.COLUMNS.resolveWithHistogramFallback(hasHistogram = true))
    }

    @Test
    fun stackedBarMode_unaffectedByHistogramPresenceOrAbsence() {
        assertEquals(ZoneCardDisplayMode.STACKED_BAR, ZoneCardDisplayMode.STACKED_BAR.resolveWithHistogramFallback(hasHistogram = false))
        assertEquals(ZoneCardDisplayMode.STACKED_BAR, ZoneCardDisplayMode.STACKED_BAR.resolveWithHistogramFallback(hasHistogram = true))
    }

    @Test
    fun fiveZonesLegacyAlias_unaffectedByHistogramPresenceOrAbsence() {
        @Suppress("DEPRECATION")
        val legacy = ZoneCardDisplayMode.FIVE_ZONES
        assertEquals(legacy, legacy.resolveWithHistogramFallback(hasHistogram = false))
        assertEquals(legacy, legacy.resolveWithHistogramFallback(hasHistogram = true))
    }
}
