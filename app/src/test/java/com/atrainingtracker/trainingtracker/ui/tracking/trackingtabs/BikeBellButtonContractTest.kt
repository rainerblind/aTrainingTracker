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

        // 1. Must NOT attach pointerInput or detectTapGestures to ElevatedButton (Rule 53)
        assertFalse(
            "BikeBellButton must NOT nest pointerInput or detectTapGestures on ElevatedButton (Rule 53)",
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
}
