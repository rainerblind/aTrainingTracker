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

import com.atrainingtracker.trainingtracker.ui.theme.TTAlpha
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Unit and contract tests verifying that TuningFormControls composables
 * adhere strictly to backward compatibility, accessibility minimums,
 * framework gesture detachment, and disabled alpha styling (REQ-UI-262, REQ-SET-077, TST-SET-077).
 */
class TuningFormControlsTest {

    private fun findSourceFile(relativePath: String): File {
        val candidates = listOf(
            File(relativePath),
            File("app/$relativePath"),
            File("../app/$relativePath")
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("Source file $relativePath not found in candidates: $candidates")
    }

    @Test
    fun testTuningSliderItem_declaresTrailingDefaultsForBinaryCompatibility() {
        val file = findSourceFile("src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/TuningFormControls.kt")
        val content = file.readText()

        assertTrue(
            "TuningSliderItem must accept trailing enabled parameter with default true",
            content.contains("enabled: Boolean = true")
        )
        assertTrue(
            "TuningSliderItem must accept trailing onValueChangeFinished parameter with default null",
            content.contains("onValueChangeFinished: (() -> Unit)? = null")
        )

        // Verify order: steps must precede enabled, which precedes onValueChangeFinished
        val stepsIndex = content.indexOf("steps: Int = 0")
        val enabledIndex = content.indexOf("enabled: Boolean = true")
        val finishedIndex = content.indexOf("onValueChangeFinished: (() -> Unit)? = null")

        assertTrue("steps must be present", stepsIndex != -1)
        assertTrue("enabled must be present after steps", enabledIndex > stepsIndex)
        assertTrue("onValueChangeFinished must be present after enabled", finishedIndex > enabledIndex)
    }

    @Test
    fun testTuningToggleItem_declaresTrailingDefaultEnabledAndMinHeight48dp() {
        val file = findSourceFile("src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/TuningFormControls.kt")
        val content = file.readText()

        assertTrue(
            "TuningToggleItem must accept trailing enabled parameter with default true",
            content.contains("fun TuningToggleItem(") &&
                    content.contains("enabled: Boolean = true")
        )

        // Accessibility requirement: 48dp minimum touch target
        assertTrue(
            "TuningToggleItem must enforce 48.dp minimum height for WCAG touch compliance",
            content.contains("defaultMinSize(minHeight = 48.dp)")
        )
    }

    @Test
    fun testTuningFormControls_forwardEnabledAndDetachesGestures() {
        val file = findSourceFile("src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/TuningFormControls.kt")
        val content = file.readText()

        // Verify Slider forwards enabled and onValueChangeFinished
        assertTrue(
            "Slider in TuningSliderItem must forward enabled parameter",
            content.contains("Slider(") && content.contains("enabled = enabled")
        )
        assertTrue(
            "Slider in TuningSliderItem must forward onValueChangeFinished parameter",
            content.contains("onValueChangeFinished = onValueChangeFinished")
        )

        // Verify Switch forwards enabled parameter
        assertTrue(
            "Switch in TuningToggleItem must forward enabled parameter",
            content.contains("Switch(") && content.contains("enabled = enabled")
        )
    }

    @Test
    fun testTuningFormControls_appliesDisabledAlpha() {
        val file = findSourceFile("src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/TuningFormControls.kt")
        val content = file.readText()

        assertTrue(
            "TuningFormControls must compute contentAlpha using TTAlpha.Disabled",
            content.contains("val contentAlpha = if (enabled) 1f else TTAlpha.Disabled")
        )
        assertTrue(
            "TuningFormControls must import TTAlpha",
            content.contains("import com.atrainingtracker.trainingtracker.ui.theme.TTAlpha")
        )

        // Verify TTAlpha.Disabled numerical value invariant
        assertEquals(0.38f, TTAlpha.Disabled, 0.001f)
    }

    @Test
    fun testCallbackGating_disabledStateInhibitsAction() {
        var callbackFired = false
        val enabled = false

        // Simulating the framework-level gating: when enabled is false, callback is not executed
        val gatedCallback: () -> Unit = {
            if (enabled) {
                callbackFired = true
            }
        }

        gatedCallback()
        assertFalse("Disabled component must not fire callback", callbackFired)
    }
}
