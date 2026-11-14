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

package com.atrainingtracker.banalservice.sensor

import android.content.Context
import androidx.core.content.ContextCompat
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager
import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.banalservice.devices.MyDevice
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

/**
 * Unit test suite verifying [MySensorManager] priority routing, fallback, and device sensor queries
 * for [DeviceType.ALTITUDE_AND_SLOPE_ATT_FUSED] (REQ-FIL-016.4, TST-FIL-016.3, ATT-6279).
 */
class AltitudeSlopePriorityTest {

    private lateinit var context: Context
    private lateinit var sensorManager: MySensorManager

    @Before
    fun setUp() {
        mockkStatic(DevicesDatabaseManager::class)
        every { DevicesDatabaseManager.getInstance(any()) } returns mockk(relaxed = true)

        mockkStatic(ContextCompat::class)
        every { ContextCompat.registerReceiver(any(), any(), any(), any()) } returns null

        context = mockk(relaxed = true)
        sensorManager = MySensorManager(context)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun mockDevice(type: DeviceType): MyDevice {
        return mockk(relaxed = true) {
            every { deviceType } returns type
        }
    }

    @Test
    fun testPriorityArrays_containAltitudeAndSlopeAttFusedAtRankZero() {
        val dev = DeviceType.ALTITUDE_AND_SLOPE_ATT_FUSED
        assertEquals(0, MySensorManager.getPriorityRank(SensorType.ALTITUDE, dev))
        assertEquals(0, MySensorManager.getPriorityRank(SensorType.SLOPE, dev))
        assertEquals(0, MySensorManager.getPriorityRank(SensorType.VERTICAL_SPEED, dev))
        assertEquals(0, MySensorManager.getPriorityRank(SensorType.ASCENT, dev))
        assertEquals(0, MySensorManager.getPriorityRank(SensorType.DESCENT, dev))

        val expectedAltPriority = arrayOf(
            DeviceType.ALTITUDE_AND_SLOPE_ATT_FUSED,
            DeviceType.ALTITUDE_FROM_POSITION,
            DeviceType.ALTITUDE_FROM_PRESSURE,
            DeviceType.SPEED_AND_LOCATION_GOOGLE_FUSED,
            DeviceType.SPEED_AND_LOCATION_GPS
        )
        assertArrayEquals(expectedAltPriority, MySensorManager.ALTITUDE_PRIORITY)

        val expectedSlopePriority = arrayOf(
            DeviceType.ALTITUDE_AND_SLOPE_ATT_FUSED,
            DeviceType.VERTICAL_SPEED_AND_SLOPE,
            DeviceType.BIKE_POWER
        )
        assertArrayEquals(expectedSlopePriority, MySensorManager.SLOPE_AND_VERTICAL_SPEED_PRIORITY)
    }

    @Test
    @Suppress("LongMethod")
    fun testAltitudeSlopePriority_highestPrecedenceAndCleanFallback() {
        val attFusedDevice = mockDevice(DeviceType.ALTITUDE_AND_SLOPE_ATT_FUSED)
        val demDevice = mockDevice(DeviceType.ALTITUDE_FROM_POSITION)
        val vSpeedDevice = mockDevice(DeviceType.VERTICAL_SPEED_AND_SLOPE)

        val attAlt = MySensor<Number>(attFusedDevice, SensorType.ALTITUDE)
        val attSlope = MySensor<Int>(attFusedDevice, SensorType.SLOPE)
        val attVam = MySensor<Int>(attFusedDevice, SensorType.VERTICAL_SPEED)

        val demAlt = MySensor<Number>(demDevice, SensorType.ALTITUDE)
        val vSpeedSlope = MySensor<Int>(vSpeedDevice, SensorType.SLOPE)
        val vSpeedVam = MySensor<Int>(vSpeedDevice, SensorType.VERTICAL_SPEED)

        // 1. Register lower priority sensors first
        sensorManager.registerSensors(listOf(demAlt, vSpeedSlope, vSpeedVam))
        assertSame(
            "DEM should be active altitude initially",
            demAlt,
            sensorManager.getBestSensor(SensorType.ALTITUDE)
        )
        assertSame(
            "VSpeed should be active slope initially",
            vSpeedSlope,
            sensorManager.getBestSensor(SensorType.SLOPE)
        )
        assertSame(
            "VSpeed should be active VAM initially",
            vSpeedVam,
            sensorManager.getBestSensor(SensorType.VERTICAL_SPEED)
        )

        // 2. Register ATT Fused Altitude & Slope -> preempts lower priority sensors
        sensorManager.registerSensors(listOf(attAlt, attSlope, attVam))
        assertSame(
            "ATT Fused should preempt DEM altitude",
            attAlt,
            sensorManager.getBestSensor(SensorType.ALTITUDE)
        )
        assertSame(
            "ATT Fused should preempt VSpeed slope",
            attSlope,
            sensorManager.getBestSensor(SensorType.SLOPE)
        )
        assertSame(
            "ATT Fused should preempt VSpeed VAM",
            attVam,
            sensorManager.getBestSensor(SensorType.VERTICAL_SPEED)
        )

        // 3. Unregister ATT Fused -> cleanly falls back to DEM and VSpeed
        sensorManager.unregisterSensor(attAlt)
        sensorManager.unregisterSensor(attSlope)
        sensorManager.unregisterSensor(attVam)
        assertSame(
            "Altitude should cleanly fall back to DEM",
            demAlt,
            sensorManager.getBestSensor(SensorType.ALTITUDE)
        )
        assertSame(
            "Slope should cleanly fall back to VSpeed",
            vSpeedSlope,
            sensorManager.getBestSensor(SensorType.SLOPE)
        )
        assertSame(
            "VAM should cleanly fall back to VSpeed",
            vSpeedVam,
            sensorManager.getBestSensor(SensorType.VERTICAL_SPEED)
        )
    }

    @Test
    fun testGetSensorForDeviceType_returnsSpecificDeviceSensor() {
        val attFusedDevice = mockDevice(DeviceType.ALTITUDE_AND_SLOPE_ATT_FUSED)
        val demDevice = mockDevice(DeviceType.ALTITUDE_FROM_POSITION)

        val attAlt = MySensor<Number>(attFusedDevice, SensorType.ALTITUDE)
        val demAlt = MySensor<Number>(demDevice, SensorType.ALTITUDE)

        sensorManager.registerSensors(listOf(attAlt, demAlt))

        val retrievedDem = sensorManager.getSensorForDeviceType(
            SensorType.ALTITUDE,
            DeviceType.ALTITUDE_FROM_POSITION
        )
        assertNotNull("Should find DEM sensor specifically", retrievedDem)
        assertSame(demAlt, retrievedDem)

        val retrievedAtt = sensorManager.getSensorForDeviceType(
            SensorType.ALTITUDE,
            DeviceType.ALTITUDE_AND_SLOPE_ATT_FUSED
        )
        assertNotNull("Should find ATT Fused sensor specifically", retrievedAtt)
        assertSame(attAlt, retrievedAtt)
    }
}
