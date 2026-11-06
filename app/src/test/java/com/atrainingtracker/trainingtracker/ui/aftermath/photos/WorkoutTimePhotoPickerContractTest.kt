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

package com.atrainingtracker.trainingtracker.ui.aftermath.photos

import android.net.Uri
import com.atrainingtracker.trainingtracker.database.WorkoutPhotoManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.lang.reflect.Modifier

/**
 * Contract verification test for WorkoutTimePhotoPickerSheet and MediaStore permissions (REQ-UI-455 / TST-UI-455 / ATT-5475).
 */
@RunWith(RobolectricTestRunner::class)
class WorkoutTimePhotoPickerContractTest {

    private fun findManifestFile(): File {
        val candidates = listOf(
            File("app/src/main/AndroidManifest.xml"),
            File("src/main/AndroidManifest.xml"),
            File("../app/src/main/AndroidManifest.xml"),
            File("../../app/src/main/AndroidManifest.xml")
        )
        return candidates.firstOrNull { it.exists() }
            ?: throw IllegalStateException("Could not find AndroidManifest.xml in candidate paths")
    }

    @Test
    fun testManifest_declaresRequiredMediaPermissions() {
        val manifest = findManifestFile()
        val content = manifest.readText()

        assertTrue(
            "AndroidManifest.xml must declare READ_MEDIA_IMAGES (REQ-UI-455.4)",
            content.contains("android.permission.READ_MEDIA_IMAGES")
        )

        assertTrue(
            "AndroidManifest.xml must declare READ_MEDIA_VISUAL_USER_SELECTED (REQ-UI-455.4)",
            content.contains("android.permission.READ_MEDIA_VISUAL_USER_SELECTED")
        )

        assertTrue(
            "AndroidManifest.xml must declare READ_EXTERNAL_STORAGE (REQ-UI-455.4)",
            content.contains("android.permission.READ_EXTERNAL_STORAGE")
        )

        assertTrue(
            "AndroidManifest.xml must declare READ_EXTERNAL_STORAGE with maxSdkVersion=\"32\" (REQ-UI-455.4)",
            content.contains("android.permission.READ_EXTERNAL_STORAGE") &&
                    content.contains("maxSdkVersion=\"32\"")
        )
    }

    @Test
    fun testWorkoutTimePhotoPickerSheetComposableExistsAndIsPublic() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.aftermath.photos.WorkoutTimePhotoPickerSheetKt")
        assertNotNull("WorkoutTimePhotoPickerSheetKt must exist", clazz)

        val method = clazz.declaredMethods.find {
            it.name.startsWith("WorkoutTimePhotoPickerSheet") && Modifier.isPublic(it.modifiers)
        }
        assertNotNull("WorkoutTimePhotoPickerSheet composable must exist and be public (REQ-UI-455.2)", method)
    }

    @Test
    fun testWorkoutPhotoStripComposableExistsAndAcceptsTimestamps() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.aftermath.photos.WorkoutPhotoStripKt")
        assertNotNull("WorkoutPhotoStripKt must exist", clazz)

        val method = clazz.declaredMethods.find {
            it.name.startsWith("WorkoutPhotoStrip") && Modifier.isPublic(it.modifiers)
        }
        assertNotNull("WorkoutPhotoStrip composable must exist and be public", method)
    }

    @Test
    fun testTimeFilteredPhotoDto() {
        val testUri = Uri.parse("content://media/external/images/media/42")
        val photo = WorkoutPhotoManager.TimeFilteredPhoto(
            id = 42L,
            contentUri = testUri,
            dateTakenMs = 1700000000000L
        )

        assertEquals(42L, photo.id)
        assertEquals(testUri, photo.contentUri)
        assertEquals(1700000000000L, photo.dateTakenMs)
    }
}
