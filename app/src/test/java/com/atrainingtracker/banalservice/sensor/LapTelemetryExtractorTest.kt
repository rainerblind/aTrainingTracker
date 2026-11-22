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

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [LapTelemetryExtractor] verifying type-safe [Number] extraction,
 * non-finite (`NaN`/`Infinity`) guards, zero-duration speed safety, and display fallbacks
 * (REQ-TRK-094.1, TST-TRK-094.1, ATT-6034).
 */
class LapTelemetryExtractorTest {

    @Test
    fun extract_standardIntegerAndDoubleValues_returnsExpectedSnapshotAndSpeed() {
        val lapNr = SensorData(SensorType.LAP_NR, 3, "3", null)
        val lapTime = SensorData(SensorType.TIME_LAP, 200, "00:03:20", null)
        val lapDist = SensorData(SensorType.DISTANCE_m_LAP, 1000.0, "1.00 km", null)

        val result = LapTelemetryExtractor.extract(lapNr, lapTime, lapDist)

        assertEquals(3, result.lapNr)
        assertEquals(200, result.lapTimeSeconds)
        assertEquals("00:03:20", result.lapTimeDisplay)
        assertEquals(1000.0, result.lapDistanceMeters, 1e-6)
        assertEquals("1.00 km", result.lapDistanceDisplay)
        assertEquals(5.0, result.lapSpeedMps, 1e-6)
    }

    @Test
    fun extract_mixedNumberSubtypes_longFloatDouble_convertsWithoutClassCastException() {
        val lapNr = SensorData(SensorType.LAP_NR, 4L, "4", null)
        val lapTime = SensorData(SensorType.TIME_LAP, 125.0, "00:02:05", null)
        val lapDist = SensorData(SensorType.DISTANCE_m_LAP, 500.5f, "500.5 m", null)

        val result = LapTelemetryExtractor.extract(lapNr, lapTime, lapDist)

        assertEquals(4, result.lapNr)
        assertEquals(125, result.lapTimeSeconds)
        assertEquals("00:02:05", result.lapTimeDisplay)
        assertEquals(500.5, result.lapDistanceMeters, 1e-3)
        assertEquals("500.5 m", result.lapDistanceDisplay)
        assertEquals(500.5 / 125.0, result.lapSpeedMps, 1e-3)
    }

    @Test
    fun extract_nullSensorsAndNullValues_preservesBanalDisplayFallbacks() {
        val resultNull = LapTelemetryExtractor.extract(null, null, null)
        assertEquals(0, resultNull.lapNr)
        assertEquals(0, resultNull.lapTimeSeconds)
        assertEquals("??:??:??", resultNull.lapTimeDisplay)
        assertEquals(0.0, resultNull.lapDistanceMeters, 1e-6)
        assertEquals("0", resultNull.lapDistanceDisplay)
        assertEquals(0.0, resultNull.lapSpeedMps, 1e-6)

        val nullValueLapNr = SensorData<Int?>(SensorType.LAP_NR, null, null, null)
        val nullValueTime = SensorData<Int?>(SensorType.TIME_LAP, null, null, null)
        val nullValueDist = SensorData<Double?>(SensorType.DISTANCE_m_LAP, null, null, null)
        val resultNullVals = LapTelemetryExtractor.extract(nullValueLapNr, nullValueTime, nullValueDist)
        assertEquals(0, resultNullVals.lapNr)
        assertEquals(0, resultNullVals.lapTimeSeconds)
        assertEquals("??:??:??", resultNullVals.lapTimeDisplay)
        assertEquals(0.0, resultNullVals.lapDistanceMeters, 1e-6)
        assertEquals("0", resultNullVals.lapDistanceDisplay)
        assertEquals(0.0, resultNullVals.lapSpeedMps, 1e-6)
    }

    @Test
    fun extract_nonFiniteAndNonNumericValues_guardsAgainstInfinityAndClassCast() {
        val infLapNr = SensorData<Any>(SensorType.LAP_NR, Double.POSITIVE_INFINITY, "inf", null)
        val nanLapTime = SensorData<Any>(SensorType.TIME_LAP, Double.NaN, "NaN", null)
        val negInfDist = SensorData<Any>(SensorType.DISTANCE_m_LAP, Float.NEGATIVE_INFINITY, "-inf", null)

        val nonFiniteResult = LapTelemetryExtractor.extract(infLapNr, nanLapTime, negInfDist)
        assertEquals("POSITIVE_INFINITY must yield 0, not Int.MAX_VALUE", 0, nonFiniteResult.lapNr)
        assertEquals(0, nonFiniteResult.lapTimeSeconds)
        assertEquals("??:??:??", nonFiniteResult.lapTimeDisplay)
        assertEquals(0.0, nonFiniteResult.lapDistanceMeters, 1e-6)
        assertEquals("0", nonFiniteResult.lapDistanceDisplay)
        assertEquals(0.0, nonFiniteResult.lapSpeedMps, 1e-6)

        val strLapNr = SensorData<Any>(SensorType.LAP_NR, "not-a-number", "bad", null)
        val strLapTime = SensorData<Any>(SensorType.TIME_LAP, "abc", "abc", null)
        val strLapDist = SensorData<Any>(SensorType.DISTANCE_m_LAP, "xyz", "xyz", null)

        val nonNumericResult = LapTelemetryExtractor.extract(strLapNr, strLapTime, strLapDist)
        assertEquals(0, nonNumericResult.lapNr)
        assertEquals(0, nonNumericResult.lapTimeSeconds)
        assertEquals("??:??:??", nonNumericResult.lapTimeDisplay)
        assertEquals(0.0, nonNumericResult.lapDistanceMeters, 1e-6)
        assertEquals("0", nonNumericResult.lapDistanceDisplay)
        assertEquals(0.0, nonNumericResult.lapSpeedMps, 1e-6)
    }

    @Test
    fun extract_zeroOrNegativeLapTime_yieldsZeroSpeed() {
        val zeroTime = SensorData(SensorType.TIME_LAP, 0, "00:00:00", null)
        val negTime = SensorData(SensorType.TIME_LAP, -10, "-10", null)
        val dist = SensorData(SensorType.DISTANCE_m_LAP, 250.0, "250 m", null)

        assertEquals(0.0, LapTelemetryExtractor.extract(null, zeroTime, dist).lapSpeedMps, 1e-6)
        assertEquals(0.0, LapTelemetryExtractor.extract(null, negTime, dist).lapSpeedMps, 1e-6)
    }
}
