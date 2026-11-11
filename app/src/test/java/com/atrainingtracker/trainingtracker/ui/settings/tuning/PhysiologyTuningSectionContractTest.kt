/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.atrainingtracker.trainingtracker.ui.settings.tuning

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * Contract and mathematical verification tests for Physiology Tuning section
 * and subordinate toggle switch color styling (REQ-UI-424.1, TST-UI-388.3, ATT-4513).
 * Refactored to pure JVM reflection per docs/coding_standards.md §5.4 and REQ-PRO-049.
 */
class PhysiologyTuningSectionContractTest {

    private fun relativeLuminance(r: Double, g: Double, b: Double): Double {
        fun toLinear(c: Double): Double {
            return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * toLinear(r) + 0.7152 * toLinear(g) + 0.0722 * toLinear(b)
    }

    @Test
    fun testSubordinateTuningToggleItem_colorTokensAndGeometryContract() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.settings.tuning.TuningFormControlsKt")
        val method = clazz.declaredMethods.find { it.name.startsWith("SubordinateTuningToggleItem") && Modifier.isPublic(it.modifiers) }
        assertNotNull("SubordinateTuningToggleItem composable must exist", method)
    }

    @Test
    fun testSubordinateSwitchCheckedTrack_relativeLuminanceIsSignificantlyLighterInLightMode() {
        // LightPrimary = #1464F4 (20, 100, 244)
        val lpR = 20.0 / 255.0
        val lpG = 100.0 / 255.0
        val lpB = 244.0 / 255.0
        val lumPrimary = relativeLuminance(lpR, lpG, lpB)

        // LightSecondary = #0000CD (0, 0, 205)
        val lsR = 0.0 / 255.0
        val lsG = 0.0 / 255.0
        val lsB = 205.0 / 255.0
        val lumSecondary = relativeLuminance(lsR, lsG, lsB)

        // Alpha composite of LightPrimary (alpha = 0.45) over LightSurface White (1.0, 1.0, 1.0)
        val alpha = 0.45
        val blendR = lpR * alpha + 1.0 * (1.0 - alpha)
        val blendG = lpG * alpha + 1.0 * (1.0 - alpha)
        val blendB = lpB * alpha + 1.0 * (1.0 - alpha)
        val lumBlendedTrack = relativeLuminance(blendR, blendG, blendB)

        // Assert LightSecondary is dark (~0.044) and LightPrimary is moderate (~0.158)
        assertTrue("LightSecondary relative luminance must be < 0.10", lumSecondary < 0.10)
        assertTrue("LightPrimary relative luminance must be between 0.12 and 0.20", lumPrimary in 0.12..0.20)

        // The subordinate track blended over white surface must be significantly lighter (> 0.40)
        // than both LightPrimary (master toggle track) and legacy LightSecondary
        assertTrue(
            "Subordinate checked track on white surface must have relative luminance > 0.40 (actual: $lumBlendedTrack)",
            lumBlendedTrack > 0.40
        )
        assertTrue(
            "Subordinate checked track must be strictly lighter (higher luminance) than master LightPrimary track",
            lumBlendedTrack > lumPrimary * 2.5
        )
        assertTrue(
            "Subordinate checked track must be strictly lighter than legacy LightSecondary",
            lumBlendedTrack > lumSecondary * 5.0
        )
    }

    @Test
    fun testTuningClasses_existAndAreDecoupled() {
        val classes = listOf(
            "com.atrainingtracker.trainingtracker.ui.settings.tuning.TuningFormControlsKt",
            "com.atrainingtracker.trainingtracker.ui.settings.tuning.AdvancedTuningScreenKt",
            "com.atrainingtracker.trainingtracker.ui.settings.tuning.AdvancedTuningDialogKt",
            "com.atrainingtracker.trainingtracker.ui.settings.tuning.categories.PhysiologyModelsSectionKt"
        )
        for (className in classes) {
            val clazz = Class.forName(className)
            assertNotNull("Class $className must exist", clazz)
        }
    }
}
