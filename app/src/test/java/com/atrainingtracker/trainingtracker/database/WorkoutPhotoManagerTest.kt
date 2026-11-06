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

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import com.google.android.gms.maps.model.LatLng
import io.mockk.every
import io.mockk.mockk
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

    @Test
    fun testExtractExifMetadata_emptyFile_returnsNullCoordinates() {
        val tempFile = File(context.cacheDir, "test_empty_exif.jpg")
        tempFile.writeText("fake image bytes without exif")
        val metadata = WorkoutPhotoManager.extractExifMetadata(tempFile)
        assertNull(metadata.latitude)
        assertNull(metadata.longitude)
        tempFile.delete()
    }

    @Test
    fun testSelfHealPhotos_nullIslandCoordinatesResetToNullWhenNoTrack() {
        val summariesDb = WorkoutSummariesDatabaseManager.getInstance(context)
        val workoutId = 12345L
        val photoPath = File(context.cacheDir, "photo1.jpg").apply { writeText("img") }.absolutePath

        val insertedId = summariesDb.insertPhoto(workoutId, photoPath, 1700000000000L, 0.0, 0.0, "Test")
        assertTrue(insertedId > 0)

        val unhealed = summariesDb.getPhotosForWorkout(workoutId)
        assertEquals(1, unhealed.size)
        assertEquals(0.0, unhealed[0].latitude!!, 1e-6)
        assertEquals(0.0, unhealed[0].longitude!!, 1e-6)

        // Run self-healing
        val healed = WorkoutPhotoManager.selfHealPhotos(context, workoutId, unhealed)
        assertEquals(1, healed.size)
        assertNull("Latitude must be reset to null when interpolation cannot be performed", healed[0].latitude)
        assertNull("Longitude must be reset to null when interpolation cannot be performed", healed[0].longitude)

        // Verify SQLite was updated
        val fromDb = summariesDb.getPhotosForWorkout(workoutId)
        assertEquals(1, fromDb.size)
        assertNull("DB row latitude must be updated to null", fromDb[0].latitude)
        assertNull("DB row longitude must be updated to null", fromDb[0].longitude)

        // Cleanup
        summariesDb.deletePhotosForWorkout(workoutId)
        File(photoPath).delete()
    }

    @Test
    fun testUpdatePhotoCoordinatesInDatabase() {
        val summariesDb = WorkoutSummariesDatabaseManager.getInstance(context)
        val workoutId = 12346L
        val photoPath = File(context.cacheDir, "photo2.jpg").apply { writeText("img2") }.absolutePath

        val insertedId = summariesDb.insertPhoto(workoutId, photoPath, 1700000000000L, 0.0, 0.0, "Before")
        assertTrue(insertedId > 0)

        val updated = summariesDb.updatePhotoCoordinates(insertedId, 48.65, 9.05)
        assertTrue(updated)

        val photos = summariesDb.getPhotosForWorkout(workoutId)
        assertEquals(1, photos.size)
        assertEquals(48.65, photos[0].latitude!!, 1e-5)
        assertEquals(9.05, photos[0].longitude!!, 1e-5)

        // Cleanup
        summariesDb.deletePhotosForWorkout(workoutId)
        File(photoPath).delete()
    }

    @Test
    fun testCalculateWorkoutTimeWindow_defaultTolerance() {
        val startMs = 1_700_000_000_000L
        val endMs = 1_700_003_600_000L // 1 hour workout
        val (wStart, wEnd) = WorkoutPhotoManager.calculateWorkoutTimeWindow(startMs, endMs)

        // 300,000L ms = 5 minutes
        assertEquals(startMs - 300_000L, wStart)
        assertEquals(endMs + 300_000L, wEnd)
    }

    @Test
    fun testCalculateWorkoutTimeWindow_customTolerance() {
        val startMs = 1_700_000_000_000L
        val endMs = 1_700_001_800_000L
        val customTol = 60_000L
        val (wStart, wEnd) = WorkoutPhotoManager.calculateWorkoutTimeWindow(startMs, endMs, customTol)

        assertEquals(startMs - 60_000L, wStart)
        assertEquals(endMs + 60_000L, wEnd)
    }

    @Test
    fun testQueryWorkoutTimePhotos_invalidTimestamps_returnsEmptyList() {
        // Zero start time
        val res1 = WorkoutPhotoManager.queryWorkoutTimePhotos(context, 0L, 1000L)
        assertTrue(res1.isEmpty())

        // Negative start time
        val res2 = WorkoutPhotoManager.queryWorkoutTimePhotos(context, -100L, 1000L)
        assertTrue(res2.isEmpty())

        // End time before start time
        val res3 = WorkoutPhotoManager.queryWorkoutTimePhotos(context, 2000L, 1000L)
        assertTrue(res3.isEmpty())
    }

    @Test
    fun testQueryWorkoutTimePhotos_matchingWindow() {
        val workoutStart = 1_700_000_000_000L
        val workoutEnd = 1_700_003_600_000L

        val mockContext = mockk<Context>()
        val mockResolver = mockk<ContentResolver>()
        val mockCursor = mockk<Cursor>()

        every { mockContext.contentResolver } returns mockResolver
        every {
            mockResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                any(),
                any(),
                any(),
                any()
            )
        } returns mockCursor

        every { mockCursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID) } returns 0
        every { mockCursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN) } returns 1
        every { mockCursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED) } returns 2
        every { mockCursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED) } returns 3

        // Simulate 2 rows: 1 inside window, 1 outside window
        every { mockCursor.moveToNext() } returnsMany listOf(true, true, false)
        every { mockCursor.getLong(0) } returnsMany listOf(101L, 102L)
        // Row 1: inside window (workoutStart + 30 min)
        // Row 2: outside window (workoutStart - 2 hours)
        every { mockCursor.getLong(1) } returnsMany listOf(workoutStart + 1_800_000L, workoutStart - 7_200_000L)
        every { mockCursor.getLong(2) } returnsMany listOf((workoutStart + 1_800_000L) / 1000L, (workoutStart - 7_200_000L) / 1000L)
        every { mockCursor.getLong(3) } returnsMany listOf((workoutStart + 1_800_000L) / 1000L, (workoutStart - 7_200_000L) / 1000L)
        every { mockCursor.close() } returns Unit

        val photos = WorkoutPhotoManager.queryWorkoutTimePhotos(mockContext, workoutStart, workoutEnd)
        assertEquals("Exactly 1 photo inside window must be returned", 1, photos.size)
        assertEquals(101L, photos[0].id)
        assertEquals(workoutStart + 1_800_000L, photos[0].dateTakenMs)
    }

    @Test
    fun testQueryWorkoutTimePhotos_fallbackToDateAddedWhenDateTakenZero() {
        val workoutStart = 1_700_000_000_000L
        val workoutEnd = 1_700_003_600_000L

        val mockContext = mockk<Context>()
        val mockResolver = mockk<ContentResolver>()
        val mockCursor = mockk<Cursor>()

        every { mockContext.contentResolver } returns mockResolver
        every {
            mockResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                any(),
                any(),
                any(),
                any()
            )
        } returns mockCursor

        every { mockCursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID) } returns 0
        every { mockCursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN) } returns 1
        every { mockCursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED) } returns 2
        every { mockCursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED) } returns 3

        // Row has DATE_TAKEN = 0, but valid DATE_ADDED * 1000 inside window
        every { mockCursor.moveToNext() } returnsMany listOf(true, false)
        every { mockCursor.getLong(0) } returns 103L
        every { mockCursor.getLong(1) } returns 0L
        every { mockCursor.getLong(2) } returns (workoutStart + 600_000L) / 1000L
        every { mockCursor.getLong(3) } returns 0L
        every { mockCursor.close() } returns Unit

        val photos = WorkoutPhotoManager.queryWorkoutTimePhotos(mockContext, workoutStart, workoutEnd)
        assertEquals(1, photos.size)
        assertEquals(103L, photos[0].id)
        assertEquals(workoutStart + 600_000L, photos[0].dateTakenMs)
    }
}
