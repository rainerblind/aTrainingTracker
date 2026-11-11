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

package com.atrainingtracker.trainingtracker.helpers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Unit & Contract tests verifying ShareUtils branding footer, supporter emblem rendering,
 * and dynamic text auto-scaling without text pills (REQ-UI-447.4, REQ-UI-445.5 / TST-UI-447.3).
 */
class ShareUtilsTest {

    @Test
    fun testCalculateAvailableWatermarkWidth_withoutEmblem() {
        val totalWidth = 1080
        val available = calculateAvailableWatermarkWidth(
            totalWidth = totalWidth,
            margin = 24f,
            logoSize = 80,
            hasEmblem = false
        )
        // (1080 - 24) - (24 + 80 + 20) = 1056 - 124 = 932
        assertEquals(932f, available, 0.01f)
    }

    @Test
    fun testCalculateAvailableWatermarkWidth_withEmblem() {
        val totalWidth = 1080
        val available = calculateAvailableWatermarkWidth(
            totalWidth = totalWidth,
            margin = 24f,
            logoSize = 80,
            hasEmblem = true,
            emblemSize = 56
        )
        // (1080 - 24 - 56 - 16) - (24 + 80 + 20) = 984 - 124 = 860
        assertEquals(860f, available, 0.01f)
    }

    @Test
    fun testCalculateWatermarkScaledTextSize_fitsWithinWidth_retainsBaseSize() {
        val scaled = calculateWatermarkScaledTextSize(
            measuredWidth = 500f,
            availableWidth = 860f,
            baseTextSize = 38f
        )
        assertEquals(38f, scaled, 0.01f)
    }

    @Test
    fun testCalculateWatermarkScaledTextSize_exceedsWidth_scalesDownProportionally() {
        val scaled = calculateWatermarkScaledTextSize(
            measuredWidth = 1000f,
            availableWidth = 500f,
            baseTextSize = 38f
        )
        // 38 * (500 / 1000) = 19
        assertEquals(19f, scaled, 0.01f)
    }

    @Test
    fun testCalculateWatermarkScaledTextSize_veryNarrow_clampsToMinimumSize() {
        val scaled = calculateWatermarkScaledTextSize(
            measuredWidth = 1000f,
            availableWidth = 100f,
            baseTextSize = 38f,
            minTextSize = 18f
        )
        assertEquals(18f, scaled, 0.01f)
    }

    @Test
    fun testShareUtils_contractSupporterEmblemOnly_noTextPills() {
        val file = File("src/main/java/com/atrainingtracker/trainingtracker/helpers/ShareUtils.kt")
        val altFile = File("app/src/main/java/com/atrainingtracker/trainingtracker/helpers/ShareUtils.kt")
        val targetFile = if (file.exists()) file else altFile
        assertTrue("ShareUtils.kt must exist on disk", targetFile.exists())

        val source = targetFile.readText()

        // Verify absence of obsolete text pill badges (REQ-UI-447.4)
        assertFalse("Must not contain '◆ SPONSOR' text pill badge", source.contains("◆ SPONSOR"))
        assertFalse("Must not contain '★ GOLD PRO' text pill badge", source.contains("★ GOLD PRO"))
        assertFalse("Must not contain '★ SILVER PRO' text pill badge", source.contains("★ SILVER PRO"))
        assertFalse("Must not contain '★ BRONZE' text pill badge", source.contains("★ BRONZE"))
        assertFalse("Must not contain '★ PIONEER' text pill badge", source.contains("★ PIONEER"))

        // Verify usage of LauncherIconVariant and vector emblem
        assertTrue("Must query LauncherIconVariant for tier-matched resources", source.contains("LauncherIconVariant.fromTier"))
        assertTrue("Must check and draw variant.emblemRes", source.contains("variant.emblemRes"))
        assertTrue("Must load tier-matched launcher icon variant.iconRes", source.contains("variant.iconRes"))

        // Verify auto-scaling text helper integration
        assertTrue("Must call calculateAvailableWatermarkWidth", source.contains("calculateAvailableWatermarkWidth"))
        assertTrue("Must call calculateWatermarkScaledTextSize", source.contains("calculateWatermarkScaledTextSize"))

        // Verify shareText support in saveAndShare and combineWorkoutAndShare (REQ-RAD-007 / ATT-5122)
        assertTrue("Must support shareText parameter in saveAndShare", source.contains("shareText: String? = null"))
        assertTrue("Must populate Intent.EXTRA_TEXT when shareText provided", source.contains("Intent.EXTRA_TEXT, shareText"))
    }
}
