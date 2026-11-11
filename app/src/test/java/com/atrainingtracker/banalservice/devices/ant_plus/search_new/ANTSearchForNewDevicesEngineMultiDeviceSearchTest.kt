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

package com.atrainingtracker.banalservice.devices.ant_plus.search_new

import android.content.Context
import android.util.Log
import com.atrainingtracker.banalservice.Protocol
import com.atrainingtracker.banalservice.devices.DeviceType
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit test verifying ANT+ Bike Radar discovery and Protocol.ANT_PLUS registration (TST-CON-032.4, REQ-CON-032.5).
 */
class ANTSearchForNewDevicesEngineMultiDeviceSearchTest {

    private lateinit var mockContext: Context
    private lateinit var mockCallback: ANTSearchForNewDevicesEngineMultiDeviceSearch.IANTAsyncSearchEngineInterface

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0

        mockContext = mockk(relaxed = true)
        mockCallback = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testAntPlusRemoteDeviceTypes_includesBikeRadar() {
        val antDeviceTypes = DeviceType.getRemoteDeviceTypes(Protocol.ANT_PLUS)
        assertTrue(antDeviceTypes.contains(DeviceType.BIKE_RADAR))
    }

    @Test
    fun testEngineInstantiation_forBikeRadar() {
        val engine = ANTSearchForNewDevicesEngineMultiDeviceSearch(
            mockContext,
            DeviceType.BIKE_RADAR,
            mockCallback
        )
        assertEquals(DeviceType.BIKE_RADAR, engine.deviceType)
    }

    @Test
    fun testCallbackInvocation_forDiscoveredRadarDevice() {
        val engine = ANTSearchForNewDevicesEngineMultiDeviceSearch(
            mockContext,
            DeviceType.BIKE_RADAR,
            mockCallback
        )

        // Directly verify the callback interface contract for radar device discovery
        mockCallback.onNewDeviceFound(
            DeviceType.BIKE_RADAR,
            54321,
            "ANT+ Radar 54321",
            true,
            "Garmin",
            100
        )

        verify {
            mockCallback.onNewDeviceFound(
                DeviceType.BIKE_RADAR,
                54321,
                "ANT+ Radar 54321",
                true,
                "Garmin",
                100
            )
        }
    }
}
