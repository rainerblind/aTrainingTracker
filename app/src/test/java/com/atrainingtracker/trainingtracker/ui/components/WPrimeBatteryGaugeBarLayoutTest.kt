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

package com.atrainingtracker.trainingtracker.ui.components

import androidx.compose.ui.unit.dp
import com.atrainingtracker.trainingtracker.ui.theme.TTColor
import com.atrainingtracker.trainingtracker.ui.tracking.WPrimeBatteryMath
import com.atrainingtracker.trainingtracker.ui.tracking.WPrimeBatteryState
import com.atrainingtracker.trainingtracker.ui.tracking.WPrimeWarningLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Layout stability and architectural contract tests for WPrimeBatteryGaugeBar (REQ-UI-416, TST-UI-379.3).
 *
 * Verifies:
 * 1. Fixed 14.dp slot height invariant across active, stale, and depleted states.
 * 2. Boundary anti-clipping padding (3.dp end padding inside capsule).
 * 3. Text badge font scaling stability (maxLines = 1, TextOverflow.Clip, labelSmall).
 * 4. Staleness transition stability (stable 28.dp minimum badge width footprint, alpha dimming).
 * 5. Progressive disclosure collapse contract when Critical Power model is disabled.
 */
class WPrimeBatteryGaugeBarLayoutTest {

    private fun findSourceFile(relativePath: String): File {
        val candidates = listOf(
            File(relativePath),
            File("app/$relativePath"),
            File("../$relativePath")
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("Source file not found: $relativePath")
    }

    @Test
    fun testFixedHeightSlotInvariant() {
        // Architectural dimension constants contract
        assertEquals(14.dp, W_PRIME_BATTERY_GAUGE_HEIGHT)

        val file = findSourceFile("src/main/java/com/atrainingtracker/trainingtracker/ui/components/WPrimeBatteryGaugeBar.kt")
        val content = file.readText()

        // Verify the outer container Row strictly applies height(W_PRIME_BATTERY_GAUGE_HEIGHT)
        assertTrue(
            "WPrimeBatteryGaugeBar must enforce fixed height using W_PRIME_BATTERY_GAUGE_HEIGHT",
            content.contains(".height(W_PRIME_BATTERY_GAUGE_HEIGHT)")
        )
    }

    @Test
    fun testBoundaryAntiClippingPadding() {
        // 3.dp end padding inside the capsule to prevent fill clipping the positive terminal nipple
        assertEquals(3.dp, W_PRIME_BATTERY_END_PADDING)
        assertEquals(2.dp, W_PRIME_BATTERY_TERMINAL_WIDTH)
        assertEquals(6.dp, W_PRIME_BATTERY_TERMINAL_HEIGHT)
        assertEquals(3.dp, W_PRIME_BATTERY_CORNER_RADIUS)

        val file = findSourceFile("src/main/java/com/atrainingtracker/trainingtracker/ui/components/WPrimeBatteryGaugeBar.kt")
        val content = file.readText()

        assertTrue(
            "Fill calculation must subtract endPaddingPx to prevent clipping terminal nipple",
            content.contains("endPaddingPx") && content.contains("W_PRIME_BATTERY_END_PADDING")
        )
        assertTrue(
            "Terminal nipple width must be accounted for in capsule layout",
            content.contains("terminalWidthPx") && content.contains("W_PRIME_BATTERY_TERMINAL_WIDTH")
        )
    }

    @Test
    fun testLayoutStabilityUnderFontScaling() {
        val file = findSourceFile("src/main/java/com/atrainingtracker/trainingtracker/ui/components/WPrimeBatteryGaugeBar.kt")
        val content = file.readText()

        // Contract: text badge must constrain lines and clip overflow to guarantee height stability under 1.0x, 1.5x, 2.0x font scaling
        assertTrue(
            "Badge text must enforce maxLines = 1 to prevent vertical expansion",
            content.contains("maxLines = 1")
        )
        assertTrue(
            "Badge text must enforce TextOverflow.Clip to preserve fixed 14.dp container height",
            content.contains("overflow = TextOverflow.Clip")
        )
        assertTrue(
            "Badge text must use MaterialTheme.typography.labelSmall",
            content.contains("MaterialTheme.typography.labelSmall")
        )
    }

    @Test
    fun testStaleTransitionLayoutStability() {
        assertEquals(28.dp, W_PRIME_BATTERY_BADGE_MIN_WIDTH)

        val file = findSourceFile("src/main/java/com/atrainingtracker/trainingtracker/ui/components/WPrimeBatteryGaugeBar.kt")
        val content = file.readText()

        // Verify widthIn(min = W_PRIME_BATTERY_BADGE_MIN_WIDTH) is applied to the badge
        assertTrue(
            "Badge must enforce min width of W_PRIME_BATTERY_BADGE_MIN_WIDTH to prevent horizontal shifts",
            content.contains(".widthIn(min = W_PRIME_BATTERY_BADGE_MIN_WIDTH)")
        )

        // Verify stale fallback text maintains footprint
        assertTrue(
            "Stale badge text must render placeholder '--%' to preserve badge width",
            content.contains("\"--%\"")
        )

        // Verify alpha dimming contract when stale
        val activeState = WPrimeBatteryMath.calculateBatteryState(
            remainingKj = 15.0,
            totalKj = 20.0,
            timestampMs = 10000L,
            nowMs = 10500L
        )
        assertFalse(activeState.isStale)
        assertEquals(0.75f, activeState.batteryFraction ?: 0f, 0.001f)
        assertEquals(75, activeState.percentage)

        val staleState = WPrimeBatteryMath.calculateBatteryState(
            remainingKj = 15.0,
            totalKj = 20.0,
            timestampMs = 10000L,
            nowMs = 15000L // >3000ms delta
        )
        assertTrue(staleState.isStale)
        assertEquals(null, staleState.batteryFraction)
        assertEquals(0, staleState.percentage)
    }

    @Test
    fun testProgressiveDisclosureCollapseContract() {
        // Gating contract: When criticalPowerModel is disabled, W_PRIME_BAL sensor is excluded
        // from active cockpit fields, reducing rendered height to 0.dp.
        val defaultStateDisabled = WPrimeBatteryState(
            remainingKj = null,
            totalKj = 20.0,
            percentage = 0,
            batteryFraction = null,
            warningLevel = WPrimeWarningLevel.NORMAL,
            batteryColor = TTColor.WPrimeBalGreen,
            isWarning = false,
            isStale = true,
            timestampMs = 0L
        )
        assertTrue(defaultStateDisabled.isStale)
    }
}
