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

package com.atrainingtracker.trainingtracker.tracker

import com.atrainingtracker.banalservice.sensor.SensorType
import com.google.android.gms.maps.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for [LiveWorkoutSession] running statistics and spatial coordinate binding (REQ-MAP-019, ATT-499).
 */
class LiveWorkoutSessionTest {

    @Test
    fun testLateSpatialBindingForMin() {
        val session = LiveWorkoutSession(1L, setOf(SensorType.ALTITUDE))

        // 1. Initial reading arrives before GPS satellite fix (position is null)
        session.addSample(SensorType.ALTITUDE, 1283.0, null)
        val stats1 = session.sensorStats[SensorType.ALTITUDE]
        assertNotNull(stats1)
        assertEquals(1283.0, stats1!!.min, 1e-4)
        assertNull(stats1.minPos)

        // 2. GPS fix acquired at the trailhead at the same baseline elevation
        val trailheadPos = LatLng(46.8833, 11.7542)
        session.addSample(SensorType.ALTITUDE, 1283.0, trailheadPos)
        assertEquals(1283.0, stats1.min, 1e-4)
        assertNotNull(stats1.minPos)
        assertEquals(trailheadPos.latitude, stats1.minPos.latitude, 1e-6)
        assertEquals(trailheadPos.longitude, stats1.minPos.longitude, 1e-6)

        // 3. Ascending: higher altitude readings should NOT overwrite min or minPos
        val midClimbPos = LatLng(46.8900, 11.7600)
        session.addSample(SensorType.ALTITUDE, 1380.0, midClimbPos)
        assertEquals(1283.0, stats1.min, 1e-4)
        assertEquals(trailheadPos.latitude, stats1.minPos.latitude, 1e-6)
        assertEquals(trailheadPos.longitude, stats1.minPos.longitude, 1e-6)
    }

    @Test
    fun testLateSpatialBindingForMax() {
        val session = LiveWorkoutSession(2L, setOf(SensorType.SPEED_mps))

        // Initial speed peak arrives before GPS fix
        session.addSample(SensorType.SPEED_mps, 8.5, null)
        val stats = session.sensorStats[SensorType.SPEED_mps]
        assertNotNull(stats)
        assertEquals(8.5, stats!!.max, 1e-4)
        assertNull(stats.maxPos)

        // Position arrives matching the max value
        val sprintPos = LatLng(46.8850, 11.7550)
        session.addSample(SensorType.SPEED_mps, 8.5, sprintPos)
        assertEquals(8.5, stats.max, 1e-4)
        assertEquals(sprintPos.latitude, stats.maxPos.latitude, 1e-6)
        assertEquals(sprintPos.longitude, stats.maxPos.longitude, 1e-6)
    }

    @Test
    fun testAltitudeCorrectionPreservesAnchoredMinPosition() {
        val session = LiveWorkoutSession(3L, setOf(SensorType.ALTITUDE))
        val startPos = LatLng(46.8800, 11.7500)
        session.addSample(SensorType.ALTITUDE, 1200.0, startPos)

        val stats = session.sensorStats[SensorType.ALTITUDE]
        assertNotNull(stats)
        assertEquals(1200.0, stats!!.min, 1e-4)
        assertEquals(startPos.latitude, stats.minPos.latitude, 1e-6)

        // Mid-workout calibration offset of +83.0m
        session.applyAltitudeCorrection(83.0)
        assertEquals(1283.0, stats.min, 1e-4)
        assertEquals(startPos.latitude, stats.minPos.latitude, 1e-6)
    }
}
