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

package com.atrainingtracker.trainingtracker.ui.map

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Architectural contract tests for [ElevationProfile] verifying:
 * 1. Complete elimination of the accented ridge stroke overdraw on the elevation profile curve,
 *    preserving intrinsic slope gradient colors (REQ-UI-299 / ATT-2510).
 * 2. Dedicated category-colored horizontal climb span bars along the X-axis baseline.
 * 3. Proper distance coordinate bounds mapping and clipping.
 * 4. Full inclusion of all climbs, including [com.atrainingtracker.trainingtracker.climbs.ClimbCategory.UNCATEGORIZED] climbs (REQ-UI-307).
 * 5. Retention of summit category pins and badges.
 * (TST-UI-259, TST-UI-267)
 */
class ElevationProfileClimbSpanContractTest {

    private val projectRoot: File by lazy {
        var dir = File(System.getProperty("user.dir") ?: ".")
        while (!File(dir, "app").exists() && dir.parentFile != null) {
            dir = dir.parentFile!!
        }
        dir
    }

    private val elevationProfileFile: File by lazy {
        File(projectRoot, "app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/ElevationProfile.kt")
    }

    @Test
    fun testElevationProfile_eliminatesAccentRidgeStrokeOverdraw() {
        assertTrue("ElevationProfile.kt must exist", elevationProfileFile.exists())
        val content = elevationProfileFile.readText()

        // 1. Must NOT contain the old accented ridge stroke comment or overdraw loop
        assertFalse(
            "ElevationProfile must not draw accented ridge stroke over elevation curve (REQ-UI-299)",
            content.contains("// Render accented ridge stroke for recognized climbs")
        )
        assertFalse(
            "ElevationProfile must not draw thick ridge lines over (px1, py1) to (px2, py2)",
            content.contains("strokeWidth = 3.5.dp.toPx()")
        )

        // 2. Slope gradient curve rendering must remain intact
        assertTrue(
            "ElevationProfile must render slope segments with seg.color",
            content.contains("drawLine(seg.color, Offset(x1, y1), Offset(x2, y2), 2.dp.toPx())")
        )
    }

    @Test
    fun testElevationProfile_rendersClimbSpansAlongXAxisBaseline() {
        assertTrue("ElevationProfile.kt must exist", elevationProfileFile.exists())
        val content = elevationProfileFile.readText()

        // 1. Must contain REQ-UI-299 / REQ-UI-307 climb span comment
        assertTrue(
            "ElevationProfile must contain REQ-UI-299 / REQ-UI-307 X-axis climb span reference",
            content.contains("Render horizontal climb span indicators along the X-axis baseline (REQ-UI-299, REQ-UI-307)")
        )

        // 2. Baseline Y coordinate must be inset to avoid bottom boundary clipping
        assertTrue(
            "ElevationProfile must inset baselineY by 2.dp.toPx()",
            content.contains("val baselineY = height - 2.dp.toPx()")
        )

        // 3. Must include all recognized climbs including UNCATEGORIZED (REQ-UI-307)
        assertFalse(
            "ElevationProfile must not filter out UNCATEGORIZED climbs (REQ-UI-307)",
            content.contains("climbs.filter { it.category != ClimbCategory.UNCATEGORIZED }")
        )
        assertTrue(
            "ElevationProfile must iterate over all climbs on baseline",
            content.contains("climbs.forEach { climb ->")
        )

        // 4. Must map start and end distance via ElevationProfileZoomMath.distanceToCanvasX
        assertTrue(
            "ElevationProfile must map start distance to canvas X",
            content.contains("ElevationProfileZoomMath.distanceToCanvasX(startDist, currentStartDist, visibleSpan, width)")
        )
        assertTrue(
            "ElevationProfile must map end distance to canvas X",
            content.contains("ElevationProfileZoomMath.distanceToCanvasX(endDist, currentStartDist, visibleSpan, width)")
        )

        // 5. Must clamp coordinates to [0f, width]
        assertTrue(
            "ElevationProfile must coerce start coordinate within canvas width",
            content.contains("val x1 = rawX1.coerceIn(0f, width)")
        )
        assertTrue(
            "ElevationProfile must coerce end coordinate within canvas width",
            content.contains("val x2 = rawX2.coerceIn(0f, width)")
        )

        // 6. Must draw horizontal line with category color, 4.dp.toPx() stroke, and StrokeCap.Round
        assertTrue(
            "ElevationProfile must draw climb span line with baselineY",
            content.contains("start = Offset(x1, baselineY)") && content.contains("end = Offset(x2, baselineY)")
        )
        assertTrue(
            "ElevationProfile must use 4.dp.toPx() stroke width",
            content.contains("strokeWidth = 4.dp.toPx()")
        )
        assertTrue(
            "ElevationProfile must use StrokeCap.Round",
            content.contains("cap = StrokeCap.Round")
        )
    }

    @Test
    fun testElevationProfile_preservesSummitBadges() {
        assertTrue("ElevationProfile.kt must exist", elevationProfileFile.exists())
        val content = elevationProfileFile.readText()

        // Summit category badges must remain intact
        assertTrue(
            "ElevationProfile must render summit category badges",
            content.contains("// Render Summit Category Badges (REQ-UI-274)")
        )
        assertTrue(
            "ElevationProfile must draw summit pin stem",
            content.contains("canvas.nativeCanvas.drawLine(summitX, summitY, summitX, pillBottom, stemPaint)")
        )
        assertTrue(
            "ElevationProfile must draw category pill",
            content.contains("canvas.nativeCanvas.drawRoundRect")
        )
    }
}
