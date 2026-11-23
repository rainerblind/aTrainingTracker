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
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0/>.
 */

package com.atrainingtracker.trainingtracker.util

import com.atrainingtracker.trainingtracker.util.NumericalEncodingUtils
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.PolyUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pre-refactor Golden-Value characterization tests verifying bit-for-bit parity between
 * [StreamSummaryCalculator] and every legacy caller variant before caller migration.
 *
 * // Verifies REQ-DAT-047 / TST-DAT-043
 */
class StreamSummaryCalculatorTest {

    // Verifies REQ-DAT-047 / TST-DAT-043
    @Test
    fun goldenBounds_matchesLegacyLoopsForLatLngListAndEncodedPolyline() {
        val track = listOf(
            LatLng(48.7758, 9.1829),
            LatLng(48.7912, 9.1501),
            LatLng(48.7604, 9.2145),
            LatLng(48.7800, 9.1900)
        )
        // Legacy loop (WorkoutSummariesDatabaseManager.updateMapAndStreams / migrateSpatialBoundsStatic / SegmentsDatabaseManager)
        var legacyMinLat = 90.0
        var legacyMaxLat = -90.0
        var legacyMinLng = 180.0
        var legacyMaxLng = -180.0
        for (p in track) {
            if (p.latitude < legacyMinLat) legacyMinLat = p.latitude
            if (p.latitude > legacyMaxLat) legacyMaxLat = p.latitude
            if (p.longitude < legacyMinLng) legacyMinLng = p.longitude
            if (p.longitude > legacyMaxLng) legacyMaxLng = p.longitude
        }

        val bounds = StreamSummaryCalculator.computeBounds(track)!!
        assertEquals(legacyMinLat, bounds.minLat, 0.0)
        assertEquals(legacyMaxLat, bounds.maxLat, 0.0)
        assertEquals(legacyMinLng, bounds.minLng, 0.0)
        assertEquals(legacyMaxLng, bounds.maxLng, 0.0)

        val encoded = PolyUtil.encode(track)
        val decoded = PolyUtil.decode(encoded)
        val boundsFromPolyline = StreamSummaryCalculator.computeBoundsFromPolyline(encoded)!!
        assertEquals(decoded.minOf { it.latitude }, boundsFromPolyline.minLat, 0.0)
        assertEquals(decoded.maxOf { it.latitude }, boundsFromPolyline.maxLat, 0.0)
        assertEquals(decoded.minOf { it.longitude }, boundsFromPolyline.minLng, 0.0)
        assertEquals(decoded.maxOf { it.longitude }, boundsFromPolyline.maxLng, 0.0)

        assertNull(StreamSummaryCalculator.computeBounds(emptyList()))
        assertNull(StreamSummaryCalculator.computeBounds(null))
        assertNull(StreamSummaryCalculator.computeBoundsFromPolyline(null))
        assertNull(StreamSummaryCalculator.computeBoundsFromPolyline(""))
    }

    // Verifies REQ-DAT-047 / TST-DAT-043
    @Test
    fun goldenAscentDescentRaw_matchesShiftAltitudeDataAndCropWorkoutNullableGaps() {
        // Variant A: WorkoutSummariesDatabaseManager.shiftAltitudeData (non-null Double list)
        val shiftedAlts = listOf(250.0, 255.5, 255.5, 252.0, 268.25, 260.0, 275.0)
        var legacyAsc = 0.0
        var legacyDesc = 0.0
        for (i in 1 until shiftedAlts.size) {
            val diff = shiftedAlts[i] - shiftedAlts[i - 1]
            if (diff > 0) legacyAsc += diff else if (diff < 0) legacyDesc += -diff
        }
        val rawResult = StreamSummaryCalculator.computeAscentDescentRaw(shiftedAlts)
        assertEquals(legacyAsc, rawResult.ascending, 0.0)
        assertEquals(legacyDesc, rawResult.descending, 0.0)

        // Variant B: WorkoutRepository.cropWorkout (nullable list — gaps MUST NOT bridge across null)
        val nullableAlts: List<Double?> = listOf(100.0, 110.0, null, 150.0, 145.0, 145.0, null, null, 200.0, 212.5)
        var cropAsc = 0.0
        var cropDesc = 0.0
        for (i in 1 until nullableAlts.size) {
            val prevAlt = nullableAlts[i - 1]
            val currAlt = nullableAlts[i]
            if (prevAlt != null && currAlt != null) {
                val diff = currAlt - prevAlt
                if (diff > 0) cropAsc += diff else cropDesc += -diff
            }
        }
        val nullableResult = StreamSummaryCalculator.computeAscentDescentRaw(nullableAlts)
        assertEquals(cropAsc, nullableResult.ascending, 0.0)
        assertEquals(cropDesc, nullableResult.descending, 0.0)
        assertEquals(22.5, nullableResult.ascending, 0.0)
        assertEquals(5.0, nullableResult.descending, 0.0)
    }

