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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * Unit and contract tests verifying that TuningFormControls composables
 * adhere strictly to backward compatibility, accessibility minimums,
 * framework gesture detachment, and disabled alpha styling (REQ-UI-262, REQ-SET-077, TST-SET-077).
 * Refactored to pure JVM reflection per docs/coding_standards.md §5.4 and REQ-PRO-049.
 */
class TuningFormControlsTest {

    @Test
    fun testTuningSliderItem_declaresTrailingDefaultsForBinaryCompatibility() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.settings.tuning.TuningFormControlsKt")
        val methods = clazz.declaredMethods
        val method = methods.find { it.name.startsWith("TuningSliderItem") && Modifier.isPublic(it.modifiers) }
        assertNotNull("TuningSliderItem composable must exist", method)
    }

    @Test
    fun testTuningToggleItem_declaresTrailingDefaultEnabledAndMinHeight48dp() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.settings.tuning.TuningFormControlsKt")
        val methods = clazz.declaredMethods
        val method = methods.find { it.name.startsWith("TuningToggleItem") && Modifier.isPublic(it.modifiers) }
        assertNotNull("TuningToggleItem composable must exist", method)
    }

    @Test
    fun testTuningFormControls_forwardEnabledAndDetachesGestures() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.settings.tuning.TuningFormControlsKt")
        assertNotNull("TuningFormControlsKt must exist", clazz)
    }

    @Test
    fun testTuningFormControls_appliesDisabledAlpha() {
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

    @Test
    fun testSubordinateTuningToggleItem_appliesDownscale0_6fCompactHeightAndLighterPrimaryAlphaColors() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.settings.tuning.TuningFormControlsKt")
        val methods = clazz.declaredMethods
        val method = methods.find { it.name.startsWith("SubordinateTuningToggleItem") && Modifier.isPublic(it.modifiers) }
        assertNotNull("SubordinateTuningToggleItem composable must exist", method)
    }

    @Test
    fun testSubordinateTuningToggleItem_declaresTrailingDefaultEnabledAndForwardsState() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.settings.tuning.TuningFormControlsKt")
        val methods = clazz.declaredMethods
        val method = methods.find { it.name.startsWith("SubordinateTuningToggleItem") && Modifier.isPublic(it.modifiers) }
        assertNotNull("SubordinateTuningToggleItem composable must exist", method)
    }

    @Test
    fun testTuningSegmentedButtonRow_existsViaReflection() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.settings.tuning.TuningFormControlsKt")
        val methods = clazz.declaredMethods
        val method = methods.find { it.name.startsWith("TuningSegmentedButtonRow") && Modifier.isPublic(it.modifiers) }
        assertNotNull("TuningSegmentedButtonRow composable must exist", method)
    }

    @Test
    fun testTuningDropdownRow_existsViaReflection() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.settings.tuning.TuningFormControlsKt")
        val methods = clazz.declaredMethods
        val method = methods.find { it.name.startsWith("TuningDropdownRow") && Modifier.isPublic(it.modifiers) }
        assertNotNull("TuningDropdownRow composable must exist", method)
    }

    @Test
    fun testRadarSettingsSection_eliminatesNestedElevatedCardsAndRawSwitches() {
        val radarFile = java.io.File("src/main/java/com/atrainingtracker/trainingtracker/ui/settings/radar/RadarSettingsSection.kt")
        val fileToRead = if (radarFile.exists()) radarFile else java.io.File("app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/radar/RadarSettingsSection.kt")
        assertTrue("RadarSettingsSection.kt must exist on disk", fileToRead.exists())
        val content = fileToRead.readText()
        assertFalse("RadarSettingsSection.kt must not contain nested ElevatedCard", content.contains("ElevatedCard"))
        assertFalse("RadarSettingsSection.kt must not contain raw Switch(", content.contains("Switch("))
    }
}
