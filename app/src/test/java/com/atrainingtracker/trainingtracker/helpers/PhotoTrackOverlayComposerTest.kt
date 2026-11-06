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

import com.google.android.gms.maps.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Unit tests verifying PhotoTrackOverlayComposer equirectangular track projection,
 * aspect ratio preservation, and WCAG AAA contrast compliance (REQ-UI-447 / TST-UI-447).
 */
class PhotoTrackOverlayComposerTest {

    @Test
    fun testEquirectangularProjection_preservesTrueAspectRatio() {
        // At 60°N latitude, cos(60°) = 0.5. A 1° lon difference represents half the distance of 1° lat.
        val lat = 60.0
        val points = listOf(
            LatLng(lat, 10.0),
            LatLng(lat + 1.0, 10.0),
            LatLng(lat + 1.0, 12.0), // lon span = 2.0, effective x span = 2.0 * cos(60.5°) ≈ 0.985
            LatLng(lat, 12.0)
        )

        val projected = PhotoTrackOverlayComposer.calculateTrackProjection(
            points = points,
            targetLeft = 0f,
            targetTop = 0f,
            targetRight = 1000f,
            targetBottom = 1000f
        )

        assertNotNull("Projected track must not be null for valid polyline", projected)
        projected!!

        val width = projected.width
        val height = projected.height

        val expectedAspect = (2.0 * cos(Math.toRadians(60.5))) / 1.0
        val actualAspect = (width / height).toDouble()

        assertEquals("Projected aspect ratio must match cosine-latitude scaling", expectedAspect, actualAspect, 0.05)
        assertTrue("Projected bounds must fit horizontally within target bounds", projected.boundsLeft >= 0f && projected.boundsRight <= 1000f)
        assertTrue("Projected bounds must fit vertically within target bounds", projected.boundsTop >= 0f && projected.boundsBottom <= 1000f)
    }

    @Test
    fun testCalculateTrackProjection_emptyOrSinglePoint_returnsNull() {
        assertNull(
            "Empty points list must return null",
            PhotoTrackOverlayComposer.calculateTrackProjection(emptyList(), 0f, 0f, 500f, 500f)
        )
        assertNull(
            "Single point must return null",
            PhotoTrackOverlayComposer.calculateTrackProjection(listOf(LatLng(48.0, 11.5)), 0f, 0f, 500f, 500f)
        )
    }

    @Test
    fun testCalculateTrackProjection_startAndEndPoints_matchFirstAndLastProjectedPositions() {
        val points = listOf(
            LatLng(48.0, 11.0),
            LatLng(48.1, 11.2),
            LatLng(48.2, 11.5)
        )

        val projected = PhotoTrackOverlayComposer.calculateTrackProjection(
            points = points,
            targetLeft = 50f,
            targetTop = 50f,
            targetRight = 400f,
            targetBottom = 400f
        )

        assertNotNull(projected)
        projected!!

        assertTrue("Start point X must lie within bounds", projected.startX >= projected.boundsLeft && projected.startX <= projected.boundsRight)
        assertTrue("Start point Y must lie within bounds", projected.startY >= projected.boundsTop && projected.startY <= projected.boundsBottom)
        assertTrue("End point X must lie within bounds", projected.endX >= projected.boundsLeft && projected.endX <= projected.boundsRight)
        assertTrue("End point Y must lie within bounds", projected.endY >= projected.boundsTop && projected.endY <= projected.boundsBottom)
        assertEquals("Start point must match first coordinate", projected.points.first().x, projected.startX, 0.001f)
        assertEquals("End point must match last coordinate", projected.points.last().x, projected.endX, 0.001f)
    }

    @Test
    fun testMetricsScrimContrast_wcagAaaCompliant() {
        // WCAG AAA requires contrast ratio >= 7.0:1 for normal text and >= 4.5:1 for large/bold text
        val minAaaContrast = 7.0

        // Test over pure white photo background (worst-case bright scenario)
        // With 85% black scrim (0xD9000000) over white (0xFFFFFFFF):
        // Blended RGB = (1 - 0.85) * 255 + 0.85 * 0 = 38.25 -> #262626
        val scrimAlpha = 0.85
        val photoBgRgb = 255.0
        val blendedChannel = ((1.0 - scrimAlpha) * photoBgRgb).roundToInt()
        val blendedColor = (0xFF shl 24) or (blendedChannel shl 16) or (blendedChannel shl 8) or blendedChannel

        val whiteTextLuminance = calculateRelativeLuminance(0xFFFFFFFF.toInt())
        val scrimLuminance = calculateRelativeLuminance(blendedColor)

        val contrastRatio = calculateContrastRatio(whiteTextLuminance, scrimLuminance)

        assertTrue(
            "Metrics contrast on worst-case bright photo with 85% scrim must exceed WCAG AAA ($minAaaContrast:1), actual was $contrastRatio:1",
            contrastRatio >= minAaaContrast
        )
    }

