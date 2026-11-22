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

package com.atrainingtracker.trainingtracker.routes

import android.location.Location
import com.atrainingtracker.trainingtracker.database.WorkoutClusterEngine
import com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.SpatialRegionEngine
import com.google.android.gms.maps.model.LatLng
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Golden-value and delegation parity tests for [AndroidGeoDistance] (`TST-PRO-137.1`, `REQ-PRO-137.1`).
 */
class AndroidGeoDistanceTest {

    @After
    fun tearDown() {
        unmockkStatic(Location::class)
    }

    @Test
    fun ellipsoidalDistanceMeters_unmockedJvmRuntimeException_fallsBackToGeoUtilsHaversine() {
        val lat1 = 48.137154
        val lon1 = 11.576124
        val lat2 = 47.491694
        val lon2 = 11.095498

        val expectedHaversine = GeoUtils.haversineDistanceMeters(lat1, lon1, lat2, lon2)
        val actual = AndroidGeoDistance.ellipsoidalDistanceMeters(lat1, lon1, lat2, lon2)
        assertEquals(expectedHaversine, actual, 1e-6)

        val p1 = LatLng(lat1, lon1)
        val p2 = LatLng(lat2, lon2)
        assertEquals(expectedHaversine.toFloat(), WorkoutClusterEngine.distanceBetween(p1, p2), 1e-3f)
        assertEquals(expectedHaversine, SpatialRegionEngine.distanceBetween(p1, p2), 1e-6)
    }

    @Test
    fun ellipsoidalDistanceMeters_whenLocationDistanceBetweenSucceeds_returnsEllipsoidalValue() {
        mockkStatic(Location::class)
        val expectedEllipsoidal = 80_211.75f
        every {
            Location.distanceBetween(any(), any(), any(), any(), any())
        } answers {
            val out = arg<FloatArray>(4)
            out[0] = expectedEllipsoidal
        }

        val p1 = LatLng(48.137154, 11.576124)
        val p2 = LatLng(47.491694, 11.095498)

        assertEquals(
            expectedEllipsoidal.toDouble(),
            AndroidGeoDistance.ellipsoidalDistanceMeters(p1.latitude, p1.longitude, p2.latitude, p2.longitude),
            1e-6
        )
        assertEquals(expectedEllipsoidal, WorkoutClusterEngine.distanceBetween(p1, p2), 1e-6f)
        assertEquals(expectedEllipsoidal.toDouble(), SpatialRegionEngine.distanceBetween(p1, p2), 1e-6)
    }
}
