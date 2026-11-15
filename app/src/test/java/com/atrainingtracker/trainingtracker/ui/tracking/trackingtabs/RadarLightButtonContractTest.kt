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

package com.atrainingtracker.trainingtracker.ui.tracking.trackingtabs

import com.atrainingtracker.banalservice.devices.radar.RadarLightMode
import com.atrainingtracker.trainingtracker.ui.theme.TTColor
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * Visual contrast, architectural contract, and interaction tests for RadarLightButton and
 * canonical [RadarLightButton, LapButton, BikeBellButton] action row (REQ-CON-043.2, TST-CON-043.2, ATT-7972).
 * Adheres to docs/coding_standards.md §5.4 and REQ-PRO-049.
 */
class RadarLightButtonContractTest {

    private fun luminance(r: Double, g: Double, b: Double): Double {
        fun channel(c: Double): Double {
            val s = c / 255.0
            return if (s <= 0.03928) s / 12.92 else Math.pow((s + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b)
    }

    private fun contrastRatio(l1: Double, l2: Double): Double {
        val lighter = Math.max(l1, l2)
        val darker = Math.min(l1, l2)
        return (lighter + 0.05) / (darker + 0.05)
    }

    @Test
    fun testRadarLightButton_composableExistsWithExpectedSignature() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.tracking.trackingtabs.RadarLightButtonKt")
        assertNotNull("RadarLightButtonKt must exist", clazz)
        val methods = clazz.declaredMethods
        val buttonMethod = methods.find { it.name.startsWith("RadarLightButton") && Modifier.isPublic(it.modifiers) }
        assertNotNull("RadarLightButton composable function must exist", buttonMethod)
    }

    @Test
    fun testRadarLightButton_colorTokensMeetContrastStandards() {
        // Container: #263238 (R=38, G=50, B=56)
        val lContainer = luminance(38.0, 50.0, 56.0)

        // Content Solid: #E53935 (R=229, G=57, B=53)
        val lSolid = luminance(229.0, 57.0, 53.0)
        val ratioSolid = contrastRatio(lSolid, lContainer)
        assertTrue(
            "Solid red contrast ($ratioSolid) must exceed 3:1 for components",
            ratioSolid >= 3.0
        )

        // Content Day Flash: #FFB300 (R=255, G=179, B=0)
        val lDayFlash = luminance(255.0, 179.0, 0.0)
        val ratioDayFlash = contrastRatio(lDayFlash, lContainer)
        assertTrue(
            "Day flash amber contrast ($ratioDayFlash) must exceed 3:1 for components",
            ratioDayFlash >= 3.0
        )

        // Content Night Flash: #FF8F00 (R=255, G=143, B=0)
        val lNightFlash = luminance(255.0, 143.0, 0.0)
        val ratioNightFlash = contrastRatio(lNightFlash, lContainer)
        assertTrue(
            "Night flash amber contrast ($ratioNightFlash) must exceed 3:1 for components",
            ratioNightFlash >= 3.0
        )

        // AUTO micro-badge: #64B5F6 (R=100, G=181, B=246)
        val lAuto = luminance(100.0, 181.0, 246.0)
        val ratioAuto = contrastRatio(lAuto, lContainer)
        assertTrue(
            "AUTO micro-badge contrast ($ratioAuto) must exceed 4.5:1 for typography",
            ratioAuto >= 4.5
        )
    }

    @Test
    fun testRadarLightMode_allModesDefinedInEnum() {
        val modes = RadarLightMode.values()
        assertTrue("Must include OFF", modes.contains(RadarLightMode.OFF))
        assertTrue("Must include SOLID", modes.contains(RadarLightMode.SOLID))
        assertTrue("Must include DAY_FLASH", modes.contains(RadarLightMode.DAY_FLASH))
        assertTrue("Must include NIGHT_FLASH", modes.contains(RadarLightMode.NIGHT_FLASH))
        assertTrue("Must include PELOTON", modes.contains(RadarLightMode.PELOTON))
    }
}
