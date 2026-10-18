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

    /**
     * TST-TRK-023.1 & TST-TRK-023.4: Verifies that rehydrateStreams restores startLatLng, lastLatLng,
     * raw/sampled coordinate streams, altitude/distance streams, and seeds lastLatE5/lastLngE5/lastAltE2/lastDistE2
     * so the next 20s stream increment computes a true delta rather than a jump from 0.
     */
    @Test
    fun testRehydrateStreams_restoresStartAndLastLatLng_andContinuesDeltaEncodingFromLastSample() {
        val session = LiveWorkoutSession(10L, setOf(SensorType.ALTITUDE, SensorType.DISTANCE_m))
        val p1 = LatLng(48.1000, 11.5000)
        val p2 = LatLng(48.1050, 11.5050)
        val p3 = LatLng(48.1100, 11.5100)

        session.rehydrateStreams(
            listOf(p1, p2, p3),
            listOf(500.0, 510.0, 525.0),
            listOf(0.0, 400.0, 1000.0)
        )

        assertNotNull(session.startLatLng)
        assertNotNull(session.lastLatLng)
        assertEquals(p1.latitude, session.startLatLng.latitude, 1e-6)
        assertEquals(p3.latitude, session.lastLatLng.latitude, 1e-6)
        assertEquals(3, session.rawLatLngs.size)
        assertEquals(3, session.sampledLatLngs.size)
        assertEquals(listOf(500.0, 510.0, 525.0), session.sampledAltitudes)
        assertEquals(listOf(0.0, 400.0, 1000.0), session.sampledDistances)

        // Post-resume: advance 20 steps to emit a new StreamIncrement at p4, 532.0m (+7m above 525.0m), 1150.0m (+150m above 1000.0m)
        val p4 = LatLng(48.1150, 11.5150)
        var increment: LiveWorkoutSession.StreamIncrement? = null
        for (i in 1..20) {
            increment = session.recordStreamPoint(p4, 532.0, 1150.0)
        }
        assertNotNull("20th post-resume step must emit a StreamIncrement", increment)
        assertEquals(4, session.sampledLatLngs.size)
        assertEquals(listOf(500.0, 510.0, 525.0, 532.0), session.sampledAltitudes)
        assertEquals(listOf(0.0, 400.0, 1000.0, 1150.0), session.sampledDistances)

        // Verify that concatenating the pre-resume encoded stream + post-resume increment decodes to the exact 4 values (no jump from 0)
        val preEncodedAlt = com.atrainingtracker.trainingtracker.ui.utils.NumericalEncodingUtils.encodeDoubles(listOf(500.0, 510.0, 525.0))
        val combinedAlt = com.atrainingtracker.trainingtracker.ui.utils.NumericalEncodingUtils.decodeDoubles(preEncodedAlt + increment!!.altitudeIncrement)
        assertEquals(listOf(500.0, 510.0, 525.0, 532.0), combinedAlt)

        val preEncodedDist = com.atrainingtracker.trainingtracker.ui.utils.NumericalEncodingUtils.encodeDoubles(listOf(0.0, 400.0, 1000.0))
        val combinedDist = com.atrainingtracker.trainingtracker.ui.utils.NumericalEncodingUtils.decodeDoubles(preEncodedDist + increment.distanceIncrement)
        assertEquals(listOf(0.0, 400.0, 1000.0, 1150.0), combinedDist)
    }

    /**
     * TST-TRK-023.1 Boundary Check: Verifies that rehydrateStreams with null or empty lists
     * leaves startLatLng and lastLatLng null and does not insert dummy (0.0, 0.0) points.
     */
    @Test
    fun testRehydrateStreams_emptyOrNullLists_doesNotCrashOrPopulateDummyCoords() {
        val session = LiveWorkoutSession(11L, setOf(SensorType.ALTITUDE))
        session.rehydrateStreams(null, emptyList(), null)

        assertNull(session.startLatLng)
        assertNull(session.lastLatLng)
        assertEquals(0, session.rawLatLngs.size)
        assertEquals(0, session.sampledLatLngs.size)
        assertEquals(0, session.sampledAltitudes.size)
        assertEquals(0, session.sampledDistances.size)
    }

    /**
     * TST-TRK-023.2: Verifies that rehydrateSensorStats preserves pre-resume MIN/MAX values and
     * spatial coordinates, uses the persisted sample count for mid-workout sensors, and keeps newly
     * paired post-resume sensors isolated with count = 0 before their first post-resume sample.
     */
    @Test
    fun testRehydrateSensorStats_preservesMinMaxAndWeightedAvg_includingMidWorkoutCountAndNewlyPairedSensor() {
        val session = LiveWorkoutSession(
            12L,
            setOf(SensorType.HR, SensorType.POWER, SensorType.CADENCE)
        )
        val minHrPos = LatLng(48.1000, 11.5000)
        val maxHrPos = LatLng(48.1200, 11.5200)

        // HR was active for the full 100s pre-resume segment (avg = 140 bpm)
        session.rehydrateSensorStats(SensorType.HR, 110.0, minHrPos, 175.0, maxHrPos, 140.0, 100)

        // POWER was paired mid-workout and only recorded 20 samples before pause (avg = 250 W),
        // even though workout active time was 100s.
        val maxPowerPos = LatLng(48.1150, 11.5150)
        session.rehydrateSensorStats(SensorType.POWER, 100.0, null, 450.0, maxPowerPos, 250.0, 20)

        // Post-resume: add 20 HR samples at 160 bpm -> combined avg over 120 samples = (140*100 + 160*20)/120 = 143.3333
        val postResumePos = LatLng(48.1300, 11.5300)
        for (i in 1..20) {
            session.addSample(SensorType.HR, 160.0, postResumePos)
        }
        val hrStats = session.sensorStats[SensorType.HR]!!
        assertEquals(110.0, hrStats.min, 1e-4)
        assertEquals(minHrPos.latitude, hrStats.minPos.latitude, 1e-6)
        assertEquals(175.0, hrStats.max, 1e-4)
        assertEquals(maxHrPos.latitude, hrStats.maxPos.latitude, 1e-6)
        assertEquals(120, hrStats.count)
        assertEquals(143.3333, hrStats.average, 1e-3)

        // Post-resume: add 20 POWER samples at 300 W -> combined avg over 40 samples = (250*20 + 300*20)/40 = 275.0 W
        for (i in 1..20) {
            session.addSample(SensorType.POWER, 300.0, postResumePos)
        }
        val powerStats = session.sensorStats[SensorType.POWER]!!
        assertEquals(100.0, powerStats.min, 1e-4)
        assertEquals(450.0, powerStats.max, 1e-4)
        assertEquals(maxPowerPos.latitude, powerStats.maxPos.latitude, 1e-6)
        assertEquals(40, powerStats.count)
        assertEquals(275.0, powerStats.average, 1e-4)

        // Newly paired CADENCE sensor after resume (had no pre-resume extrema rows)
        session.addSample(SensorType.CADENCE, 90.0, postResumePos)
        val cadenceStats = session.sensorStats[SensorType.CADENCE]!!
        assertEquals(1, cadenceStats.count)
        assertEquals(90.0, cadenceStats.min, 1e-4)
        assertEquals(90.0, cadenceStats.max, 1e-4)
        assertEquals(90.0, cadenceStats.average, 1e-4)
    }
}
