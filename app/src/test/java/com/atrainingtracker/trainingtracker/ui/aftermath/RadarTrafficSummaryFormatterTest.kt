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

package com.atrainingtracker.trainingtracker.ui.aftermath

import android.content.Context
import com.atrainingtracker.R
import io.mockk.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Locale

/**
 * Unit verification tests for [RadarTrafficSummaryFormatter] (REQ-RAD-007 / TST-RAD-007.1).
 */
class RadarTrafficSummaryFormatterTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        every { context.getString(R.string.radar_summary_header, *anyVararg()) } answers {
            val formatArgs = args[1] as Array<*>
            val count = formatArgs[0]
            val density = formatArgs[1]
            "📡 Radar: $count vehicles passed ($density/km)"
        }
        every { context.getString(R.string.radar_summary_close_passes_singular, *anyVararg()) } answers {
            val formatArgs = args[1] as Array<*>
            val dist = formatArgs[0]
            "⚠️ 1 close pass (<1.5m, min ${dist}m)"
        }
        every { context.getString(R.string.radar_summary_close_passes, *anyVararg()) } answers {
            val formatArgs = args[1] as Array<*>
            val count = formatArgs[0]
            val dist = formatArgs[1]
            "⚠️ $count close passes (<1.5m, min ${dist}m)"
        }
        every { context.getString(R.string.radar_summary_heavy_vehicles_singular) } returns "🚛 1 heavy vehicle"
        every { context.getString(R.string.radar_summary_heavy_vehicles, *anyVararg()) } answers {
            val formatArgs = args[1] as Array<*>
            val count = formatArgs[0]
            "🚛 $count heavy vehicles"
        }
        every { context.getString(R.string.radar_summary_max_speed_simple, *anyVararg()) } answers {
            val formatArgs = args[1] as Array<*>
            val speed = formatArgs[0]
            "🚀 Max $speed km/h"
        }
        every { context.getString(R.string.radar_summary_max_speed, *anyVararg()) } answers {
            val formatArgs = args[1] as Array<*>
            val speed = formatArgs[0]
            val appSpeed = formatArgs[1]
            "🚀 Max $speed km/h (+$appSpeed km/h approach)"
        }
        Locale.setDefault(Locale.US)
    }

    @Test
    fun format_returnsNull_whenSummaryIsNull() {
        val formatted = RadarTrafficSummaryFormatter.format(null, context)
        assertNull(formatted)
    }

    @Test
    fun format_returnsNull_whenTotalVehiclesIsZero() {
        val summary = RadarTrafficSummary(totalVehicles = 0)
        val formatted = RadarTrafficSummaryFormatter.format(summary, context)
        assertNull(formatted)
    }

    @Test
    fun format_returnsNull_whenTotalVehiclesIsNegative() {
        val summary = RadarTrafficSummary(totalVehicles = -5)
        val formatted = RadarTrafficSummaryFormatter.format(summary, context)
        assertNull(formatted)
    }

    @Test
    fun format_standardV1RadarSummary_formatsOvertakesDensityAndSpeeds() {
        val summary = RadarTrafficSummary(
            totalVehicles = 114,
            vehiclesPerKm = 1.84,
            maxVehicleSpeedMps = 104.0 / 3.6,
            maxApproachSpeedMps = 68.0 / 3.6
        )
        val formatted = RadarTrafficSummaryFormatter.format(summary, context)
        assertNotNull(formatted)
        assertTrue("Must contain radar prefix", formatted!!.startsWith("📡 Radar: 114 vehicles passed (1.8/km)"))
        assertTrue("Must contain max speed and approach speed", formatted.contains("🚀 Max 104 km/h (+68 km/h approach)"))
    }

    @Test
    fun format_v2RadarSummary_withClosePassesAndHeavyVehicles_formatsAllBadges() {
        val summary = RadarTrafficSummary(
            totalVehicles = 114,
            vehiclesPerKm = 1.84,
            maxVehicleSpeedMps = 104.0 / 3.6,
            maxApproachSpeedMps = 68.0 / 3.6,
            closePassCount = 3,
            minPassingDistanceM = 1.12,
            heavyVehicleCount = 9
        )
        val formatted = RadarTrafficSummaryFormatter.format(summary, context)
        assertNotNull(formatted)
        assertTrue("Must contain radar prefix", formatted!!.startsWith("📡 Radar: 114 vehicles passed (1.8/km)"))
        assertTrue("Must contain close passes", formatted.contains("⚠️ 3 close passes (<1.5m, min 1.12m)"))
        assertTrue("Must contain heavy vehicles", formatted.contains("🚛 9 heavy vehicles"))
        assertTrue("Must contain max vehicle speed", formatted.contains("🚀 Max 104 km/h"))
    }

    @Test
    fun format_v2RadarSummary_withSingularClosePassAndHeavyVehicle_formatsSingularPhrasing() {
        val summary = RadarTrafficSummary(
            totalVehicles = 42,
            vehiclesPerKm = 2.1,
            maxVehicleSpeedMps = 85.0 / 3.6,
            maxApproachSpeedMps = 45.0 / 3.6,
            closePassCount = 1,
            minPassingDistanceM = 0.95,
            heavyVehicleCount = 1
        )
        val formatted = RadarTrafficSummaryFormatter.format(summary, context)
        assertNotNull(formatted)
        assertTrue("Must contain singular close pass", formatted!!.contains("⚠️ 1 close pass (<1.5m, min 0.95m)"))
        assertTrue("Must contain singular heavy vehicle", formatted.contains("🚛 1 heavy vehicle"))
        assertTrue("Must contain max vehicle speed", formatted.contains("🚀 Max 85 km/h"))
    }
}
