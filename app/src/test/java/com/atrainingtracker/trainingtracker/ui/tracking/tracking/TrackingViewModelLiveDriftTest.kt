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

import android.app.Application
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import com.atrainingtracker.banalservice.ActivityType
import com.atrainingtracker.banalservice.filters.FilterType
import com.atrainingtracker.banalservice.filters.FilteredSensorData
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.repositories.BANALServiceRepository
import com.atrainingtracker.trainingtracker.repositories.RoutesRepository
import com.atrainingtracker.trainingtracker.segments.LiveSegmentsRepository
import com.atrainingtracker.trainingtracker.ui.tracking.TrackingViewsRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test

/**
 * Unit tests verifying semantic zone color mapping for [SensorType.LIVE_HR_DRIFT]
 * in [TrackingViewModel] (REQ-REV-012, TST-REV-013.4).
 */
class TrackingViewModelLiveDriftTest {

    private lateinit var viewModel: TrackingViewModel
    private val colorZone2 = Color(0xFF00FF00)
    private val colorZone3 = Color(0xFFFFFF00)
    private val colorZone4 = Color(0xFFFF0000)

    @Before
    fun setUp() {
        mockkStatic(ContextCompat::class)
        every { ContextCompat.getColor(any(), R.color.zone_1) } returns 0xFF0000FF.toInt()
        every { ContextCompat.getColor(any(), R.color.zone_2) } returns 0xFF00FF00.toInt()
        every { ContextCompat.getColor(any(), R.color.zone_3) } returns 0xFFFFFF00.toInt()
        every { ContextCompat.getColor(any(), R.color.zone_4) } returns 0xFFFF0000.toInt()
        every { ContextCompat.getColor(any(), R.color.zone_5) } returns 0xFFFF00FF.toInt()
        every { ContextCompat.getColor(any(), R.color.zone_6) } returns 0xFF00FFFF.toInt()
        every { ContextCompat.getColor(any(), R.color.zone_7) } returns 0xFF888888.toInt()

        val app = mockk<Application>(relaxed = true)
        val trackingViewsRepo = mockk<TrackingViewsRepository>(relaxed = true)
        val banalServiceRepo = mockk<BANALServiceRepository>(relaxed = true)
        val liveSegmentsRepo = mockk<LiveSegmentsRepository>(relaxed = true)
        val routesRepo = mockk<RoutesRepository>(relaxed = true)

        viewModel = TrackingViewModel(
            application = app,
            trackingViewsRepository = trackingViewsRepo,
            banalServiceRepository = banalServiceRepo,
            liveSegmentsRepository = liveSegmentsRepo,
            routesRepository = routesRepo,
            viewId = 1L
        )
    }

    @After
    fun tearDown() {
        unmockkStatic(ContextCompat::class)
    }

    private fun callCalculateZoneColor(driftValue: Double?): Color {
        val filteredData = FilteredSensorData<Double>(
            SensorType.LIVE_HR_DRIFT,
            driftValue,
            null,
            "cycling_power_metrics",
            FilterType.INSTANTANEOUS,
            1.0
        )
        val method = TrackingViewModel::class.java.declaredMethods.first { it.name.startsWith("calculateZoneColor") }
        method.isAccessible = true
        val result = method.invoke(viewModel, filteredData, ActivityType.BIKE_POWER)
        return when (result) {
            is Color -> result
            is Long -> Color(result.toULong())
            else -> Color.Transparent
        }
    }

    @Test
    fun testLiveHrDrift_colorMapping_distinctSemanticZones() {
        // Drift <= 5.0 bpm -> Zone 2 (neutral / fresh)
        val colorFresh = callCalculateZoneColor(2.0)
        val colorZero = callCalculateZoneColor(0.0)
        val colorNegative = callCalculateZoneColor(-3.0)
        val colorThreshold5 = callCalculateZoneColor(5.0)
        assertEquals(colorZone2, colorFresh)
        assertEquals(colorZone2, colorZero)
        assertEquals(colorZone2, colorNegative)
        assertEquals(colorZone2, colorThreshold5)

        // Drift 5.1..10.0 bpm -> Zone 3 (moderate decoupling)
        val colorModerate = callCalculateZoneColor(6.0)
        val colorThreshold10 = callCalculateZoneColor(10.0)
        assertEquals(colorZone3, colorModerate)
        assertEquals(colorZone3, colorThreshold10)
        assertNotEquals(colorFresh, colorModerate)

        // Drift > 10.0 bpm -> Zone 4 (elevated fatigue / high drift)
        val colorHighBoundary = callCalculateZoneColor(10.1)
        val colorHigh = callCalculateZoneColor(12.0)
        assertEquals(colorZone4, colorHighBoundary)
        assertEquals(colorZone4, colorHigh)
        assertNotEquals(colorFresh, colorHigh)
        assertNotEquals(colorModerate, colorHigh)
    }

    @Test
    fun testLiveHrDrift_colorMapping_nullValueReturnsDefaultColor() {
        val colorNull = callCalculateZoneColor(null)
        assertEquals(Color.Transparent, colorNull)
    }
}
