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
import androidx.core.content.ContextCompat
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.repositories.BANALServiceRepository
import com.atrainingtracker.trainingtracker.repositories.RoutesRepository
import com.atrainingtracker.trainingtracker.segments.LiveSegmentsRepository
import com.atrainingtracker.trainingtracker.settings.SettingsDataStore
import com.atrainingtracker.trainingtracker.ui.tracking.TrackingViewsRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [TrackingViewModel] HRV telemetry integration conforming to REQ-TRK-061.3 and TST-TRK-061.3:
 * - ZoneType resolution for HRV_DFA_A1 mapping to HR_RUN and HR_BIKE
 * - HR ZoneDisplayOptions parity inheritance
 */
class TrackingViewModelHrvTest {

    private lateinit var viewModel: TrackingViewModel

    @Before
    fun setUp() {
        mockkStatic(ContextCompat::class)
        every { ContextCompat.getColor(any(), any()) } returns 0xFF000000.toInt()

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

    private fun callGetZoneType(sensorType: SensorType, sportType: BSportType): SettingsDataStore.ZoneType? {
        val method = TrackingViewModel::class.java.declaredMethods.first { it.name.startsWith("getZoneType") }
        method.isAccessible = true
        return method.invoke(viewModel, sensorType, sportType) as? SettingsDataStore.ZoneType
    }

    @Test
    fun testGetZoneType_hrvDfaA1_mapsToHrZonesBySport() {
        // REQ-TRK-061.3: HRV_DFA_A1 maps to HR zone types
        val runZone = callGetZoneType(SensorType.HRV_DFA_A1, BSportType.RUN)
        assertEquals("HRV_DFA_A1 in RUN must map to HR_RUN", SettingsDataStore.ZoneType.HR_RUN, runZone)

        val bikeZone = callGetZoneType(SensorType.HRV_DFA_A1, BSportType.BIKE)
        assertEquals("HRV_DFA_A1 in BIKE must map to HR_BIKE", SettingsDataStore.ZoneType.HR_BIKE, bikeZone)

        val otherZone = callGetZoneType(SensorType.HRV_DFA_A1, BSportType.UNKNOWN)
        assertNull("HRV_DFA_A1 in UNKNOWN must return null zoneType", otherZone)
    }

    @Test
    fun testGetZoneType_hrParity_matchesDfaA1Mapping() {
        val hrRunZone = callGetZoneType(SensorType.HR, BSportType.RUN)
        val dfaRunZone = callGetZoneType(SensorType.HRV_DFA_A1, BSportType.RUN)
        assertEquals(hrRunZone, dfaRunZone)

        val hrBikeZone = callGetZoneType(SensorType.HR, BSportType.BIKE)
        val dfaBikeZone = callGetZoneType(SensorType.HRV_DFA_A1, BSportType.BIKE)
        assertEquals(hrBikeZone, dfaBikeZone)
    }
}
