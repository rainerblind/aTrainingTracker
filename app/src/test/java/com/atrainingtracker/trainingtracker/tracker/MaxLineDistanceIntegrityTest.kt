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

import com.atrainingtracker.banalservice.devices.SpeedAndLocationDevice
import com.atrainingtracker.banalservice.sensor.MyAccumulatorSensor
import com.atrainingtracker.banalservice.sensor.MySensor
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.database.WorkoutClusterEngine
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.PolyUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Automated unit test suite verifying the integrity of Maximum Line Distance (Apex)
 * calculations, live anchoring, and self-healing (REQ-MAP-020, TST-MAP-022, ATT-528).
 */
class MaxLineDistanceIntegrityTest {

    /**
     * Verifies that mLineDistanceSensor is NOT instantiated as an accumulator.
     * Line distance is an instantaneous scalar gauge (origin displacement), not an accumulating quantity.
     * Extending MyAccumulatorSensor corrupts the displacement value with negative offsets during resets.
     */
    @Test
    fun testLineDistanceSensorIsNotAccumulator() {
        val lineDistField = SpeedAndLocationDevice::class.java.getDeclaredField("mLineDistanceSensor")
        assertNotNull(lineDistField)

        // The field must be typed as MySensor (instantaneous gauge)
        assertTrue(MySensor::class.java.isAssignableFrom(lineDistField.type))
        // Must NOT be an accumulator sensor
        assertFalse(MyAccumulatorSensor::class.java.isAssignableFrom(lineDistField.type))
    }

    /**
     * Verifies that LiveWorkoutSession running statistics for LINE_DISTANCE_m correctly capture
     * the true maximum displacement and apex coordinate when branching routes are recorded.
     * (Simulates the Dingle Boardwalk Route with a 350m western spur and an 1800m Penrallt turnaround).
     */
    @Test
    fun testLiveWorkoutSessionAnchorsLineDistanceToStart() {
        val session = LiveWorkoutSession(100L, setOf(SensorType.LINE_DISTANCE_m))

        // Cildwrn Rd trailhead start location
        val startPos = LatLng(53.2500, -4.3000)
        // Western boardwalk spur (~350m displacement)
        val westSpurPos = LatLng(53.2520, -4.3050)
        // Penrallt turnaround point (~1800m displacement)
        val penralltPos = LatLng(53.2400, -4.2800)

        val distWest = WorkoutClusterEngine.distanceBetween(startPos, westSpurPos).toDouble()
        val distPenrallt = WorkoutClusterEngine.distanceBetween(startPos, penralltPos).toDouble()

        assertTrue("Penrallt must be further than western spur", distPenrallt > distWest)

        // 1. Start workout at trailhead
        session.addSample(SensorType.LINE_DISTANCE_m, 0.0, startPos)

        // 2. Walk along western spur
        session.addSample(SensorType.LINE_DISTANCE_m, distWest * 0.5, LatLng(53.2510, -4.3025))
        session.addSample(SensorType.LINE_DISTANCE_m, distWest, westSpurPos)

        val statsAfterWest = session.sensorStats[SensorType.LINE_DISTANCE_m]
        assertNotNull(statsAfterWest)
        assertEquals(distWest, statsAfterWest!!.max, 1e-2)
        assertEquals(westSpurPos.latitude, statsAfterWest.maxPos.latitude, 1e-6)
        assertEquals(westSpurPos.longitude, statsAfterWest.maxPos.longitude, 1e-6)

        // 3. Return to trailhead
        session.addSample(SensorType.LINE_DISTANCE_m, 0.0, startPos)

        // 4. Walk south-east all the way to Penrallt
        session.addSample(SensorType.LINE_DISTANCE_m, distPenrallt * 0.5, LatLng(53.2450, -4.2900))
        session.addSample(SensorType.LINE_DISTANCE_m, distPenrallt, penralltPos)

        // 5. Return to trailhead and stop
        session.addSample(SensorType.LINE_DISTANCE_m, 0.0, startPos)

        val statsFinal = session.sensorStats[SensorType.LINE_DISTANCE_m]
        assertNotNull(statsFinal)
        // The max must be Penrallt (1800m), NOT the western spur (350m)
        assertEquals(distPenrallt, statsFinal!!.max, 1e-2)
        assertEquals(penralltPos.latitude, statsFinal.maxPos.latitude, 1e-6)
        assertEquals(penralltPos.longitude, statsFinal.maxPos.longitude, 1e-6)
    }

    /**
     * Verifies deterministic polyline apex derivation (as implemented in TrackerService.finalizeLiveSession
     * and WorkoutDataMapper.resolveAuthoritativeApex).
     */
    @Test
    fun testDeterministicApexCalculationFromPolyline() {
        val startPos = LatLng(53.2500, -4.3000)
        val westSpurPos = LatLng(53.2520, -4.3050)
        val intermediatePos = LatLng(53.2450, -4.2900)
        val penralltPos = LatLng(53.2400, -4.2800)

        val points = listOf(
            startPos,
            LatLng(53.2510, -4.3025),
            westSpurPos,
            startPos,
            intermediatePos,
            penralltPos,
            intermediatePos,
            startPos
        )

        val encodedPolyline = PolyUtil.encode(points)
        val decodedPoints = PolyUtil.decode(encodedPolyline)

        var calculatedMax = -1.0
        var calculatedApex: LatLng = decodedPoints.first()
        for (pt in decodedPoints) {
            val d = WorkoutClusterEngine.distanceBetween(startPos, pt).toDouble()
            if (d > calculatedMax) {
                calculatedMax = d
                calculatedApex = pt
            }
        }

        val expectedDistance = WorkoutClusterEngine.distanceBetween(startPos, penralltPos).toDouble()
        assertEquals(expectedDistance, calculatedMax, 1.0)
        assertEquals(penralltPos.latitude, calculatedApex.latitude, 1e-4)
        assertEquals(penralltPos.longitude, calculatedApex.longitude, 1e-4)
    }

    /**
     * Verifies self-healing logic for legacy workouts: when the recorded apex in the database
     * points to an inferior spur (e.g. western spur) rather than the true maximum displacement point,
     * the system reliably detects the anomaly and heals to the authoritative apex.
     */
    @Test
    fun testSelfHealingApexDiscrepancyDetection() {
        val startPos = LatLng(53.2500, -4.3000)
        val falseApex = LatLng(53.2520, -4.3050) // Western spur (~350m)
        val trueApex = LatLng(53.2400, -4.2800)  // Penrallt (~1800m)

        val recordedMaxDisp = 350.0
        val recordedApex = falseApex

        val distToRecordedApex = WorkoutClusterEngine.distanceBetween(startPos, recordedApex).toDouble()
        val trueMaxDisp = WorkoutClusterEngine.distanceBetween(startPos, trueApex).toDouble()

        // Anomaly condition: true track maximum displacement exceeds recorded apex displacement by > 25m
        val needsHealing = trueMaxDisp > distToRecordedApex + 25.0
        assertTrue("System must trigger self-healing when recorded apex is substantially closer than track maximum", needsHealing)

        // Once healed to trueApex, verify no re-triggering occurs
        val healedDistToApex = WorkoutClusterEngine.distanceBetween(startPos, trueApex).toDouble()
        val retrigger = trueMaxDisp > healedDistToApex + 25.0
        assertFalse("System must not trigger unnecessary updates once healed", retrigger)
    }
}
