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

package com.atrainingtracker.trainingtracker.ui.aftermath

import android.content.Context
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.database.WorkoutPhotoEntry
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.google.android.gms.maps.model.LatLng
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.lang.reflect.Modifier
import java.time.LocalDateTime

/**
 * UI and Data contract verification suite for Workout Photos (REQ-DAT-038, REQ-UI-448 / ATT-43).
 */
@RunWith(RobolectricTestRunner::class)
class WorkoutPhotoUiContractTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        mockkStatic(ContextCompat::class)
        every { ContextCompat.registerReceiver(any(), any(), any(), any()) } returns null
    }

    @After
    fun tearDown() {
        WorkoutRepository.resetForTesting(null)
        unmockkAll()
    }

    private fun createWorkoutData(id: Long): WorkoutData {
        return WorkoutData(
            id = id,
            finished = true,
            fileBaseName = "test_$id",
            workoutName = "Test Workout",
            sportId = 1L,
            sportName = "Cycling",
            bSportType = BSportType.BIKE,
            startTimeS = 1700000000L,
            formattedDate = "2026-09-29",
            formattedTime = "10:00",
            localDateTime = LocalDateTime.now(),
            equipmentName = null,
            equipmentId = 0L,
            commute = false,
            trainer = false,
            mapPolyline = "",
            encodedAltitudes = "",
            encodedDistances = "",
            uploadToStrava = 0,
            totalDistance = 10000.0,
            maxDisplacement = null,
            activeTimeSec = 1800L,
            totalTimeSec = 1800L,
            avgSpeedMps = 5.5,
            ascentMeters = 100L,
            descentMeters = 100L,
            minAltitude = null,
            maxAltitude = null,
            startLatLng = null,
            endLatLng = null,
            startLocationName = "Start",
            endLocationName = "Stop",
            description = null,
            goal = null,
            method = null,
            stravaSportName = null
        )
    }

    @Test
    fun testWorkoutPhotoEntryPropertiesAndAccessors() {
        val entry = WorkoutPhotoEntry(
            id = 10L,
            workoutId = 1L,
            filePath = "/data/user/0/com.atrainingtracker/files/workout_photos/1/photo_1.jpg",
            takenAtMs = 1716000000000L,
            latitude = 48.137,
            longitude = 11.576,
            caption = "Summit view"
        )

        assertEquals(10L, entry.id)
        assertEquals(1L, entry.workoutId)
        assertEquals("/data/user/0/com.atrainingtracker/files/workout_photos/1/photo_1.jpg", entry.filePath)
        assertEquals(1716000000000L, entry.takenAtMs)
        assertEquals(48.137, entry.latitude!!, 0.0001)
        assertEquals(11.576, entry.longitude!!, 0.0001)
        assertEquals("Summit view", entry.caption)

        val uri = entry.uri
        assertNotNull(uri)
        assertEquals("file", uri.scheme)

        val file = entry.file
        assertNotNull(file)
        assertEquals("photo_1.jpg", file.name)
    }

    @Test
    fun testWorkoutPhotoStripComposableExists() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.aftermath.photos.WorkoutPhotoStripKt")
        assertNotNull("WorkoutPhotoStripKt must exist", clazz)

        val method = clazz.declaredMethods.find {
            it.name.startsWith("WorkoutPhotoStrip") && Modifier.isPublic(it.modifiers)
        }
        assertNotNull("WorkoutPhotoStrip composable function must exist and be public", method)
    }

    @Test
    fun testWorkoutPhotoPreviewBottomSheetComposableExists() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.aftermath.photos.WorkoutPhotoPreviewBottomSheetKt")
        assertNotNull("WorkoutPhotoPreviewBottomSheetKt must exist", clazz)

        val method = clazz.declaredMethods.find {
            it.name.startsWith("WorkoutPhotoPreviewBottomSheet") && Modifier.isPublic(it.modifiers)
        }
        assertNotNull("WorkoutPhotoPreviewBottomSheet composable function must exist and be public", method)
    }

    @Test
    fun testPhotoLocationMarkersInWorkoutRepository() {
        runBlocking {
            val summariesDb = WorkoutSummariesDatabaseManager.getInstance(context)
            val workoutId = 99999L
            val tempFile = File(context.filesDir, "test_photo_marker.jpg")
            tempFile.writeText("test image data")

            val insertedId = summariesDb.insertPhoto(
                workoutId,
                tempFile.absolutePath,
                System.currentTimeMillis(),
                47.5,
                11.2,
                "Lake Panorama"
            )
            assertTrue(insertedId > 0)

            val repository = WorkoutRepository.getInstance(context as android.app.Application)
            val dummyData = createWorkoutData(workoutId)

            val markers = repository.getWorkoutMarkers(dummyData)
            val photoMarker = markers.find { it.title == "Lake Panorama" }

            assertNotNull("Photo location marker must be present in getWorkoutMarkers", photoMarker)
            assertEquals(47.5, photoMarker!!.position.latitude, 0.0001)
            assertEquals(11.2, photoMarker.position.longitude, 0.0001)
            assertEquals(R.drawable.ic_place, photoMarker.iconResId)

            // Cleanup
            summariesDb.deletePhotosForWorkout(workoutId)
            tempFile.delete()
        }
    }

    @Test
    fun testPhotoLocationMarkerExcludedWhenNullIslandCoordinates() {
        runBlocking {
            val summariesDb = WorkoutSummariesDatabaseManager.getInstance(context)
            val workoutId = 88888L
            val tempFile = File(context.filesDir, "test_null_island_photo.jpg")
            tempFile.writeText("test image data")

            val insertedId = summariesDb.insertPhoto(
                workoutId,
                tempFile.absolutePath,
                System.currentTimeMillis(),
                0.0,
                0.0,
                "Null Island Photo"
            )
            assertTrue(insertedId > 0)

            val repository = WorkoutRepository.getInstance(context as android.app.Application)
            val dummyData = createWorkoutData(workoutId)

            val markers = repository.getWorkoutMarkers(dummyData)
            val photoMarker = markers.find { it.title == "Null Island Photo" }

            org.junit.Assert.assertNull(
                "Photo marker with (0.0, 0.0) coordinates must be excluded from getWorkoutMarkers",
                photoMarker
            )

            // Cleanup
            summariesDb.deletePhotosForWorkout(workoutId)
            tempFile.delete()
        }
    }

    private fun findSourceFile(relativePath: String): File {
        val candidates = listOf(
            File(relativePath),
            File("app/$relativePath"),
            File("../$relativePath")
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("File not found in candidates: $relativePath")
    }

    @Test
    fun testWorkoutPhotoStrip_neutralStylingAndHeaderAddButtonContract() {
        val file = findSourceFile("src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/photos/WorkoutPhotoStrip.kt")
        val content = file.readText()

        // 1. Header camera icon uses onSurfaceVariant (no primary blue)
        assertTrue(
            "WorkoutPhotoStrip camera icon must use onSurfaceVariant",
            content.contains("imageVector = Icons.Default.PhotoCamera") &&
                    content.contains("tint = MaterialTheme.colorScheme.onSurfaceVariant")
        )

        // 2. Add photos icon uses onSurfaceVariant in compact header IconButton
        assertTrue(
            "AddPhotoAlternate icon must use onSurfaceVariant",
            content.contains("imageVector = Icons.Default.AddPhotoAlternate") &&
                    content.contains("tint = MaterialTheme.colorScheme.onSurfaceVariant")
        )

        // 3. Header contains compact IconButton for Add Photo action (ATT-5811)
        assertTrue(
            "Add photo action must be hosted in a compact header IconButton",
            content.contains("IconButton(") &&
                    content.contains("contentDescription = stringResource(R.string.workout_photos_add)")
        )

        // 4. LazyRow is rendered conditionally only when photos exist (ATT-5811)
        assertTrue(
            "LazyRow must be guarded by photos.isNotEmpty() to prevent empty tile bloat",
            content.contains("if (photos.isNotEmpty())")
        )

        // 5. Oversized add_photo_tile OutlinedCard is completely removed from LazyRow (ATT-5811)
        assertFalse(
            "add_photo_tile must be removed from LazyRow",
            content.contains("add_photo_tile")
        )
        assertFalse(
            "OutlinedCard must not be used for add photo tile",
            content.contains("OutlinedCard")
        )

        // 6. No primary blue color token in WorkoutPhotoStrip.kt
        assertFalse(
            "WorkoutPhotoStrip must not reference MaterialTheme.colorScheme.primary (REQ-UI-448.1)",
            content.contains("MaterialTheme.colorScheme.primary")
        )
    }

    @Test
    fun testWorkoutSummary_photoSectionDividerContract() {
        val file = findSourceFile("src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutSummary.kt")
        val content = file.readText()

        // Photos section block must contain HorizontalDivider
        val photosBlockIndex = content.indexOf("WorkoutSectionType.PHOTOS ->")
        assertTrue("WorkoutSummary must contain WorkoutSectionType.PHOTOS", photosBlockIndex != -1)

        val photosBlock = content.substring(photosBlockIndex, photosBlockIndex + 600)
        assertTrue(
            "WorkoutSectionType.PHOTOS block must contain HorizontalDivider preceding WorkoutPhotoStrip",
            photosBlock.contains("HorizontalDivider") && photosBlock.contains("WorkoutPhotoStrip")
        )
        assertTrue(
            "HorizontalDivider must precede WorkoutPhotoStrip",
            photosBlock.indexOf("HorizontalDivider") < photosBlock.indexOf("WorkoutPhotoStrip")
        )
    }

    @Test
    fun testWorkoutPhotoPreviewBottomSheet_stackedButtonsContract() {
        val file = findSourceFile("src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/photos/WorkoutPhotoPreviewBottomSheet.kt")
        val content = file.readText()

        // 1. Must not use horizontal actions row with weight(1f)
        assertFalse(
            "WorkoutPhotoPreviewBottomSheet must not use horizontal weight(1f) actions row",
            content.contains("actions = {")
        )

        // 2. Must render full-width buttons (Rule 73)
        assertTrue(
            "WorkoutPhotoPreviewBottomSheet must render fillMaxWidth buttons",
            content.contains("Button(") &&
                    content.contains("OutlinedButton(") &&
                    content.contains("Modifier.fillMaxWidth()")
        )

        // 3. Must use 8dp vertical spacing between action buttons
        assertTrue(
            "Must use 8.dp vertical arrangement for stacked action buttons",
            content.contains("Arrangement.spacedBy(8.dp)")
        )
    }
}
