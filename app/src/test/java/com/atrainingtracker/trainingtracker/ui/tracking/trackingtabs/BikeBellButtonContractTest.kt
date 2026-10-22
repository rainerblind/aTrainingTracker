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
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0>.
 */

package com.atrainingtracker.trainingtracker.ui.tracking.trackingtabs

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Contract test suite verifying multi-mode availability and pointer gesture isolation
 * in BikeBellButton (REQ-UI-421.2, TST-UI-384.5, Governance Rule 53).
 */
class BikeBellButtonContractTest {

    private val projectRoot: File by lazy {
        var dir = File(System.getProperty("user.dir") ?: ".")
        while (!File(dir, "app").exists() && dir.parentFile != null) {
            dir = dir.parentFile!!
        }
        dir
    }

    private val bikeBellButtonFile: File by lazy {
        File(
            projectRoot,
            "app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/trackingtabs/BikeBellButton.kt"
        )
    }

    @Test
    fun testBikeBellButton_multiModeAvailability_rule53Compliance() {
        assertTrue("BikeBellButton.kt must exist", bikeBellButtonFile.exists())
        val content = bikeBellButtonFile.readText()

        // 1. Must evaluate availability across READY, TRACKING, and PAUSED modes (Rule 53)
        assertTrue(
            "BikeBellButton must define isAvailable checking READY, TRACKING, and PAUSED modes (Rule 53)",
            content.contains("val isAvailable = trackingMode in setOf(TrackingMode.READY, TrackingMode.TRACKING, TrackingMode.PAUSED)")
        )

        // 2. Must bind enabled property directly to isAvailable
        assertTrue(
            "BikeBellButton must set enabled = isAvailable on ElevatedButton",
            content.contains("enabled = isAvailable,")
        )

        // 3. Must set elevation based on isAvailable
        assertTrue(
            "BikeBellButton must set elevation based on isAvailable",
            content.contains("defaultElevation = if (isAvailable) 6.dp else 0.dp")
        )

        // 4. Must set icon tint based on isAvailable
        assertTrue(
            "BikeBellButton must tint icon based on isAvailable",
            content.contains("tint = if (isAvailable)")
        )
    }

    @Test
    fun testBikeBellButton_pointerGestureIsolation_rule53Compliance() {
        assertTrue("BikeBellButton.kt must exist", bikeBellButtonFile.exists())
        val content = bikeBellButtonFile.readText()

        // 1. Must NOT attach pointerInput or detectTapGestures to ElevatedButton (Rules 53 & 58)
        assertFalse(
            "BikeBellButton must NOT nest pointerInput or detectTapGestures on ElevatedButton (Rules 53 & 58)",
            content.contains("pointerInput") || content.contains("detectTapGestures")
        )

        // 2. Must handle single-tap triggers directly in ElevatedButton(onClick)
        assertTrue(
            "BikeBellButton must handle triggerShakeAnimation, haptic, and onSingleRing directly in onClick",
            content.contains("onClick = {") &&
                content.contains("if (isAvailable)") &&
                content.contains("triggerShakeAnimation()") &&
                content.contains("haptic.performHapticFeedback(HapticFeedbackType.LongPress)") &&
                content.contains("onSingleRing()")
        )
    }

    @Test
    fun testBikeBellButton_interactionSourcePressMonitoring_longPressContinuousRing_TST_UI_390_1() {
        assertTrue("BikeBellButton.kt must exist", bikeBellButtonFile.exists())
        val content = bikeBellButtonFile.readText()

        // 1. Must instantiate MutableInteractionSource and bind to ElevatedButton
        assertTrue(
            "BikeBellButton must instantiate MutableInteractionSource",
            content.contains("val interactionSource = remember { MutableInteractionSource() }")
        )
        assertTrue(
            "BikeBellButton must pass interactionSource to ElevatedButton",
            content.contains("interactionSource = interactionSource,")
        )

        // 2. Must monitor interactions in LaunchedEffect(interactionSource, isAvailable)
        assertTrue(
            "BikeBellButton must collect interactions in LaunchedEffect",
            content.contains("LaunchedEffect(interactionSource, isAvailable)") &&
                content.contains("interactionSource.interactions.collect")
        )

        // 3. Must evaluate system long-press timeout
        assertTrue(
            "BikeBellButton must evaluate viewConfiguration.longPressTimeoutMillis",
            content.contains("delay(viewConfiguration.longPressTimeoutMillis)")
        )

        // 4. Must invoke onContinuousRingStart and continuous animation on long press
        assertTrue(
            "BikeBellButton must invoke onContinuousRingStart on timer expiry",
            content.contains("onContinuousRingStart()") &&
                content.contains("isContinuousRingActive = true") &&
                content.contains("wasLongPressHandled = true")
        )
    }

    @Test
    fun testBikeBellButton_pressReleaseAndCancelTeardown_stopsContinuousRing_TST_UI_390_2() {
        assertTrue("BikeBellButton.kt must exist", bikeBellButtonFile.exists())
        val content = bikeBellButtonFile.readText()

        // 1. Release must stop continuous ring and reset rotation
        assertTrue(
            "BikeBellButton must stop continuous ring on PressInteraction.Release",
            content.contains("is PressInteraction.Release -> {") &&
                content.contains("onContinuousRingStop()")
        )

        // 2. Cancel must stop continuous ring
        assertTrue(
            "BikeBellButton must stop continuous ring on PressInteraction.Cancel",
            content.contains("is PressInteraction.Cancel -> {")
        )

        // 3. DisposableEffect must guarantee teardown on unmount
        assertTrue(
            "BikeBellButton must provide DisposableEffect teardown",
            content.contains("DisposableEffect(Unit)") &&
                content.contains("onDispose {")
        )
    }

    @Test
    fun testBikeBellButton_singleTapDisambiguation_suppressesSingleRingAfterLongPress_TST_UI_390_3() {
        assertTrue("BikeBellButton.kt must exist", bikeBellButtonFile.exists())
        val content = bikeBellButtonFile.readText()

        // 1. wasLongPressHandled must suppress onSingleRing() in onClick
        assertTrue(
            "BikeBellButton onClick must check wasLongPressHandled to suppress onSingleRing",
            content.contains("if (wasLongPressHandled) {") &&
                content.contains("wasLongPressHandled = false") &&
                content.contains("onSingleRing()")
        )
    }
}
