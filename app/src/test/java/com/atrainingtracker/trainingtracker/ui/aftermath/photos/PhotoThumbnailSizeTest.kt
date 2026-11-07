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

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.WorkoutCardSectionPreferences
import com.atrainingtracker.trainingtracker.WorkoutDetailPreferences
import com.atrainingtracker.trainingtracker.parsePhotoThumbnailSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

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

    /**
     * TST-UI-457.1: Dynamic decode max dimension calculation across sizes and screen densities (REQ-UI-457.1 / REQ-UI-457.4).
     */
    @Test
    fun testCalculatePhotoDecodeMaxDim_densityScalingAndClamping() {
        val density1x = Density(1.0f)
        val density2x = Density(2.0f)
        val density275x = Density(2.75f)
        val density3x = Density(3.0f)

        // LARGE at 360dp width:
        val large = calculatePhotoItemDimensions(PhotoThumbnailSize.LARGE, 360.dp) // width 360dp, height 234dp
        // 1.0x -> 360px
        assertEquals(360, calculatePhotoDecodeMaxDim(large, density1x))
        // 2.0x -> 720px
        assertEquals(720, calculatePhotoDecodeMaxDim(large, density2x))
        // 2.75x -> 990px
        assertEquals(990, calculatePhotoDecodeMaxDim(large, density275x))
        // 3.0x -> 1080px
        assertEquals(1080, calculatePhotoDecodeMaxDim(large, density3x))

        // LARGE on large tablet (500dp width) at 3.0x -> 1500px -> clamped to upper bound 1200px (OOM protection)
        val largeTablet = calculatePhotoItemDimensions(PhotoThumbnailSize.LARGE, 500.dp)
        assertEquals(1200, calculatePhotoDecodeMaxDim(largeTablet, density3x))

        // MEDIUM at 360dp width (176dp):
        val medium = calculatePhotoItemDimensions(PhotoThumbnailSize.MEDIUM, 360.dp)
        // 1.0x -> 176px -> clamped to lower bound 256px
        assertEquals(256, calculatePhotoDecodeMaxDim(medium, density1x))
        // 2.0x -> 352px
        assertEquals(352, calculatePhotoDecodeMaxDim(medium, density2x))
        // 2.75x -> 484px
        assertEquals(484, calculatePhotoDecodeMaxDim(medium, density275x))
        // 3.0x -> 528px
        assertEquals(528, calculatePhotoDecodeMaxDim(medium, density3x))

        // SMALL at 360dp width (90dp):
        val small = calculatePhotoItemDimensions(PhotoThumbnailSize.SMALL, 360.dp)
        // 1.0x -> 90px -> clamped to 256px
        assertEquals(256, calculatePhotoDecodeMaxDim(small, density1x))
        // 2.0x -> 180px -> clamped to 256px
        assertEquals(256, calculatePhotoDecodeMaxDim(small, density2x))
        // 2.75x -> 248px -> clamped to 256px
        assertEquals(256, calculatePhotoDecodeMaxDim(small, density275x))
        // 3.0x -> 270px
        assertEquals(270, calculatePhotoDecodeMaxDim(small, density3x))
    }

    /**
     * TST-UI-457.2: PhotoThumbnailCard structural contract for dynamic targetMaxDim and LaunchedEffect keying (REQ-UI-457.2).
     */
    @Test
    fun testPhotoThumbnailCard_contractLaunchedEffectKeying() {
        val candidates = listOf(
            File("app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/photos/WorkoutPhotoStrip.kt"),
            File("src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/photos/WorkoutPhotoStrip.kt")
        )
        val file = candidates.firstOrNull { it.exists() }
            ?: error("WorkoutPhotoStrip.kt not found")
        val content = file.readText()

        // 1. Computes targetMaxDim via calculatePhotoDecodeMaxDim
        assertTrue(
            "PhotoThumbnailCard must compute targetMaxDim",
            content.contains("calculatePhotoDecodeMaxDim(dimensions, density)")
        )

        // 2. remember state keys on photo.filePath and targetMaxDim
        assertTrue(
            "bitmap remember must key on (photo.filePath, targetMaxDim)",
            content.contains("remember(photo.filePath, targetMaxDim)")
        )

        // 3. LaunchedEffect keys on (photo.filePath, targetMaxDim)
        assertTrue(
            "LaunchedEffect must key on (photo.filePath, targetMaxDim)",
            content.contains("LaunchedEffect(photo.filePath, targetMaxDim)")
        )

        // 4. Passes maxDim = targetMaxDim to decodeSampledBitmap
        assertTrue(
            "decodeSampledBitmap must pass maxDim = targetMaxDim",
            content.contains("maxDim = targetMaxDim")
        )
    }
}
