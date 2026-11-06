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

import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.WorkoutCardSectionPreferences
import com.atrainingtracker.trainingtracker.WorkoutDetailPreferences
import com.atrainingtracker.trainingtracker.parsePhotoThumbnailSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [PhotoThumbnailSize] presets, fallback parsing, and default preferences
 * (REQ-UI-414.6 / TST-UI-377.7 / ATT-5474).
 */
class PhotoThumbnailSizeTest {

    @Test
    fun testPhotoThumbnailSize_presetsAndDimensions() {
        assertEquals(3, PhotoThumbnailSize.values().size)

        assertEquals(56, PhotoThumbnailSize.SMALL.dpSize)
        assertEquals(R.string.settings_workout_photos_size_small, PhotoThumbnailSize.SMALL.labelRes)

        assertEquals(76, PhotoThumbnailSize.MEDIUM.dpSize)
        assertEquals(R.string.settings_workout_photos_size_medium, PhotoThumbnailSize.MEDIUM.labelRes)

        assertEquals(104, PhotoThumbnailSize.LARGE.dpSize)
        assertEquals(R.string.settings_workout_photos_size_large, PhotoThumbnailSize.LARGE.labelRes)

        assertEquals(PhotoThumbnailSize.MEDIUM, PhotoThumbnailSize.DEFAULT)
    }

    @Test
    fun testParsePhotoThumbnailSize_validValuesAndFallback() {
        assertEquals(PhotoThumbnailSize.SMALL, parsePhotoThumbnailSize("SMALL"))
        assertEquals(PhotoThumbnailSize.MEDIUM, parsePhotoThumbnailSize("MEDIUM"))
        assertEquals(PhotoThumbnailSize.LARGE, parsePhotoThumbnailSize("LARGE"))

        // Null raw value -> fallback to default (MEDIUM)
        assertEquals(PhotoThumbnailSize.MEDIUM, parsePhotoThumbnailSize(null))
        // Null raw value with explicit fallback
        assertEquals(PhotoThumbnailSize.LARGE, parsePhotoThumbnailSize(null, PhotoThumbnailSize.LARGE))

        // Corrupted / unknown value -> fallback to default
        assertEquals(PhotoThumbnailSize.MEDIUM, parsePhotoThumbnailSize("UNKNOWN_SIZE"))
        assertEquals(PhotoThumbnailSize.SMALL, parsePhotoThumbnailSize("INVALID", PhotoThumbnailSize.SMALL))
    }

    @Test
    fun testDefaultPreferences_photoFields() {
        val cardPrefs = WorkoutCardSectionPreferences()
        assertTrue(cardPrefs.showPhotos)
        assertTrue(cardPrefs.showPhotoAddButton)
        assertEquals(PhotoThumbnailSize.MEDIUM, cardPrefs.photoThumbnailSize)

        val detailPrefs = WorkoutDetailPreferences()
        assertTrue(detailPrefs.showPhotos)
        assertTrue(detailPrefs.showPhotoAddButton)
        assertEquals(PhotoThumbnailSize.MEDIUM, detailPrefs.photoThumbnailSize)
    }
}