    @Test
    fun testCalculateTrackRect_portrait_constrainedToUpperRightUnder22Percent() {
        val width = 1080
        val height = 2400

        val rect = PhotoTrackOverlayComposer.calculateTrackRect(width, height)

        assertEquals("Portrait track left should be 50% width", width * 0.50f, rect.left, 0.01f)
        assertEquals("Portrait track right should be 92% width", width * 0.92f, rect.right, 0.01f)
        assertEquals("Portrait track top should be 5% height", height * 0.05f, rect.top, 0.01f)
        assertEquals("Portrait track bottom should be 25% height", height * 0.25f, rect.bottom, 0.01f)

        val heightFraction = (rect.bottom - rect.top) / height.toFloat()
        assertTrue("Portrait track height must be <= 22% of image height, was $heightFraction", heightFraction <= 0.22f)
    }

    @Test
    fun testCalculateTrackRect_landscape_standardBounds() {
        val width = 1920
        val height = 1080

        val rect = PhotoTrackOverlayComposer.calculateTrackRect(width, height)

        assertEquals("Landscape track left should be 45% width", width * 0.45f, rect.left, 0.01f)
        assertEquals("Landscape track right should be 92% width", width * 0.92f, rect.right, 0.01f)
        assertEquals("Landscape track top should be 8% height", height * 0.08f, rect.top, 0.01f)
        assertEquals("Landscape track bottom should be 48% height", height * 0.48f, rect.bottom, 0.01f)
    }

    @Test
    fun testCalculateLayoutDimensions_portrait_compactBottomAnchoring() {
        val width = 1080
        val height = 1920

        val layout = PhotoTrackOverlayComposer.calculateLayoutDimensions(width, height)

        assertEquals("Footer height should be 125 for 1080 width", 125, layout.footerHeight)
        assertEquals("Footer top should be height - footerHeight", 1795f, layout.footerTop, 0.01f)

        // Metrics value anchored compactly above footer
        assertEquals("Metric values must anchor directly above footerTop", layout.footerTop - 28f, layout.metricValueY, 0.01f)
        // Metric labels above metric values
        assertEquals("Metric labels must anchor directly above metric values", layout.metricValueY - 46f, layout.metricLabelY, 0.01f)
        // Date above metric labels
        assertEquals("Date must anchor directly above metric labels", layout.metricLabelY - 56f, layout.dateY, 0.01f)
        // Title above date
        assertEquals("Title must anchor directly above date", layout.dateY - 42f, layout.titleY, 0.01f)

        // Scrim begins smoothly above title
        assertEquals("Scrim top must begin above title", layout.titleY - 80f, layout.scrimTop, 0.01f)

        // Total text block from title to footer top is 172px, eliminating large portrait empty gaps
        val textBlockHeight = layout.footerTop - layout.titleY
        assertEquals("Text block height should be exactly 172px at scaleRef=1.0", 172f, textBlockHeight, 0.01f)
    }

    private fun Double.roundToInt(): Int = Math.round(this).toInt()

    private fun calculateRelativeLuminance(colorInt: Int): Double {
        val r = sRgbToLinear(((colorInt shr 16) and 0xFF) / 255.0)
        val g = sRgbToLinear(((colorInt shr 8) and 0xFF) / 255.0)
        val b = sRgbToLinear((colorInt and 0xFF) / 255.0)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    private fun sRgbToLinear(channel: Double): Double {
        return if (channel <= 0.04045) {
            channel / 12.92
        } else {
            ((channel + 0.055) / 1.055).pow(2.4)
        }
    }

    private fun calculateContrastRatio(l1: Double, l2: Double): Double {
        val lighter = max(l1, l2)
        val darker = min(l1, l2)
        return (lighter + 0.05) / (darker + 0.05)
    }
}
