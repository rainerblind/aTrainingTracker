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

import androidx.compose.ui.unit.dp
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

    @Test
    fun testPhotoItemDimensions_responsiveCalculation() {
        // TST-UI-377.1 / REQ-UI-414.6: Responsive item dimensions
        // LARGE: full available width, height (width * 0.65).coerceIn(180.dp, 260.dp)
        val large = calculatePhotoItemDimensions(PhotoThumbnailSize.LARGE, 360.dp)
        assertEquals(360f, large.width.value, 0.05f)
        assertEquals(234f, large.height.value, 0.05f)

        // LARGE tablet: 500.dp width -> clamped height to 260.dp
        val largeTablet = calculatePhotoItemDimensions(PhotoThumbnailSize.LARGE, 500.dp)
        assertEquals(500f, largeTablet.width.value, 0.05f)
        assertEquals(260f, largeTablet.height.value, 0.05f)

        // MEDIUM: 2 items side-by-side: (availableWidth - 8.dp) / 2, square aspect ratio
        val medium = calculatePhotoItemDimensions(PhotoThumbnailSize.MEDIUM, 360.dp)
        assertEquals(176f, medium.width.value, 0.05f)
        assertEquals(176f, medium.height.value, 0.05f)

        // SMALL: 3-4 items side-by-side: ((availableWidth - 24.dp) / 3.5f).coerceIn(60.dp, 90.dp)
        // 360.dp -> (360 - 24) / 3.5 = 96dp -> coerced to 90.dp
        val small = calculatePhotoItemDimensions(PhotoThumbnailSize.SMALL, 360.dp)
        assertEquals(90f, small.width.value, 0.05f)
        assertEquals(90f, small.height.value, 0.05f)

        // SMALL narrow: 280.dp -> (280 - 24) / 3.5 = 73.14.dp
        val smallNarrow = calculatePhotoItemDimensions(PhotoThumbnailSize.SMALL, 280.dp)
        val expectedSmallNarrow = (280.dp - 24.dp) / 3.5f
        assertEquals(expectedSmallNarrow.value, smallNarrow.width.value, 0.05f)
        assertEquals(smallNarrow.width.value, smallNarrow.height.value, 0.05f)
    }
}
