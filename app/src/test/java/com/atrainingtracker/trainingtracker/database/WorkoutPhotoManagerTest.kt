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

package com.atrainingtracker.trainingtracker.database

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import com.google.android.gms.maps.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class WorkoutPhotoManagerTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun testGetWorkoutPhotosDirCreatesAndReturnsExpectedPath() {
        val dir = WorkoutPhotoManager.getWorkoutPhotosDir(context, 42L)
        assertTrue(dir.exists())
        assertTrue(dir.isDirectory)
        assertTrue(dir.absolutePath.endsWith("workout_photos/42"))
    }

    @Test
    fun testParseExifDateTime() {
        val parsed = WorkoutPhotoManager.parseExifDateTime("2024:05:18 14:30:00")
        assertNotNull(parsed)
        assertTrue(parsed!! > 0)

        assertNull(WorkoutPhotoManager.parseExifDateTime(null))
        assertNull(WorkoutPhotoManager.parseExifDateTime(""))
        assertNull(WorkoutPhotoManager.parseExifDateTime("invalid-date-format"))
    }

    @Test
    fun testInterpolateCoordinatesFromPoints() {
        val points = listOf(
            PathPoint(distance = 0.0, latLng = LatLng(48.0, 11.0), timeSec = 100L),
            PathPoint(distance = 100.0, latLng = LatLng(48.1, 11.2), timeSec = 200L),
            PathPoint(distance = 200.0, latLng = LatLng(48.2, 11.4), timeSec = 300L)
        )

        // Exact match at point 0
        val exact = WorkoutPhotoManager.interpolateCoordinatesFromPoints(points, 100L)
        assertNotNull(exact)
        assertEquals(48.0, exact!!.latitude, 0.0001)
        assertEquals(11.0, exact.longitude, 0.0001)

        // Midpoint between point 0 and point 1 (t = 150s, halfway)
        val mid = WorkoutPhotoManager.interpolateCoordinatesFromPoints(points, 150L)
        assertNotNull(mid)
        assertEquals(48.05, mid!!.latitude, 0.0001)
        assertEquals(11.1, mid.longitude, 0.0001)

        // Out of range: before first point clamps to first point
        val before = WorkoutPhotoManager.interpolateCoordinatesFromPoints(points, 50L)
        assertNotNull(before)
        assertEquals(48.0, before!!.latitude, 0.0001)
        assertEquals(11.0, before.longitude, 0.0001)

        // Out of range: after last point clamps to last point
        val after = WorkoutPhotoManager.interpolateCoordinatesFromPoints(points, 350L)
        assertNotNull(after)
        assertEquals(48.2, after!!.latitude, 0.0001)
        assertEquals(11.4, after.longitude, 0.0001)

        // Empty list
        val empty = WorkoutPhotoManager.interpolateCoordinatesFromPoints(emptyList(), 150L)
        assertNull(empty)
    }
}
