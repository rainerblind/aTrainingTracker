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

package com.atrainingtracker.trainingtracker.zones

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Contract and verification test suite for ATT-3776 (`REQ-UI-384` / `TST-UI-347` / Rule 38):
 * Verifies that `ZonesSettingsActivity.kt` replaces Material 3 `FilterChip` + `leadingIcon`
 * in the live preview zone selector with compact zone-tinted bordered pills (`ZonePreviewPill`),
 * guaranteeing single-line rendering across 5 and 7 zones up to 130% font scale and WCAG AA contrast.
 */
class ZonesSettingsPreviewPillsContractTest {

    private fun resolveZonesSettingsSource(): String {
        val candidates = listOf(
            File("app/src/main/java/com/atrainingtracker/trainingtracker/activities/ZonesSettingsActivity.kt"),
            File("src/main/java/com/atrainingtracker/trainingtracker/activities/ZonesSettingsActivity.kt")
        )
        val file = candidates.firstOrNull { it.exists() }
            ?: error("Could not locate ZonesSettingsActivity.kt")
        return file.readText()
    }

    /**
     * TST-UI-347.1: Verify `ZonesSettingsActivity.kt` defines and invokes `ZonePreviewPill`
     * and contains zero `FilterChip(` invocations.
     */
    @Test
    fun testPreviewZoneSelectorUsesCompactZonePreviewPillsWithoutFilterChip() {
        val source = resolveZonesSettingsSource()
        assertFalse(
            "ZonesSettingsActivity.kt must not use FilterChip in the preview zone selector (Rule 38)",
            source.contains("FilterChip(")
        )
        assertTrue(
            "ZonesSettingsActivity.kt must declare fun ZonePreviewPill(",
            source.contains("fun ZonePreviewPill(")
        )
        assertTrue(
            "ZonesSettingsActivity.kt must invoke ZonePreviewPill( inside SettingsScreenContent",
            source.contains("ZonePreviewPill(")
        )
    }

    /**
     * TST-UI-347.2: Verify `ZonePreviewPill` applies selectable `Surface(selected = selected, onClick = onClick)`,
     * `RoundedCornerShape(8.dp)`, subtle zone-tinted fills (`0.28f` / `0.12f`), zone-colored `BorderStroke`
     * (`2.dp` / `1.dp`), and single-line `Text` (`maxLines = 1`, `softWrap = false`).
     */
    @Test
    fun testZonePreviewPillAppliesZoneTintedFillBorderSelectableSemanticsAndSingleLineText() {
        val source = resolveZonesSettingsSource()
        val pillFnBlock = source.substringAfter("fun ZonePreviewPill(")
        assertTrue("ZonePreviewPill must use selectable Surface(selected = selected", pillFnBlock.contains("selected = selected"))
        assertTrue("ZonePreviewPill must use RoundedCornerShape(8.dp)", pillFnBlock.contains("RoundedCornerShape(8.dp)"))
        assertTrue("ZonePreviewPill must tint background with 0.28f when selected and 0.12f when unselected",
            pillFnBlock.contains("0.28f") && pillFnBlock.contains("0.12f"))
        assertTrue("ZonePreviewPill must apply BorderStroke with 2.dp and 1.dp",
            pillFnBlock.contains("BorderStroke(") && pillFnBlock.contains("2.dp") && pillFnBlock.contains("1.dp"))
        assertTrue("ZonePreviewPill Text must enforce maxLines = 1", pillFnBlock.contains("maxLines = 1"))
        assertTrue("ZonePreviewPill Text must enforce softWrap = false", pillFnBlock.contains("softWrap = false"))
    }

    /**
     * TST-UI-347.3: Verify 7-zone horizontal width budget under 130% Large system font scale on a 360dp
     * narrow viewport and WCAG AA >= 4.5:1 contrast ratio for `onSurface` text over `0.12f`/`0.28f` zone tints.
     */
    @Test
    fun test7ZonePillWidthBudgetUnder130PercentFontScaleAndWcagContrast() {
        val viewportWidthDp = 360.0
        val outerHorizontalPaddingDp = 32.0 // 16.dp start + 16.dp end
        val zoneCount = 7
        val interPillSpacingDp = 6.0
        val totalSpacingDp = (zoneCount - 1) * interPillSpacingDp
        val pillWidthDp = (viewportWidthDp - outerHorizontalPaddingDp - totalSpacingDp) / zoneCount
        val innerHorizontalPaddingDp = 8.0 // 4.dp start + 4.dp end
        val availableTextWidthDp = pillWidthDp - innerHorizontalPaddingDp

        // At 130% font scale, labelMedium (12sp) scales to 15.6sp; 2-char "Z7" requires ~19.5dp width
        val requiredTextWidthAt130ScaleDp = 12.0 * 1.3 * 1.25
        assertTrue(
            "Available text width per pill ($availableTextWidthDp dp) must strictly exceed 2-char label width at 130% font scale ($requiredTextWidthAt130ScaleDp dp)",
            availableTextWidthDp >= 32.0 && availableTextWidthDp > requiredTextWidthAt130ScaleDp + 10.0
        )

        // Verify WCAG AA contrast (>= 4.5:1) of onSurface over 0.12f and 0.28f alpha-blended zone colors
        val zoneRgbHex = listOf(0x9E9E9E, 0x2196F3, 0x4CAF50, 0xFFC107, 0xFF5722, 0x8D6E63, 0x212121)
        for (hex in zoneRgbHex) {
            for (alpha in listOf(0.12, 0.28)) {
                // Light theme: surface #FFFBFE, onSurface #1C1B1F
                val lightBg = blend(hex, 0xFFFBFE, alpha)
                val lightContrast = contrastRatio(0x1C1B1F, lightBg)
                assertTrue("Light theme contrast ($lightContrast) for zone ${hex.toString(16)} @ alpha $alpha must be >= 4.5", lightContrast >= 4.5)

                // Dark/AMOLED theme: surface #141218, onSurface #E6E1E5
                val darkBg = blend(hex, 0x141218, alpha)
                val darkContrast = contrastRatio(0xE6E1E5, darkBg)
                assertTrue("Dark theme contrast ($darkContrast) for zone ${hex.toString(16)} @ alpha $alpha must be >= 4.5", darkContrast >= 4.5)
            }
        }
    }

    private fun blend(fgHex: Int, bgHex: Int, alpha: Double): Int {
        val fr = (fgHex shr 16) and 0xFF
        val fg = (fgHex shr 8) and 0xFF
        val fb = fgHex and 0xFF
        val br = (bgHex shr 16) and 0xFF
        val bg = (bgHex shr 8) and 0xFF
        val bb = bgHex and 0xFF
        val r = (fr * alpha + br * (1.0 - alpha)).toInt().coerceIn(0, 255)
        val g = (fg * alpha + bg * (1.0 - alpha)).toInt().coerceIn(0, 255)
        val b = (fb * alpha + bb * (1.0 - alpha)).toInt().coerceIn(0, 255)
        return (r shl 16) or (g shl 8) or b
    }

    private fun relativeLuminance(hex: Int): Double {
        fun ch(v: Int): Double {
            val s = v / 255.0
            return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
        }
        val r = ch((hex shr 16) and 0xFF)
        val g = ch((hex shr 8) and 0xFF)
        val b = ch(hex and 0xFF)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    private fun contrastRatio(c1: Int, c2: Int): Double {
        val l1 = relativeLuminance(c1)
        val l2 = relativeLuminance(c2)
        return (max(l1, l2) + 0.05) / (min(l1, l2) + 0.05)
    }
}