    // Verifies REQ-DAT-047 / TST-DAT-043
    @Test
    fun goldenAscentDescentMovingAverage_matchesLegacyImportEngine300SampleWindow() {
        // Synthetic 650-sample climb + descent + rolling hills profile exercising >300-sample eviction
        val altitudes = List(650) { i ->
            when {
                i < 200 -> 300.0 + i * 0.45 + (if (i % 2 == 0) 0.3 else -0.3)
                i < 420 -> 390.0 - (i - 200) * 0.35 + (if (i % 3 == 0) 0.5 else -0.2)
                else -> 313.0 + (i - 420) * 0.25
            }
        }
        // Legacy LegacyImportEngine.recalculateStats implementation (ATT-301)
        var legacyAsc = 0.0
        var legacyDesc = 0.0
        val windowSize = 300
        var windowSum = 0.0
        val windowQueue = java.util.ArrayDeque<Double>()
        var lastFiltered: Double? = null
        altitudes.forEach { rawAlt ->
            windowSum += rawAlt
            windowQueue.addLast(rawAlt)
            if (windowQueue.size > windowSize) {
                windowSum -= windowQueue.removeFirst()
            }
            val filtered = windowSum / windowQueue.size
            val prevFiltered = lastFiltered
            if (prevFiltered != null) {
                val delta = filtered - prevFiltered
                if (delta > 0) legacyAsc += delta
                else if (delta < 0) legacyDesc += -delta
            }
            lastFiltered = filtered
        }

        val calc = StreamSummaryCalculator.computeAscentDescentMovingAverage(altitudes, 300)
        assertEquals(legacyAsc, calc.ascending, 0.0)
        assertEquals(legacyDesc, calc.descending, 0.0)
        assertEquals(legacyAsc.toInt(), calc.ascending.toInt())
        assertEquals(legacyDesc.toInt(), calc.descending.toInt())
    }

    // Verifies REQ-DAT-047 / TST-DAT-043
    @Test
    fun goldenDownsamplingAndInterpolation_matchesErtsLegacyImportAndCropWorkout() {
        val values = List(45) { it * 12.5 }

        // 1. ERTS step=20 with lastIndex retention
        val expectedErts = listOf(values[0], values[20], values[40], values[44])
        assertEquals(expectedErts, StreamSummaryCalculator.subsampleStream(values, 20))
        assertEquals(
            NumericalEncodingUtils.encodeDoubles(expectedErts),
            StreamSummaryCalculator.encodeDoubleStream(StreamSummaryCalculator.subsampleStream(values, 20))
        )

        // 2. LegacyImportEngine 5-sample stride ((index + 1) % 5 == 0 when size > 5)
        val expectedLegacy = values.filterIndexed { idx, _ -> (idx + 1) % 5 == 0 }
        assertEquals(expectedLegacy, StreamSummaryCalculator.subsampleLegacyStride(values, 5))
        val shortList = listOf(10.0, 20.0, 30.0, 40.0, 50.0)
        assertEquals(shortList, StreamSummaryCalculator.subsampleLegacyStride(shortList, 5))

        // 3. CropWorkout distance-weighted missing-altitude interpolation & distance origin shift
        val altitudes = MutableList<Double?>(45) { idx ->
            when (idx) {
                0 -> null // leading null -> clamps to next valid (idx 5 = 100.0)
                20 -> null // interior null -> interpolates linearly between idx 15 (200.0 @ 150m) and idx 25 (300.0 @ 250m) -> 250.0
                44 -> null // trailing null -> clamps to prev valid (idx 40 = 400.0)
                5 -> 100.0
                15 -> 200.0
                25 -> 300.0
                40 -> 400.0
                else -> null
            }
        }
        val distances = List(45) { 50.0 + it * 10.0 } // origin = 50.0
        val interpolated = StreamSummaryCalculator.subsampleWithAltitudeInterpolation(
            altitudes = altitudes,
            distances = distances,
            distanceOrigin = 50.0,
            stepSize = 20
        )
        assertEquals(listOf(100.0, 250.0, 400.0, 400.0), interpolated.sampledAltitudes)
        assertEquals(listOf(0.0, 200.0, 400.0, 440.0), interpolated.sampledDistances)

        // 4. All-null altitudes returns empty sampledAltitudes while preserving sampledDistances
        val noAlt = StreamSummaryCalculator.subsampleWithAltitudeInterpolation(
            altitudes = List(25) { null },
            distances = List(25) { 100.0 + it * 5.0 },
            distanceOrigin = 100.0,
            stepSize = 20
        )
        assertEquals(emptyList<Double>(), noAlt.sampledAltitudes)
        assertEquals(listOf(0.0, 100.0, 120.0), noAlt.sampledDistances)
    }

    // Verifies REQ-DAT-047 / TST-DAT-043
    @Test
    fun goldenPolylineEncoding_matchesSimplifiedAndUnsimplifiedCallers() {
        val points = listOf(
            LatLng(48.77580, 9.18290),
            LatLng(48.77581, 9.18291),
            LatLng(48.77750, 9.18500),
            LatLng(48.78100, 9.19200)
        )
        val expectedSimplified = PolyUtil.encode(PolyUtil.simplify(points, 10.0))
        val expectedRaw = PolyUtil.encode(points)

        assertEquals(expectedSimplified, StreamSummaryCalculator.encodePolyline(points, 10.0))
        assertEquals(expectedRaw, StreamSummaryCalculator.encodePolyline(points, null))
        assertNull(StreamSummaryCalculator.encodePolyline(emptyList(), 10.0))
        assertEquals("", StreamSummaryCalculator.encodeDoubleStreamOrEmpty(emptyList()))
        assertNull(StreamSummaryCalculator.encodeDoubleStream(emptyList()))
    }
}
